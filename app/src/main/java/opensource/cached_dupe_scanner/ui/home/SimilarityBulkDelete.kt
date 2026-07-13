package opensource.cached_dupe_scanner.ui.home

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import opensource.cached_dupe_scanner.cache.DuplicateGroupEntity
import opensource.cached_dupe_scanner.cache.SimilarityClusterEntity
import opensource.cached_dupe_scanner.core.FileMetadata
import opensource.cached_dupe_scanner.core.SortDirection
import opensource.cached_dupe_scanner.storage.SimilarityMemberSortColumn
import opensource.cached_dupe_scanner.storage.SimilaritySettingsRepository

private const val SIMILARITY_BULK_DELETE_SOURCE_PAGE_SIZE = 100
private const val SIMILARITY_BULK_DELETE_MEMBER_PAGE_SIZE = 200
private const val SIMILARITY_BULK_DELETE_PREVIEW_ATTEMPTS = 2

internal class SimilarityBulkDeleteOperations(
    private val repository: SimilaritySettingsRepository,
    private val settingId: Long,
    override val totalGroupCount: Int,
    private val sourcePageSize: Int = SIMILARITY_BULK_DELETE_SOURCE_PAGE_SIZE
) : BulkDeleteOperations {
    init {
        require(sourcePageSize > 0) { "sourcePageSize must be positive" }
    }

    override val snapshotAvailable: Boolean
        get() = totalGroupCount > 0

    override suspend fun buildKeepOneByTextPreview(
        filterDefinition: ResultsFilterDefinition,
        config: KeepOneByTextBulkDeleteCommandConfig,
        onProgress: (ResultsBulkDeletePreviewProgress) -> Unit
    ): ResultsBulkDeletePreview {
        return buildSimilarityBulkDeletePreview(
            repository = repository,
            settingId = settingId,
            totalGroupCount = totalGroupCount,
            filterDefinition = filterDefinition,
            sourcePageSize = sourcePageSize,
            onProgress = onProgress
        ) { group, members ->
            buildKeepOneByTextBulkDeleteCandidate(group, members, config)
        }
    }

    override suspend fun buildKeepModifiedPreview(
        filterDefinition: ResultsFilterDefinition,
        keepNewest: Boolean,
        onProgress: (ResultsBulkDeletePreviewProgress) -> Unit
    ): ResultsBulkDeletePreview {
        return buildSimilarityBulkDeletePreview(
            repository = repository,
            settingId = settingId,
            totalGroupCount = totalGroupCount,
            filterDefinition = filterDefinition,
            sourcePageSize = sourcePageSize,
            onProgress = onProgress
        ) { group, members ->
            buildKeepModifiedBulkDeleteCandidate(group, members, keepNewest)
        }
    }

    override suspend fun buildKeepDurationPreview(
        filterDefinition: ResultsFilterDefinition,
        config: KeepByDurationBulkDeleteCommandConfig,
        onProgress: (ResultsBulkDeletePreviewProgress) -> Unit
    ): ResultsBulkDeletePreview {
        return buildSimilarityBulkDeletePreview(
            repository = repository,
            settingId = settingId,
            totalGroupCount = totalGroupCount,
            filterDefinition = filterDefinition,
            sourcePageSize = sourcePageSize,
            resolveCandidateDurations = true,
            onProgress = onProgress
        ) { group, members ->
            buildKeepDurationBulkDeleteCandidate(group, members, config)
        }
    }

    override suspend fun executeKeepOneByText(
        preview: ResultsBulkDeletePreview,
        filterDefinition: ResultsFilterDefinition,
        config: KeepOneByTextBulkDeleteCommandConfig,
        onDeleteFile: suspend (FileMetadata) -> Boolean,
        onProgress: (ResultsBulkDeleteExecutionProgress) -> Unit
    ): ResultsBulkDeleteExecutionOutcome {
        return executeSimilarityBulkDeleteCommand(
            repository = repository,
            settingId = settingId,
            filterDefinition = filterDefinition,
            sourcePageSize = sourcePageSize,
            totalDeleteTargetCount = preview.candidateFileCount,
            onDeleteFile = onDeleteFile,
            onProgress = onProgress
        ) { group, members ->
            buildKeepOneByTextBulkDeleteCandidate(group, members, config)
        }
    }

    override suspend fun executeKeepModified(
        preview: ResultsBulkDeletePreview,
        filterDefinition: ResultsFilterDefinition,
        keepNewest: Boolean,
        onDeleteFile: suspend (FileMetadata) -> Boolean,
        onProgress: (ResultsBulkDeleteExecutionProgress) -> Unit
    ): ResultsBulkDeleteExecutionOutcome {
        return executeSimilarityBulkDeleteCommand(
            repository = repository,
            settingId = settingId,
            filterDefinition = filterDefinition,
            sourcePageSize = sourcePageSize,
            totalDeleteTargetCount = preview.candidateFileCount,
            onDeleteFile = onDeleteFile,
            onProgress = onProgress
        ) { group, members ->
            buildKeepModifiedBulkDeleteCandidate(group, members, keepNewest)
        }
    }

    override suspend fun executeKeepDuration(
        preview: ResultsBulkDeletePreview,
        filterDefinition: ResultsFilterDefinition,
        config: KeepByDurationBulkDeleteCommandConfig,
        onDeleteFile: suspend (FileMetadata) -> Boolean,
        onProgress: (ResultsBulkDeleteExecutionProgress) -> Unit
    ): ResultsBulkDeleteExecutionOutcome {
        return executeSimilarityBulkDeleteCommand(
            repository = repository,
            settingId = settingId,
            filterDefinition = filterDefinition,
            sourcePageSize = sourcePageSize,
            totalDeleteTargetCount = preview.candidateFileCount,
            resolveCandidateDurations = true,
            onDeleteFile = onDeleteFile,
            onProgress = onProgress
        ) { group, members ->
            buildKeepDurationBulkDeleteCandidate(group, members, config)
        }
    }

    override suspend fun hasSnapshotChanged(preview: ResultsBulkDeletePreview): Boolean {
        val currentSnapshotId = withContext(Dispatchers.IO) {
            repository.clusterSnapshotKey(settingId)
        }
        return currentSnapshotId != preview.sourceSnapshotId
    }

    override suspend fun refreshGroups(touchedGroups: Set<ResultsBulkDeleteTouchedGroupKey>) = Unit
}

