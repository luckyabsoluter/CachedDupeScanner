package opensource.cached_dupe_scanner.storage

import opensource.cached_dupe_scanner.cache.CacheDatabase
import opensource.cached_dupe_scanner.cache.CachedFileEntity
import opensource.cached_dupe_scanner.cache.FileCacheDao
import opensource.cached_dupe_scanner.cache.SimilarityClusterEntity
import opensource.cached_dupe_scanner.cache.SimilarityDurationCandidateEntity
import opensource.cached_dupe_scanner.cache.SimilarityExperimentDao
import opensource.cached_dupe_scanner.cache.SimilarityExperimentRunEntity
import opensource.cached_dupe_scanner.core.AndroidVideoDurationExtractor
import opensource.cached_dupe_scanner.core.AndroidVideoFrameSignatureExtractor
import opensource.cached_dupe_scanner.core.DurationNeighborListStep
import opensource.cached_dupe_scanner.core.DurationToleranceStep
import opensource.cached_dupe_scanner.core.ExactThumbnailHashStep
import opensource.cached_dupe_scanner.core.FileMetadata
import opensource.cached_dupe_scanner.core.SimilarityExperimentSpec
import opensource.cached_dupe_scanner.core.SimilarityMediaScope
import opensource.cached_dupe_scanner.core.VideoDurationExtractor
import opensource.cached_dupe_scanner.core.VideoFrameSignatureExtractor
import opensource.cached_dupe_scanner.core.buildDurationNeighborListSignature
import opensource.cached_dupe_scanner.core.buildDurationToleranceSignature
import opensource.cached_dupe_scanner.core.durationNeighborToleranceMillis
import opensource.cached_dupe_scanner.core.durationToleranceMillis
import java.io.File

data class SimilarityExperimentProgress(
    val total: Int,
    val processed: Int,
    val skipped: Int,
    val clusterCandidates: Int,
    val currentPath: String?
)

data class SimilarityExperimentSummary(
    val experimentId: String,
    val candidateCount: Int,
    val processedCount: Int,
    val skippedCount: Int,
    val clusterCount: Int,
    val duplicateFileCount: Int,
    val cancelled: Boolean
)

data class SimilarityExperimentRunRequest(
    val experiment: SimilarityExperimentSpec,
    val mediaScope: SimilarityMediaScope,
    val minSizeBytes: Long,
    val exactThumbnailStep: ExactThumbnailHashStep? = null,
    val durationToleranceStep: DurationToleranceStep? = null,
    val durationNeighborListStep: DurationNeighborListStep? = null
)

data class SimilarityClusterMember(
    val metadata: FileMetadata,
    val durationMillis: Long?
)

