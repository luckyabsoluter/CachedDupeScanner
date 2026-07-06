package opensource.cached_dupe_scanner.storage

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.util.UUID
import opensource.cached_dupe_scanner.cache.CacheDatabase
import opensource.cached_dupe_scanner.cache.CachedFileEntity
import opensource.cached_dupe_scanner.core.DurationNeighborListStep
import opensource.cached_dupe_scanner.core.DurationToleranceStep
import opensource.cached_dupe_scanner.core.ExactThumbnailHashStep
import opensource.cached_dupe_scanner.core.SimilarityMediaScope
import opensource.cached_dupe_scanner.core.SortDirection
import opensource.cached_dupe_scanner.core.VideoDurationExtractor
import opensource.cached_dupe_scanner.core.VideoFrameSignatureExtractor
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SimilaritySettingsRepositoryTest {
    private lateinit var database: CacheDatabase
    private lateinit var tempDir: File

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, CacheDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        tempDir = File(
            File(requireNotNull(System.getProperty("user.dir")), "build/test-temp"),
            "similarity-settings-${UUID.randomUUID()}"
        )
        tempDir.mkdirs()
    }

    @After
    fun teardown() {
        database.close()
        tempDir.deleteRecursively()
    }

    @Test
    fun exactThumbnailMaintenancePersistsClustersForOneSetting() {
        val first = videoFile("a.mp4")
        val second = videoFile("b.mp4")
        val unique = videoFile("c.mp4")
        database.fileCacheDao().upsert(entity(first))
        database.fileCacheDao().upsert(entity(second))
        database.fileCacheDao().upsert(entity(unique))
        val repository = repository(
            signatures = mapOf(
                first.absolutePath to "same",
                second.absolutePath to "same",
                unique.absolutePath to "unique"
            )
        )
        val setting = repository.createExactThumbnailSetting(
            mediaScope = SimilarityMediaScope.Video,
            minSizeBytes = 1L,
            step = exactStep(width = 2, height = 2),
            enabled = true
        )

        val summary = repository.runSettingMaintenance(
            settingId = setting.settingId,
            rebuild = true,
            shouldContinue = { true },
            onProgress = {}
        )

        assertEquals(3, summary.candidateCount)
        assertEquals(1, summary.clusterCount)
        assertEquals(2, summary.duplicateFileCount)
        val cluster = repository.listClusters(setting.settingId).single()
        assertEquals("same", cluster.clusterKey)
        assertEquals(
            listOf(first, second).map { it.normalizedPath() },
            repository.listClusterMembers(cluster.clusterId).map { it.metadata.normalizedPath }
        )
    }

    @Test
    fun settingsWithDifferentThumbnailSizesUseDifferentNumericRows() {
        val repository = repository()

        val twoByTwo = repository.createExactThumbnailSetting(
            mediaScope = SimilarityMediaScope.Video,
            minSizeBytes = 1L,
            step = exactStep(width = 2, height = 2)
        )
        val threeByThree = repository.createExactThumbnailSetting(
            mediaScope = SimilarityMediaScope.Video,
            minSizeBytes = 1L,
            step = exactStep(width = 3, height = 3)
        )

        assertNotEquals(twoByTwo.settingId, threeByThree.settingId)
        assertEquals(2, repository.listSettings().count { it.minSizeBytes == 1L })
    }

    @Test
    fun customSettingNamesPersistAndCanBeRenamed() {
        val repository = repository()

        val setting = repository.createExactThumbnailSetting(
            mediaScope = SimilarityMediaScope.Video,
            minSizeBytes = 1L,
            step = exactStep(width = 2, height = 2),
            displayName = "  Small videos  "
        )

        assertEquals("Small videos", setting.displayName)

        repository.renameSetting(setting.settingId, "Renamed videos")

        assertEquals("Renamed videos", repository.listSettings().single().displayName)

        val sameIdentity = repository.createExactThumbnailSetting(
            mediaScope = SimilarityMediaScope.Video,
            minSizeBytes = 1L,
            step = exactStep(width = 2, height = 2),
            displayName = "Updated videos"
        )

        assertEquals(setting.settingId, sameIdentity.settingId)
        assertEquals("Updated videos", sameIdentity.displayName)
        assertEquals(1, repository.listSettings().size)
    }

    @Test
    fun creatingExistingSettingCanEnableIt() {
        val repository = repository()
        val disabled = repository.createExactThumbnailSetting(
            mediaScope = SimilarityMediaScope.Video,
            minSizeBytes = 1L,
            step = exactStep(width = 2, height = 2),
            enabled = false
        )

        assertFalse(disabled.enabled)

        val enabled = repository.createExactThumbnailSetting(
            mediaScope = SimilarityMediaScope.Video,
            minSizeBytes = 1L,
            step = exactStep(width = 2, height = 2),
            enabled = true
        )

        assertEquals(disabled.settingId, enabled.settingId)
        assertTrue(enabled.enabled)
        assertTrue(repository.listSettings().single().enabled)
    }

    @Test
    fun listSettingsDoesNotCreateDefaultRows() {
        val repository = repository()

        assertTrue(repository.listSettings().isEmpty())
        assertFalse(repository.hasEnabledSettings())
        assertEquals(0, database.similaritySettingsDao().countSettings())
    }

    @Test
    fun hasEnabledSettingsReflectsEnabledRowsOnly() {
        val repository = repository()
        val setting = repository.createExactThumbnailSetting(
            mediaScope = SimilarityMediaScope.Video,
            minSizeBytes = 1L,
            step = exactStep(width = 2, height = 2),
            enabled = false
        )

        assertFalse(repository.hasEnabledSettings())

        repository.setEnabled(setting.settingId, true)
        assertTrue(repository.hasEnabledSettings())

        repository.setEnabled(setting.settingId, false)
        assertFalse(repository.hasEnabledSettings())
    }

    @Test
    fun enabledSettingCatchesUpDuringNextScanCacheGeneration() {
        val first = videoFile("enable-a.mp4")
        val second = videoFile("enable-b.mp4")
        val third = videoFile("enable-c.mp4")
        database.fileCacheDao().upsert(entity(first))
        database.fileCacheDao().upsert(entity(second))
        val repository = repository(
            signatures = mapOf(
                first.absolutePath to "same",
                second.absolutePath to "same",
                third.absolutePath to "same"
            )
        )
        val setting = repository.createExactThumbnailSetting(
            mediaScope = SimilarityMediaScope.Video,
            minSizeBytes = 1L,
            step = exactStep(width = 1, height = 1),
            enabled = true
        )

        repository.generateEnabledResults(shouldContinue = { true }, onProgress = {})
        repository.setEnabled(setting.settingId, false)
        database.fileCacheDao().upsert(entity(third))

        repository.setEnabled(setting.settingId, true)
        val beforeNextGeneration = repository.listClusters(setting.settingId).single()
        assertEquals(2, beforeNextGeneration.fileCount)

        repository.generateEnabledResults(shouldContinue = { true }, onProgress = {})

        val cluster = repository.listClusters(setting.settingId).single()
        assertEquals("same", cluster.clusterKey)
        assertEquals(3, cluster.fileCount)
        assertEquals(
            listOf(first, second, third).map { file -> file.normalizedPath() },
            repository.listClusterMembers(cluster.clusterId).map { member -> member.metadata.normalizedPath }
        )
    }

    @Test
    fun cacheMutationObserverRemovesStaleMembersInsideHistoryTransaction() {
        val first = videoFile("active-a.mp4")
        val second = videoFile("active-b.mp4")
        database.fileCacheDao().upsert(entity(first))
        database.fileCacheDao().upsert(entity(second))
        val repository = repository(
            signatures = mapOf(
                first.absolutePath to "same",
                second.absolutePath to "same"
            )
        )
        val setting = repository.createExactThumbnailSetting(
            mediaScope = SimilarityMediaScope.Video,
            minSizeBytes = 1L,
            step = exactStep(width = 1, height = 1),
            enabled = true
        )
        repository.runSettingMaintenance(setting.settingId, rebuild = true, shouldContinue = { true }, onProgress = {})
        val cluster = repository.listClusters(setting.settingId).single()
        val history = ScanHistoryRepository(
            dao = database.fileCacheDao(),
            settingsStore = AppSettingsStore(ApplicationProvider.getApplicationContext()),
            groupDao = database.duplicateGroupDao(),
            database = database,
            cacheMutationObserver = repository
        )

        history.deleteByNormalizedPath(second.normalizedPath())

        assertTrue(repository.listClusters(setting.settingId).isEmpty())
        assertEquals(0, repository.getClusterSummary(setting.settingId).clusterCount)
        assertEquals(null, repository.getCluster(setting.settingId, cluster.clusterId))
        assertTrue(
            repository.listClustersPage(
                settingId = setting.settingId,
                offset = 0,
                limit = 10,
                sortColumn = SimilarityClusterSortColumn.FileCount,
                direction = SortDirection.Desc
            ).isEmpty()
        )
    }

    @Test
    fun cacheDeletionCanPreserveStoredClusterUntilMaintenance() {
        val first = videoFile("preserved-a.mp4")
        val second = videoFile("preserved-b.mp4")
        database.fileCacheDao().upsert(entity(first))
        database.fileCacheDao().upsert(entity(second))
        val repository = repository(
            signatures = mapOf(
                first.absolutePath to "same",
                second.absolutePath to "same"
            )
        )
        val setting = repository.createExactThumbnailSetting(
            mediaScope = SimilarityMediaScope.Video,
            minSizeBytes = 1L,
            step = exactStep(width = 1, height = 1),
            enabled = true
        )
        repository.runSettingMaintenance(
            settingId = setting.settingId,
            rebuild = true,
            shouldContinue = { true },
            onProgress = {}
        )
        val history = ScanHistoryRepository(
            dao = database.fileCacheDao(),
            settingsStore = AppSettingsStore(ApplicationProvider.getApplicationContext()),
            groupDao = database.duplicateGroupDao(),
            database = database,
            cacheMutationObserver = repository
        )

        history.deleteByNormalizedPath(
            normalizedPath = second.normalizedPath(),
            notifyCacheMutationObserver = false
        )

        assertEquals(1, repository.getClusterSummary(setting.settingId).clusterCount)
        assertEquals(
            1,
            repository.listClustersPage(
                settingId = setting.settingId,
                offset = 0,
                limit = 10,
                sortColumn = SimilarityClusterSortColumn.FileCount,
                direction = SortDirection.Desc
            ).size
        )

        repository.runSettingMaintenance(
            settingId = setting.settingId,
            rebuild = false,
            shouldContinue = { true },
            onProgress = {}
        )

        assertEquals(0, repository.getClusterSummary(setting.settingId).clusterCount)
    }

    @Test
    fun durationToleranceMaintenanceUsesSeparateDurationFeatureTable() {
        val first = videoFile("duration-a.mp4")
        val second = videoFile("duration-b.mp4")
        val unique = videoFile("duration-c.mp4")
        database.fileCacheDao().upsert(entity(first))
        database.fileCacheDao().upsert(entity(second))
        database.fileCacheDao().upsert(entity(unique))
        val repository = repository(
            durations = mapOf(
                first.absolutePath to 10_000L,
                second.absolutePath to 10_500L,
                unique.absolutePath to 20_000L
            )
        )
        val setting = repository.createDurationToleranceSetting(
            minSizeBytes = 1L,
            step = DurationToleranceStep(toleranceSeconds = 1),
            enabled = true
        )

        val summary = repository.runSettingMaintenance(setting.settingId, rebuild = true, shouldContinue = { true }, onProgress = {})

        assertEquals(1, summary.clusterCount)
        assertTrue(repository.listClusters(setting.settingId).single().clusterKey.startsWith("duration-v1:1000:"))
    }

    @Test
    fun durationNeighborMaintenanceClustersConnectedNeighbors() {
        val first = videoFile("neighbor-a.mp4")
        val second = videoFile("neighbor-b.mp4")
        val third = videoFile("neighbor-c.mp4")
        val unique = videoFile("neighbor-d.mp4")
        listOf(first, second, third, unique).forEach { file -> database.fileCacheDao().upsert(entity(file)) }
        val repository = repository(
            durations = mapOf(
                first.absolutePath to 10_000L,
                second.absolutePath to 10_500L,
                third.absolutePath to 11_400L,
                unique.absolutePath to 20_000L
            )
        )
        val setting = repository.createDurationNeighborListSetting(
            minSizeBytes = 1L,
            step = DurationNeighborListStep(toleranceSeconds = 1),
            enabled = true
        )

        val summary = repository.runSettingMaintenance(setting.settingId, rebuild = true, shouldContinue = { true }, onProgress = {})
        val cluster = repository.listClusters(setting.settingId).single()
        val memberRows = repository.listClusterMembers(cluster.clusterId)
        val members = memberRows.map { member -> member.metadata.normalizedPath }
        val descendingPage = repository.listClusterMembersPage(
            clusterId = cluster.clusterId,
            offset = 0,
            limit = 2,
            direction = SortDirection.Desc
        )

        assertEquals(1, summary.clusterCount)
        assertEquals("duration-neighbor-list-v1:1000:0000000010000-0000000011400", cluster.clusterKey)
        assertEquals(
            listOf(first, second, third).map { file -> file.normalizedPath() },
            members
        )
        assertEquals(listOf(10_000L, 10_500L, 11_400L), memberRows.map { member -> member.durationMillis })
        assertEquals(
            listOf(third, second).map { file -> file.normalizedPath() },
            descendingPage.map { member -> member.metadata.normalizedPath }
        )
    }

    @Test
    fun createSettingNormalizesSimilarityIdentity() {
        val repository = repository()

        val first = repository.createDurationNeighborListSetting(
            minSizeBytes = 0L,
            step = DurationNeighborListStep(toleranceMillis = 0L)
        )
        val second = repository.createDurationNeighborListSetting(
            minSizeBytes = -1L,
            step = DurationNeighborListStep(toleranceMillis = -1L)
        )

        assertEquals(first.settingId, second.settingId)
        assertEquals(0L, second.minSizeBytes)
        assertEquals(first.paramsHash, second.paramsHash)
    }

    @Test
    fun clearSettingResultsLeavesSettingRow() {
        val first = videoFile("clear-a.mp4")
        val second = videoFile("clear-b.mp4")
        database.fileCacheDao().upsert(entity(first))
        database.fileCacheDao().upsert(entity(second))
        val repository = repository(
            signatures = mapOf(
                first.absolutePath to "same",
                second.absolutePath to "same"
            )
        )
        val setting = repository.createExactThumbnailSetting(
            mediaScope = SimilarityMediaScope.Video,
            minSizeBytes = 1L,
            step = exactStep(width = 1, height = 1),
            enabled = true
        )
        repository.runSettingMaintenance(setting.settingId, rebuild = true, shouldContinue = { true }, onProgress = {})

        repository.clearSettingResults(setting.settingId)

        assertTrue(repository.listClusters(setting.settingId).isEmpty())
        assertEquals(setting.settingId, repository.listSettings().first { it.settingId == setting.settingId }.settingId)
    }

    @Test
    fun deleteSettingRemovesSettingRowAndGeneratedData() {
        val first = videoFile("delete-a.mp4")
        val second = videoFile("delete-b.mp4")
        database.fileCacheDao().upsert(entity(first))
        database.fileCacheDao().upsert(entity(second))
        val repository = repository(
            signatures = mapOf(
                first.absolutePath to "same",
                second.absolutePath to "same"
            )
        )
        val setting = repository.createExactThumbnailSetting(
            mediaScope = SimilarityMediaScope.Video,
            minSizeBytes = 1L,
            step = exactStep(width = 1, height = 1),
            enabled = true
        )
        repository.runSettingMaintenance(setting.settingId, rebuild = true, shouldContinue = { true }, onProgress = {})

        repository.deleteSetting(setting.settingId)

        assertTrue(repository.listSettings().none { it.settingId == setting.settingId })
        assertTrue(repository.listClusters(setting.settingId).isEmpty())
        assertEquals(0, database.similaritySettingsDao().countSettings())
    }

    @Test
    fun clusterMembersCanBeLoadedByPage() {
        val first = videoFile("page-a.mp4")
        val second = videoFile("page-b.mp4")
        val third = videoFile("page-c.mp4")
        listOf(first, second, third).forEach { file -> database.fileCacheDao().upsert(entity(file)) }
        val repository = repository(
            signatures = mapOf(
                first.absolutePath to "same",
                second.absolutePath to "same",
                third.absolutePath to "same"
            )
        )
        val setting = repository.createExactThumbnailSetting(
            mediaScope = SimilarityMediaScope.Video,
            minSizeBytes = 1L,
            step = exactStep(width = 1, height = 1),
            enabled = true
        )
        repository.runSettingMaintenance(setting.settingId, rebuild = true, shouldContinue = { true }, onProgress = {})
        val cluster = repository.listClusters(setting.settingId).single()

        val firstPage = repository.listClusterMembersPage(cluster.clusterId, offset = 0, limit = 2)
        val secondPage = repository.listClusterMembersPage(cluster.clusterId, offset = 2, limit = 2)

        assertEquals(
            listOf(first, second).map { file -> file.normalizedPath() },
            firstPage.map { member -> member.metadata.normalizedPath }
        )
        assertEquals(
            listOf(third).map { file -> file.normalizedPath() },
            secondPage.map { member -> member.metadata.normalizedPath }
        )
    }

    @Test
    fun clustersCanBeLoadedBySortedPagesWithSummary() {
        val alphaOne = videoFile("cluster-alpha-1.mp4")
        val alphaTwo = videoFile("cluster-alpha-2.mp4")
        val betaOne = videoFile("cluster-beta-1.mp4")
        val betaTwo = videoFile("cluster-beta-2.mp4")
        val betaThree = videoFile("cluster-beta-3.mp4")
        val gammaOne = videoFile("cluster-gamma-1.mp4")
        val gammaTwo = videoFile("cluster-gamma-2.mp4")
        listOf(
            entity(alphaOne, sizeBytes = 100L),
            entity(alphaTwo, sizeBytes = 100L),
            entity(betaOne, sizeBytes = 50L),
            entity(betaTwo, sizeBytes = 50L),
            entity(betaThree, sizeBytes = 50L),
            entity(gammaOne, sizeBytes = 300L),
            entity(gammaTwo, sizeBytes = 300L)
        ).forEach { entity -> database.fileCacheDao().upsert(entity) }
        val repository = repository(
            signatures = mapOf(
                alphaOne.absolutePath to "alpha",
                alphaTwo.absolutePath to "alpha",
                betaOne.absolutePath to "beta",
                betaTwo.absolutePath to "beta",
                betaThree.absolutePath to "beta",
                gammaOne.absolutePath to "gamma",
                gammaTwo.absolutePath to "gamma"
            )
        )
        val setting = repository.createExactThumbnailSetting(
            mediaScope = SimilarityMediaScope.Video,
            minSizeBytes = 1L,
            step = exactStep(width = 1, height = 1),
            enabled = true
        )
        repository.runSettingMaintenance(setting.settingId, rebuild = true, shouldContinue = { true }, onProgress = {})

        val summary = repository.getClusterSummary(setting.settingId)
        val firstByCount = repository.listClustersPage(
            settingId = setting.settingId,
            offset = 0,
            limit = 2,
            sortColumn = SimilarityClusterSortColumn.FileCount,
            direction = SortDirection.Desc
        )
        val secondByCount = repository.listClustersPage(
            settingId = setting.settingId,
            offset = 2,
            limit = 2,
            sortColumn = SimilarityClusterSortColumn.FileCount,
            direction = SortDirection.Desc
        )
        val firstBySize = repository.listClustersPage(
            settingId = setting.settingId,
            offset = 0,
            limit = 3,
            sortColumn = SimilarityClusterSortColumn.TotalSize,
            direction = SortDirection.Asc
        )

        assertEquals(3, summary.clusterCount)
        assertEquals(7, summary.fileCount)
        assertEquals(listOf("beta", "gamma"), firstByCount.map { cluster -> cluster.clusterKey })
        assertEquals(listOf("alpha"), secondByCount.map { cluster -> cluster.clusterKey })
        assertEquals(listOf("beta", "alpha", "gamma"), firstBySize.map { cluster -> cluster.clusterKey })
        assertEquals(
            "beta",
            repository.getCluster(setting.settingId, firstByCount.first().clusterId)?.clusterKey
        )
    }

    @Test
    fun clusterMembersCanBeLoadedBySortedPages() {
        val older = videoFile("member-a.mp4").apply { setLastModified(1_000L) }
        val newest = videoFile("member-b.mp4").apply { setLastModified(3_000L) }
        val middle = videoFile("member-c.mp4").apply { setLastModified(2_000L) }
        listOf(older, newest, middle).forEach { file ->
            database.fileCacheDao().upsert(entity(file))
        }
        val repository = repository(
            signatures = mapOf(
                older.absolutePath to "same",
                newest.absolutePath to "same",
                middle.absolutePath to "same"
            )
        )
        val setting = repository.createExactThumbnailSetting(
            mediaScope = SimilarityMediaScope.Video,
            minSizeBytes = 1L,
            step = exactStep(width = 1, height = 1),
            enabled = true
        )
        repository.runSettingMaintenance(setting.settingId, rebuild = true, shouldContinue = { true }, onProgress = {})
        val cluster = repository.listClusters(setting.settingId).single()

        val firstPage = repository.listClusterMembersPage(
            clusterId = cluster.clusterId,
            offset = 0,
            limit = 2,
            sortColumn = SimilarityMemberSortColumn.Modified,
            direction = SortDirection.Desc
        )
        val secondPage = repository.listClusterMembersPage(
            clusterId = cluster.clusterId,
            offset = 2,
            limit = 2,
            sortColumn = SimilarityMemberSortColumn.Modified,
            direction = SortDirection.Desc
        )

        assertEquals(
            listOf(newest.normalizedPath(), middle.normalizedPath()),
            firstPage.map { it.metadata.normalizedPath }
        )
        assertEquals(listOf(older.normalizedPath()), secondPage.map { it.metadata.normalizedPath })
    }

    @Test
    fun rebuildCancellationBeforeClusteringRecordsCancelledSummary() {
        val first = videoFile("cancel-a.mp4")
        val second = videoFile("cancel-b.mp4")
        database.fileCacheDao().upsert(entity(first))
        database.fileCacheDao().upsert(entity(second))
        val repository = repository(
            signatures = mapOf(
                first.absolutePath to "same",
                second.absolutePath to "same"
            )
        )
        val setting = repository.createExactThumbnailSetting(
            mediaScope = SimilarityMediaScope.Video,
            minSizeBytes = 1L,
            step = exactStep(width = 1, height = 1),
            enabled = true
        )
        repository.runSettingMaintenance(setting.settingId, rebuild = true, shouldContinue = { true }, onProgress = {})
        assertNotNull(repository.listClusters(setting.settingId).singleOrNull())

        var shouldContinueCalls = 0
        val summary = repository.runSettingMaintenance(
            settingId = setting.settingId,
            rebuild = true,
            shouldContinue = { shouldContinueCalls++ == 0 },
            onProgress = {}
        )

        assertTrue(summary.cancelled)
        assertEquals(0, summary.processedCount)
        assertEquals(0, repository.getClusterSummary(setting.settingId).clusterCount)
    }

    private fun repository(
        signatures: Map<String, String> = emptyMap(),
        durations: Map<String, Long> = emptyMap()
    ): SimilaritySettingsRepository {
        return SimilaritySettingsRepository(
            database = database,
            fileDao = database.fileCacheDao(),
            similarityDao = database.similaritySettingsDao(),
            frameSignatureExtractor = FakeSignatureExtractor(signatures),
            durationExtractor = FakeDurationExtractor(durations)
        )
    }

    private fun videoFile(name: String): File {
        val file = File(tempDir, name)
        file.writeText("video")
        return file
    }

    private fun entity(file: File, sizeBytes: Long = file.length()): CachedFileEntity {
        return CachedFileEntity(
            normalizedPath = file.normalizedPath(),
            path = file.absolutePath,
            sizeBytes = sizeBytes,
            lastModifiedMillis = file.lastModified(),
            hashHex = null
        )
    }

    private fun File.normalizedPath(): String {
        return absolutePath.replace('\\', '/').lowercase()
    }

    private fun exactStep(width: Int, height: Int): ExactThumbnailHashStep {
        return ExactThumbnailHashStep(
            frameSeconds = listOf(0),
            resizeWidthPx = width,
            resizeHeightPx = height,
            quantizationLevels = 16,
            grayscale = false
        )
    }
}

private class FakeSignatureExtractor(
    private val signaturesByPath: Map<String, String>
) : VideoFrameSignatureExtractor {
    override fun signature(
        file: File,
        mediaScope: SimilarityMediaScope,
        step: ExactThumbnailHashStep,
        shouldContinue: () -> Boolean
    ): String? {
        return signaturesByPath[file.absolutePath]
    }
}

private class FakeDurationExtractor(
    private val durationsByPath: Map<String, Long>
) : VideoDurationExtractor {
    override fun durationMillis(file: File, shouldContinue: () -> Boolean): Long? {
        return durationsByPath[file.absolutePath]
    }
}