private suspend fun buildSimilarityBulkDeletePreview(
    repository: SimilaritySettingsRepository,
    settingId: Long,
    totalGroupCount: Int,
    filterDefinition: ResultsFilterDefinition,
    sourcePageSize: Int = SIMILARITY_BULK_DELETE_SOURCE_PAGE_SIZE,
    resolveCandidateDurations: Boolean = false,
    onProgress: (ResultsBulkDeletePreviewProgress) -> Unit,
    buildCandidate: (DuplicateGroupEntity, List<FileMetadata>) -> ResultsBulkDeleteCandidate?
): ResultsBulkDeletePreview {
    repeat(SIMILARITY_BULK_DELETE_PREVIEW_ATTEMPTS) {
        val snapshotId = withContext(Dispatchers.IO) {
            repository.clusterSnapshotKey(settingId)
        }
        val preview = buildSimilarityBulkDeletePreviewAtSnapshot(
            repository = repository,
            settingId = settingId,
            totalGroupCount = totalGroupCount,
            filterDefinition = filterDefinition,
            sourcePageSize = sourcePageSize,
            resolveCandidateDurations = resolveCandidateDurations,
            onProgress = onProgress,
            buildCandidate = buildCandidate
        )
        val snapshotAfterPreview = withContext(Dispatchers.IO) {
            repository.clusterSnapshotKey(settingId)
        }
        if (snapshotId == snapshotAfterPreview) {
            return preview.copy(sourceSnapshotId = snapshotId)
        }
    }
    error("Similarity results changed while the bulk delete preview was being built")
}