class SimilarityExperimentRepository(
    private val database: CacheDatabase,
    private val fileDao: FileCacheDao,
    private val experimentDao: SimilarityExperimentDao,
    private val frameSignatureExtractor: VideoFrameSignatureExtractor = AndroidVideoFrameSignatureExtractor(),
    private val durationExtractor: VideoDurationExtractor = AndroidVideoDurationExtractor()
) {
    fun countCandidates(
        mediaScope: SimilarityMediaScope,
        minSizeBytes: Long
    ): Int {
        return when (mediaScope) {
            SimilarityMediaScope.Video -> fileDao.countVideoCandidates(minSizeBytes)
            SimilarityMediaScope.Image -> fileDao.countImageCandidates(minSizeBytes)
        }
    }

    fun listRuns(): List<SimilarityExperimentRunEntity> {
        return experimentDao.listRuns()
    }

    fun countDurationCandidates(experimentId: String): Int {
        return experimentDao.countDurationCandidates(experimentId)
    }

    fun listClusters(experimentId: String): List<SimilarityClusterEntity> {
        val clusters = experimentDao.listClusters(experimentId)
        if (!experimentId.startsWith(DURATION_NEIGHBOR_EXPERIMENT_ID_PREFIX)) {
            return clusters
        }
        return clusters.sortedBy { cluster -> durationNeighborSortMillis(cluster.signature) }
    }

    fun listClusterMembers(
        cluster: SimilarityClusterEntity,
        limit: Int? = null
    ): List<FileMetadata> {
        return listClusterMemberRows(
            cluster = cluster,
            limit = limit
        ).map { member -> member.metadata }
    }

    fun listClusterMemberRows(
        cluster: SimilarityClusterEntity,
        limit: Int? = null
    ): List<SimilarityClusterMember> {
        val entries = parseSimilarityClusterMemberEntries(cluster.memberNormalizedPathsText)
            .let { parsed ->
                if (limit == null) parsed else parsed.take(limit.coerceAtLeast(0))
            }
        if (entries.isEmpty()) return emptyList()

        val paths = entries.map { entry -> entry.normalizedPath }
        val entryByPath = entries.associateBy { entry -> entry.normalizedPath }
        val membersByPath = linkedMapOf<String, FileMetadata>()
        paths
            .chunked(SIMILARITY_CLUSTER_MEMBER_LOOKUP_CHUNK_SIZE)
            .flatMap { chunk -> fileDao.findByNormalizedOrDisplayPaths(chunk) }
            .forEach { entity ->
                val metadata = entity.toMetadata()
                membersByPath[entity.normalizedPath] = metadata
                if (entity.path.isNotBlank()) {
                    membersByPath[entity.path] = metadata
                }
            }
        return paths.mapNotNull { path ->
            membersByPath[path]?.let { metadata ->
                val storedDurationMillis = entryByPath[path]?.durationMillis
                SimilarityClusterMember(
                    metadata = metadata,
                    durationMillis = storedDurationMillis ?: fallbackDurationMillis(
                        cluster = cluster,
                        metadata = metadata
                    )
                )
            }
        }
    }

    private fun fallbackDurationMillis(
        cluster: SimilarityClusterEntity,
        metadata: FileMetadata
    ): Long? {
        if (!isDurationSimilaritySignature(cluster.signature)) return null
        val path = metadata.path.ifBlank { metadata.normalizedPath }
        val file = File(path)
        if (!file.exists()) return null
        return durationExtractor.durationMillis(
            file = file,
            shouldContinue = { true }
        )
    }

    fun runExactThumbnailHashExperiment(
        request: SimilarityExperimentRunRequest,
        shouldContinue: () -> Boolean,
        onProgress: (SimilarityExperimentProgress) -> Unit
    ): SimilarityExperimentSummary {
        val experiment = request.experiment
        val exactStep = requireNotNull(request.exactThumbnailStep) {
            "Exact thumbnail step is required for exact thumbnail hash experiments."
        }
        val candidateCount = countCandidates(
            mediaScope = request.mediaScope,
            minSizeBytes = request.minSizeBytes
        )
        val startedAt = System.currentTimeMillis()
        val signatureGroups = linkedMapOf<String, MutableList<CachedFileEntity>>()
        var processed = 0
        var skipped = 0
        var afterPath = ""
        var currentPath: String? = null

        while (shouldContinue()) {
            val batch = listCandidatesAfter(
                mediaScope = request.mediaScope,
                minSizeBytes = request.minSizeBytes,
                afterPath = afterPath,
                limit = SIMILARITY_EXPERIMENT_BATCH_SIZE
            )
            if (batch.isEmpty()) {
                break
            }

            for (entity in batch) {
                if (!shouldContinue()) {
                    return summaryWithoutSaving(
                        experimentId = experiment.id,
                        candidateCount = candidateCount,
                        processed = processed,
                        skipped = skipped,
                        groups = signatureGroups,
                        cancelled = true
                    )
                }

                currentPath = entity.path.ifBlank { entity.normalizedPath }
                val file = File(currentPath)
                val signature = if (file.exists()) {
                    frameSignatureExtractor.signature(
                        file = file,
                        mediaScope = request.mediaScope,
                        step = exactStep,
                        shouldContinue = shouldContinue
                    )
                } else {
                    null
                }
                if (signature == null) {
                    skipped += 1
                } else {
                    signatureGroups.getOrPut(signature) { mutableListOf() }.add(entity)
                }
                processed += 1
                afterPath = entity.normalizedPath
                onProgress(
                    SimilarityExperimentProgress(
                        total = candidateCount,
                        processed = processed,
                        skipped = skipped,
                        clusterCandidates = signatureGroups.count { (_, members) -> members.size > 1 },
                        currentPath = currentPath
                    )
                )
            }

            if (batch.size < SIMILARITY_EXPERIMENT_BATCH_SIZE) {
                break
            }
        }

        val finishedAt = System.currentTimeMillis()
        val clusters = signatureGroups
            .filter { (_, members) -> members.size > 1 }
            .map { (signature, members) ->
                SimilarityClusterEntity(
                    experimentId = experiment.id,
                    signature = signature,
                    fileCount = members.size,
                    totalBytes = members.sumOf { it.sizeBytes },
                    memberNormalizedPathsText = members.joinToString("\n") { member -> member.normalizedPath },
                    updatedAtMillis = finishedAt
                )
            }
        val duplicateFileCount = clusters.sumOf { it.fileCount }
        val run = SimilarityExperimentRunEntity(
            experimentId = experiment.id,
            experimentName = experiment.name,
            startedAtMillis = startedAt,
            finishedAtMillis = finishedAt,
            candidateCount = candidateCount,
            processedCount = processed,
            skippedCount = skipped,
            clusterCount = clusters.size,
            duplicateFileCount = duplicateFileCount
        )
        database.runInTransaction {
            experimentDao.deleteDurationCandidates(experiment.id)
            experimentDao.deleteClusters(experiment.id)
            experimentDao.deleteRun(experiment.id)
            experimentDao.upsertRun(run)
            clusters.chunked(SIMILARITY_CLUSTER_INSERT_CHUNK_SIZE).forEach { chunk ->
                experimentDao.insertClusters(chunk)
            }
        }

        return SimilarityExperimentSummary(
            experimentId = experiment.id,
            candidateCount = candidateCount,
            processedCount = processed,
            skippedCount = skipped,
            clusterCount = clusters.size,
            duplicateFileCount = duplicateFileCount,
            cancelled = false
        )
    }

    fun runDurationToleranceExperiment(
        request: SimilarityExperimentRunRequest,
        shouldContinue: () -> Boolean,
        onProgress: (SimilarityExperimentProgress) -> Unit
    ): SimilarityExperimentSummary {
        val experiment = request.experiment
        val durationStep = requireNotNull(request.durationToleranceStep) {
            "Duration tolerance step is required for duration experiments."
        }
        val toleranceMillis = durationToleranceMillis(durationStep)
        val candidateCount = countCandidates(
            mediaScope = request.mediaScope,
            minSizeBytes = request.minSizeBytes
        )
        val startedAt = System.currentTimeMillis()
        val durationCandidates = mutableListOf<DurationCandidate>()
        val progressBuckets = mutableMapOf<Long, Int>()
        var progressClusterCandidates = 0
        var processed = 0
        var skipped = 0
        var afterPath = ""
        var currentPath: String? = null

        while (shouldContinue()) {
            val batch = listCandidatesAfter(
                mediaScope = request.mediaScope,
                minSizeBytes = request.minSizeBytes,
                afterPath = afterPath,
                limit = SIMILARITY_EXPERIMENT_BATCH_SIZE
            )
            if (batch.isEmpty()) {
                break
            }

            for (entity in batch) {
                if (!shouldContinue()) {
                    return durationSummaryWithoutSaving(
                        experimentId = experiment.id,
                        candidateCount = candidateCount,
                        processed = processed,
                        skipped = skipped,
                        candidates = durationCandidates,
                        toleranceMillis = toleranceMillis,
                        durationStep = durationStep,
                        cancelled = true
                    )
                }

                currentPath = entity.path.ifBlank { entity.normalizedPath }
                val file = File(currentPath)
                val durationMillis = if (file.exists()) {
                    durationExtractor.durationMillis(
                        file = file,
                        shouldContinue = shouldContinue
                    )
                } else {
                    null
                }
                if (durationMillis == null) {
                    skipped += 1
                } else {
                    durationCandidates += DurationCandidate(
                        entity = entity,
                        durationMillis = durationMillis
                    )
                    val bucket = durationProgressBucket(
                        durationMillis = durationMillis,
                        toleranceMillis = toleranceMillis
                    )
                    val previousBucketCount = progressBuckets[bucket] ?: 0
                    progressBuckets[bucket] = previousBucketCount + 1
                    if (previousBucketCount == 1) {
                        progressClusterCandidates += 1
                    }
                }
                processed += 1
                afterPath = entity.normalizedPath
                onProgress(
                    SimilarityExperimentProgress(
                        total = candidateCount,
                        processed = processed,
                        skipped = skipped,
                        clusterCandidates = progressClusterCandidates,
                        currentPath = currentPath
                    )
                )
            }

            if (batch.size < SIMILARITY_EXPERIMENT_BATCH_SIZE) {
                break
            }
        }

        val finishedAt = System.currentTimeMillis()
        val clusters = durationToleranceClusters(
            experimentId = experiment.id,
            candidates = durationCandidates,
            toleranceMillis = toleranceMillis,
            durationStep = durationStep,
            updatedAtMillis = finishedAt
        )
        val duplicateFileCount = clusters.sumOf { it.fileCount }
        val run = SimilarityExperimentRunEntity(
            experimentId = experiment.id,
            experimentName = experiment.name,
            startedAtMillis = startedAt,
            finishedAtMillis = finishedAt,
            candidateCount = candidateCount,
            processedCount = processed,
            skippedCount = skipped,
            clusterCount = clusters.size,
            duplicateFileCount = duplicateFileCount
        )
        database.runInTransaction {
            experimentDao.deleteDurationCandidates(experiment.id)
            experimentDao.deleteClusters(experiment.id)
            experimentDao.deleteRun(experiment.id)
            experimentDao.upsertRun(run)
            clusters.chunked(SIMILARITY_CLUSTER_INSERT_CHUNK_SIZE).forEach { chunk ->
                experimentDao.insertClusters(chunk)
            }
        }

        return SimilarityExperimentSummary(
            experimentId = experiment.id,
            candidateCount = candidateCount,
            processedCount = processed,
            skippedCount = skipped,
            clusterCount = clusters.size,
            duplicateFileCount = duplicateFileCount,
            cancelled = false
        )
    }

    fun runDurationNeighborListExperiment(
        request: SimilarityExperimentRunRequest,
        shouldContinue: () -> Boolean,
        onProgress: (SimilarityExperimentProgress) -> Unit
    ): SimilarityExperimentSummary {
        val experiment = request.experiment
        val neighborStep = requireNotNull(request.durationNeighborListStep) {
            "Duration neighbor list step is required for duration neighbor experiments."
        }
        val toleranceMillis = durationNeighborToleranceMillis(neighborStep)
        val candidateCount = countCandidates(
            mediaScope = request.mediaScope,
            minSizeBytes = request.minSizeBytes
        )
        val startedAt = System.currentTimeMillis()
        val durationCandidates = mutableListOf<DurationCandidate>()
        val progressBuckets = mutableMapOf<Long, Int>()
        var progressClusterCandidates = 0
        var processed = 0
        var skipped = 0
        var afterPath = ""
        var currentPath: String? = null

        while (shouldContinue()) {
            val batch = listCandidatesAfter(
                mediaScope = request.mediaScope,
                minSizeBytes = request.minSizeBytes,
                afterPath = afterPath,
                limit = SIMILARITY_EXPERIMENT_BATCH_SIZE
            )
            if (batch.isEmpty()) {
                break
            }

            for (entity in batch) {
                if (!shouldContinue()) {
                    return durationNeighborSummaryWithoutSaving(
                        experimentId = experiment.id,
                        candidateCount = candidateCount,
                        processed = processed,
                        skipped = skipped,
                        candidates = durationCandidates,
                        toleranceMillis = toleranceMillis,
                        neighborStep = neighborStep,
                        cancelled = true
                    )
                }

                currentPath = entity.path.ifBlank { entity.normalizedPath }
                val file = File(currentPath)
                val durationMillis = if (file.exists()) {
                    durationExtractor.durationMillis(
                        file = file,
                        shouldContinue = shouldContinue
                    )
                } else {
                    null
                }
                if (durationMillis == null) {
                    skipped += 1
                } else {
                    durationCandidates += DurationCandidate(
                        entity = entity,
                        durationMillis = durationMillis
                    )
                    val bucket = durationProgressBucket(
                        durationMillis = durationMillis,
                        toleranceMillis = toleranceMillis
                    )
                    val previousBucketCount = progressBuckets[bucket] ?: 0
                    progressBuckets[bucket] = previousBucketCount + 1
                    if (previousBucketCount == 1) {
                        progressClusterCandidates += 1
                    }
                }
                processed += 1
                afterPath = entity.normalizedPath
                onProgress(
                    SimilarityExperimentProgress(
                        total = candidateCount,
                        processed = processed,
                        skipped = skipped,
                        clusterCandidates = progressClusterCandidates,
                        currentPath = currentPath
                    )
                )
            }

            if (batch.size < SIMILARITY_EXPERIMENT_BATCH_SIZE) {
                break
            }
        }

        val finishedAt = System.currentTimeMillis()
        val clusters = durationNeighborListClusters(
            experimentId = experiment.id,
            candidates = durationCandidates,
            toleranceMillis = toleranceMillis,
            neighborStep = neighborStep,
            updatedAtMillis = finishedAt
        )
        val storedDurationCandidates = durationCandidates.map { candidate ->
            candidate.toDurationCandidateEntity(
                experimentId = experiment.id,
                updatedAtMillis = finishedAt
            )
        }
        val duplicateFileCount = clusters.sumOf { it.fileCount }
        val run = SimilarityExperimentRunEntity(
            experimentId = experiment.id,
            experimentName = experiment.name,
            startedAtMillis = startedAt,
            finishedAtMillis = finishedAt,
            candidateCount = candidateCount,
            processedCount = processed,
            skippedCount = skipped,
            clusterCount = clusters.size,
            duplicateFileCount = duplicateFileCount
        )
        database.runInTransaction {
            experimentDao.deleteDurationCandidates(experiment.id)
            experimentDao.deleteClusters(experiment.id)
            experimentDao.deleteRun(experiment.id)
            experimentDao.upsertRun(run)
            storedDurationCandidates.chunked(SIMILARITY_DURATION_CANDIDATE_INSERT_CHUNK_SIZE).forEach { chunk ->
                experimentDao.insertDurationCandidates(chunk)
            }
            clusters.chunked(SIMILARITY_CLUSTER_INSERT_CHUNK_SIZE).forEach { chunk ->
                experimentDao.insertClusters(chunk)
            }
        }

        return SimilarityExperimentSummary(
            experimentId = experiment.id,
            candidateCount = candidateCount,
            processedCount = processed,
            skippedCount = skipped,
            clusterCount = clusters.size,
            duplicateFileCount = duplicateFileCount,
            cancelled = false
        )
    }

    fun rebuildDurationNeighborListFromStoredDurations(
        experimentId: String,
        neighborStep: DurationNeighborListStep
    ): SimilarityExperimentSummary {
        val previousRun = requireNotNull(experimentDao.getRun(experimentId)) {
            "Similarity experiment run $experimentId is not available."
        }
        val storedCandidates = experimentDao.listDurationCandidates(experimentId)
            .map { row -> row.toDurationCandidate() }
        val startedAt = System.currentTimeMillis()
        val finishedAt = System.currentTimeMillis()
        val toleranceMillis = durationNeighborToleranceMillis(neighborStep)
        val clusters = durationNeighborListClusters(
            experimentId = experimentId,
            candidates = storedCandidates,
            toleranceMillis = toleranceMillis,
            neighborStep = neighborStep,
            updatedAtMillis = finishedAt
        )
        val duplicateFileCount = clusters.sumOf { cluster -> cluster.fileCount }
        val run = previousRun.copy(
            startedAtMillis = startedAt,
            finishedAtMillis = finishedAt,
            clusterCount = clusters.size,
            duplicateFileCount = duplicateFileCount
        )
        database.runInTransaction {
            experimentDao.deleteClusters(experimentId)
            experimentDao.upsertRun(run)
            clusters.chunked(SIMILARITY_CLUSTER_INSERT_CHUNK_SIZE).forEach { chunk ->
                experimentDao.insertClusters(chunk)
            }
        }

        return SimilarityExperimentSummary(
            experimentId = experimentId,
            candidateCount = run.candidateCount,
            processedCount = run.processedCount,
            skippedCount = run.skippedCount,
            clusterCount = clusters.size,
            duplicateFileCount = duplicateFileCount,
            cancelled = false
        )
    }

    private fun summaryWithoutSaving(
        experimentId: String,
        candidateCount: Int,
        processed: Int,
        skipped: Int,
        groups: Map<String, List<CachedFileEntity>>,
        cancelled: Boolean
    ): SimilarityExperimentSummary {
        val clusters = groups.values.filter { members -> members.size > 1 }
        return SimilarityExperimentSummary(
            experimentId = experimentId,
            candidateCount = candidateCount,
            processedCount = processed,
            skippedCount = skipped,
            clusterCount = clusters.size,
            duplicateFileCount = clusters.sumOf { it.size },
            cancelled = cancelled
        )
    }

    private fun listCandidatesAfter(
        mediaScope: SimilarityMediaScope,
        minSizeBytes: Long,
        afterPath: String,
        limit: Int
    ): List<CachedFileEntity> {
        return when (mediaScope) {
            SimilarityMediaScope.Video -> fileDao.listVideoCandidatesAfter(
                minSizeBytes = minSizeBytes,
                afterPath = afterPath,
                limit = limit
            )
            SimilarityMediaScope.Image -> fileDao.listImageCandidatesAfter(
                minSizeBytes = minSizeBytes,
                afterPath = afterPath,
                limit = limit
            )
        }
    }
}

