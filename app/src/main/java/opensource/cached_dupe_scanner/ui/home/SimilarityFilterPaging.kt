package opensource.cached_dupe_scanner.ui.home

import opensource.cached_dupe_scanner.cache.DuplicateGroupEntity
import opensource.cached_dupe_scanner.cache.SimilarityClusterEntity
import opensource.cached_dupe_scanner.cache.SimilarityClusterDurationStatsRow
import opensource.cached_dupe_scanner.cache.SimilarityClusterFilterMemberRow
import opensource.cached_dupe_scanner.core.FileMetadata
import opensource.cached_dupe_scanner.core.SortDirection
import opensource.cached_dupe_scanner.storage.SimilarityClusterSortColumn
import opensource.cached_dupe_scanner.storage.SimilarityMemberResolutionEvent
import opensource.cached_dupe_scanner.storage.SimilarityMemberResolutionKind
import opensource.cached_dupe_scanner.storage.SimilaritySettingsRepository

private const val SIMILARITY_FILTER_PROGRESS_UPDATE_INTERVAL = 16
private const val SIMILARITY_FILTER_SOURCE_BATCH_SIZE = 500

internal data class FilteredSimilarityClustersPage(
    val clusters: List<SimilarityClusterEntity>,
    val nextSourceOffset: Int,
    val exhausted: Boolean
)

internal data class SimilarityFilterResolutionProgress(
    val processed: Int,
    val total: Int,
    val currentPath: String?,
    val kind: SimilarityMemberResolutionKind?
)

private data class SimilarityFilterSourceCursor(
    val offset: Int,
    val afterCluster: SimilarityClusterEntity?
)