private suspend fun buildSimilarityBulkDeletePreviewAtSnapshot(
    repository: SimilaritySettingsRepository,
    settingId: Long,
    totalGroupCount: Int,
    filterDefinition: ResultsFilterDefinition,
    sourcePageSize: Int,
    resolveCandidateDurations: Boolean,
    onProgress: (ResultsBulkDeletePreviewProgress) -> Unit,
    buildCandidate: (DuplicateGroupEntity, List<FileMetadata>) -> ResultsBulkDeleteCandidate?
): ResultsBulkDeletePreview {
    val candidates = mutableListOf<ResultsBulkDeleteCandidate>()
    val safeTotalGroupCount = totalGroupCount.coerceAtLeast(0)
    var afterClusterId = 0L
    var scannedGroupCount = 0
    var filterMatchedGroupCount = 0
    var candidateGroupCount = 0
    var candidateFileCount = 0
    onProgress(ResultsBulkDeletePreviewProgress(totalGroupCount = safeTotalGroupCount))

    while (true) {
        val page = withContext(Dispatchers.IO) {
            repository.listClustersAfterId(
                settingId = settingId,
                afterClusterId = afterClusterId,
                limit = sourcePageSize
            )
        }
        if (page.isEmpty()) break

        page.forEach { cluster ->
            afterClusterId = cluster.clusterId
            scannedGroupCount += 1
            val group = cluster.asFilterGroup()
            if (matchesSimilarityBulkDeleteFilter(repository, cluster, group, filterDefinition)) {
                val members = withContext(Dispatchers.IO) {
                    listSimilarityBulkDeleteMembers(
                        repository = repository,
                        clusterId = cluster.clusterId,
                        resolveDurations = resolveCandidateDurations
                    )
                }
                filterMatchedGroupCount += 1
                buildCandidate(group, members)?.let { candidate ->
                    candidateGroupCount += 1
                    candidateFileCount += candidate.deleteTargets.size
                    candidates += candidate
                }
            }
        }
        onProgress(
            ResultsBulkDeletePreviewProgress(
                scannedGroupCount = scannedGroupCount.coerceAtMost(safeTotalGroupCount),
                totalGroupCount = safeTotalGroupCount,
                filterMatchedGroupCount = filterMatchedGroupCount,
                candidateGroupCount = candidateGroupCount,
                candidateFileCount = candidateFileCount
            )
        )
        if (page.size < sourcePageSize) break
    }

    return ResultsBulkDeletePreview(
        snapshotUpdatedAtMillis = 0L,
        totalGroupCount = safeTotalGroupCount,
        filterMatchedGroupCount = filterMatchedGroupCount,
        candidates = candidates,
        candidateGroupCount = candidateGroupCount,
        candidateFileCount = candidateFileCount
    )
}

