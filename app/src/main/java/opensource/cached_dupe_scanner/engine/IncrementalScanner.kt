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

        val totalDetect = uniqueScanned.size
        var detectCount = 0
        val pending = mutableListOf<FileMetadata>()
        val includeZeroSize = !skipZeroSizeInDb
        val sizeCounts = uniqueScanned
            .filter { includeZeroSize || it.sizeBytes > 0L }
            .groupingBy { it.sizeBytes }
            .eachCount()
        val cachedSizeCounts = cacheStore.countBySizes(sizeCounts.keys)
        val cachedOverlapCounts = cacheStore.countBySizesForPaths(
            uniqueScanned.map { it.normalizedPath }.toSet()
        )

        uniqueScanned.forEach { current ->
            if (!shouldContinue()) {
                return ScanResult(
                    scannedAtMillis = scannedAtMillis,
                    files = emptyList(),
                    duplicateGroups = emptyList()
                )
            }
            if (current.sizeBytes == 0L) {
                val finalMetadata = current.copy(hashHex = null)
                files.add(finalMetadata)
                if (!skipZeroSizeInDb) {
                    pending.add(finalMetadata)
                }
                detectCount += 1
                onProgress(detectCount, totalDetect, finalMetadata, ScanPhase.Collecting)
                return@forEach
            }
            detectCount += 1
            onProgress(detectCount, totalDetect, current, ScanPhase.Detecting)
        }

        val candidates = uniqueScanned.filter {
            val size = it.sizeBytes
            if (size == 0L && !includeZeroSize) return@filter false
            if ((sizeCounts[size] ?: 0) > 1) return@filter true
            if ((cachedSizeCounts[size] ?: 0) > (cachedOverlapCounts[size] ?: 0)) return@filter true
            false
        }
        val candidatePaths = candidates.map { it.normalizedPath }.toSet()
        val lookupByPath = candidates.associateBy({ it.normalizedPath }) { cacheStore.lookup(it) }
        val hashTargets = candidates.filter {
            val cached = lookupByPath[it.normalizedPath]
            cached == null ||
                cached.status != CacheStatus.FRESH ||
                cached.cached?.hashHex == null
        }.toSet()
        val totalHash = hashTargets.size
        var hashCount = 0

        uniqueScanned.forEach { current ->
            if (!shouldContinue()) {
                return ScanResult(
                    scannedAtMillis = scannedAtMillis,
                    files = files,
                    duplicateGroups = emptyList()
                )
            }
            val finalMetadata = if (candidatePaths.contains(current.normalizedPath)) {
                val cached = lookupByPath[current.normalizedPath]
                val hashHex = when {
                    cached?.status == CacheStatus.FRESH && cached.cached?.hashHex != null -> {
                        cached.cached.hashHex
                    }
                    else -> {
                        hashCount += 1
                        val computed = fileHasher.hash(File(current.path))
                        onProgress(hashCount, totalHash, current, ScanPhase.Hashing)
                        computed
                    }
                }
                current.copy(hashHex = hashHex)
            } else {
                current.copy(hashHex = null)
            }

            files.add(finalMetadata)
            pending.add(finalMetadata)
            // progress for hashing is reported only when actual hashing occurs
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
        val totalSave = toStore.size
        if (totalSave > 0) {
            var saved = 0
            toStore.chunked(500).forEach { batch ->
                if (!shouldContinue()) {
                    return ScanResult(
                        scannedAtMillis = scannedAtMillis,
                        files = files,
                        duplicateGroups = emptyList()
                    )
                }
                cacheStore.upsertAll(batch)
                saved += batch.size
                val current = batch.last()
                onProgress(saved, totalSave, current, ScanPhase.Saving)
            }
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