private data class DurationCandidate(
    val entity: CachedFileEntity,
    val durationMillis: Long
)

private fun DurationCandidate.toDurationCandidateEntity(
    experimentId: String,
    updatedAtMillis: Long
): SimilarityDurationCandidateEntity {
    return SimilarityDurationCandidateEntity(
        experimentId = experimentId,
        normalizedPath = entity.normalizedPath,
        durationMillis = durationMillis,
        sizeBytes = entity.sizeBytes,
        updatedAtMillis = updatedAtMillis
    )
}

private fun SimilarityDurationCandidateEntity.toDurationCandidate(): DurationCandidate {
    return DurationCandidate(
        entity = CachedFileEntity(
            normalizedPath = normalizedPath,
            path = normalizedPath,
            sizeBytes = sizeBytes,
            lastModifiedMillis = 0L,
            hashHex = null
        ),
        durationMillis = durationMillis
    )
}

private fun durationProgressBucket(
    durationMillis: Long,
    toleranceMillis: Long
): Long {
    val bucketSizeMillis = toleranceMillis.coerceAtLeast(1L)
    return durationMillis.coerceAtLeast(0L) / bucketSizeMillis
}

private fun durationToleranceClusters(
    experimentId: String,
    candidates: List<DurationCandidate>,
    toleranceMillis: Long,
    durationStep: DurationToleranceStep,
    updatedAtMillis: Long
): List<SimilarityClusterEntity> {
    val sortedCandidates = candidates.sortedWith(
        compareBy<DurationCandidate> { candidate -> candidate.durationMillis }
            .thenBy { candidate -> candidate.entity.normalizedPath }
    )
    val clusters = mutableListOf<SimilarityClusterEntity>()
    val current = mutableListOf<DurationCandidate>()
    var currentMinDurationMillis = 0L

    fun flushCurrent() {
        if (current.size <= 1) return
        val firstDurationMillis = current.first().durationMillis
        val lastDurationMillis = current.last().durationMillis
        clusters += SimilarityClusterEntity(
            experimentId = experimentId,
            signature = buildDurationToleranceSignature(
                minDurationMillis = firstDurationMillis,
                maxDurationMillis = lastDurationMillis,
                step = durationStep
            ),
            fileCount = current.size,
            totalBytes = current.sumOf { candidate -> candidate.entity.sizeBytes },
            memberNormalizedPathsText = durationClusterMemberText(current),
            updatedAtMillis = updatedAtMillis
        )
    }

    sortedCandidates.forEach { candidate ->
        if (current.isEmpty()) {
            current += candidate
            currentMinDurationMillis = candidate.durationMillis
        } else if (candidate.durationMillis - currentMinDurationMillis <= toleranceMillis) {
            current += candidate
        } else {
            flushCurrent()
            current.clear()
            current += candidate
            currentMinDurationMillis = candidate.durationMillis
        }
    }
    flushCurrent()

    return clusters
}

