package opensource.cached_dupe_scanner.storage

import opensource.cached_dupe_scanner.cache.CacheDatabase
import opensource.cached_dupe_scanner.cache.CachedFileEntity
import opensource.cached_dupe_scanner.cache.FileCacheDao
import opensource.cached_dupe_scanner.cache.SimilarityClusterEntity
import opensource.cached_dupe_scanner.cache.SimilarityExperimentDao
import opensource.cached_dupe_scanner.cache.SimilarityExperimentRunEntity
import opensource.cached_dupe_scanner.core.AndroidVideoFrameSignatureExtractor
import opensource.cached_dupe_scanner.core.ExactThumbnailHashStep
import opensource.cached_dupe_scanner.core.FileMetadata
import opensource.cached_dupe_scanner.core.SimilarityExperimentSpec
import opensource.cached_dupe_scanner.core.SimilarityMediaScope
import opensource.cached_dupe_scanner.core.VideoFrameSignatureExtractor
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
    val exactThumbnailStep: ExactThumbnailHashStep
)

class SimilarityExperimentRepository(
    private val database: CacheDatabase,
    private val fileDao: FileCacheDao,
    private val experimentDao: SimilarityExperimentDao,
    private val frameSignatureExtractor: VideoFrameSignatureExtractor = AndroidVideoFrameSignatureExtractor()
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

    fun listClusters(experimentId: String): List<SimilarityClusterEntity> {
        return experimentDao.listClusters(experimentId)
    }

    fun listClusterMembers(
        cluster: SimilarityClusterEntity,
        limit: Int? = null
    ): List<FileMetadata> {
        val paths = parseSimilarityClusterMemberPaths(cluster.memberNormalizedPathsText)
            .let { parsed ->
                if (limit == null) parsed else parsed.take(limit.coerceAtLeast(0))
            }
        if (paths.isEmpty()) return emptyList()

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
        return paths.mapNotNull { path -> membersByPath[path] }
    }

    fun runExactThumbnailHashExperiment(
        request: SimilarityExperimentRunRequest,
        shouldContinue: () -> Boolean,
        onProgress: (SimilarityExperimentProgress) -> Unit
    ): SimilarityExperimentSummary {
        val experiment = request.experiment
        val exactStep = request.exactThumbnailStep
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

internal fun parseSimilarityClusterMemberPaths(text: String): List<String> {
    return text.lineSequence()
        .map { line -> line.trim() }
        .filter { line -> line.isNotEmpty() }
        .distinct()
        .toList()
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
private const val SIMILARITY_CLUSTER_MEMBER_LOOKUP_CHUNK_SIZE = 500
