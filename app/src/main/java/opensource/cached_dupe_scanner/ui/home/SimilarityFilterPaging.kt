package opensource.cached_dupe_scanner.ui.home

import opensource.cached_dupe_scanner.cache.DuplicateGroupEntity
import opensource.cached_dupe_scanner.cache.SimilarityClusterEntity
import opensource.cached_dupe_scanner.core.SortDirection
import opensource.cached_dupe_scanner.storage.SimilarityClusterSortColumn
import opensource.cached_dupe_scanner.storage.SimilarityMemberSortColumn
import opensource.cached_dupe_scanner.storage.SimilaritySettingsRepository

internal data class FilteredSimilarityClustersPage(
    val clusters: List<SimilarityClusterEntity>,
    val nextSourceOffset: Int,
    val exhausted: Boolean
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
    memberPageSize: Int = 200
): FilteredSimilarityClustersPage {
    if (sourcePageSize <= 0 || memberPageSize <= 0) {
        return FilteredSimilarityClustersPage(emptyList(), startOffset, exhausted = true)
    }
    val needsMembers = definition.requiresGroupMembers(SIMILARITY_FILTER_TARGETS)
    val resolveDimensions = definition.hasActiveTarget(
        target = ResultsFilterTarget.SameResolution,
        supportedTargets = SIMILARITY_FILTER_TARGETS
    )
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
            SourcePage(
                items = clusters,
                nextCursor = offset + clusters.size,
                exhausted = clusters.isEmpty() || clusters.size < sourcePageSize
            )
        },
        transformMatch = { cluster ->
            val group = cluster.asFilterGroup()
            val matched = if (needsMembers) {
                matchesResultsFilterPagedMembers(
                    definition = definition,
                    group = group,
                    supportedTargets = SIMILARITY_FILTER_TARGETS,
                    memberPages = {
                        sequence {
                            var memberOffset = 0
                            do {
                                val members = repository.listClusterMembersPage(
                                    clusterId = cluster.clusterId,
                                    offset = memberOffset,
                                    limit = memberPageSize,
                                    sortColumn = SimilarityMemberSortColumn.Position,
                                    direction = SortDirection.Asc,
                                    resolveDimensions = resolveDimensions
                                )
                                if (members.isNotEmpty()) {
                                    yield(members.map { it.metadata })
                                    memberOffset += members.size
                                }
                            } while (members.size == memberPageSize)
                        }
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

internal fun SimilarityClusterEntity.asFilterGroup(): DuplicateGroupEntity {
    return DuplicateGroupEntity(
        sizeBytes = if (fileCount > 0) totalBytes / fileCount else 0L,
        hashHex = clusterKey,
        fileCount = fileCount,
        totalBytes = totalBytes,
        updatedAtMillis = updatedAtMillis
    )
}