private suspend fun executeSimilarityBulkDeleteCommand(
    repository: SimilaritySettingsRepository,
    settingId: Long,
    filterDefinition: ResultsFilterDefinition,
    sourcePageSize: Int = SIMILARITY_BULK_DELETE_SOURCE_PAGE_SIZE,
    totalDeleteTargetCount: Int,
    resolveCandidateDurations: Boolean = false,
    onDeleteFile: suspend (FileMetadata) -> Boolean,
    onProgress: (ResultsBulkDeleteExecutionProgress) -> Unit,
    buildCandidate: (DuplicateGroupEntity, List<FileMetadata>) -> ResultsBulkDeleteCandidate?
): ResultsBulkDeleteExecutionOutcome {
    val total = totalDeleteTargetCount.coerceAtLeast(0)
    val failedPaths = linkedSetOf<String>()
    val touchedClusterIds = linkedSetOf<Long>()
    var afterClusterId = 0L
    var processed = 0
    var successCount = 0
    onProgress(ResultsBulkDeleteExecutionProgress(total = total))

    while (true) {
        val page = withContext(Dispatchers.IO) {
            repository.listClustersAfterId(
                settingId = settingId,
                afterClusterId = afterClusterId,
                limit = sourcePageSize
            )
        }
        if (page.isEmpty()) break

        page.forEach { cluster ->
            afterClusterId = cluster.clusterId
            val group = cluster.asFilterGroup()
            if (matchesSimilarityBulkDeleteFilter(repository, cluster, group, filterDefinition)) {
                val members = withContext(Dispatchers.IO) {
                    listSimilarityBulkDeleteMembers(
                        repository = repository,
                        clusterId = cluster.clusterId,
                        resolveDurations = resolveCandidateDurations
                    )
                }
                buildCandidate(group, members)?.let { candidate ->
                    var clusterChanged = false
                    candidate.deleteTargets.forEach { file ->
                        val deleted = runCatching { onDeleteFile(file) }.getOrDefault(false)
                        processed += 1
                        if (deleted) {
                            successCount += 1
                            clusterChanged = true
                        } else {
                            failedPaths += file.normalizedPath
                        }
                        onProgress(
                            ResultsBulkDeleteExecutionProgress(
                                processed = processed,
                                total = total,
                                failed = failedPaths.size,
                                currentPath = file.normalizedPath
                            )
                        )
                    }
                    if (clusterChanged) {
                        touchedClusterIds += cluster.clusterId
                    }
                }
            }
        }
        if (page.size < sourcePageSize) break
    }

    return ResultsBulkDeleteExecutionOutcome(
        successCount = successCount,
        failedPaths = failedPaths,
        touchedSourceIds = touchedClusterIds
    )
}

private fun listSimilarityBulkDeleteMembers(
    repository: SimilaritySettingsRepository,
    clusterId: Long,
    resolveDurations: Boolean
): List<FileMetadata> {
    return similarityBulkDeleteMemberPages(
        repository = repository,
        clusterId = clusterId,
        resolveDimensions = false,
        resolveDurations = resolveDurations
    ).flatten().toList()
}

private suspend fun matchesSimilarityBulkDeleteFilter(
    repository: SimilaritySettingsRepository,
    cluster: SimilarityClusterEntity,
    group: DuplicateGroupEntity,
    definition: ResultsFilterDefinition
): Boolean {
    return withContext(Dispatchers.IO) {
        if (definition.requiresGroupMembers(SIMILARITY_FILTER_TARGETS)) {
            val resolveDimensions = definition.hasActiveTarget(
                target = ResultsFilterTarget.SameResolution,
                supportedTargets = SIMILARITY_FILTER_TARGETS
            )
            val resolveDurations = definition.hasActiveTarget(
                target = ResultsFilterTarget.DurationFromAverage,
                supportedTargets = SIMILARITY_FILTER_TARGETS
            )
            matchesResultsFilterPagedMembers(
                definition = definition,
                group = group,
                supportedTargets = SIMILARITY_FILTER_TARGETS,
                memberPages = {
                    similarityBulkDeleteMemberPages(
                        repository = repository,
                        clusterId = cluster.clusterId,
                        resolveDimensions = resolveDimensions,
                        resolveDurations = resolveDurations
                    )
                }
            )
        } else {
            matchesResultsFilter(
                definition = definition,
                group = group,
                members = emptyList(),
                supportedTargets = SIMILARITY_FILTER_TARGETS
            )
        }
    }
}

private fun similarityBulkDeleteMemberPages(
    repository: SimilaritySettingsRepository,
    clusterId: Long,
    resolveDimensions: Boolean,
    resolveDurations: Boolean
): Sequence<List<FileMetadata>> {
    return sequence {
        var offset = 0
        do {
            val page = repository.listClusterMembersPage(
                clusterId = clusterId,
                offset = offset,
                limit = SIMILARITY_BULK_DELETE_MEMBER_PAGE_SIZE,
                sortColumn = SimilarityMemberSortColumn.Position,
                direction = SortDirection.Asc,
                resolveDimensions = resolveDimensions,
                resolveDurations = resolveDurations
            ).map { member -> member.metadata }
            if (page.isNotEmpty()) {
                yield(page)
                offset += page.size
            }
        } while (page.size == SIMILARITY_BULK_DELETE_MEMBER_PAGE_SIZE)
    }
}
