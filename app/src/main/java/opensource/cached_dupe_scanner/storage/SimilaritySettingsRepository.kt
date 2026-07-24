package opensource.cached_dupe_scanner.storage

import opensource.cached_dupe_scanner.cache.CacheDatabase
import opensource.cached_dupe_scanner.cache.CachedFileEntity
import opensource.cached_dupe_scanner.cache.FileCacheDao
import opensource.cached_dupe_scanner.cache.SimilarityClusterEntity
import opensource.cached_dupe_scanner.cache.SimilarityClusterDurationStatsEntity
import opensource.cached_dupe_scanner.cache.SimilarityClusterDurationStatsRow
import opensource.cached_dupe_scanner.cache.SimilarityClusterFilterMemberRow
import opensource.cached_dupe_scanner.cache.SimilarityClusterMemberEntity
import opensource.cached_dupe_scanner.cache.SimilarityClusterMemberFileRow
import opensource.cached_dupe_scanner.cache.SimilarityDurationFeatureEntity
import opensource.cached_dupe_scanner.cache.SimilarityDurationFeatureRow
import opensource.cached_dupe_scanner.cache.SimilarityExactThumbnailFeatureEntity
import opensource.cached_dupe_scanner.cache.SimilarityFilterMetadataResolutionRow
import opensource.cached_dupe_scanner.cache.SimilarityMaintenanceRunEntity
import opensource.cached_dupe_scanner.cache.SimilaritySettingEntity
import opensource.cached_dupe_scanner.cache.SimilaritySettingFileEntity
import opensource.cached_dupe_scanner.cache.SimilaritySettingsDao
import opensource.cached_dupe_scanner.cache.StoredHash
import opensource.cached_dupe_scanner.core.AndroidMediaDimensionsExtractor
import opensource.cached_dupe_scanner.core.AndroidVideoDurationExtractor
import opensource.cached_dupe_scanner.core.AndroidVideoFrameSignatureExtractor
import opensource.cached_dupe_scanner.core.DurationNeighborListStep
import opensource.cached_dupe_scanner.core.DurationToleranceStep
import opensource.cached_dupe_scanner.core.ExactThumbnailHashStep
import opensource.cached_dupe_scanner.core.FileMetadata
import opensource.cached_dupe_scanner.core.MediaDimensions
import opensource.cached_dupe_scanner.core.MediaDimensionsExtractor
import opensource.cached_dupe_scanner.core.SIMILARITY_METHOD_DURATION_NEIGHBOR_LIST
import opensource.cached_dupe_scanner.core.SIMILARITY_METHOD_DURATION_TOLERANCE
import opensource.cached_dupe_scanner.core.SIMILARITY_METHOD_EXACT_THUMBNAIL
import opensource.cached_dupe_scanner.core.SimilarityMediaScope
import opensource.cached_dupe_scanner.core.SimilaritySettingDraft
import opensource.cached_dupe_scanner.core.SortDirection
import opensource.cached_dupe_scanner.core.VideoDurationExtractor
import opensource.cached_dupe_scanner.core.VideoFrameSignatureExtractor
import opensource.cached_dupe_scanner.core.buildDurationNeighborListSignature
import opensource.cached_dupe_scanner.core.buildDurationToleranceSignature
import opensource.cached_dupe_scanner.core.buildThumbnailHashClusterKey
import opensource.cached_dupe_scanner.core.durationNeighborListSettingDraft
import opensource.cached_dupe_scanner.core.durationNeighborListStepFromParams
import opensource.cached_dupe_scanner.core.durationNeighborToleranceMillis
import opensource.cached_dupe_scanner.core.durationToleranceMillis
import opensource.cached_dupe_scanner.core.durationToleranceSettingDraft
import opensource.cached_dupe_scanner.core.durationToleranceStepFromParams
import opensource.cached_dupe_scanner.core.exactThumbnailSettingDraft
import opensource.cached_dupe_scanner.core.exactThumbnailStepFromParams
import opensource.cached_dupe_scanner.core.isSha256HashHex
import opensource.cached_dupe_scanner.core.normalizedSimilaritySettingDisplayName
import opensource.cached_dupe_scanner.core.sanitizeScanWorkerCount
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorCompletionService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs

data class SimilarityMaintenanceProgress(
    val total: Int,
    val processed: Int,
    val skipped: Int,
    val clusterCandidates: Int,
    val currentPath: String?,
    val settingName: String?
)

data class SimilarityMaintenanceSummary(
    val settingCount: Int,
    val candidateCount: Int,
    val processedCount: Int,
    val skippedCount: Int,
    val clusterCount: Int,
    val duplicateFileCount: Int,
    val cancelled: Boolean
)

enum class SimilarityClearMode {
    Standard,
    Incremental
}

enum class SimilarityClearPhase {
    Preparing,
    ClusterMembers,
    Clusters,
    FilesAndFeatures,
    OrphanFeatures,
    History
}

data class SimilarityClearProgress(
    val total: Int,
    val processed: Int,
    val remaining: Int,
    val phase: SimilarityClearPhase
)

data class SimilarityClearSummary(
    val total: Int,
    val processed: Int,
    val remaining: Int,
    val cancelled: Boolean
)

data class SimilarityClusterMember(
    val metadata: FileMetadata,
    val durationMillis: Long? = null
)

data class SimilarityClusterSummary(
    val clusterCount: Int = 0,
    val fileCount: Int = 0
)

enum class SimilarityClusterSortColumn {
    FileCount,
    TotalSize
}

enum class SimilarityMemberSortColumn {
    Position,
    Path,
    Modified
}

enum class SimilarityMemberResolutionKind {
    Dimensions,
    Duration
}

data class SimilarityMemberResolutionEvent(
    val kind: SimilarityMemberResolutionKind,
    val path: String,
    val completed: Boolean
)

