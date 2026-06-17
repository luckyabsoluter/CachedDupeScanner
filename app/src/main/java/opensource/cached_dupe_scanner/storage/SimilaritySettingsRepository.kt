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
import opensource.cached_dupe_scanner.core.AndroidVideoDurationExtractor
import opensource.cached_dupe_scanner.core.AndroidVideoFrameSignatureExtractor
import opensource.cached_dupe_scanner.core.DurationNeighborListStep
import opensource.cached_dupe_scanner.core.DurationToleranceStep
import opensource.cached_dupe_scanner.core.ExactThumbnailHashStep
import opensource.cached_dupe_scanner.core.FileMetadata
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
import opensource.cached_dupe_scanner.core.durationNeighborListSettingDraft
import opensource.cached_dupe_scanner.core.durationNeighborListStepFromParams
import opensource.cached_dupe_scanner.core.durationNeighborToleranceMillis
import opensource.cached_dupe_scanner.core.durationToleranceMillis
import opensource.cached_dupe_scanner.core.durationToleranceSettingDraft
import opensource.cached_dupe_scanner.core.durationToleranceStepFromParams
import opensource.cached_dupe_scanner.core.exactThumbnailSettingDraft
import opensource.cached_dupe_scanner.core.exactThumbnailStepFromParams
import opensource.cached_dupe_scanner.core.normalizedSimilaritySettingDisplayName
import java.io.File
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

data class SimilarityClusterMember(
    val metadata: FileMetadata,
    val durationMillis: Long? = null
)