private fun durationNeighborListClusters(
    experimentId: String,
    candidates: List<DurationCandidate>,
    toleranceMillis: Long,
    neighborStep: DurationNeighborListStep,
    updatedAtMillis: Long
): List<SimilarityClusterEntity> {
    val sortedCandidates = candidates.sortedWith(
        compareBy<DurationCandidate> { candidate -> candidate.durationMillis }
            .thenBy { candidate -> candidate.entity.normalizedPath }
    )
    val listedCandidates = sortedCandidates.filterIndexed { index, candidate ->
        val previous = sortedCandidates.getOrNull(index - 1)
        val next = sortedCandidates.getOrNull(index + 1)
        val closeToPrevious = previous != null &&
            candidate.durationMillis - previous.durationMillis <= toleranceMillis
        val closeToNext = next != null &&
            next.durationMillis - candidate.durationMillis <= toleranceMillis
        closeToPrevious || closeToNext
    }
    if (listedCandidates.size <= 1) return emptyList()

    val firstDurationMillis = listedCandidates.first().durationMillis
    val lastDurationMillis = listedCandidates.last().durationMillis
    return listOf(
        SimilarityClusterEntity(
            experimentId = experimentId,
            signature = buildDurationNeighborListSignature(
                minDurationMillis = firstDurationMillis,
                maxDurationMillis = lastDurationMillis,
                step = neighborStep
            ),
            fileCount = listedCandidates.size,
            totalBytes = listedCandidates.sumOf { candidate -> candidate.entity.sizeBytes },
            memberNormalizedPathsText = durationClusterMemberText(listedCandidates),
            updatedAtMillis = updatedAtMillis
        )
    )
}

