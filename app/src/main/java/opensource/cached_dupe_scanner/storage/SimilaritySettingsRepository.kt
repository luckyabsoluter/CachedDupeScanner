package opensource.cached_dupe_scanner.storage

import opensource.cached_dupe_scanner.cache.CacheDatabase
import opensource.cached_dupe_scanner.cache.CachedFileEntity
import opensource.cached_dupe_scanner.cache.FileCacheDao
import opensource.cached_dupe_scanner.cache.SimilarityClusterEntity
import opensource.cached_dupe_scanner.cache.SimilarityClusterMemberEntity
import opensource.cached_dupe_scanner.cache.SimilarityClusterMemberFileRow
import opensource.cached_dupe_scanner.cache.SimilarityDurationFeatureEntity
import opensource.cached_dupe_scanner.cache.SimilarityExactThumbnailFeatureEntity
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
        if (resolveDimensions) {
            count += similarityDao.countUncheckedDimensionsForClusters(settingId, distinctClusterIds)
        }
        if (resolveDurations) {
            count += similarityDao.countUncheckedDurationsForClusters(settingId, distinctClusterIds)
        }
        return count.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
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
                val existing = similarityDao.getSettingFile(setting.settingId, entity.fileId)
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
                    (readyFeatureIsFresh && existing?.dimensionsChecked == true)
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

            val cancelled = calculateFeatureWorkBatch(
                setting = setting,
                mediaScope = mediaScope,
                workItems = workItems,
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

            if (preparedFiles.isNotEmpty()) {
                database.runInTransaction {
                    similarityDao.upsertSettingFiles(preparedFiles.map { prepared -> prepared.settingFile })
                    val exactFeatures = preparedFiles.mapNotNull { prepared -> prepared.exactFeature }
                    val durationFeatures = preparedFiles.mapNotNull { prepared -> prepared.durationFeature }
                    if (exactFeatures.isNotEmpty()) similarityDao.upsertExactThumbnailFeatures(exactFeatures)
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
                frameSignatureExtractor.signature(
                    file = file,
                    mediaScope = mediaScope,
                    step = step,
                    shouldContinue = shouldContinue
                )?.let(CalculatedFeature::ExactThumbnail)
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
                            durationFeature = null
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
                    val dimensions = if (feature == null) {
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
        rows: List<opensource.cached_dupe_scanner.cache.SimilarityDurationFeatureRow>,
        step: DurationToleranceStep
    ): List<ClusterDraft> {
        val tolerance = durationToleranceMillis(step)
        val sorted = rows.sortedWith(compareBy({ it.durationMillis }, { it.normalizedPath }))
        val clusters = mutableListOf<ClusterDraft>()
        var current = mutableListOf<opensource.cached_dupe_scanner.cache.SimilarityDurationFeatureRow>()
        for (row in sorted) {
            if (current.isEmpty()) {
                current.add(row)
                continue
            }
            val min = current.minOf { it.durationMillis }
            val max = current.maxOf { it.durationMillis }
            val candidateMin = minOf(min, row.durationMillis)
            val candidateMax = maxOf(max, row.durationMillis)
            if (candidateMax - candidateMin <= tolerance) {
                current.add(row)
            } else {
                if (current.size > 1) clusters.add(durationCluster(current, step))
                current = mutableListOf(row)
            }
        }
        if (current.size > 1) clusters.add(durationCluster(current, step))
        return clusters
    }

    private fun durationNeighborClusters(
        rows: List<opensource.cached_dupe_scanner.cache.SimilarityDurationFeatureRow>,
        step: DurationNeighborListStep
    ): List<ClusterDraft> {
        val tolerance = durationNeighborToleranceMillis(step)
        val sorted = rows.sortedWith(compareBy({ it.durationMillis }, { it.normalizedPath }))
        val clusters = mutableListOf<ClusterDraft>()
        var current = mutableListOf<opensource.cached_dupe_scanner.cache.SimilarityDurationFeatureRow>()
        for (row in sorted) {
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
        rows: List<opensource.cached_dupe_scanner.cache.SimilarityDurationFeatureRow>,
        step: DurationToleranceStep
    ): ClusterDraft {
        val min = rows.minOf { it.durationMillis }
        val max = rows.maxOf { it.durationMillis }
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
        rows: List<opensource.cached_dupe_scanner.cache.SimilarityDurationFeatureRow>,
        step: DurationNeighborListStep
    ): ClusterDraft {
        val min = rows.minOf { it.durationMillis }
        val max = rows.maxOf { it.durationMillis }
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
            true
        }
    }

    private fun repairStoredClusters(clusterIds: List<Long>) {
        if (clusterIds.isEmpty()) return
        clusterIds.distinct().chunked(SIMILARITY_DB_BIND_CHUNK_SIZE).forEach { ids ->
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
                dimensionsChecked = ready,
                durationChecked = ready && (
                    mediaScope == SimilarityMediaScope.Image || feature is CalculatedFeature.Duration
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
            durationFeature = (feature as? CalculatedFeature.Duration)?.let { duration ->
                SimilarityDurationFeatureEntity(
                    settingId = setting.settingId,
                    fileId = entity.fileId,
                    durationMillis = duration.durationMillis
                )
            }
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
        val resolved = uncheckedRows.mapNotNull { row ->
            if (!shouldContinue()) return@mapNotNull null
            onResolutionEvent(
                SimilarityMemberResolutionEvent(
                    kind = SimilarityMemberResolutionKind.Dimensions,
                    path = row.path,
                    completed = false
                )
            )
            val file = File(row.path)
            val dimensions = if (file.exists()) {
                mediaDimensionsExtractor.dimensions(
                    file = file,
                    mediaScope = mediaScope,
                    shouldContinue = shouldContinue
                )
            } else {
                null
            }
            if (!shouldContinue()) {
                null
            } else {
                onResolutionEvent(
                    SimilarityMemberResolutionEvent(
                        kind = SimilarityMemberResolutionKind.Dimensions,
                        path = row.path,
                        completed = true
                    )
                )
                PendingDimensionResolution(row, dimensions)
            }
        }
        if (resolved.isEmpty()) return rows

        val updatedRows = linkedMapOf<Long, SimilarityClusterMemberFileRow>()
        val updatedAtMillis = System.currentTimeMillis()
        database.runInTransaction {
            resolved.forEach { pending ->
                val row = pending.row
                val dimensions = pending.dimensions
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
        val resolved = uncheckedRows.mapNotNull { row ->
            if (!shouldContinue()) return@mapNotNull null
            onResolutionEvent(
                SimilarityMemberResolutionEvent(
                    kind = SimilarityMemberResolutionKind.Duration,
                    path = row.path,
                    completed = false
                )
            )
            val file = File(row.path)
            val durationMillis = if (mediaScope == SimilarityMediaScope.Video && file.exists()) {
                durationExtractor.durationMillis(
                    file = file,
                    shouldContinue = shouldContinue
                )?.takeIf { value -> value >= 0L }
            } else {
                null
            }
            if (!shouldContinue()) {
                null
            } else {
                onResolutionEvent(
                    SimilarityMemberResolutionEvent(
                        kind = SimilarityMemberResolutionKind.Duration,
                        path = row.path,
                        completed = true
                    )
                )
                PendingDurationResolution(row, durationMillis)
            }
        }
        if (resolved.isEmpty()) return rows

        val updatedRows = linkedMapOf<Long, SimilarityClusterMemberFileRow>()
        val acceptedDurations = mutableListOf<SimilarityDurationFeatureEntity>()
        val updatedAtMillis = System.currentTimeMillis()
        database.runInTransaction {
            resolved.forEach { pending ->
                val row = pending.row
                val durationMillis = pending.durationMillis
                val updated = similarityDao.updateSettingFileDurationIfCurrent(
                    settingId = row.settingId,
                    fileId = row.fileId,
                    sizeBytes = row.sizeBytes,
                    lastModifiedMillis = row.lastModifiedMillis,
                    updatedAtMillis = updatedAtMillis
                )
                if (updated > 0) {
                    similarityDao.deleteDurationFeature(row.settingId, row.fileId)
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
            if (acceptedDurations.isNotEmpty()) {
                similarityDao.upsertDurationFeatures(acceptedDurations)
            }
        }
        return rows.map { row -> updatedRows[row.fileId] ?: row }
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

private sealed interface CalculatedFeature {
    data class ExactThumbnail(val signature: String) : CalculatedFeature
    data class Duration(val durationMillis: Long) : CalculatedFeature
}

private data class PreparedSettingFile(
    val settingFile: SimilaritySettingFileEntity,
    val exactFeature: SimilarityExactThumbnailFeatureEntity?,
    val durationFeature: SimilarityDurationFeatureEntity?
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

private data class PendingDimensionResolution(
    val row: SimilarityClusterMemberFileRow,
    val dimensions: MediaDimensions?
)

private data class PendingDurationResolution(
    val row: SimilarityClusterMemberFileRow,
    val durationMillis: Long?
)

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
private const val SIMILARITY_DB_BIND_CHUNK_SIZE = 500
private const val SIMILARITY_CLEAR_BATCH_SIZE = 100
private const val SIMILARITY_CLUSTER_REPLACE_MAX_ATTEMPTS = 2
private const val SIMILARITY_WORK_COMPLETION_POLL_MILLIS = 100L
private const val SIMILARITY_WORK_EXECUTOR_SHUTDOWN_SECONDS = 5L
private const val SIMILARITY_FILE_STATUS_READY = "ready"
private const val SIMILARITY_FILE_STATUS_SKIPPED = "skipped-v2"
