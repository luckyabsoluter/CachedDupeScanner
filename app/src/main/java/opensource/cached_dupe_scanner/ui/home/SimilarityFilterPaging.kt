package opensource.cached_dupe_scanner.ui.home

import opensource.cached_dupe_scanner.cache.DuplicateGroupEntity
import opensource.cached_dupe_scanner.cache.SimilarityClusterEntity
import opensource.cached_dupe_scanner.cache.SimilarityClusterFilterMemberRow
import opensource.cached_dupe_scanner.core.FileMetadata
import opensource.cached_dupe_scanner.core.SortDirection
import opensource.cached_dupe_scanner.storage.SimilarityClusterSortColumn
import opensource.cached_dupe_scanner.storage.SimilarityMemberResolutionEvent
import opensource.cached_dupe_scanner.storage.SimilarityMemberResolutionKind
import opensource.cached_dupe_scanner.storage.SimilaritySettingsRepository

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

internal fun loadFilteredSimilarityClustersPage(
    repository: SimilaritySettingsRepository,
    settingId: Long,
    sortColumn: SimilarityClusterSortColumn,
    sortDirection: SortDirection,
    definition: ResultsFilterDefinition,
    startOffset: Int,
    minMatches: Int,
    sourcePageSize: Int,
    memberPageSize: Int = 200,
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
    var resolutionProcessed = 0
    var resolutionTotal = 0
    var currentPageMatches = emptyMap<Long, Boolean>()
    val publishResolutionEvent: (SimilarityMemberResolutionEvent) -> Unit = { event ->
        if (event.completed) {
            resolutionProcessed = (resolutionProcessed + 1).coerceAtMost(resolutionTotal)
        }
        onResolutionProgress(
            SimilarityFilterResolutionProgress(
                processed = resolutionProcessed,
                total = resolutionTotal,
                currentPath = event.path,
                kind = event.kind
            )
        )
    }
    val page = loadFilteredSourcePage(
        startCursor = startOffset,
        minMatches = minMatches,
        loadPage = { offset ->
            val clusters = repository.listClustersPage(
                settingId = settingId,
                offset = offset,
                limit = sourcePageSize,
                sortColumn = sortColumn,
                direction = sortDirection
            )
            val clusterIds = clusters.map { cluster -> cluster.clusterId }
            val addedResolutionWork = repository.countFilterResolutionWorkForClusters(
                settingId = settingId,
                clusterIds = clusterIds,
                resolveDimensions = resolveDimensions,
                resolveDurations = resolveDurations
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
            currentPageMatches = if (needsMembers && clusters.isNotEmpty()) {
                matchSimilarityClustersFromMemberPages(
                    repository = repository,
                    settingId = settingId,
                    clusters = clusters,
                    definition = definition,
                    memberPageSize = memberPageSize
                )
            } else {
                emptyMap()
            }
            SourcePage(
                items = clusters,
                nextCursor = offset + clusters.size,
                exhausted = clusters.isEmpty() || clusters.size < sourcePageSize
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
        nextSourceOffset = page.nextCursor ?: startOffset,
        exhausted = page.exhausted
    )
}

private fun matchSimilarityClustersFromMemberPages(
    repository: SimilaritySettingsRepository,
    settingId: Long,
    clusters: List<SimilarityClusterEntity>,
    definition: ResultsFilterDefinition,
    memberPageSize: Int
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
