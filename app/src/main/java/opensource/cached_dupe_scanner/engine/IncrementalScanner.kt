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
        skipZeroSizeInDb: Boolean = false
    ): ScanResult {
        val scannedAtMillis = System.currentTimeMillis()
        val files = mutableListOf<FileMetadata>()

        val scanned = fileWalker.walk(root, ignore)
            .map { FileMetadata.fromFile(it) }
            .toList()

        val bySize = scanned.groupBy { it.sizeBytes }
        val needsHash = bySize.filterValues { it.size > 1 }.values.flatten().toSet()

        scanned.forEach { current ->
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

            if (!skipZeroSizeInDb || finalMetadata.sizeBytes > 0) {
                cacheStore.upsert(finalMetadata)
            }
            files.add(finalMetadata)
        }

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