class SimilaritySettingsRepository(
    private val database: CacheDatabase,
    private val fileDao: FileCacheDao,
    private val similarityDao: SimilaritySettingsDao,
    private val frameSignatureExtractor: VideoFrameSignatureExtractor = AndroidVideoFrameSignatureExtractor(),
    private val durationExtractor: VideoDurationExtractor = AndroidVideoDurationExtractor(),
    private val mediaDimensionsExtractor: MediaDimensionsExtractor = AndroidMediaDimensionsExtractor(),
    private val workerCountProvider: () -> Int = { 1 }
) : CacheMutationObserver {
    private val maintenanceLock = Any()

    fun createExactThumbnailSetting(
        mediaScope: SimilarityMediaScope,
        minSizeBytes: Long,
        step: ExactThumbnailHashStep,
        enabled: Boolean = false,
        displayName: String? = null
    ): SimilaritySettingEntity {
        val defaultDraft = exactThumbnailSettingDraft(
            mediaScope = mediaScope,
            minSizeBytes = minSizeBytes,
            step = step
        )
        return createOrGetSetting(
            draft = defaultDraft.copy(
                displayName = normalizedSimilaritySettingDisplayName(displayName, defaultDraft.displayName)
            ),
            enabled = enabled,
            updateExistingDisplayName = displayName?.isNotBlank() == true
        )
    }

    fun createDurationToleranceSetting(
        minSizeBytes: Long,
        step: DurationToleranceStep,
        enabled: Boolean = false,
        displayName: String? = null
    ): SimilaritySettingEntity {
        val defaultDraft = durationToleranceSettingDraft(minSizeBytes = minSizeBytes, step = step)
        return createOrGetSetting(
            draft = defaultDraft.copy(
                displayName = normalizedSimilaritySettingDisplayName(displayName, defaultDraft.displayName)
            ),
            enabled = enabled,
            updateExistingDisplayName = displayName?.isNotBlank() == true
        )
    }

    fun createDurationNeighborListSetting(
        minSizeBytes: Long,
        step: DurationNeighborListStep,
        enabled: Boolean = false,
        displayName: String? = null
    ): SimilaritySettingEntity {
        val defaultDraft = durationNeighborListSettingDraft(minSizeBytes = minSizeBytes, step = step)
        return createOrGetSetting(
            draft = defaultDraft.copy(
                displayName = normalizedSimilaritySettingDisplayName(displayName, defaultDraft.displayName)
            ),
            enabled = enabled,
            updateExistingDisplayName = displayName?.isNotBlank() == true
        )
    }

    fun createOrGetSetting(
        draft: SimilaritySettingDraft,
        enabled: Boolean,
        updateExistingDisplayName: Boolean = false
    ): SimilaritySettingEntity {
        val now = System.currentTimeMillis()
        database.runInTransaction {
            val insertedId = similarityDao.insertSetting(
                SimilaritySettingEntity(
                    methodId = draft.methodId,
                    mediaScope = draft.mediaScope.name,
                    minSizeBytes = draft.minSizeBytes,
                    paramsJson = draft.paramsJson,
                    paramsHash = draft.paramsHash,
                    displayName = draft.displayName,
                    enabled = enabled,
                    createdAtMillis = now,
                    updatedAtMillis = now
                )
            )
            if (insertedId > 0L) return@runInTransaction
            val existing = similarityDao.getSettingByIdentity(
                methodId = draft.methodId,
                mediaScope = draft.mediaScope.name,
                minSizeBytes = draft.minSizeBytes,
                paramsHash = draft.paramsHash
            ) ?: return@runInTransaction
            if (existing.paramsJson != draft.paramsJson) {
                error("Similarity parameter hash collision for ${draft.methodId}.")
            }
            val nextDisplayName = if (updateExistingDisplayName) draft.displayName else existing.displayName
            val nextEnabled = existing.enabled || enabled
            if (existing.displayName != nextDisplayName || existing.enabled != nextEnabled) {
                similarityDao.updateSetting(
                    existing.copy(
                        displayName = nextDisplayName,
                        enabled = nextEnabled,
                        updatedAtMillis = now
                    )
                )
            }
        }
        return requireNotNull(
            similarityDao.getSettingByIdentity(
                methodId = draft.methodId,
                mediaScope = draft.mediaScope.name,
                minSizeBytes = draft.minSizeBytes,
                paramsHash = draft.paramsHash
            )
        )
    }

    fun listSettings(): List<SimilaritySettingEntity> {
        return similarityDao.listSettings()
    }

    fun hasEnabledSettings(): Boolean {
        return similarityDao.countEnabledSettings() > 0
    }

    fun setEnabled(settingId: Long, enabled: Boolean) {
        database.runInTransaction {
            similarityDao.updateSettingEnabled(
                settingId = settingId,
                enabled = enabled,
                updatedAtMillis = System.currentTimeMillis()
            )
        }
    }

    fun renameSetting(settingId: Long, displayName: String) {
        database.runInTransaction {
            val existing = similarityDao.getSetting(settingId) ?: return@runInTransaction
            val normalizedName = normalizedSimilaritySettingDisplayName(displayName, existing.displayName)
            if (normalizedName == existing.displayName) return@runInTransaction
            similarityDao.updateSettingDisplayName(
                settingId = settingId,
                displayName = normalizedName,
                updatedAtMillis = System.currentTimeMillis()
            )
        }
    }

    fun clearSettingResults(settingId: Long): SimilarityClearSummary {
        return clearSettingResults(
            settingId = settingId,
            mode = SimilarityClearMode.Standard,
            shouldContinue = { true },
            onProgress = {}
        )
    }

    fun clearSettingResults(
        settingId: Long,
        mode: SimilarityClearMode,
        shouldContinue: () -> Boolean,
        onProgress: (SimilarityClearProgress) -> Unit
    ): SimilarityClearSummary {
        return synchronized(maintenanceLock) {
            when (mode) {
                SimilarityClearMode.Standard -> clearSettingResultsByStage(
                    settingId = settingId,
                    shouldContinue = shouldContinue,
                    onProgress = onProgress
                )
                SimilarityClearMode.Incremental -> clearSettingResultsIncrementally(
                    settingId = settingId,
                    shouldContinue = shouldContinue,
                    onProgress = onProgress
                )
            }
        }
    }

    fun deleteSetting(settingId: Long) {
        database.runInTransaction {
            clearSettingDataLocked(settingId)
            similarityDao.deleteMaintenanceRunsForSetting(settingId)
            similarityDao.deleteSetting(settingId)
        }
    }

    fun clearAllResults() {
        database.runInTransaction {
            similarityDao.deleteAllSettingFiles()
            similarityDao.deleteAllExactThumbnailFeatures()
            similarityDao.deleteAllDurationFeatures()
            similarityDao.deleteAllClusterMembers()
            similarityDao.deleteAllClusters()
            similarityDao.deleteAllMaintenanceRuns()
        }
    }

    fun listClusters(settingId: Long): List<SimilarityClusterEntity> {
        return similarityDao.listActiveClusters(settingId)
    }

    fun getCluster(settingId: Long, clusterId: Long): SimilarityClusterEntity? {
        return similarityDao.getStoredCluster(settingId = settingId, clusterId = clusterId)
    }

    fun getClusterSummary(settingId: Long): SimilarityClusterSummary {
        val row = similarityDao.storedClusterSummary(settingId)
        return SimilarityClusterSummary(
            clusterCount = row.clusterCount,
            fileCount = row.fileCount
        )
    }

    fun clusterIdsContainingPaths(normalizedPaths: Set<String>): Set<Long> {
        if (normalizedPaths.isEmpty()) return emptySet()
        return normalizedPaths
            .chunked(SIMILARITY_DB_BIND_CHUNK_SIZE)
            .flatMapTo(linkedSetOf()) { chunk ->
                similarityDao.listClusterIdsForMemberPaths(chunk)
            }
    }

    fun listClustersPage(
        settingId: Long,
        offset: Int,
        limit: Int,
        sortColumn: SimilarityClusterSortColumn,
        direction: SortDirection
    ): List<SimilarityClusterEntity> {
        val safeOffset = offset.coerceAtLeast(0)
        val safeLimit = limit.coerceAtLeast(0)
        return when (sortColumn) {
            SimilarityClusterSortColumn.FileCount -> {
                if (direction == SortDirection.Asc) {
                    similarityDao.listStoredClustersByFileCountAsc(
                        settingId = settingId,
                        offset = safeOffset,
                        limit = safeLimit
                    )
                } else {
                    similarityDao.listStoredClustersByFileCountDesc(
                        settingId = settingId,
                        offset = safeOffset,
                        limit = safeLimit
                    )
                }
            }
            SimilarityClusterSortColumn.TotalSize -> {
                if (direction == SortDirection.Asc) {
                    similarityDao.listStoredClustersByTotalSizeAsc(
                        settingId = settingId,
                        offset = safeOffset,
                        limit = safeLimit
                    )
                } else {
                    similarityDao.listStoredClustersByTotalSizeDesc(
                        settingId = settingId,
                        offset = safeOffset,
                        limit = safeLimit
                    )
                }
            }
        }
    }

    internal fun listClustersPageAfter(
        settingId: Long,
        afterCluster: SimilarityClusterEntity,
        limit: Int,
        sortColumn: SimilarityClusterSortColumn,
        direction: SortDirection
    ): List<SimilarityClusterEntity> {
        val safeLimit = limit.coerceAtLeast(0)
        return when (sortColumn) {
            SimilarityClusterSortColumn.FileCount -> {
                if (direction == SortDirection.Asc) {
                    similarityDao.listStoredClustersByFileCountAscAfter(
                        settingId = settingId,
                        afterFileCount = afterCluster.fileCount,
                        afterTotalBytes = afterCluster.totalBytes,
                        afterClusterKey = afterCluster.clusterKey,
                        limit = safeLimit
                    )
                } else {
                    similarityDao.listStoredClustersByFileCountDescAfter(
                        settingId = settingId,
                        afterFileCount = afterCluster.fileCount,
                        afterTotalBytes = afterCluster.totalBytes,
                        afterClusterKey = afterCluster.clusterKey,
                        limit = safeLimit
                    )
                }
            }
            SimilarityClusterSortColumn.TotalSize -> {
                if (direction == SortDirection.Asc) {
                    similarityDao.listStoredClustersByTotalSizeAscAfter(
                        settingId = settingId,
                        afterFileCount = afterCluster.fileCount,
                        afterTotalBytes = afterCluster.totalBytes,
                        afterClusterKey = afterCluster.clusterKey,
                        limit = safeLimit
                    )
                } else {
                    similarityDao.listStoredClustersByTotalSizeDescAfter(
                        settingId = settingId,
                        afterFileCount = afterCluster.fileCount,
                        afterTotalBytes = afterCluster.totalBytes,
                        afterClusterKey = afterCluster.clusterKey,
                        limit = safeLimit
                    )
                }
            }
        }
    }

    fun listClustersAfterId(
        settingId: Long,
        afterClusterId: Long,
        limit: Int
    ): List<SimilarityClusterEntity> {
        return similarityDao.listStoredClustersAfterId(
            settingId = settingId,
            afterClusterId = afterClusterId.coerceAtLeast(0L),
            limit = limit.coerceAtLeast(0)
        )
    }

    fun clusterSnapshotKey(settingId: Long, pageSize: Int = 200): String {
        require(pageSize > 0) { "pageSize must be positive" }
        val digest = MessageDigest.getInstance("SHA-256")
        var afterClusterId = 0L
        while (true) {
            val page = similarityDao.listStoredClustersAfterId(
                settingId = settingId,
                afterClusterId = afterClusterId,
                limit = pageSize
            )
            if (page.isEmpty()) break
            page.forEach { cluster ->
                digest.updateLong(cluster.clusterId)
                digest.updateString(cluster.clusterKey)
                digest.updateLong(cluster.fileCount.toLong())
                digest.updateLong(cluster.totalBytes)
                digest.updateLong(cluster.updatedAtMillis)
                afterClusterId = cluster.clusterId
            }
            if (page.size < pageSize) break
        }
        return digest.digest().joinToString(separator = "") { byte ->
            "%02x".format(byte.toInt() and 0xff)
        }
    }

    fun listClusterMembers(clusterId: Long): List<SimilarityClusterMember> {
        return similarityDao.listStoredClusterMembers(clusterId).map { row ->
            row.toClusterMember()
        }
    }

    fun listClusterMembersPage(
        clusterId: Long,
        offset: Int,
        limit: Int,
        sortColumn: SimilarityMemberSortColumn = SimilarityMemberSortColumn.Position,
        direction: SortDirection = SortDirection.Asc,
        resolveDimensions: Boolean = false,
        resolveDurations: Boolean = false,
        onResolutionEvent: (SimilarityMemberResolutionEvent) -> Unit = {}
    ): List<SimilarityClusterMember> {
        val safeOffset = offset.coerceAtLeast(0)
        val safeLimit = limit.coerceAtLeast(0)
        val rows = when (sortColumn) {
            SimilarityMemberSortColumn.Position -> {
                if (direction == SortDirection.Desc) {
                    similarityDao.listStoredClusterMembersPageDescending(
                        clusterId = clusterId,
                        offset = safeOffset,
                        limit = safeLimit
                    )
                } else {
                    similarityDao.listStoredClusterMembersPage(
                        clusterId = clusterId,
                        offset = safeOffset,
                        limit = safeLimit
                    )
                }
            }
            SimilarityMemberSortColumn.Path -> {
                if (direction == SortDirection.Desc) {
                    similarityDao.listStoredClusterMembersPageByPathDesc(
                        clusterId = clusterId,
                        offset = safeOffset,
                        limit = safeLimit
                    )
                } else {
                    similarityDao.listStoredClusterMembersPageByPathAsc(
                        clusterId = clusterId,
                        offset = safeOffset,
                        limit = safeLimit
                    )
                }
            }
            SimilarityMemberSortColumn.Modified -> {
                if (direction == SortDirection.Desc) {
                    similarityDao.listStoredClusterMembersPageByModifiedDesc(
                        clusterId = clusterId,
                        offset = safeOffset,
                        limit = safeLimit
                    )
                } else {
                    similarityDao.listStoredClusterMembersPageByModifiedAsc(
                        clusterId = clusterId,
                        offset = safeOffset,
                        limit = safeLimit
                    )
                }
            }
        }
        var resolvedRows = rows
        if (resolveDimensions) {
            resolvedRows = resolveUncheckedDimensions(resolvedRows, onResolutionEvent)
        }
        if (resolveDurations) {
            resolvedRows = resolveUncheckedDurations(resolvedRows, onResolutionEvent)
        }
        return resolvedRows.map { row ->
            row.toClusterMember()
        }
    }

    internal fun countFilterResolutionWorkForClusters(
        settingId: Long,
        clusterIds: List<Long>,
        resolveDimensions: Boolean,
        resolveDurations: Boolean
    ): Int {
        val distinctClusterIds = clusterIds.distinct()
        if (distinctClusterIds.isEmpty()) return 0
        var count = 0L
        distinctClusterIds.chunked(SIMILARITY_DB_BIND_CHUNK_SIZE).forEach { clusterIdChunk ->
            val work = similarityDao.countFilterResolutionWorkForClusters(
                settingId = settingId,
                clusterIds = clusterIdChunk,
                resolveDimensions = resolveDimensions,
                resolveDurations = resolveDurations
            )
            count += work.dimensionCount.toLong() + work.durationCount.toLong()
        }
        return count.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    internal fun resolveFilterMetadataForClusters(
        settingId: Long,
        clusterIds: List<Long>,
        resolveDimensions: Boolean,
        resolveDurations: Boolean,
        onResolutionEvent: (SimilarityMemberResolutionEvent) -> Unit
    ) {
        val distinctClusterIds = clusterIds.distinct()
        if (distinctClusterIds.isEmpty() || (!resolveDimensions && !resolveDurations)) return
        distinctClusterIds.chunked(SIMILARITY_DB_BIND_CHUNK_SIZE).forEach { clusterIdChunk ->
            var afterClusterId = -1L
            var afterPosition = -1
            var afterFileId = -1L
            while (!Thread.currentThread().isInterrupted) {
                val queriedRows = similarityDao.listUncheckedFilterMetadataMembersForClusters(
                    settingId = settingId,
                    clusterIds = clusterIdChunk,
                    resolveDimensions = resolveDimensions,
                    resolveDurations = resolveDurations,
                    afterClusterId = afterClusterId,
                    afterPosition = afterPosition,
                    afterFileId = afterFileId,
                    limit = SIMILARITY_FILTER_RESOLUTION_BATCH_SIZE
                )
                val uncheckedRows = queriedRows
                    .distinctBy { row -> row.fileId }
                    .map(SimilarityFilterMetadataResolutionRow::toMemberFileRow)
                if (uncheckedRows.isEmpty()) break
                val lastQueriedRow = queriedRows.last()
                afterClusterId = lastQueriedRow.clusterId
                afterPosition = lastQueriedRow.position
                afterFileId = lastQueriedRow.fileId
                val rowsWithDimensions = if (resolveDimensions) {
                    resolveUncheckedDimensions(uncheckedRows, onResolutionEvent)
                } else {
                    uncheckedRows
                }
                if (resolveDurations) {
                    resolveUncheckedDurations(rowsWithDimensions, onResolutionEvent)
                }
                if (queriedRows.size < SIMILARITY_FILTER_RESOLUTION_BATCH_SIZE) break
            }
        }
    }

    internal fun visitFilterMemberPagesForClusters(
        settingId: Long,
        clusterIds: List<Long>,
        pageSize: Int,
        onPage: (List<SimilarityClusterFilterMemberRow>) -> Set<Long>
    ) {
        val safePageSize = pageSize.coerceIn(1, SIMILARITY_FILTER_MEMBER_BATCH_SIZE)
        clusterIds.distinct()
            .chunked(SIMILARITY_DB_BIND_CHUNK_SIZE)
            .forEach { clusterIdChunk ->
                val remainingClusterIds = clusterIdChunk.toMutableSet()
                var afterClusterId = -1L
                var afterPosition = -1
                var afterFileId = -1L
                while (remainingClusterIds.isNotEmpty() && !Thread.currentThread().isInterrupted) {
                    val rows = similarityDao.listFilterMembersForClustersPage(
                        settingId = settingId,
                        clusterIds = remainingClusterIds.toList(),
                        afterClusterId = afterClusterId,
                        afterPosition = afterPosition,
                        afterFileId = afterFileId,
                        limit = safePageSize
                    )
                    if (rows.isEmpty()) break
                    remainingClusterIds.removeAll(onPage(rows))
                    val last = rows.last()
                    afterClusterId = last.clusterId
                    afterPosition = last.position
                    afterFileId = last.fileId
                    if (rows.size < safePageSize) break
                }
            }
    }

    internal fun listFilterDurationStatsForClusters(
        settingId: Long,
        clusterIds: List<Long>
    ): List<SimilarityClusterDurationStatsRow> {
        return clusterIds.distinct().chunked(SIMILARITY_DB_BIND_CHUNK_SIZE).flatMap { clusterIdChunk ->
            val cachedRows = similarityDao.listCachedDurationStatsForClusters(
                settingId = settingId,
                clusterIds = clusterIdChunk
            )
            val cachedByClusterId = cachedRows.associateBy { row -> row.clusterId }
            val uncachedClusterIds = clusterIdChunk.filterNot(cachedByClusterId::containsKey)
            val refreshedRows = if (uncachedClusterIds.isEmpty()) {
                emptyList()
            } else {
                database.runInTransaction<List<SimilarityClusterDurationStatsRow>> {
                    calculateAndStoreDurationStatsForClusters(
                        settingId = settingId,
                        clusterIds = uncachedClusterIds
                    )
                }
            }
            val rowsByClusterId = (cachedRows + refreshedRows).associateBy { row -> row.clusterId }
            clusterIdChunk.mapNotNull(rowsByClusterId::get)
        }
    }

    private fun calculateAndStoreDurationStatsForClusters(
        settingId: Long,
        clusterIds: List<Long>
    ): List<SimilarityClusterDurationStatsRow> {
        val rows = clusterIds.distinct()
            .chunked(SIMILARITY_DB_BIND_CHUNK_SIZE)
            .flatMap { clusterIdChunk ->
                similarityDao.calculateDurationStatsForClusters(
                    settingId = settingId,
                    clusterIds = clusterIdChunk
                )
            }
        if (rows.isNotEmpty()) {
            similarityDao.upsertClusterDurationStats(
                rows.map { row ->
                    SimilarityClusterDurationStatsEntity(
                        clusterId = row.clusterId,
                        clusterUpdatedAtMillis = row.clusterUpdatedAtMillis,
                        memberCount = row.memberCount,
                        checkedCount = row.checkedCount,
                        durationCount = row.durationCount,
                        durationSumMillis = row.durationSumMillis,
                        minimumDurationMillis = row.minimumDurationMillis,
                        maximumDurationMillis = row.maximumDurationMillis
                    )
                }
            )
        }
        return rows
    }

    fun generateEnabledResults(
        shouldContinue: () -> Boolean,
        onProgress: (SimilarityMaintenanceProgress) -> Unit
    ): SimilarityMaintenanceSummary {
        return synchronized(maintenanceLock) {
            val settings = similarityDao.listEnabledSettings()
            runSettingsMaintenance(
                settings = settings,
                rebuild = false,
                shouldContinue = shouldContinue,
                onProgress = onProgress
            )
        }
    }

    fun refreshRestoredFileResults(
        normalizedPath: String,
        shouldContinue: () -> Boolean
    ): Int {
        return synchronized(maintenanceLock) {
            val entity = fileDao.getByNormalizedPath(normalizedPath) ?: return@synchronized 0
            var refreshedSettingCount = 0
            for (setting in similarityDao.listRestoreEligibleSettings()) {
                if (!shouldContinue()) break
                val mediaScope = runCatching { SimilarityMediaScope.valueOf(setting.mediaScope) }
                    .getOrDefault(SimilarityMediaScope.Video)
                if (entity.sizeBytes < setting.minSizeBytes || !mediaScope.accepts(entity.normalizedPath)) {
                    continue
                }

                val feature = calculateFeature(setting, mediaScope, entity, shouldContinue)
                if (!shouldContinue()) break
                val dimensions = if (feature == null) {
                    null
                } else {
                    extractDimensions(mediaScope, entity, shouldContinue)
                }
                if (!shouldContinue()) break
                val restoredFile = prepareSettingFile(
                    setting = setting,
                    mediaScope = mediaScope,
                    entity = entity,
                    feature = feature,
                    dimensions = dimensions,
                    updatedAtMillis = System.currentTimeMillis()
                )
                database.runInTransaction {
                    similarityDao.upsertSettingFiles(listOf(restoredFile.settingFile))
                    restoredFile.exactFeature?.let { exactFeature ->
                        similarityDao.upsertExactThumbnailFeatures(listOf(exactFeature))
                    }
                    restoredFile.durationFeature?.let { durationFeature ->
                        similarityDao.upsertDurationFeatures(listOf(durationFeature))
                    }
                    similarityDao.deleteClusterDurationStatsForMemberFileIds(
                        listOf(restoredFile.settingFile.fileId)
                    )
                }

                replaceClusters(setting, buildClusters(setting))
                refreshedSettingCount += 1
            }
            refreshedSettingCount
        }
    }

    fun runSettingMaintenance(
        settingId: Long,
        rebuild: Boolean,
        shouldContinue: () -> Boolean,
        onProgress: (SimilarityMaintenanceProgress) -> Unit
    ): SimilarityMaintenanceSummary {
        return synchronized(maintenanceLock) {
            val setting = similarityDao.getSetting(settingId) ?: return@synchronized emptySummary(cancelled = false)
            runSettingsMaintenance(
                settings = listOf(setting),
                rebuild = rebuild,
                shouldContinue = shouldContinue,
                onProgress = onProgress
            )
        }
    }

    override fun onCachedFilesChanged(normalizedPaths: List<String>) {
        if (normalizedPaths.isEmpty()) return
        normalizedPaths.chunked(SIMILARITY_DB_BIND_CHUNK_SIZE).forEach { chunk ->
            val affectedClusterIds = similarityDao.listClusterIdsForMemberPaths(chunk)
            if (affectedClusterIds.isNotEmpty()) {
                similarityDao.deleteClusterDurationStatsByIds(affectedClusterIds)
            }
            similarityDao.deleteSettingFilesByPaths(chunk)
            similarityDao.deleteExactThumbnailFeaturesByPaths(chunk)
            similarityDao.deleteDurationFeaturesByPaths(chunk)
            similarityDao.deleteClusterMembersByPaths(chunk)
            repairStoredClusters(affectedClusterIds)
        }
    }

    override fun onCacheCleared() {
        similarityDao.deleteAllSettingFiles()
        similarityDao.deleteAllExactThumbnailFeatures()
        similarityDao.deleteAllDurationFeatures()
        similarityDao.deleteAllClusterMembers()
        similarityDao.deleteAllClusters()
    }

    private fun runSettingsMaintenance(
        settings: List<SimilaritySettingEntity>,
        rebuild: Boolean,
        shouldContinue: () -> Boolean,
        onProgress: (SimilarityMaintenanceProgress) -> Unit
    ): SimilarityMaintenanceSummary {
        var candidateCount = 0
        var processedCount = 0
        var skippedCount = 0
        var clusterCount = 0
        var duplicateFileCount = 0
        val workerCount = sanitizeScanWorkerCount(workerCountProvider())

        settings.forEach { setting ->
            if (!shouldContinue()) {
                return SimilarityMaintenanceSummary(
                    settingCount = settings.size,
                    candidateCount = candidateCount,
                    processedCount = processedCount,
                    skippedCount = skippedCount,
                    clusterCount = clusterCount,
                    duplicateFileCount = duplicateFileCount,
                    cancelled = true
                )
            }
            val summary = runSingleSettingMaintenance(
                setting = setting,
                rebuild = rebuild,
                workerCount = workerCount,
                shouldContinue = shouldContinue
            ) { progress ->
                onProgress(
                    progress.copy(
                        total = progress.total,
                        processed = processedCount + progress.processed,
                        skipped = skippedCount + progress.skipped
                    )
                )
            }
            candidateCount += summary.candidateCount
            processedCount += summary.processedCount
            skippedCount += summary.skippedCount
            clusterCount += summary.clusterCount
            duplicateFileCount += summary.duplicateFileCount
            if (summary.cancelled) {
                return SimilarityMaintenanceSummary(
                    settingCount = settings.size,
                    candidateCount = candidateCount,
                    processedCount = processedCount,
                    skippedCount = skippedCount,
                    clusterCount = clusterCount,
                    duplicateFileCount = duplicateFileCount,
                    cancelled = true
                )
            }
        }

        return SimilarityMaintenanceSummary(
            settingCount = settings.size,
            candidateCount = candidateCount,
            processedCount = processedCount,
            skippedCount = skippedCount,
            clusterCount = clusterCount,
            duplicateFileCount = duplicateFileCount,
            cancelled = false
        )
    }

    private fun runSingleSettingMaintenance(
        setting: SimilaritySettingEntity,
        rebuild: Boolean,
        workerCount: Int,
        shouldContinue: () -> Boolean,
        onProgress: (SimilarityMaintenanceProgress) -> Unit
    ): SimilarityMaintenanceSummary {
        val startedAt = System.currentTimeMillis()
        if (rebuild) {
            database.runInTransaction { clearSettingDataLocked(setting.settingId) }
        }
        val mediaScope = runCatching { SimilarityMediaScope.valueOf(setting.mediaScope) }
            .getOrDefault(SimilarityMediaScope.Video)
        val total = countCandidates(mediaScope, setting.minSizeBytes)
        var processed = 0
        var skipped = 0
        var afterPath = ""
        var currentPath: String? = null

        while (shouldContinue()) {
            val batch = listCandidatesAfter(
                mediaScope = mediaScope,
                minSizeBytes = setting.minSizeBytes,
                afterPath = afterPath,
                limit = SIMILARITY_MAINTENANCE_BATCH_SIZE
            )
            if (batch.isEmpty()) break

            val preparedFiles = mutableListOf<PreparedSettingFile>()
            val workItems = mutableListOf<SimilarityFeatureWorkItem>()
            val now = System.currentTimeMillis()
            val existingFilesById = if (rebuild) {
                emptyMap()
            } else {
                similarityDao.listSettingFilesByIds(
                    settingId = setting.settingId,
                    fileIds = batch.map { entity -> entity.fileId }
                ).associateBy { settingFile -> settingFile.fileId }
            }
            val requiresDimensions = setting.methodId == SIMILARITY_METHOD_EXACT_THUMBNAIL

            for (entity in batch) {
                if (!shouldContinue()) {
                    return finishSingleSummary(
                        setting = setting,
                        startedAt = startedAt,
                        candidateCount = total,
                        processed = processed,
                        skipped = skipped,
                        cancelled = true
                    )
                }
                currentPath = entity.path.ifBlank { entity.normalizedPath }
                val existing = existingFilesById[entity.fileId]
                val storedFileIsFresh = !rebuild &&
                    existing != null &&
                    existing.sizeBytes == entity.sizeBytes &&
                    existing.lastModifiedMillis == entity.lastModifiedMillis
                val readyFeatureIsFresh = storedFileIsFresh &&
                    existing?.status == SIMILARITY_FILE_STATUS_READY
                val skippedFeatureIsFresh = storedFileIsFresh &&
                    existing?.status == SIMILARITY_FILE_STATUS_SKIPPED
                if (
                    skippedFeatureIsFresh ||
                    (readyFeatureIsFresh && (!requiresDimensions || existing?.dimensionsChecked == true))
                ) {
                    if (skippedFeatureIsFresh) skipped += 1
                    processed += 1
                    onProgress(
                        SimilarityMaintenanceProgress(
                            total = total,
                            processed = processed,
                            skipped = skipped,
                            clusterCandidates = 0,
                            currentPath = currentPath,
                            settingName = setting.displayName
                        )
                    )
                    continue
                }
                workItems += SimilarityFeatureWorkItem(
                    entity = entity,
                    freshSettingFile = existing.takeIf { readyFeatureIsFresh }
                )
            }

            val durationMethod = setting.methodId == SIMILARITY_METHOD_DURATION_TOLERANCE ||
                setting.methodId == SIMILARITY_METHOD_DURATION_NEIGHBOR_LIST
            val workItemsByFileId = workItems.associateBy { workItem -> workItem.entity.fileId }
            val reusableDurationsByFileId = if (durationMethod && workItems.isNotEmpty()) {
                similarityDao.listReusableDurationsForFiles(
                    settingId = setting.settingId,
                    mediaScope = setting.mediaScope,
                    fileIds = workItems.map { workItem -> workItem.entity.fileId }
                ).associateBy { reusable -> reusable.fileId }
            } else {
                emptyMap()
            }
            var cancelledDuringReuse = false
            for ((fileId, reusable) in reusableDurationsByFileId) {
                if (!shouldContinue()) {
                    cancelledDuringReuse = true
                    break
                }
                val workItem = workItemsByFileId.getValue(fileId)
                val feature = reusable.durationMillis?.let(CalculatedFeature::Duration)
                preparedFiles += prepareSettingFile(
                    setting = setting,
                    mediaScope = mediaScope,
                    entity = workItem.entity,
                    feature = feature,
                    dimensions = null,
                    updatedAtMillis = now
                )
                if (feature == null) skipped += 1
                processed += 1
                currentPath = workItem.entity.path.ifBlank { workItem.entity.normalizedPath }
                onProgress(
                    SimilarityMaintenanceProgress(
                        total = total,
                        processed = processed,
                        skipped = skipped,
                        clusterCandidates = 0,
                        currentPath = currentPath,
                        settingName = setting.displayName
                    )
                )
            }
            val unresolvedWorkItems = if (reusableDurationsByFileId.isEmpty()) {
                workItems
            } else {
                workItems.filterNot { workItem ->
                    reusableDurationsByFileId.containsKey(workItem.entity.fileId)
                }
            }

            val cancelled = if (cancelledDuringReuse) {
                true
            } else {
                calculateFeatureWorkBatch(
                    setting = setting,
                    mediaScope = mediaScope,
                    workItems = unresolvedWorkItems,
                    updatedAtMillis = now,
                    workerCount = workerCount,
                    shouldContinue = shouldContinue
                ) { result ->
                    val preparedFile = requireNotNull(result.preparedFile)
                    preparedFiles += preparedFile
                    if (result.skipped) skipped += 1
                    processed += 1
                    currentPath = result.workItem.entity.path.ifBlank {
                        result.workItem.entity.normalizedPath
                    }
                    onProgress(
                        SimilarityMaintenanceProgress(
                            total = total,
                            processed = processed,
                            skipped = skipped,
                            clusterCandidates = 0,
                            currentPath = currentPath,
                            settingName = setting.displayName
                        )
                    )
                }
            }

            if (preparedFiles.isNotEmpty()) {
                database.runInTransaction {
                    similarityDao.upsertSettingFiles(preparedFiles.map { prepared -> prepared.settingFile })
                    similarityDao.deleteClusterDurationStatsForMemberFileIds(
                        preparedFiles.map { prepared -> prepared.settingFile.fileId }
                    )
                    val exactFeatures = preparedFiles.mapNotNull { prepared -> prepared.exactFeature }
                    val durationFeatures = preparedFiles.mapNotNull { prepared -> prepared.durationFeature }
                    val replacedDurationFileIds = preparedFiles.mapNotNull { prepared ->
                        prepared.settingFile.fileId.takeIf { prepared.replaceDurationFeature }
                    }
                    if (exactFeatures.isNotEmpty()) similarityDao.upsertExactThumbnailFeatures(exactFeatures)
                    if (replacedDurationFileIds.isNotEmpty()) {
                        similarityDao.deleteDurationFeaturesForSettingByIds(
                            settingId = setting.settingId,
                            fileIds = replacedDurationFileIds
                        )
                    }
                    if (durationFeatures.isNotEmpty()) similarityDao.upsertDurationFeatures(durationFeatures)
                }
            }

            if (cancelled) {
                return finishSingleSummary(
                    setting = setting,
                    startedAt = startedAt,
                    candidateCount = total,
                    processed = processed,
                    skipped = skipped,
                    cancelled = true
                )
            }

            afterPath = batch.last().normalizedPath
            if (batch.size < SIMILARITY_MAINTENANCE_BATCH_SIZE) break
        }

        if (!shouldContinue()) {
            return finishSingleSummary(
                setting = setting,
                startedAt = startedAt,
                candidateCount = total,
                processed = processed,
                skipped = skipped,
                cancelled = true
            )
        }
        val clusterDrafts = buildClusters(setting)
        if (!shouldContinue()) {
            return finishSingleSummary(
                setting = setting,
                startedAt = startedAt,
                candidateCount = total,
                processed = processed,
                skipped = skipped,
                cancelled = true
            )
        }
        replaceClusters(setting, clusterDrafts)
        val clusterSummary = similarityDao.listActiveClusters(setting.settingId)
        val duplicateFiles = clusterSummary.sumOf { cluster -> cluster.fileCount }
        val finishedAt = System.currentTimeMillis()
        database.runInTransaction {
            similarityDao.insertMaintenanceRun(
                SimilarityMaintenanceRunEntity(
                    settingId = setting.settingId,
                    startedAtMillis = startedAt,
                    finishedAtMillis = finishedAt,
                    candidateCount = total,
                    processedCount = processed,
                    skippedCount = skipped,
                    clusterCount = clusterSummary.size,
                    duplicateFileCount = duplicateFiles,
                    cancelled = false
                )
            )
        }

        return SimilarityMaintenanceSummary(
            settingCount = 1,
            candidateCount = total,
            processedCount = processed,
            skippedCount = skipped,
            clusterCount = clusterSummary.size,
            duplicateFileCount = duplicateFiles,
            cancelled = false
        )
    }

    private fun finishSingleSummary(
        setting: SimilaritySettingEntity,
        startedAt: Long,
        candidateCount: Int,
        processed: Int,
        skipped: Int,
        cancelled: Boolean
    ): SimilarityMaintenanceSummary {
        val activeClusters = similarityDao.listActiveClusters(setting.settingId)
        val duplicateFiles = activeClusters.sumOf { cluster -> cluster.fileCount }
        database.runInTransaction {
            similarityDao.insertMaintenanceRun(
                SimilarityMaintenanceRunEntity(
                    settingId = setting.settingId,
                    startedAtMillis = startedAt,
                    finishedAtMillis = System.currentTimeMillis(),
                    candidateCount = candidateCount,
                    processedCount = processed,
                    skippedCount = skipped,
                    clusterCount = activeClusters.size,
                    duplicateFileCount = duplicateFiles,
                    cancelled = cancelled
                )
            )
        }
        return SimilarityMaintenanceSummary(
            settingCount = 1,
            candidateCount = candidateCount,
            processedCount = processed,
            skippedCount = skipped,
            clusterCount = activeClusters.size,
            duplicateFileCount = duplicateFiles,
            cancelled = cancelled
        )
    }

    private fun calculateFeature(
        setting: SimilaritySettingEntity,
        mediaScope: SimilarityMediaScope,
        entity: CachedFileEntity,
        shouldContinue: () -> Boolean
    ): CalculatedFeature? {
        val path = entity.path.ifBlank { entity.normalizedPath }
        val file = File(path)
        if (!file.exists()) return null
        return when (setting.methodId) {
            SIMILARITY_METHOD_EXACT_THUMBNAIL -> {
                val step = exactThumbnailStepFromParams(setting.paramsJson)
                frameSignatureExtractor.signatureWithMetadata(
                    file = file,
                    mediaScope = mediaScope,
                    step = step,
                    shouldContinue = shouldContinue
                )?.let { result ->
                    CalculatedFeature.ExactThumbnail(
                        signature = result.signature,
                        durationMillis = result.durationMillis?.takeIf { value -> value >= 0L }
                    )
                }
            }
            SIMILARITY_METHOD_DURATION_TOLERANCE,
            SIMILARITY_METHOD_DURATION_NEIGHBOR_LIST -> {
                durationExtractor.durationMillis(
                    file = file,
                    shouldContinue = shouldContinue
                )?.let(CalculatedFeature::Duration)
            }
            else -> null
        }
    }

    private fun calculateFeatureWorkBatch(
        setting: SimilaritySettingEntity,
        mediaScope: SimilarityMediaScope,
        workItems: List<SimilarityFeatureWorkItem>,
        updatedAtMillis: Long,
        workerCount: Int,
        shouldContinue: () -> Boolean,
        onCompleted: (SimilarityFeatureWorkResult) -> Unit
    ): Boolean {
        if (workItems.isEmpty()) return !shouldContinue()
        val concurrency = workerCount.coerceAtMost(workItems.size)
        val threadIndex = AtomicInteger(0)
        val executor = Executors.newFixedThreadPool(concurrency) { runnable ->
            Thread(
                runnable,
                "CachedDupeScanner-Similarity-${threadIndex.incrementAndGet()}"
            ).apply {
                isDaemon = true
            }
        }
        val completionService = ExecutorCompletionService<SimilarityFeatureWorkResult>(executor)
        workItems.forEach { workItem ->
            completionService.submit(
                Callable {
                    calculateFeatureWorkItem(
                        setting = setting,
                        mediaScope = mediaScope,
                        workItem = workItem,
                        updatedAtMillis = updatedAtMillis,
                        shouldContinue = shouldContinue
                    )
                }
            )
        }

        var remaining = workItems.size
        try {
            while (remaining > 0) {
                if (!shouldContinue()) return true
                val completedFuture = try {
                    completionService.poll(
                        SIMILARITY_WORK_COMPLETION_POLL_MILLIS,
                        TimeUnit.MILLISECONDS
                    )
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return true
                } ?: continue
                remaining -= 1
                val result = completedFuture.get()
                result.failure?.let { failure -> throw failure }
                if (result.cancelled || !shouldContinue()) return true
                onCompleted(result)
            }
        } finally {
            executor.shutdownNow()
            try {
                executor.awaitTermination(
                    SIMILARITY_WORK_EXECUTOR_SHUTDOWN_SECONDS,
                    TimeUnit.SECONDS
                )
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
        return false
    }

    private fun calculateFeatureWorkItem(
        setting: SimilaritySettingEntity,
        mediaScope: SimilarityMediaScope,
        workItem: SimilarityFeatureWorkItem,
        updatedAtMillis: Long,
        shouldContinue: () -> Boolean
    ): SimilarityFeatureWorkResult {
        if (!shouldContinue()) return SimilarityFeatureWorkResult(workItem = workItem, cancelled = true)
        return try {
            val freshSettingFile = workItem.freshSettingFile
            if (freshSettingFile != null) {
                val dimensions = extractDimensions(mediaScope, workItem.entity, shouldContinue)
                if (!shouldContinue()) {
                    SimilarityFeatureWorkResult(workItem = workItem, cancelled = true)
                } else {
                    SimilarityFeatureWorkResult(
                        workItem = workItem,
                        preparedFile = PreparedSettingFile(
                            settingFile = freshSettingFile.copy(
                                widthPixels = dimensions?.widthPixels,
                                heightPixels = dimensions?.heightPixels,
                                dimensionsChecked = true,
                                updatedAtMillis = updatedAtMillis
                            ),
                            exactFeature = null,
                            durationFeature = null,
                            replaceDurationFeature = false
                        )
                    )
                }
            } else {
                val feature = calculateFeature(
                    setting = setting,
                    mediaScope = mediaScope,
                    entity = workItem.entity,
                    shouldContinue = shouldContinue
                )
                if (!shouldContinue()) {
                    SimilarityFeatureWorkResult(workItem = workItem, cancelled = true)
                } else {
                    val dimensions = if (
                        feature == null || setting.methodId != SIMILARITY_METHOD_EXACT_THUMBNAIL
                    ) {
                        null
                    } else {
                        extractDimensions(mediaScope, workItem.entity, shouldContinue)
                    }
                    if (!shouldContinue()) {
                        SimilarityFeatureWorkResult(workItem = workItem, cancelled = true)
                    } else {
                        SimilarityFeatureWorkResult(
                            workItem = workItem,
                            preparedFile = prepareSettingFile(
                                setting = setting,
                                mediaScope = mediaScope,
                                entity = workItem.entity,
                                feature = feature,
                                dimensions = dimensions,
                                updatedAtMillis = updatedAtMillis
                            ),
                            skipped = feature == null
                        )
                    }
                }
            }
        } catch (error: Exception) {
            SimilarityFeatureWorkResult(workItem = workItem, failure = error)
        }
    }

    private fun buildClusters(setting: SimilaritySettingEntity): List<ClusterDraft> {
        return when (setting.methodId) {
            SIMILARITY_METHOD_EXACT_THUMBNAIL -> {
                similarityDao.listActiveExactThumbnailFeatures(setting.settingId)
                    .groupBy { row -> row.thumbnailHash }
                    .filterValues { rows -> rows.size > 1 }
                    .map { (thumbnailHash, rows) ->
                        ClusterDraft(
                            clusterKey = exactThumbnailClusterKey(setting, thumbnailHash),
                            members = rows.map { row ->
                                ClusterMemberDraft(
                                    fileId = row.fileId,
                                    sizeBytes = row.sizeBytes
                                )
                            }
                        )
                    }
            }
            SIMILARITY_METHOD_DURATION_TOLERANCE -> {
                val step = durationToleranceStepFromParams(setting.paramsJson)
                durationToleranceClusters(
                    rows = similarityDao.listActiveDurationFeatures(setting.settingId),
                    step = step
                )
            }
            SIMILARITY_METHOD_DURATION_NEIGHBOR_LIST -> {
                val step = durationNeighborListStepFromParams(setting.paramsJson)
                durationNeighborClusters(
                    rows = similarityDao.listActiveDurationFeatures(setting.settingId),
                    step = step
                )
            }
            else -> emptyList()
        }
    }

    private fun durationToleranceClusters(
        rows: List<SimilarityDurationFeatureRow>,
        step: DurationToleranceStep
    ): List<ClusterDraft> {
        val tolerance = durationToleranceMillis(step)
        return durationToleranceClusterRanges(rows, tolerance).map { range ->
            durationCluster(rows.subList(range.first, range.last + 1), step)
        }
    }

    private fun durationNeighborClusters(
        rows: List<SimilarityDurationFeatureRow>,
        step: DurationNeighborListStep
    ): List<ClusterDraft> {
        val tolerance = durationNeighborToleranceMillis(step)
        val clusters = mutableListOf<ClusterDraft>()
        var current = mutableListOf<SimilarityDurationFeatureRow>()
        for (row in rows) {
            if (current.isEmpty()) {
                current.add(row)
                continue
            }
            val previous = current.last()
            if (abs(row.durationMillis - previous.durationMillis) <= tolerance) {
                current.add(row)
            } else {
                if (current.size > 1) clusters.add(durationNeighborCluster(current, step))
                current = mutableListOf(row)
            }
        }
        if (current.size > 1) clusters.add(durationNeighborCluster(current, step))
        return clusters
    }

    private fun durationCluster(
        rows: List<SimilarityDurationFeatureRow>,
        step: DurationToleranceStep
    ): ClusterDraft {
        val min = rows.first().durationMillis
        val max = rows.last().durationMillis
        val key = buildDurationToleranceSignature(min, max, step)
        return ClusterDraft(
            clusterKey = key,
            members = rows.map { row ->
                ClusterMemberDraft(
                    fileId = row.fileId,
                    sizeBytes = row.sizeBytes
                )
            }
        )
    }

    private fun durationNeighborCluster(
        rows: List<SimilarityDurationFeatureRow>,
        step: DurationNeighborListStep
    ): ClusterDraft {
        val min = rows.first().durationMillis
        val max = rows.last().durationMillis
        val key = buildDurationNeighborListSignature(min, max, step)
        return ClusterDraft(
            clusterKey = key,
            members = rows.map { row ->
                ClusterMemberDraft(
                    fileId = row.fileId,
                    sizeBytes = row.sizeBytes
                )
            }
        )
    }

    private fun replaceClusters(
        setting: SimilaritySettingEntity,
        initialDrafts: List<ClusterDraft>
    ) {
        var drafts = initialDrafts
        repeat(SIMILARITY_CLUSTER_REPLACE_MAX_ATTEMPTS) { attempt ->
            if (replaceClustersIfCurrent(setting.settingId, drafts)) return
            if (attempt + 1 < SIMILARITY_CLUSTER_REPLACE_MAX_ATTEMPTS) {
                drafts = buildClusters(setting)
            }
        }
    }

    private fun replaceClustersIfCurrent(settingId: Long, drafts: List<ClusterDraft>): Boolean {
        val now = System.currentTimeMillis()
        val draftFileIds = drafts
            .asSequence()
            .flatMap { draft -> draft.members.asSequence() }
            .map { member -> member.fileId }
            .distinct()
            .toList()
        return database.runInTransaction<Boolean> {
            val existingFileIds = hashSetOf<Long>()
            draftFileIds.chunked(SIMILARITY_DB_BIND_CHUNK_SIZE).forEach { fileIds ->
                existingFileIds += similarityDao.listExistingSettingFileIds(settingId, fileIds)
            }
            if (existingFileIds.size != draftFileIds.size) {
                return@runInTransaction false
            }

            val existingByKey = similarityDao.listStoredClusters(settingId).associateBy { it.clusterKey }
            val draftKeys = drafts.mapTo(hashSetOf()) { it.clusterKey }
            val staleIds = existingByKey
                .filterKeys { key -> key !in draftKeys }
                .values
                .map { cluster -> cluster.clusterId }
            if (staleIds.isNotEmpty()) {
                staleIds.chunked(SIMILARITY_DB_BIND_CHUNK_SIZE).forEach { ids ->
                    similarityDao.deleteClusterMembersByIds(ids)
                    similarityDao.deleteClustersByIds(ids)
                }
            }
            val refreshedClusterIds = ArrayList<Long>(drafts.size)
            drafts.forEach { draft ->
                val totalBytes = draft.members.sumOf { member -> member.sizeBytes }
                val existing = existingByKey[draft.clusterKey]
                val clusterId = existing?.clusterId ?: run {
                    val insertedId = similarityDao.insertCluster(
                        SimilarityClusterEntity(
                            settingId = settingId,
                            clusterKey = draft.clusterKey,
                            fileCount = draft.members.size,
                            totalBytes = totalBytes,
                            updatedAtMillis = now
                        )
                    )
                    if (insertedId > 0L) {
                        insertedId
                    } else {
                        requireNotNull(similarityDao.getClusterByKey(settingId, draft.clusterKey)).clusterId
                    }
                }
                refreshedClusterIds += clusterId
                similarityDao.deleteClusterDurationStatsByIds(listOf(clusterId))
                similarityDao.updateCluster(
                    clusterId = clusterId,
                    fileCount = draft.members.size,
                    totalBytes = totalBytes,
                    updatedAtMillis = now
                )
                similarityDao.deleteClusterMembersByIds(listOf(clusterId))
                similarityDao.upsertClusterMembers(
                    draft.members.mapIndexed { index, member ->
                        SimilarityClusterMemberEntity(
                            clusterId = clusterId,
                            fileId = member.fileId,
                            position = index
                        )
                    }
                )
            }
            calculateAndStoreDurationStatsForClusters(
                settingId = settingId,
                clusterIds = refreshedClusterIds
            )
            true
        }
    }

    private fun repairStoredClusters(clusterIds: List<Long>) {
        if (clusterIds.isEmpty()) return
        clusterIds.distinct().chunked(SIMILARITY_DB_BIND_CHUNK_SIZE).forEach { ids ->
            similarityDao.deleteClusterDurationStatsByIds(ids)
            similarityDao.listClusterRepairRows(ids).forEach { row ->
                if (row.fileCount <= 1) {
                    similarityDao.deleteClusterMembersByIds(listOf(row.clusterId))
                    similarityDao.deleteClustersByIds(listOf(row.clusterId))
                } else {
                    similarityDao.updateCluster(
                        clusterId = row.clusterId,
                        fileCount = row.fileCount,
                        totalBytes = row.totalBytes,
                        updatedAtMillis = System.currentTimeMillis()
                    )
                }
            }
        }
    }

    private fun clearSettingResultsByStage(
        settingId: Long,
        shouldContinue: () -> Boolean,
        onProgress: (SimilarityClearProgress) -> Unit
    ): SimilarityClearSummary {
        val total = countSettingResultRows(settingId)
        var processed = 0
        var cancellationRequested = false
        fun publish(phase: SimilarityClearPhase) {
            onProgress(
                SimilarityClearProgress(
                    total = total,
                    processed = processed.coerceAtMost(total),
                    remaining = (total - processed).coerceAtLeast(0),
                    phase = phase
                )
            )
        }
        fun stopRequested(): Boolean {
            if (shouldContinue()) return false
            cancellationRequested = true
            return true
        }

        publish(SimilarityClearPhase.Preparing)
        if (!stopRequested()) {
            var clearedRows = 0
            database.runInTransaction {
                clearedRows += similarityDao.deleteClusterMembersForSetting(settingId)
                clearedRows += similarityDao.deleteClustersForSetting(settingId)
            }
            processed += clearedRows
            publish(SimilarityClearPhase.Clusters)
        }
        if (!cancellationRequested && !stopRequested()) {
            var clearedRows = 0
            database.runInTransaction {
                clearedRows += similarityDao.deleteExactThumbnailFeatures(settingId)
                clearedRows += similarityDao.deleteDurationFeatures(settingId)
                clearedRows += similarityDao.deleteSettingFiles(settingId)
            }
            processed += clearedRows
            publish(SimilarityClearPhase.FilesAndFeatures)
        }
        if (!cancellationRequested && !stopRequested()) {
            processed += database.runInTransaction<Int> {
                similarityDao.deleteMaintenanceRunsForSetting(settingId)
            }
            publish(SimilarityClearPhase.History)
        }
        return finishSettingClear(
            settingId = settingId,
            total = total,
            processed = processed,
            cancellationRequested = cancellationRequested
        )
    }

    private fun clearSettingResultsIncrementally(
        settingId: Long,
        shouldContinue: () -> Boolean,
        onProgress: (SimilarityClearProgress) -> Unit
    ): SimilarityClearSummary {
        val total = countSettingResultRows(settingId)
        var processed = 0
        var cancellationRequested = false
        fun publish(phase: SimilarityClearPhase) {
            onProgress(
                SimilarityClearProgress(
                    total = total,
                    processed = processed.coerceAtMost(total),
                    remaining = (total - processed).coerceAtLeast(0),
                    phase = phase
                )
            )
        }
        fun stopRequested(): Boolean {
            if (shouldContinue()) return false
            cancellationRequested = true
            return true
        }
        fun finish(): SimilarityClearSummary {
            return finishSettingClear(
                settingId = settingId,
                total = total,
                processed = processed,
                cancellationRequested = cancellationRequested
            )
        }

        publish(SimilarityClearPhase.Preparing)
        while (!stopRequested()) {
            val clusterId = similarityDao.firstClusterIdWithMembersForClear(settingId) ?: break
            val fileIds = similarityDao.listClusterMemberIdsForClear(
                clusterId = clusterId,
                limit = SIMILARITY_CLEAR_BATCH_SIZE
            )
            if (fileIds.isEmpty()) break
            var clearedRows = 0
            database.runInTransaction {
                val cluster = similarityDao.getClusterForClear(clusterId)
                similarityDao.deleteClusterDurationStatsByIds(listOf(clusterId))
                val clearedBytes = similarityDao.sumClusterMemberBytesForClear(clusterId, fileIds)
                val clearedMembers = similarityDao.deleteClusterMemberIdsForClear(clusterId, fileIds)
                clearedRows += clearedMembers
                val remainingFileCount = (cluster?.fileCount ?: 0) - clearedMembers
                val remainingTotalBytes = ((cluster?.totalBytes ?: 0L) - clearedBytes).coerceAtLeast(0L)
                if (cluster == null || remainingFileCount <= 1) {
                    clearedRows += similarityDao.deleteClusterMembersByIds(listOf(clusterId))
                    clearedRows += similarityDao.deleteClustersByIds(listOf(clusterId))
                } else {
                    similarityDao.updateCluster(
                        clusterId = clusterId,
                        fileCount = remainingFileCount,
                        totalBytes = remainingTotalBytes,
                        updatedAtMillis = System.currentTimeMillis()
                    )
                }
            }
            processed += clearedRows
            publish(SimilarityClearPhase.ClusterMembers)
        }
        if (cancellationRequested) return finish()

        while (!stopRequested()) {
            val clusterIds = similarityDao.listClusterIdsForClear(
                settingId = settingId,
                limit = SIMILARITY_CLEAR_BATCH_SIZE
            )
            if (clusterIds.isEmpty()) break
            var clearedRows = 0
            database.runInTransaction {
                clearedRows += similarityDao.deleteClusterMembersByIds(clusterIds)
                clearedRows += similarityDao.deleteClustersByIds(clusterIds)
            }
            processed += clearedRows
            publish(SimilarityClearPhase.Clusters)
        }
        if (cancellationRequested) return finish()

        while (!stopRequested()) {
            val fileIds = similarityDao.listSettingFileIdsForClear(
                settingId = settingId,
                limit = SIMILARITY_CLEAR_BATCH_SIZE
            )
            if (fileIds.isEmpty()) break
            var clearedRows = 0
            database.runInTransaction {
                clearedRows += similarityDao.deleteExactThumbnailFeaturesForSettingByIds(settingId, fileIds)
                clearedRows += similarityDao.deleteDurationFeaturesForSettingByIds(settingId, fileIds)
                clearedRows += similarityDao.deleteSettingFilesForSettingByIds(settingId, fileIds)
            }
            processed += clearedRows
            publish(SimilarityClearPhase.FilesAndFeatures)
        }
        if (cancellationRequested) return finish()

        while (!stopRequested()) {
            val fileIds = similarityDao.listExactThumbnailFeatureIdsForClear(
                settingId = settingId,
                limit = SIMILARITY_CLEAR_BATCH_SIZE
            )
            if (fileIds.isEmpty()) break
            processed += database.runInTransaction<Int> {
                similarityDao.deleteExactThumbnailFeaturesForSettingByIds(settingId, fileIds)
            }
            publish(SimilarityClearPhase.OrphanFeatures)
        }
        if (cancellationRequested) return finish()

        while (!stopRequested()) {
            val fileIds = similarityDao.listDurationFeatureIdsForClear(
                settingId = settingId,
                limit = SIMILARITY_CLEAR_BATCH_SIZE
            )
            if (fileIds.isEmpty()) break
            processed += database.runInTransaction<Int> {
                similarityDao.deleteDurationFeaturesForSettingByIds(settingId, fileIds)
            }
            publish(SimilarityClearPhase.OrphanFeatures)
        }
        if (cancellationRequested) return finish()

        while (!stopRequested()) {
            val runIds = similarityDao.listMaintenanceRunIdsForClear(
                settingId = settingId,
                limit = SIMILARITY_CLEAR_BATCH_SIZE
            )
            if (runIds.isEmpty()) break
            processed += database.runInTransaction<Int> {
                similarityDao.deleteMaintenanceRunsByIds(runIds)
            }
            publish(SimilarityClearPhase.History)
        }
        return finish()
    }

    private fun finishSettingClear(
        settingId: Long,
        total: Int,
        processed: Int,
        cancellationRequested: Boolean
    ): SimilarityClearSummary {
        val remaining = countSettingResultRows(settingId)
        check(cancellationRequested || remaining == 0) {
            "Similarity clear finished with $remaining generated rows remaining."
        }
        val completedProcessed = if (remaining == 0) total else processed.coerceAtMost(total)
        return SimilarityClearSummary(
            total = total,
            processed = completedProcessed,
            remaining = remaining,
            cancelled = cancellationRequested && remaining > 0
        )
    }

    private fun countSettingResultRows(settingId: Long): Int {
        return similarityDao.countSettingFiles(settingId) +
            similarityDao.countExactThumbnailFeatures(settingId) +
            similarityDao.countDurationFeatures(settingId) +
            similarityDao.countClusterMembersForSetting(settingId) +
            similarityDao.countClustersForSetting(settingId) +
            similarityDao.countMaintenanceRunsForSetting(settingId)
    }

    private fun clearSettingDataLocked(settingId: Long) {
        similarityDao.deleteClusterMembersForSetting(settingId)
        similarityDao.deleteClustersForSetting(settingId)
        similarityDao.deleteExactThumbnailFeatures(settingId)
        similarityDao.deleteDurationFeatures(settingId)
        similarityDao.deleteSettingFiles(settingId)
    }

    private fun MessageDigest.updateLong(value: Long) {
        update(ByteBuffer.allocate(Long.SIZE_BYTES).putLong(value).array())
    }

    private fun MessageDigest.updateString(value: String) {
        val bytes = value.toByteArray(StandardCharsets.UTF_8)
        updateLong(bytes.size.toLong())
        update(bytes)
    }

    private fun countCandidates(mediaScope: SimilarityMediaScope, minSizeBytes: Long): Int {
        return when (mediaScope) {
            SimilarityMediaScope.Video -> fileDao.countVideoCandidates(minSizeBytes)
            SimilarityMediaScope.Image -> fileDao.countImageCandidates(minSizeBytes)
        }
    }

    private fun listCandidatesAfter(
        mediaScope: SimilarityMediaScope,
        minSizeBytes: Long,
        afterPath: String,
        limit: Int
    ): List<CachedFileEntity> {
        return when (mediaScope) {
            SimilarityMediaScope.Video -> fileDao.listVideoCandidatesAfter(minSizeBytes, afterPath, limit)
            SimilarityMediaScope.Image -> fileDao.listImageCandidatesAfter(minSizeBytes, afterPath, limit)
        }
    }

    private fun settingFile(
        setting: SimilaritySettingEntity,
        entity: CachedFileEntity,
        status: String,
        dimensions: MediaDimensions?,
        dimensionsChecked: Boolean,
        durationChecked: Boolean,
        updatedAtMillis: Long
    ): SimilaritySettingFileEntity {
        return SimilaritySettingFileEntity(
            settingId = setting.settingId,
            fileId = entity.fileId,
            sizeBytes = entity.sizeBytes,
            lastModifiedMillis = entity.lastModifiedMillis,
            status = status,
            widthPixels = dimensions?.widthPixels,
            heightPixels = dimensions?.heightPixels,
            dimensionsChecked = dimensionsChecked,
            durationChecked = durationChecked,
            updatedAtMillis = updatedAtMillis
        )
    }

    private fun prepareSettingFile(
        setting: SimilaritySettingEntity,
        mediaScope: SimilarityMediaScope,
        entity: CachedFileEntity,
        feature: CalculatedFeature?,
        dimensions: MediaDimensions?,
        updatedAtMillis: Long
    ): PreparedSettingFile {
        val ready = feature != null
        return PreparedSettingFile(
            settingFile = settingFile(
                setting = setting,
                entity = entity,
                status = if (ready) SIMILARITY_FILE_STATUS_READY else SIMILARITY_FILE_STATUS_SKIPPED,
                dimensions = dimensions,
                dimensionsChecked = ready && setting.methodId == SIMILARITY_METHOD_EXACT_THUMBNAIL,
                durationChecked = ready && (
                    mediaScope == SimilarityMediaScope.Image ||
                        feature is CalculatedFeature.Duration ||
                        (feature is CalculatedFeature.ExactThumbnail && feature.durationMillis != null)
                ),
                updatedAtMillis = updatedAtMillis
            ),
            exactFeature = (feature as? CalculatedFeature.ExactThumbnail)?.let { exact ->
                SimilarityExactThumbnailFeatureEntity(
                    settingId = setting.settingId,
                    fileId = entity.fileId,
                    thumbnailHash = StoredHash.fromExternalString(exact.signature)
                )
            },
            durationFeature = feature.durationMillisOrNull()?.let { durationMillis ->
                SimilarityDurationFeatureEntity(
                    settingId = setting.settingId,
                    fileId = entity.fileId,
                    durationMillis = durationMillis
                )
            },
            replaceDurationFeature = true
        )
    }

    private fun exactThumbnailClusterKey(
        setting: SimilaritySettingEntity,
        thumbnailHash: StoredHash
    ): String {
        val hashHex = thumbnailHash.toExternalString()
        if (!isSha256HashHex(hashHex)) return hashHex
        val mediaScope = runCatching { SimilarityMediaScope.valueOf(setting.mediaScope) }
            .getOrNull()
            ?: return hashHex
        val step = runCatching { exactThumbnailStepFromParams(setting.paramsJson) }
            .getOrNull()
            ?: return hashHex
        return buildThumbnailHashClusterKey(
            mediaScope = mediaScope,
            step = step,
            thumbnailHashHex = hashHex
        )
    }

    private fun extractDimensions(
        mediaScope: SimilarityMediaScope,
        entity: CachedFileEntity,
        shouldContinue: () -> Boolean
    ): MediaDimensions? {
        val path = entity.path.ifBlank { entity.normalizedPath }
        val file = File(path)
        if (!file.exists()) return null
        return mediaDimensionsExtractor.dimensions(
            file = file,
            mediaScope = mediaScope,
            shouldContinue = shouldContinue
        )
    }

    private fun resolveUncheckedDimensions(
        rows: List<SimilarityClusterMemberFileRow>,
        onResolutionEvent: (SimilarityMemberResolutionEvent) -> Unit
    ): List<SimilarityClusterMemberFileRow> {
        val uncheckedRows = rows.filterNot { row -> row.dimensionsChecked }
        if (uncheckedRows.isEmpty() || Thread.currentThread().isInterrupted) return rows
        val settingId = uncheckedRows.first().settingId
        val mediaScope = similarityDao.getSetting(settingId)
            ?.mediaScope
            ?.let { value -> runCatching { SimilarityMediaScope.valueOf(value) }.getOrNull() }
            ?: return rows
        val shouldContinue = { !Thread.currentThread().isInterrupted }
        val reusableByFileId = similarityDao.listReusableDimensions(
            settingId = settingId,
            fileIds = uncheckedRows.map { row -> row.fileId }
        ).associateBy { reusable -> reusable.fileId }
        val resolved = mutableListOf<PendingMetadataResolution<MediaDimensions>>()
        uncheckedRows.forEach { row ->
            val reusable = reusableByFileId[row.fileId] ?: return@forEach
            if (!shouldContinue()) return@forEach
            onResolutionEvent(
                row.resolutionEvent(
                    kind = SimilarityMemberResolutionKind.Dimensions,
                    completed = false
                )
            )
            resolved += PendingMetadataResolution(
                row = row,
                value = if (reusable.widthPixels != null && reusable.heightPixels != null) {
                    MediaDimensions(reusable.widthPixels, reusable.heightPixels)
                } else {
                    null
                }
            )
            onResolutionEvent(
                row.resolutionEvent(
                    kind = SimilarityMemberResolutionKind.Dimensions,
                    completed = true
                )
            )
        }
        resolved += resolveMetadataWorkBatch(
            rows = uncheckedRows.filterNot { row -> reusableByFileId.containsKey(row.fileId) },
            kind = SimilarityMemberResolutionKind.Dimensions,
            workerCount = sanitizeScanWorkerCount(workerCountProvider()),
            shouldContinue = shouldContinue,
            onResolutionEvent = onResolutionEvent,
            extract = { row ->
                val file = File(row.path)
                if (file.exists()) {
                    mediaDimensionsExtractor.dimensions(
                        file = file,
                        mediaScope = mediaScope,
                        shouldContinue = shouldContinue
                    )
                } else {
                    null
                }
            }
        )
        if (resolved.isEmpty()) return rows

        val updatedRows = linkedMapOf<Long, SimilarityClusterMemberFileRow>()
        val updatedAtMillis = System.currentTimeMillis()
        database.runInTransaction {
            resolved.forEach { pending ->
                val row = pending.row
                val dimensions = pending.value
                val updated = similarityDao.updateSettingFileDimensionsIfCurrent(
                    settingId = row.settingId,
                    fileId = row.fileId,
                    sizeBytes = row.sizeBytes,
                    lastModifiedMillis = row.lastModifiedMillis,
                    widthPixels = dimensions?.widthPixels,
                    heightPixels = dimensions?.heightPixels,
                    updatedAtMillis = updatedAtMillis
                )
                val current = if (updated > 0) {
                    null
                } else {
                    similarityDao.getSettingFile(row.settingId, row.fileId)
                }
                when {
                    updated > 0 -> updatedRows[row.fileId] = row.copy(
                        widthPixels = dimensions?.widthPixels,
                        heightPixels = dimensions?.heightPixels,
                        dimensionsChecked = true
                    )
                    current?.dimensionsChecked == true &&
                        current.sizeBytes == row.sizeBytes &&
                        current.lastModifiedMillis == row.lastModifiedMillis -> {
                        updatedRows[row.fileId] = row.copy(
                            widthPixels = current.widthPixels,
                            heightPixels = current.heightPixels,
                            dimensionsChecked = true
                        )
                    }
                }
            }
        }
        return rows.map { row -> updatedRows[row.fileId] ?: row }
    }

    private fun resolveUncheckedDurations(
        rows: List<SimilarityClusterMemberFileRow>,
        onResolutionEvent: (SimilarityMemberResolutionEvent) -> Unit
    ): List<SimilarityClusterMemberFileRow> {
        val uncheckedRows = rows.filterNot { row -> row.durationChecked }
        if (uncheckedRows.isEmpty() || Thread.currentThread().isInterrupted) return rows
        val settingId = uncheckedRows.first().settingId
        val mediaScope = similarityDao.getSetting(settingId)
            ?.mediaScope
            ?.let { value -> runCatching { SimilarityMediaScope.valueOf(value) }.getOrNull() }
            ?: return rows
        val shouldContinue = { !Thread.currentThread().isInterrupted }
        val reusableByFileId = similarityDao.listReusableDurations(
            settingId = settingId,
            fileIds = uncheckedRows.map { row -> row.fileId }
        ).associateBy { reusable -> reusable.fileId }
        val resolved = mutableListOf<PendingMetadataResolution<Long>>()
        uncheckedRows.forEach { row ->
            val reusable = reusableByFileId[row.fileId] ?: return@forEach
            if (!shouldContinue()) return@forEach
            onResolutionEvent(
                row.resolutionEvent(
                    kind = SimilarityMemberResolutionKind.Duration,
                    completed = false
                )
            )
            resolved += PendingMetadataResolution(row = row, value = reusable.durationMillis)
            onResolutionEvent(
                row.resolutionEvent(
                    kind = SimilarityMemberResolutionKind.Duration,
                    completed = true
                )
            )
        }
        resolved += resolveMetadataWorkBatch(
            rows = uncheckedRows.filterNot { row -> reusableByFileId.containsKey(row.fileId) },
            kind = SimilarityMemberResolutionKind.Duration,
            workerCount = sanitizeScanWorkerCount(workerCountProvider()),
            shouldContinue = shouldContinue,
            onResolutionEvent = onResolutionEvent,
            extract = { row ->
                val file = File(row.path)
                if (mediaScope == SimilarityMediaScope.Video && file.exists()) {
                    durationExtractor.durationMillis(
                        file = file,
                        shouldContinue = shouldContinue
                    )?.takeIf { value -> value >= 0L }
                } else {
                    null
                }
            }
        )
        if (resolved.isEmpty()) return rows

        val updatedRows = linkedMapOf<Long, SimilarityClusterMemberFileRow>()
        val updatedFileIds = mutableListOf<Long>()
        val acceptedDurations = mutableListOf<SimilarityDurationFeatureEntity>()
        val updatedAtMillis = System.currentTimeMillis()
        database.runInTransaction {
            resolved.forEach { pending ->
                val row = pending.row
                val durationMillis = pending.value
                val updated = similarityDao.updateSettingFileDurationIfCurrent(
                    settingId = row.settingId,
                    fileId = row.fileId,
                    sizeBytes = row.sizeBytes,
                    lastModifiedMillis = row.lastModifiedMillis,
                    updatedAtMillis = updatedAtMillis
                )
                if (updated > 0) {
                    updatedFileIds += row.fileId
                    if (durationMillis != null) {
                        acceptedDurations += SimilarityDurationFeatureEntity(
                            settingId = row.settingId,
                            fileId = row.fileId,
                            durationMillis = durationMillis
                        )
                    }
                    updatedRows[row.fileId] = row.copy(
                        durationMillis = durationMillis,
                        durationChecked = true
                    )
                } else {
                    val current = similarityDao.getSettingFile(row.settingId, row.fileId)
                    if (current?.durationChecked == true &&
                        current.sizeBytes == row.sizeBytes &&
                        current.lastModifiedMillis == row.lastModifiedMillis
                    ) {
                        updatedRows[row.fileId] = row.copy(
                            durationMillis = similarityDao
                                .getDurationFeature(row.settingId, row.fileId)
                                ?.durationMillis,
                            durationChecked = true
                        )
                    }
                }
            }
            if (updatedFileIds.isNotEmpty()) {
                similarityDao.deleteDurationFeaturesForSettingByIds(settingId, updatedFileIds)
            }
            if (acceptedDurations.isNotEmpty()) {
                similarityDao.upsertDurationFeatures(acceptedDurations)
            }
            if (updatedFileIds.isNotEmpty()) {
                similarityDao.deleteClusterDurationStatsForMemberFileIds(updatedFileIds)
            }
        }
        return rows.map { row -> updatedRows[row.fileId] ?: row }
    }

    private fun <T : Any> resolveMetadataWorkBatch(
        rows: List<SimilarityClusterMemberFileRow>,
        kind: SimilarityMemberResolutionKind,
        workerCount: Int,
        shouldContinue: () -> Boolean,
        onResolutionEvent: (SimilarityMemberResolutionEvent) -> Unit,
        extract: (SimilarityClusterMemberFileRow) -> T?
    ): List<PendingMetadataResolution<T>> {
        if (rows.isEmpty() || !shouldContinue()) return emptyList()
        val concurrency = workerCount.coerceAtMost(rows.size)
        if (concurrency <= 1) {
            return rows.mapNotNull { row ->
                resolveMetadataWorkItemWithProgress(
                    row = row,
                    kind = kind,
                    shouldContinue = shouldContinue,
                    onResolutionEvent = onResolutionEvent,
                    extract = extract
                )
            }
        }

        val threadIndex = AtomicInteger(0)
        val executor = Executors.newFixedThreadPool(concurrency) { runnable ->
            Thread(
                runnable,
                "CachedDupeScanner-Filter${kind.name}-${threadIndex.incrementAndGet()}"
            ).apply {
                isDaemon = true
            }
        }
        val completionService = ExecutorCompletionService<MetadataResolutionWorkResult<T>>(executor)
        val resolved = mutableListOf<PendingMetadataResolution<T>>()
        var nextIndex = 0
        var activeWorkers = 0

        fun submitNext(): Boolean {
            if (nextIndex >= rows.size || !shouldContinue()) return false
            val row = rows[nextIndex]
            nextIndex += 1
            onResolutionEvent(row.resolutionEvent(kind = kind, completed = false))
            completionService.submit(
                Callable {
                    resolveMetadataWorkItem(
                        row = row,
                        shouldContinue = shouldContinue,
                        extract = extract
                    )
                }
            )
            activeWorkers += 1
            return true
        }

        try {
            repeat(concurrency) { submitNext() }
            while (activeWorkers > 0 && shouldContinue()) {
                val completedFuture = try {
                    completionService.poll(
                        SIMILARITY_WORK_COMPLETION_POLL_MILLIS,
                        TimeUnit.MILLISECONDS
                    )
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    break
                } ?: continue
                activeWorkers -= 1
                val result = completedFuture.get()
                result.failure?.let { failure -> throw failure }
                if (!result.cancelled && shouldContinue()) {
                    onResolutionEvent(result.row.resolutionEvent(kind = kind, completed = true))
                    resolved += PendingMetadataResolution(result.row, result.value)
                }
                submitNext()
            }
        } finally {
            executor.shutdownNow()
            try {
                executor.awaitTermination(
                    SIMILARITY_WORK_EXECUTOR_SHUTDOWN_SECONDS,
                    TimeUnit.SECONDS
                )
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
        return resolved
    }

    private fun <T : Any> resolveMetadataWorkItemWithProgress(
        row: SimilarityClusterMemberFileRow,
        kind: SimilarityMemberResolutionKind,
        shouldContinue: () -> Boolean,
        onResolutionEvent: (SimilarityMemberResolutionEvent) -> Unit,
        extract: (SimilarityClusterMemberFileRow) -> T?
    ): PendingMetadataResolution<T>? {
        if (!shouldContinue()) return null
        onResolutionEvent(row.resolutionEvent(kind = kind, completed = false))
        val result = resolveMetadataWorkItem(
            row = row,
            shouldContinue = shouldContinue,
            extract = extract
        )
        result.failure?.let { failure -> throw failure }
        if (result.cancelled || !shouldContinue()) return null
        onResolutionEvent(row.resolutionEvent(kind = kind, completed = true))
        return PendingMetadataResolution(row, result.value)
    }

    private fun <T : Any> resolveMetadataWorkItem(
        row: SimilarityClusterMemberFileRow,
        shouldContinue: () -> Boolean,
        extract: (SimilarityClusterMemberFileRow) -> T?
    ): MetadataResolutionWorkResult<T> {
        if (!shouldContinue()) return MetadataResolutionWorkResult(row = row, cancelled = true)
        return try {
            val value = extract(row)
            if (shouldContinue()) {
                MetadataResolutionWorkResult(row = row, value = value)
            } else {
                MetadataResolutionWorkResult(row = row, cancelled = true)
            }
        } catch (error: Exception) {
            MetadataResolutionWorkResult(row = row, failure = error)
        }
    }

    private fun emptySummary(cancelled: Boolean): SimilarityMaintenanceSummary {
        return SimilarityMaintenanceSummary(
            settingCount = 0,
            candidateCount = 0,
            processedCount = 0,
            skippedCount = 0,
            clusterCount = 0,
            duplicateFileCount = 0,
            cancelled = cancelled
        )
    }
}

internal fun durationToleranceClusterRanges(
    sortedRows: List<SimilarityDurationFeatureRow>,
    toleranceMillis: Long
): List<IntRange> {
    if (sortedRows.size < 2) return emptyList()
    val ranges = mutableListOf<IntRange>()
    var startIndex = 0
    for (index in 1..sortedRows.lastIndex) {
        val minimumDuration = sortedRows[startIndex].durationMillis
        val candidateMaximum = sortedRows[index].durationMillis
        if (candidateMaximum - minimumDuration > toleranceMillis) {
            if (index - startIndex > 1) {
                ranges += startIndex until index
            }
            startIndex = index
        }
    }
    if (sortedRows.size - startIndex > 1) {
        ranges += startIndex..sortedRows.lastIndex
    }
    return ranges
}

private fun SimilarityFilterMetadataResolutionRow.toMemberFileRow(): SimilarityClusterMemberFileRow {
    return SimilarityClusterMemberFileRow(
        settingId = settingId,
        fileId = fileId,
        normalizedPath = normalizedPath,
        path = path,
        sizeBytes = sizeBytes,
        lastModifiedMillis = lastModifiedMillis,
        hashBytes = hashBytes,
        durationMillis = durationMillis,
        widthPixels = widthPixels,
        heightPixels = heightPixels,
        dimensionsChecked = dimensionsChecked,
        durationChecked = durationChecked
    )
}

private sealed interface CalculatedFeature {
    data class ExactThumbnail(
        val signature: String,
        val durationMillis: Long?
    ) : CalculatedFeature
    data class Duration(val durationMillis: Long) : CalculatedFeature
}

private fun CalculatedFeature?.durationMillisOrNull(): Long? {
    return when (this) {
        is CalculatedFeature.Duration -> durationMillis
        is CalculatedFeature.ExactThumbnail -> durationMillis
        null -> null
    }
}

private data class PreparedSettingFile(
    val settingFile: SimilaritySettingFileEntity,
    val exactFeature: SimilarityExactThumbnailFeatureEntity?,
    val durationFeature: SimilarityDurationFeatureEntity?,
    val replaceDurationFeature: Boolean
)

private data class SimilarityFeatureWorkItem(
    val entity: CachedFileEntity,
    val freshSettingFile: SimilaritySettingFileEntity?
)

private data class SimilarityFeatureWorkResult(
    val workItem: SimilarityFeatureWorkItem,
    val preparedFile: PreparedSettingFile? = null,
    val skipped: Boolean = false,
    val cancelled: Boolean = false,
    val failure: Exception? = null
)

private data class ClusterDraft(
    val clusterKey: String,
    val members: List<ClusterMemberDraft>
)

private data class ClusterMemberDraft(
    val fileId: Long,
    val sizeBytes: Long
)

private data class PendingMetadataResolution<T : Any>(
    val row: SimilarityClusterMemberFileRow,
    val value: T?
)

private data class MetadataResolutionWorkResult<T : Any>(
    val row: SimilarityClusterMemberFileRow,
    val value: T? = null,
    val cancelled: Boolean = false,
    val failure: Exception? = null
)

private fun SimilarityClusterMemberFileRow.resolutionEvent(
    kind: SimilarityMemberResolutionKind,
    completed: Boolean
): SimilarityMemberResolutionEvent {
    return SimilarityMemberResolutionEvent(
        kind = kind,
        path = path,
        completed = completed
    )
}

private fun SimilarityClusterMemberFileRow.toFileMetadata(): FileMetadata {
    return FileMetadata(
        path = path,
        normalizedPath = normalizedPath,
        sizeBytes = sizeBytes,
        lastModifiedMillis = lastModifiedMillis,
        hashHex = hashHex,
        durationMillis = durationMillis,
        widthPixels = widthPixels,
        heightPixels = heightPixels
    )
}

private fun SimilarityClusterMemberFileRow.toClusterMember(): SimilarityClusterMember {
    return SimilarityClusterMember(
        metadata = toFileMetadata(),
        durationMillis = durationMillis
    )
}

private const val SIMILARITY_MAINTENANCE_BATCH_SIZE = 200
private const val SIMILARITY_FILTER_RESOLUTION_BATCH_SIZE = 500
private const val SIMILARITY_FILTER_MEMBER_BATCH_SIZE = 200
private const val SIMILARITY_DB_BIND_CHUNK_SIZE = 500
private const val SIMILARITY_CLEAR_BATCH_SIZE = 100
private const val SIMILARITY_CLUSTER_REPLACE_MAX_ATTEMPTS = 2
private const val SIMILARITY_WORK_COMPLETION_POLL_MILLIS = 100L
private const val SIMILARITY_WORK_EXECUTOR_SHUTDOWN_SECONDS = 5L
private const val SIMILARITY_FILE_STATUS_READY = "ready"
private const val SIMILARITY_FILE_STATUS_SKIPPED = "skipped-v2"