internal fun loadFilteredSimilarityClustersPage(
    repository: SimilaritySettingsRepository,
    settingId: Long,
    sortColumn: SimilarityClusterSortColumn,
    sortDirection: SortDirection,
    definition: ResultsFilterDefinition,
    startOffset: Int,
    minMatches: Int,
    sourcePageSize: Int,
    memberPageSize: Int = 500,
    onResolutionProgress: (SimilarityFilterResolutionProgress) -> Unit = {}
): FilteredSimilarityClustersPage {
    if (sourcePageSize <= 0 || memberPageSize <= 0) {
        return FilteredSimilarityClustersPage(emptyList(), startOffset, exhausted = true)
    }
    val needsMembers = definition.requiresGroupMembers(SIMILARITY_FILTER_TARGETS)
    val resolveDimensions = definition.hasActiveTarget(
        target = ResultsFilterTarget.SameResolution,
        supportedTargets = SIMILARITY_FILTER_TARGETS
    )
    val resolveDurations = definition.hasActiveTarget(
        target = ResultsFilterTarget.DurationFromAverage,
        supportedTargets = SIMILARITY_FILTER_TARGETS
    )
    val durationOnlyFilter = resolveDurations && ResultsFilterTarget.entries.all { target ->
        target == ResultsFilterTarget.DurationFromAverage ||
            !definition.hasActiveTarget(target, SIMILARITY_FILTER_TARGETS)
    }
    var sourceReadBatchSize = if (durationOnlyFilter) {
        maxOf(sourcePageSize, SIMILARITY_FILTER_SOURCE_BATCH_SIZE)
    } else {
        sourcePageSize
    }
    var resolutionProcessed = 0
    var resolutionTotal = 0
    var initialResolutionWorkPublished = false
    var currentPageMatches = emptyMap<Long, Boolean>()
    val publishResolutionEvent: (SimilarityMemberResolutionEvent) -> Unit = { event ->
        if (!event.completed && !initialResolutionWorkPublished) {
            initialResolutionWorkPublished = true
            onResolutionProgress(
                SimilarityFilterResolutionProgress(
                    processed = resolutionProcessed,
                    total = resolutionTotal,
                    currentPath = event.path,
                    kind = event.kind
                )
            )
        } else if (event.completed) {
            resolutionProcessed = (resolutionProcessed + 1).coerceAtMost(resolutionTotal)
            if (
                resolutionProcessed == resolutionTotal ||
                resolutionProcessed % SIMILARITY_FILTER_PROGRESS_UPDATE_INTERVAL == 0
            ) {
                onResolutionProgress(
                    SimilarityFilterResolutionProgress(
                        processed = resolutionProcessed,
                        total = resolutionTotal,
                        currentPath = event.path,
                        kind = event.kind
                    )
                )
            }
        }
    }
    val page = loadFilteredSourcePage(
        startCursor = SimilarityFilterSourceCursor(
            offset = startOffset,
            afterCluster = null
        ),
        minMatches = minMatches,
        loadPage = { cursor ->
            val afterCluster = cursor.afterCluster
            val queryLimit = sourceReadBatchSize
            val queriedClusters = if (afterCluster == null) {
                repository.listClustersPage(
                    settingId = settingId,
                    offset = cursor.offset,
                    limit = queryLimit,
                    sortColumn = sortColumn,
                    direction = sortDirection
                )
            } else {
                repository.listClustersPageAfter(
                    settingId = settingId,
                    afterCluster = afterCluster,
                    limit = queryLimit,
                    sortColumn = sortColumn,
                    direction = sortDirection
                )
            }
            val queriedClusterIds = queriedClusters.map { cluster -> cluster.clusterId }
            val queriedDurationStats = if (resolveDurations && queriedClusterIds.isNotEmpty()) {
                repository.listFilterDurationStatsForClusters(settingId, queriedClusterIds)
            } else {
                emptyList()
            }
            val queriedDurationStatsByClusterId = queriedDurationStats.associateBy { stats ->
                stats.clusterId
            }
            val queriedDurationResolutionWork = if (resolveDurations) {
                durationResolutionWork(
                    clusters = queriedClusters,
                    statsByClusterId = queriedDurationStatsByClusterId
                )
            } else {
                0
            }
            val queriedDimensionResolutionWork = if (
                resolveDimensions &&
                queriedClusterIds.isNotEmpty()
            ) {
                repository.countFilterResolutionWorkForClusters(
                    settingId = settingId,
                    clusterIds = queriedClusterIds,
                    resolveDimensions = true,
                    resolveDurations = false
                )
            } else {
                0
            }
            val queriedResolutionWork = addResolutionWork(
                queriedDurationResolutionWork,
                queriedDimensionResolutionWork
            )
            val clusters = if (
                queriedResolutionWork > 0 &&
                queriedClusters.size > sourcePageSize
            ) {
                sourceReadBatchSize = sourcePageSize
                queriedClusters.take(sourcePageSize)
            } else {
                queriedClusters
            }
            val clusterIds = clusters.map { cluster -> cluster.clusterId }
            val selectedClusterIds = clusterIds.toHashSet()
            val initialDurationStats = queriedDurationStats.filter { stats ->
                selectedClusterIds.contains(stats.clusterId)
            }
            val durationResolutionWork = if (resolveDurations) {
                durationResolutionWork(
                    clusters = clusters,
                    statsByClusterId = queriedDurationStatsByClusterId
                )
            } else {
                0
            }
            val dimensionResolutionWork = if (!resolveDimensions) {
                0
            } else if (clusters.size == queriedClusters.size) {
                queriedDimensionResolutionWork
            } else {
                repository.countFilterResolutionWorkForClusters(
                    settingId = settingId,
                    clusterIds = clusterIds,
                    resolveDimensions = true,
                    resolveDurations = false
                )
            }
            val addedResolutionWork = addResolutionWork(
                durationResolutionWork,
                dimensionResolutionWork
            )
            if (addedResolutionWork > 0) {
                resolutionTotal = (resolutionTotal.toLong() + addedResolutionWork)
                    .coerceAtMost(Int.MAX_VALUE.toLong())
                    .toInt()
                onResolutionProgress(
                    SimilarityFilterResolutionProgress(
                        processed = resolutionProcessed,
                        total = resolutionTotal,
                        currentPath = null,
                        kind = null
                    )
                )
            }
            if (addedResolutionWork > 0) {
                repository.resolveFilterMetadataForClusters(
                    settingId = settingId,
                    clusterIds = clusterIds,
                    resolveDimensions = resolveDimensions,
                    resolveDurations = resolveDurations,
                    onResolutionEvent = publishResolutionEvent
                )
            }
            val resolvedDurationStats = if (durationOnlyFilter && addedResolutionWork > 0) {
                repository.listFilterDurationStatsForClusters(settingId, clusterIds)
            } else {
                initialDurationStats
            }
            currentPageMatches = when {
                durationOnlyFilter && clusters.isNotEmpty() -> matchSimilarityClustersFromDurationStats(
                    clusters = clusters,
                    definition = definition,
                    statsRows = resolvedDurationStats
                )
                needsMembers && clusters.isNotEmpty() -> matchSimilarityClustersFromMemberPages(
                    repository = repository,
                    settingId = settingId,
                    clusters = clusters,
                    definition = definition,
                    memberPageSize = memberPageSize,
                    includeDurations = resolveDurations
                )
                else -> emptyMap()
            }
            if (addedResolutionWork == 0 && sourceReadBatchSize == sourcePageSize) {
                sourceReadBatchSize = maxOf(sourcePageSize, SIMILARITY_FILTER_SOURCE_BATCH_SIZE)
            }
            SourcePage(
                items = clusters,
                nextCursor = SimilarityFilterSourceCursor(
                    offset = cursor.offset + clusters.size,
                    afterCluster = clusters.lastOrNull() ?: cursor.afterCluster
                ),
                exhausted = clusters.size == queriedClusters.size &&
                    (queriedClusters.isEmpty() || queriedClusters.size < queryLimit)
            )
        },
        transformMatch = { cluster ->
            val group = cluster.asFilterGroup()
            val matched = if (needsMembers) {
                currentPageMatches[cluster.clusterId] == true
            } else {
                matchesResultsFilter(
                    definition = definition,
                    group = group,
                    members = emptyList(),
                    supportedTargets = SIMILARITY_FILTER_TARGETS
                )
            }
            cluster.takeIf { matched }
        },
        trimToMinMatches = false
    )
    return FilteredSimilarityClustersPage(
        clusters = page.items,
        nextSourceOffset = page.nextCursor?.offset ?: startOffset,
        exhausted = page.exhausted
    )
}