private fun durationSummaryWithoutSaving(
    experimentId: String,
    candidateCount: Int,
    processed: Int,
    skipped: Int,
    candidates: List<DurationCandidate>,
    toleranceMillis: Long,
    durationStep: DurationToleranceStep,
    cancelled: Boolean
): SimilarityExperimentSummary {
    val clusters = durationToleranceClusters(
        experimentId = experimentId,
        candidates = candidates,
        toleranceMillis = toleranceMillis,
        durationStep = durationStep,
        updatedAtMillis = System.currentTimeMillis()
    )
    return SimilarityExperimentSummary(
        experimentId = experimentId,
        candidateCount = candidateCount,
        processedCount = processed,
        skippedCount = skipped,
        clusterCount = clusters.size,
        duplicateFileCount = clusters.sumOf { cluster -> cluster.fileCount },
        cancelled = cancelled
    )
}

private fun durationNeighborSummaryWithoutSaving(
    experimentId: String,
    candidateCount: Int,
    processed: Int,
    skipped: Int,
    candidates: List<DurationCandidate>,
    toleranceMillis: Long,
    neighborStep: DurationNeighborListStep,
    cancelled: Boolean
): SimilarityExperimentSummary {
    val clusters = durationNeighborListClusters(
        experimentId = experimentId,
        candidates = candidates,
        toleranceMillis = toleranceMillis,
        neighborStep = neighborStep,
        updatedAtMillis = System.currentTimeMillis()
    )
    return SimilarityExperimentSummary(
        experimentId = experimentId,
        candidateCount = candidateCount,
        processedCount = processed,
        skippedCount = skipped,
        clusterCount = clusters.size,
        duplicateFileCount = clusters.sumOf { cluster -> cluster.fileCount },
        cancelled = cancelled
    )
}

