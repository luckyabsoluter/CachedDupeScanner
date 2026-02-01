package opensource.cached_dupe_scanner.engine

import opensource.cached_dupe_scanner.cache.CacheStatus
import opensource.cached_dupe_scanner.cache.CacheStore
import opensource.cached_dupe_scanner.core.DuplicateGroup
import opensource.cached_dupe_scanner.core.FileMetadata
import opensource.cached_dupe_scanner.core.ScanResult
import java.io.File

class IncrementalScanner(
    private val cacheStore: CacheStore,
    private val fileHasher: FileHasher = Sha256FileHasher(),
    private val fileWalker: FileWalker = FileWalker()
) {
    fun scan(
        root: File,
        ignore: (File) -> Boolean = { false },
        skipZeroSizeInDb: Boolean = false,
        onProgress: (scanned: Int, total: Int?, current: FileMetadata, phase: ScanPhase) -> Unit = { _, _, _, _ -> },
        shouldContinue: () -> Boolean = { true }
    ): ScanResult {
        val scannedAtMillis = System.currentTimeMillis()
        val files = mutableListOf<FileMetadata>()

        val scanned = mutableListOf<FileMetadata>()
        var discovered = 0
        fileWalker.walk(
            root,
            ignore,
            onFile = { file ->
                val metadata = FileMetadata.fromFile(file)
                scanned.add(metadata)
                discovered += 1
                onProgress(discovered, null, metadata, ScanPhase.Collecting)
            },
            shouldContinue = shouldContinue
        )

        if (!shouldContinue()) {
            return ScanResult(
                scannedAtMillis = scannedAtMillis,
                files = emptyList(),
                duplicateGroups = emptyList()
            )
        }

        val uniqueScanned = scanned.distinctBy { it.normalizedPath }

        val bySize = uniqueScanned.groupBy { it.sizeBytes }
        val needsHash = bySize.filterValues { it.size > 1 }.values.flatten().toSet()
        val total = uniqueScanned.size
        var scannedCount = 0
        val pending = mutableListOf<FileMetadata>()

        uniqueScanned.forEach { current ->
            if (!shouldContinue()) {
                return ScanResult(
                    scannedAtMillis = scannedAtMillis,
                    files = files,
                    duplicateGroups = emptyList()
                )
            }
            val finalMetadata = if (needsHash.contains(current)) {
                val cached = cacheStore.lookup(current)
                val hashHex = when (cached.status) {
                    CacheStatus.FRESH -> cached.cached?.hashHex ?: fileHasher.hash(File(current.path))
                    CacheStatus.STALE -> fileHasher.hash(File(current.path))
                    CacheStatus.MISS -> fileHasher.hash(File(current.path))
                }
                current.copy(hashHex = hashHex)
            } else {
                current.copy(hashHex = null)
            }

            files.add(finalMetadata)
            pending.add(finalMetadata)
            scannedCount += 1
            onProgress(scannedCount, total, finalMetadata, ScanPhase.Hashing)
        }

        if (!shouldContinue()) {
            return ScanResult(
                scannedAtMillis = scannedAtMillis,
                files = emptyList(),
                duplicateGroups = emptyList()
            )
        }

        val toStore = if (skipZeroSizeInDb) {
            pending.filter { it.sizeBytes > 0 }
        } else {
            pending
        }
        cacheStore.upsertAll(toStore)

        val duplicateGroups = files
            .filter { it.hashHex != null }
            .groupBy { it.hashHex!! }
            .filterValues { it.size > 1 }
            .map { (hash, groupFiles) ->
                DuplicateGroup(hash, groupFiles)
            }

        return ScanResult(
            scannedAtMillis = scannedAtMillis,
            files = files,
            duplicateGroups = duplicateGroups
        )
    }
}