class SimilaritySettingsRepository(
    private val database: CacheDatabase,
    private val fileDao: FileCacheDao,
    private val similarityDao: SimilaritySettingsDao,
    private val frameSignatureExtractor: VideoFrameSignatureExtractor = AndroidVideoFrameSignatureExtractor(),
    private val durationExtractor: VideoDurationExtractor = AndroidVideoDurationExtractor()
) : CacheMutationObserver {
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
                error("Similarity setting parameter hash collision for ${draft.methodId}.")
            }
            if (updateExistingDisplayName && existing.displayName != draft.displayName) {
                similarityDao.updateSettingDisplayName(
                    settingId = existing.settingId,
                    displayName = draft.displayName,
                    updatedAtMillis = now
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

    fun clearSettingResults(settingId: Long) {
        database.runInTransaction {
            clearSettingDataLocked(settingId)
            similarityDao.deleteMaintenanceRunsForSetting(settingId)
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

    fun listClusterMembers(clusterId: Long): List<SimilarityClusterMember> {
        return similarityDao.listActiveClusterMembers(clusterId).map { row ->
            row.toClusterMember()
        }
    }

    fun listClusterMembersPage(
        clusterId: Long,
        offset: Int,
        limit: Int,
        direction: SortDirection = SortDirection.Asc
    ): List<SimilarityClusterMember> {
        val safeOffset = offset.coerceAtLeast(0)
        val safeLimit = limit.coerceAtLeast(0)
        val rows = if (direction == SortDirection.Desc) {
            similarityDao.listActiveClusterMembersPageDescending(
                clusterId = clusterId,
                offset = safeOffset,
                limit = safeLimit
            )
        } else {
            similarityDao.listActiveClusterMembersPage(
                clusterId = clusterId,
                offset = safeOffset,
                limit = safeLimit
            )
        }
        return rows.map { row ->
            row.toClusterMember()
        }
    }

    fun generateEnabledResults(
        shouldContinue: () -> Boolean,
        onProgress: (SimilarityMaintenanceProgress) -> Unit
    ): SimilarityMaintenanceSummary {
        val settings = similarityDao.listEnabledSettings()
        return runSettingsMaintenance(
            settings = settings,
            rebuild = false,
            shouldContinue = shouldContinue,
            onProgress = onProgress
        )
    }

    fun runSettingMaintenance(
        settingId: Long,
        rebuild: Boolean,
        shouldContinue: () -> Boolean,
        onProgress: (SimilarityMaintenanceProgress) -> Unit
    ): SimilarityMaintenanceSummary {
        val setting = similarityDao.getSetting(settingId) ?: return emptySummary(cancelled = false)
        return runSettingsMaintenance(
            settings = listOf(setting),
            rebuild = rebuild,
            shouldContinue = shouldContinue,
            onProgress = onProgress
        )
    }

    override fun onCachedFilesChanged(normalizedPaths: List<String>) {
        if (normalizedPaths.isEmpty()) return
        normalizedPaths.chunked(SIMILARITY_DB_BIND_CHUNK_SIZE).forEach { chunk ->
            similarityDao.deleteSettingFilesByPaths(chunk)
            similarityDao.deleteExactThumbnailFeaturesByPaths(chunk)
            similarityDao.deleteDurationFeaturesByPaths(chunk)
            similarityDao.deleteClusterMembersByPaths(chunk)
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

            val settingFiles = mutableListOf<SimilaritySettingFileEntity>()
            val exactFeatures = mutableListOf<SimilarityExactThumbnailFeatureEntity>()
            val durationFeatures = mutableListOf<SimilarityDurationFeatureEntity>()
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
                val existing = similarityDao.getSettingFile(setting.settingId, entity.normalizedPath)
                val isFresh = !rebuild &&
                    existing?.status == SIMILARITY_FILE_STATUS_READY &&
                    existing.sizeBytes == entity.sizeBytes &&
                    existing.lastModifiedMillis == entity.lastModifiedMillis
                if (isFresh) {
                    processed += 1
                    afterPath = entity.normalizedPath
                    continue
                }

                val feature = calculateFeature(setting, mediaScope, entity, shouldContinue)
                if (feature == null) {
                    skipped += 1
                    settingFiles += settingFile(setting, entity, SIMILARITY_FILE_STATUS_SKIPPED, now)
                } else {
                    settingFiles += settingFile(setting, entity, SIMILARITY_FILE_STATUS_READY, now)
                    when (feature) {
                        is CalculatedFeature.ExactThumbnail -> exactFeatures += SimilarityExactThumbnailFeatureEntity(
                            settingId = setting.settingId,
                            normalizedPath = entity.normalizedPath,
                            thumbnailSignature = feature.signature
                        )
                        is CalculatedFeature.Duration -> durationFeatures += SimilarityDurationFeatureEntity(
                            settingId = setting.settingId,
                            normalizedPath = entity.normalizedPath,
                            durationMillis = feature.durationMillis
                        )
                    }
                }
                processed += 1
                afterPath = entity.normalizedPath
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

            database.runInTransaction {
                similarityDao.upsertSettingFiles(settingFiles)
                if (exactFeatures.isNotEmpty()) similarityDao.upsertExactThumbnailFeatures(exactFeatures)
                if (durationFeatures.isNotEmpty()) similarityDao.upsertDurationFeatures(durationFeatures)
            }

            if (batch.size < SIMILARITY_MAINTENANCE_BATCH_SIZE) break
        }

        val clusterDrafts = buildClusters(setting)
        replaceClusters(setting.settingId, clusterDrafts)
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

    private fun buildClusters(setting: SimilaritySettingEntity): List<ClusterDraft> {
        return when (setting.methodId) {
            SIMILARITY_METHOD_EXACT_THUMBNAIL -> {
                similarityDao.listActiveExactThumbnailFeatures(setting.settingId)
                    .groupBy { row -> row.thumbnailSignature }
                    .filterValues { rows -> rows.size > 1 }
                    .map { (signature, rows) ->
                        ClusterDraft(
                            clusterKey = signature,
                            members = rows.map { row ->
                                ClusterMemberDraft(
                                    normalizedPath = row.normalizedPath,
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
                    normalizedPath = row.normalizedPath,
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
                    normalizedPath = row.normalizedPath,
                    sizeBytes = row.sizeBytes
                )
            }
        )
    }

    private fun replaceClusters(settingId: Long, drafts: List<ClusterDraft>) {
        val now = System.currentTimeMillis()
        database.runInTransaction {
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
                            normalizedPath = member.normalizedPath,
                            position = index
                        )
                    }
                )
            }
        }
    }

    private fun clearSettingDataLocked(settingId: Long) {
        similarityDao.deleteSettingFiles(settingId)
        similarityDao.deleteExactThumbnailFeatures(settingId)
        similarityDao.deleteDurationFeatures(settingId)
        similarityDao.deleteClusterMembersForSetting(settingId)
        similarityDao.deleteClustersForSetting(settingId)
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
        updatedAtMillis: Long
    ): SimilaritySettingFileEntity {
        return SimilaritySettingFileEntity(
            settingId = setting.settingId,
            normalizedPath = entity.normalizedPath,
            sizeBytes = entity.sizeBytes,
            lastModifiedMillis = entity.lastModifiedMillis,
            status = status,
            updatedAtMillis = updatedAtMillis
        )
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

private data class ClusterDraft(
    val clusterKey: String,
    val members: List<ClusterMemberDraft>
)

private data class ClusterMemberDraft(
    val normalizedPath: String,
    val sizeBytes: Long
)

private fun SimilarityClusterMemberFileRow.toFileMetadata(): FileMetadata {
    return FileMetadata(
        path = path,
        normalizedPath = normalizedPath,
        sizeBytes = sizeBytes,
        lastModifiedMillis = lastModifiedMillis,
        hashHex = hashHex
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
private const val SIMILARITY_FILE_STATUS_READY = "ready"
private const val SIMILARITY_FILE_STATUS_SKIPPED = "skipped"