private fun durationResolutionWork(
    clusters: List<SimilarityClusterEntity>,
    statsByClusterId: Map<Long, SimilarityClusterDurationStatsRow>
): Int {
    return clusters.sumOf { cluster ->
        val stats = statsByClusterId[cluster.clusterId]
        if (stats == null || stats.memberCount != cluster.fileCount.toLong()) {
            cluster.fileCount.coerceAtLeast(0).toLong()
        } else {
            (stats.memberCount - stats.checkedCount).coerceAtLeast(0L)
        }
    }.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
}

private fun addResolutionWork(first: Int, second: Int): Int {
    return (first.toLong() + second.toLong()).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
}

private fun matchSimilarityClustersFromDurationStats(
    clusters: List<SimilarityClusterEntity>,
    definition: ResultsFilterDefinition,
    statsRows: List<SimilarityClusterDurationStatsRow>
): Map<Long, Boolean> {
    val statsByClusterId = statsRows.associateBy { stats -> stats.clusterId }
    return clusters.associate { cluster ->
        cluster.clusterId to matchesDurationOnlyResultsFilter(
            definition = definition,
            stats = statsByClusterId[cluster.clusterId]?.toFilterStats(),
            supportedTargets = SIMILARITY_FILTER_TARGETS
        )
    }
}

private fun SimilarityClusterDurationStatsRow.toFilterStats(): DurationAverageFilterStats {
    return DurationAverageFilterStats(
        memberCount = memberCount,
        checkedCount = checkedCount,
        durationCount = durationCount,
        durationSumMillis = durationSumMillis,
        minimumDurationMillis = minimumDurationMillis,
        maximumDurationMillis = maximumDurationMillis
    )
}

private fun matchSimilarityClustersFromMemberPages(
    repository: SimilaritySettingsRepository,
    settingId: Long,
    clusters: List<SimilarityClusterEntity>,
    definition: ResultsFilterDefinition,
    memberPageSize: Int,
    includeDurations: Boolean
): Map<Long, Boolean> {
    val matchers = clusters.associate { cluster ->
        cluster.clusterId to ResultsFilterPagedMatcher(
            definition = definition,
            group = cluster.asFilterGroup(),
            supportedTargets = SIMILARITY_FILTER_TARGETS
        )
    }
    val unresolvedClusterIds = matchers.mapNotNullTo(linkedSetOf()) { (clusterId, matcher) ->
        clusterId.takeIf { matcher.resolved() == null }
    }
    repository.visitFilterMemberPagesForClusters(
        settingId = settingId,
        clusterIds = unresolvedClusterIds.toList(),
        pageSize = memberPageSize,
        includeDurations = includeDurations,
        onPage = { rows ->
            val resolvedClusterIds = linkedSetOf<Long>()
            rows.groupBy { row -> row.clusterId }.forEach { (clusterId, clusterRows) ->
                val matcher = matchers.getValue(clusterId)
                matcher.consume(clusterRows.map { row -> row.toFilterMetadata() })
                if (matcher.resolved() != null) {
                    resolvedClusterIds += clusterId
                }
            }
            resolvedClusterIds
        }
    )
    return matchers.mapValues { (_, matcher) -> matcher.finish() }
}

private fun SimilarityClusterFilterMemberRow.toFilterMetadata(): FileMetadata {
    return FileMetadata(
        path = path,
        normalizedPath = normalizedPath,
        sizeBytes = sizeBytes,
        lastModifiedMillis = lastModifiedMillis,
        durationMillis = durationMillis,
        widthPixels = widthPixels,
        heightPixels = heightPixels
    )
}

internal fun SimilarityClusterEntity.asFilterGroup(): DuplicateGroupEntity {
    return DuplicateGroupEntity(
        sizeBytes = if (fileCount > 0) totalBytes / fileCount else 0L,
        hashHex = clusterKey,
        fileCount = fileCount,
        totalBytes = totalBytes,
        updatedAtMillis = updatedAtMillis
    )
}