private fun durationNeighborSortMillis(signature: String): Long {
    val range = signature
        .takeIf(::isDurationNeighborListSignature)
        ?.substringAfterLast(':')
        ?: return Long.MAX_VALUE
    return range.substringBefore('-').toLongOrNull() ?: Long.MAX_VALUE
}

private fun isDurationNeighborListSignature(signature: String): Boolean {
    return signature.startsWith("duration-neighbor-list-v1:") ||
        signature.startsWith("duration-neighbor-v1:")
}

private fun isDurationSimilaritySignature(signature: String): Boolean {
    return signature.startsWith("duration-v1:") || isDurationNeighborListSignature(signature)
}

internal data class SimilarityClusterMemberEntry(
    val normalizedPath: String,
    val durationMillis: Long?
)

private fun durationClusterMemberText(candidates: List<DurationCandidate>): String {
    return candidates.joinToString("\n") { candidate ->
        "${candidate.durationMillis.coerceAtLeast(0L)}\t${candidate.entity.normalizedPath}"
    }
}

internal fun parseSimilarityClusterMemberEntries(text: String): List<SimilarityClusterMemberEntry> {
    val entriesByPath = linkedMapOf<String, SimilarityClusterMemberEntry>()
    text.lineSequence()
        .map { line -> line.trim() }
        .filter { line -> line.isNotEmpty() }
        .forEach { line ->
            val tabIndex = line.indexOf('\t')
            val durationMillis = if (tabIndex > 0) {
                line.substring(0, tabIndex)
                    .toLongOrNull()
                    ?.coerceAtLeast(0L)
            } else {
                null
            }
            val entry = if (durationMillis != null) {
                val normalizedPath = line.substring(tabIndex + 1).trim()
                SimilarityClusterMemberEntry(
                    normalizedPath = normalizedPath,
                    durationMillis = durationMillis
                )
            } else {
                SimilarityClusterMemberEntry(
                    normalizedPath = line,
                    durationMillis = null
                )
            }
            if (entry.normalizedPath.isNotEmpty()) {
                entriesByPath.putIfAbsent(entry.normalizedPath, entry)
            }
        }
    return entriesByPath.values.toList()
}

internal fun parseSimilarityClusterMemberPaths(text: String): List<String> {
    return parseSimilarityClusterMemberEntries(text)
        .map { entry -> entry.normalizedPath }
}

private fun CachedFileEntity.toMetadata(): FileMetadata {
    return FileMetadata(
        path = path,
        normalizedPath = normalizedPath,
        sizeBytes = sizeBytes,
        lastModifiedMillis = lastModifiedMillis,
        hashHex = hashHex
    )
}

private const val SIMILARITY_EXPERIMENT_BATCH_SIZE = 100
private const val SIMILARITY_CLUSTER_INSERT_CHUNK_SIZE = 100
private const val SIMILARITY_DURATION_CANDIDATE_INSERT_CHUNK_SIZE = 500
private const val SIMILARITY_CLUSTER_MEMBER_LOOKUP_CHUNK_SIZE = 500
private const val DURATION_NEIGHBOR_EXPERIMENT_ID_PREFIX = "video-duration-neighbor"
