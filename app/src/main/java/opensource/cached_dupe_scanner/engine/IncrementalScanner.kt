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

        fileWalker.walk(root, ignore).forEach { file ->
            val current = FileMetadata.fromFile(file)
            val cached = cacheStore.lookup(current)
            val hashHex = when (cached.status) {
                CacheStatus.FRESH -> cached.cached?.hashHex ?: fileHasher.hash(file)
                CacheStatus.STALE -> fileHasher.hash(file)
                CacheStatus.MISS -> fileHasher.hash(file)
            }

            val finalMetadata = current.copy(hashHex = hashHex)
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
