package opensource.cached_dupe_scanner.engine

import opensource.cached_dupe_scanner.cache.CacheStatus
import opensource.cached_dupe_scanner.cache.CacheStore
import opensource.cached_dupe_scanner.core.DuplicateGroup
import opensource.cached_dupe_scanner.core.FileMetadata
import opensource.cached_dupe_scanner.core.ScanCacheSnapshot
import opensource.cached_dupe_scanner.core.ScanResult
import opensource.cached_dupe_scanner.core.sanitizeScanWorkerCount
import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorCompletionService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class IncrementalScanner(
    private val cacheStore: CacheStore,
    private val fileHasher: FileHasher = Sha256FileHasher(),
    private val fileWalker: FileWalker = FileWalker(),
    private val workerCountProvider: () -> Int = { 1 }
) {
    fun scan(
        root: File,
        ignore: (File) -> Boolean = { false },
        skipZeroSizeInDb: Boolean = false,
        onProgress: (scanned: Int, total: Int?, current: FileMetadata, phase: ScanPhase) -> Unit = { _, _, _, _ -> },
        shouldContinue: () -> Boolean = { true }
    ): ScanResult {
        val scannedAtMillis = System.currentTimeMillis()
        val workerCount = sanitizeScanWorkerCount(workerCountProvider())
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

        val lookupByPath = uniqueScanned.associateBy({ it.normalizedPath }) { cacheStore.lookup(it) }
        val cacheSnapshots = linkedMapOf<String, ScanCacheSnapshot>()
        lookupByPath.forEach { (normalizedPath, lookup) ->
            cacheSnapshots[normalizedPath] = ScanCacheSnapshot(
                normalizedPath = normalizedPath,
                previous = lookup.cached
            )
        }
        val candidates = uniqueScanned.filter {
            val size = it.sizeBytes
            if (size == 0L && !includeZeroSize) return@filter false
            if ((sizeCounts[size] ?: 0) > 1) return@filter true
            val cachedCount = cachedSizeCounts[size] ?: 0
            if (cachedCount > 1) return@filter true
            if (cachedCount == 1 && lookupByPath[it.normalizedPath]?.status == CacheStatus.MISS) return@filter true
            false
        }
        val missingCachedCandidates = cacheStore.missingHashCandidatesBySizes(
            candidates.map { it.sizeBytes }.toSet()
        ).filter { cached ->
            uniqueScanned.none { it.normalizedPath == cached.normalizedPath }
        }
        missingCachedCandidates.forEach { cached ->
            cacheSnapshots.putIfAbsent(
                cached.normalizedPath,
                ScanCacheSnapshot(
                    normalizedPath = cached.normalizedPath,
                    previous = cached
                )
            )
        }
        val candidatePaths = candidates.map { it.normalizedPath }.toSet()
        val scannedHashTargets = candidates.filter {
            val cached = lookupByPath[it.normalizedPath]
            cached == null ||
                cached.status != CacheStatus.FRESH ||
                cached.cached?.hashHex == null
        }
        val hashWorkItems = scannedHashTargets.map { metadata ->
            HashWorkItem(metadata = metadata, repairCachedEntry = false)
        } + missingCachedCandidates.map { metadata ->
            HashWorkItem(metadata = metadata, repairCachedEntry = true)
        }
        val hashBatch = hashFiles(
            workItems = hashWorkItems,
            workerCount = workerCount,
            shouldContinue = shouldContinue,
            onCompleted = { processed, total, current ->
                onProgress(processed, total, current, ScanPhase.Hashing)
            }
        ) ?: return ScanResult(
            scannedAtMillis = scannedAtMillis,
            files = emptyList(),
            duplicateGroups = emptyList()
        )

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
                    else -> hashBatch.hashesByPath[current.normalizedPath]
                        ?: return ScanResult(
                            scannedAtMillis = scannedAtMillis,
                            files = emptyList(),
                            duplicateGroups = emptyList()
                        )
                }
                current.copy(hashHex = hashHex)
            } else {
                current.copy(hashHex = null)
            }

            files.add(finalMetadata)
            pending.add(finalMetadata)
            // progress for hashing is reported only when actual hashing occurs
        }
        pending.addAll(hashBatch.repairedCandidates)

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
            duplicateGroups = duplicateGroups,
            cacheSnapshots = cacheSnapshots.values.toList()
        )
    }

    private fun hashFiles(
        workItems: List<HashWorkItem>,
        workerCount: Int,
        shouldContinue: () -> Boolean,
        onCompleted: (processed: Int, total: Int, current: FileMetadata) -> Unit
    ): HashBatchResult? {
        if (workItems.isEmpty()) return HashBatchResult(emptyMap(), emptyList())
        val concurrency = workerCount.coerceAtMost(workItems.size)
        val threadIndex = AtomicInteger(0)
        val executor = Executors.newFixedThreadPool(concurrency) { runnable ->
            Thread(
                runnable,
                "CachedDupeScanner-Hash-${threadIndex.incrementAndGet()}"
            ).apply {
                isDaemon = true
            }
        }
        val completionService = ExecutorCompletionService<HashWorkResult>(executor)
        val hashesByPath = linkedMapOf<String, String>()
        val repairedCandidates = mutableListOf<FileMetadata>()
        var nextIndex = 0
        var inFlight = 0
        var processed = 0

        fun submitNext() {
            val workItem = workItems[nextIndex]
            nextIndex += 1
            inFlight += 1
            completionService.submit(
                Callable {
                    hashWorkItem(
                        workItem = workItem,
                        shouldContinue = shouldContinue
                    )
                }
            )
        }

        repeat(concurrency) { submitNext() }
        try {
            while (inFlight > 0) {
                if (!shouldContinue()) return null
                val completedFuture = try {
                    completionService.poll(HASH_COMPLETION_POLL_MILLIS, TimeUnit.MILLISECONDS)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return null
                } ?: continue
                inFlight -= 1
                val result = completedFuture.get()
                result.failure?.let { failure -> throw failure }
                if (result.skipped) {
                    if (nextIndex < workItems.size) {
                        if (!shouldContinue()) return null
                        submitNext()
                    }
                    continue
                }
                if (!shouldContinue()) return null
                val hashHex = result.hashHex ?: return null
                val metadata = result.metadata ?: return null
                if (result.workItem.repairCachedEntry) {
                    repairedCandidates += metadata
                } else {
                    hashesByPath[result.workItem.metadata.normalizedPath] = hashHex
                }
                processed += 1
                onCompleted(processed, workItems.size, metadata)
                if (nextIndex < workItems.size) {
                    if (!shouldContinue()) return null
                    submitNext()
                }
            }
        } finally {
            executor.shutdownNow()
            try {
                executor.awaitTermination(HASH_EXECUTOR_SHUTDOWN_SECONDS, TimeUnit.SECONDS)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
        return HashBatchResult(
            hashesByPath = hashesByPath,
            repairedCandidates = repairedCandidates
        )
    }

    private fun hashWorkItem(
        workItem: HashWorkItem,
        shouldContinue: () -> Boolean
    ): HashWorkResult {
        if (!shouldContinue()) return HashWorkResult(workItem = workItem)
        val file = File(workItem.metadata.path)
        if (workItem.repairCachedEntry && !file.exists()) {
            return HashWorkResult(workItem = workItem, skipped = true)
        }
        return try {
            val hashHex = fileHasher.hash(file, shouldContinue)
            if (hashHex == null) {
                HashWorkResult(
                    workItem = workItem,
                    skipped = workItem.repairCachedEntry && shouldContinue()
                )
            } else {
                val metadata = if (workItem.repairCachedEntry) {
                    workItem.metadata.copy(
                        sizeBytes = file.length(),
                        lastModifiedMillis = file.lastModified(),
                        hashHex = hashHex
                    )
                } else {
                    workItem.metadata.copy(hashHex = hashHex)
                }
                HashWorkResult(
                    workItem = workItem,
                    hashHex = hashHex,
                    metadata = metadata
                )
            }
        } catch (error: Exception) {
            if (workItem.repairCachedEntry) {
                HashWorkResult(workItem = workItem, skipped = true)
            } else {
                HashWorkResult(workItem = workItem, failure = error)
            }
        }
    }
}

private data class HashWorkItem(
    val metadata: FileMetadata,
    val repairCachedEntry: Boolean
)

private data class HashWorkResult(
    val workItem: HashWorkItem,
    val hashHex: String? = null,
    val metadata: FileMetadata? = null,
    val skipped: Boolean = false,
    val failure: Exception? = null
)

private data class HashBatchResult(
    val hashesByPath: Map<String, String>,
    val repairedCandidates: List<FileMetadata>
)

private const val HASH_COMPLETION_POLL_MILLIS = 100L
private const val HASH_EXECUTOR_SHUTDOWN_SECONDS = 5L
