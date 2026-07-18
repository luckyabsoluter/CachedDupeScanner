package opensource.cached_dupe_scanner.storage

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import opensource.cached_dupe_scanner.cache.CacheDatabase
import opensource.cached_dupe_scanner.cache.CachedFileEntity
import opensource.cached_dupe_scanner.cache.SimilarityClusterEntity
import opensource.cached_dupe_scanner.cache.SimilarityClusterMemberEntity
import opensource.cached_dupe_scanner.cache.SimilarityDurationFeatureEntity
import opensource.cached_dupe_scanner.cache.SimilarityExactThumbnailFeatureEntity
import opensource.cached_dupe_scanner.cache.SimilarityMaintenanceRunEntity
import opensource.cached_dupe_scanner.cache.SimilaritySettingFileEntity
import opensource.cached_dupe_scanner.cache.StoredHash
import opensource.cached_dupe_scanner.core.DurationNeighborListStep
import opensource.cached_dupe_scanner.core.DurationToleranceStep
import opensource.cached_dupe_scanner.core.ExactThumbnailHashStep
import opensource.cached_dupe_scanner.core.MediaDimensions
import opensource.cached_dupe_scanner.core.MediaDimensionsExtractor
import opensource.cached_dupe_scanner.core.SimilarityMediaScope
import opensource.cached_dupe_scanner.core.SortDirection
import opensource.cached_dupe_scanner.core.VideoDurationExtractor
import opensource.cached_dupe_scanner.core.VideoFrameSignatureExtractor
import opensource.cached_dupe_scanner.ui.home.ResultsFilterCluster
import opensource.cached_dupe_scanner.ui.home.ResultsFilterDefinition
import opensource.cached_dupe_scanner.ui.home.ResultsFilterRule
import opensource.cached_dupe_scanner.ui.home.ResultsFilterTarget
import opensource.cached_dupe_scanner.ui.home.SimilarityFilterResolutionProgress
import opensource.cached_dupe_scanner.ui.home.loadFilteredSimilarityClustersPage
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
    fun exactThumbnailMaintenancePersistsHashedBlobClustersForOneSetting() {
        val first = videoFile("a.mp4")
        val second = videoFile("b.mp4")
        val unique = videoFile("c.mp4")
        database.fileCacheDao().upsert(entity(first))
        database.fileCacheDao().upsert(entity(second))
        database.fileCacheDao().upsert(entity(unique))
        val repository = repository(
            signatures = mapOf(
                first.absolutePath to THUMBNAIL_TEST_HASH,
                second.absolutePath to THUMBNAIL_TEST_HASH,
                unique.absolutePath to THUMBNAIL_UNIQUE_TEST_HASH
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
        assertEquals(
            "thumb-v2:video:color:2x2:q16:0:$THUMBNAIL_TEST_HASH",
            cluster.clusterKey
        )
        database.openHelper.writableDatabase.query(
            "SELECT typeof(thumbnailHash), length(thumbnailHash) " +
                "FROM similarity_exact_thumbnail_features LIMIT 1"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("blob", cursor.getString(0))
            assertEquals(32, cursor.getInt(1))
        }
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
    fun dbMaintenanceMissingFileDeletionAlsoRemovesSimilarityGroup() {
        val first = videoFile("maintenance-a.mp4")
        val second = videoFile("maintenance-b.mp4")
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
        assertEquals(1, repository.getClusterSummary(setting.settingId).clusterCount)
        assertTrue(second.delete())

        val summary = history.runMaintenance(
            deleteMissing = true,
            rehashStale = false,
            rehashMissing = false,
            shouldContinue = { true },
            onProgress = {}
        )

        assertEquals(1, summary.deleted)
        assertEquals(null, database.fileCacheDao().getByNormalizedPath(second.normalizedPath()))
        assertEquals(0, repository.getClusterSummary(setting.settingId).clusterCount)
        assertTrue(repository.listClusters(setting.settingId).isEmpty())
    }

    @Test
    fun concurrentMaintenanceCallsEnterFeatureExtractionSerially() {
        val first = videoFile("serialized-a.mp4")
        val second = videoFile("serialized-b.mp4")
        database.fileCacheDao().upsert(entity(first))
        database.fileCacheDao().upsert(entity(second))
        val extractor = BlockingConcurrencySignatureExtractor()
        val repository = SimilaritySettingsRepository(
            database = database,
            fileDao = database.fileCacheDao(),
            similarityDao = database.similaritySettingsDao(),
            frameSignatureExtractor = extractor,
            durationExtractor = FakeDurationExtractor(emptyMap())
        )
        val setting = repository.createExactThumbnailSetting(
            mediaScope = SimilarityMediaScope.Video,
            minSizeBytes = 1L,
            step = exactStep(width = 1, height = 1),
            enabled = true
        )
        val firstRun = Thread {
            repository.runSettingMaintenance(
                settingId = setting.settingId,
                rebuild = true,
                shouldContinue = { true },
                onProgress = {}
            )
        }
        val secondRunStarted = CountDownLatch(1)
        val secondRun = Thread {
            secondRunStarted.countDown()
            repository.runSettingMaintenance(
                settingId = setting.settingId,
                rebuild = false,
                shouldContinue = { true },
                onProgress = {}
            )
        }

        firstRun.start()
        assertTrue(extractor.firstExtractionEntered.await(5, TimeUnit.SECONDS))
        secondRun.start()
        assertTrue(secondRunStarted.await(5, TimeUnit.SECONDS))
        waitForThreadState(secondRun, Thread.State.BLOCKED)
        assertEquals(1, extractor.activeExtractions.get())

        extractor.releaseFirstExtraction.countDown()
        firstRun.join(5_000)
        secondRun.join(5_000)

        assertFalse(firstRun.isAlive)
        assertFalse(secondRun.isAlive)
        assertEquals(1, extractor.maxConcurrentExtractions.get())
    }

    @Test
    fun configuredWorkersBoundConcurrentFeatureExtractionAndPersistResults() {
        val files = (0 until 6).map { index -> videoFile("parallel-similarity-$index.mp4") }
        files.forEach { file -> database.fileCacheDao().upsert(entity(file)) }
        val extractor = BlockingParallelSignatureExtractor(expectedConcurrent = 3)
        val providerCalls = AtomicInteger(0)
        val repository = SimilaritySettingsRepository(
            database = database,
            fileDao = database.fileCacheDao(),
            similarityDao = database.similaritySettingsDao(),
            frameSignatureExtractor = extractor,
            durationExtractor = FakeDurationExtractor(emptyMap()),
            mediaDimensionsExtractor = FakeMediaDimensionsExtractor(emptyMap()),
            workerCountProvider = {
                providerCalls.incrementAndGet()
                3
            }
        )
        val setting = repository.createExactThumbnailSetting(
            mediaScope = SimilarityMediaScope.Video,
            minSizeBytes = 1L,
            step = exactStep(width = 1, height = 1),
            enabled = true
        )
        val summary = AtomicReference<SimilarityMaintenanceSummary?>()
        val failure = AtomicReference<Throwable?>()
        val progressThreads = mutableListOf<Thread>()
        val maintenanceThread = Thread {
            runCatching {
                repository.runSettingMaintenance(
                    settingId = setting.settingId,
                    rebuild = true,
                    shouldContinue = { true },
                    onProgress = { progressThreads += Thread.currentThread() }
                )
            }.onSuccess(summary::set).onFailure(failure::set)
        }

        maintenanceThread.start()
        val reachedConfiguredConcurrency = extractor.expectedWorkersEntered.await(5, TimeUnit.SECONDS)
        extractor.releaseWorkers.countDown()
        maintenanceThread.join(10_000L)

        assertTrue(reachedConfiguredConcurrency)
        assertFalse(maintenanceThread.isAlive)
        failure.get()?.let { error -> throw AssertionError(error) }
        assertEquals(1, providerCalls.get())
        assertEquals(3, extractor.maxConcurrentExtractions.get())
        assertEquals(files.size, extractor.extractionCalls.get())
        assertEquals(files.size, summary.get()?.processedCount)
        assertEquals(1, summary.get()?.clusterCount)
        assertEquals(files.size, database.similaritySettingsDao().countSettingFiles(setting.settingId))
        assertTrue(progressThreads.isNotEmpty())
        assertTrue(progressThreads.all { progressThread -> progressThread === maintenanceThread })
    }

    @Test
    fun parallelFeatureCancellationDoesNotPersistIncompleteBatch() {
        val files = (0 until 4).map { index -> videoFile("cancel-parallel-similarity-$index.mp4") }
        files.forEach { file -> database.fileCacheDao().upsert(entity(file)) }
        val allow = AtomicBoolean(true)
        val extractor = BlockingCancellationSignatureExtractor()
        val repository = SimilaritySettingsRepository(
            database = database,
            fileDao = database.fileCacheDao(),
            similarityDao = database.similaritySettingsDao(),
            frameSignatureExtractor = extractor,
            durationExtractor = FakeDurationExtractor(emptyMap()),
            mediaDimensionsExtractor = FakeMediaDimensionsExtractor(emptyMap()),
            workerCountProvider = { 3 }
        )
        val setting = repository.createExactThumbnailSetting(
            mediaScope = SimilarityMediaScope.Video,
            minSizeBytes = 1L,
            step = exactStep(width = 1, height = 1),
            enabled = true
        )
        val summary = AtomicReference<SimilarityMaintenanceSummary?>()
        val maintenanceThread = Thread {
            summary.set(
                repository.runSettingMaintenance(
                    settingId = setting.settingId,
                    rebuild = true,
                    shouldContinue = allow::get,
                    onProgress = {}
                )
            )
        }

        maintenanceThread.start()
        val workerEntered = extractor.workerEntered.await(5, TimeUnit.SECONDS)
        allow.set(false)
        extractor.releaseWorkers.countDown()
        maintenanceThread.join(10_000L)

        assertTrue(workerEntered)
        assertFalse(maintenanceThread.isAlive)
        assertTrue(summary.get()?.cancelled == true)
        assertEquals(0, summary.get()?.processedCount)
        assertEquals(0, database.similaritySettingsDao().countSettingFiles(setting.settingId))
        assertEquals(0, database.similaritySettingsDao().countExactThumbnailFeatures(setting.settingId))
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
    fun durationAverageFilterUsesStoredDurationsAcrossMemberPages() {
        val closeFiles = listOf(
            videoFile("average-close-a.mp4"),
            videoFile("average-close-b.mp4"),
            videoFile("average-close-c.mp4")
        )
        val spreadFiles = listOf(
            videoFile("average-spread-a.mp4"),
            videoFile("average-spread-b.mp4"),
            videoFile("average-spread-c.mp4")
        )
        (closeFiles + spreadFiles).forEach { file -> database.fileCacheDao().upsert(entity(file)) }
        val repository = repository(
            durations = mapOf(
                closeFiles[0].absolutePath to 10_000L,
                closeFiles[1].absolutePath to 10_100L,
                closeFiles[2].absolutePath to 10_200L,
                spreadFiles[0].absolutePath to 20_000L,
                spreadFiles[1].absolutePath to 21_000L,
                spreadFiles[2].absolutePath to 24_000L
            )
        )
        val setting = repository.createDurationToleranceSetting(
            minSizeBytes = 1L,
            step = DurationToleranceStep(toleranceMillis = 4_000L),
            enabled = true
        )
        repository.runSettingMaintenance(
            settingId = setting.settingId,
            rebuild = true,
            shouldContinue = { true },
            onProgress = {}
        )
        val definition = ResultsFilterDefinition(
            clusters = listOf(
                ResultsFilterCluster(
                    id = "cluster_duration",
                    name = "Near average",
                    rules = listOf(
                        ResultsFilterRule(
                            id = "rule_duration",
                            target = ResultsFilterTarget.DurationFromAverage,
                            durationToleranceMilliseconds = "100"
                        )
                    )
                )
            )
        )

        val filtered = loadFilteredSimilarityClustersPage(
            repository = repository,
            settingId = setting.settingId,
            sortColumn = SimilarityClusterSortColumn.FileCount,
            sortDirection = SortDirection.Desc,
            definition = definition,
            startOffset = 0,
            minMatches = 10,
            sourcePageSize = 1,
            memberPageSize = 1
        )

        assertEquals(1, filtered.clusters.size)
        assertEquals(
            closeFiles.map { file -> file.normalizedPath() },
            repository.listClusterMembers(filtered.clusters.single().clusterId)
                .map { member -> member.metadata.normalizedPath }
        )
        assertEquals(
            listOf(10_000L, 10_100L, 10_200L),
            repository.listClusterMembers(filtered.clusters.single().clusterId)
                .map { member -> member.metadata.durationMillis }
        )
    }

    @Test
    fun durationAverageFilterResolvesExactThumbnailDurationsWithoutMaintenance() {
        val files = listOf(
            videoFile("exact-duration-a.mp4"),
            videoFile("exact-duration-b.mp4"),
            videoFile("exact-duration-c.mp4")
        )
        files.forEach { file -> database.fileCacheDao().upsert(entity(file)) }
        val repository = repository(
            signatures = files.associate { file -> file.absolutePath to "same" },
            durations = mapOf(
                files[0].absolutePath to 10_000L,
                files[1].absolutePath to 10_100L,
                files[2].absolutePath to 10_200L
            )
        )
        val setting = repository.createExactThumbnailSetting(
            mediaScope = SimilarityMediaScope.Video,
            minSizeBytes = 1L,
            step = exactStep(width = 2, height = 2),
            enabled = true
        )
        repository.runSettingMaintenance(
            settingId = setting.settingId,
            rebuild = true,
            shouldContinue = { true },
            onProgress = {}
        )
        val definition = ResultsFilterDefinition(
            clusters = listOf(
                ResultsFilterCluster(
                    id = "cluster_exact_duration",
                    name = "Near average",
                    rules = listOf(
                        ResultsFilterRule(
                            id = "rule_exact_duration",
                            target = ResultsFilterTarget.DurationFromAverage,
                            durationToleranceMilliseconds = "100"
                        )
                    )
                )
            )
        )
        val progress = mutableListOf<SimilarityFilterResolutionProgress>()
        val filtered = loadFilteredSimilarityClustersPage(
            repository = repository,
            settingId = setting.settingId,
            sortColumn = SimilarityClusterSortColumn.FileCount,
            sortDirection = SortDirection.Desc,
            definition = definition,
            startOffset = 0,
            minMatches = 10,
            sourcePageSize = 1,
            memberPageSize = 1,
            onResolutionProgress = progress::add
        )

        assertEquals(1, filtered.clusters.size)
        assertEquals(0, progress.first().processed)
        assertEquals(3, progress.first().total)
        assertEquals(3, progress.last().processed)
        assertEquals(3, progress.last().total)
        assertEquals(SimilarityMemberResolutionKind.Duration, progress.last().kind)
        assertEquals(files.last().absolutePath, progress.last().currentPath)
        assertEquals(
            listOf(10_000L, 10_100L, 10_200L),
            repository.listClusterMembers(filtered.clusters.single().clusterId)
                .map { member -> member.metadata.durationMillis }
        )
        files.forEach { file ->
            assertTrue(
                requireNotNull(
                    database.similaritySettingsDao().getSettingFile(
                        settingId = setting.settingId,
                        fileId = fileId(file)
                    )
                ).durationChecked
            )
        }

        val cachedProgress = mutableListOf<SimilarityFilterResolutionProgress>()
        val cached = loadFilteredSimilarityClustersPage(
            repository = repository(),
            settingId = setting.settingId,
            sortColumn = SimilarityClusterSortColumn.FileCount,
            sortDirection = SortDirection.Desc,
            definition = definition,
            startOffset = 0,
            minMatches = 10,
            sourcePageSize = 1,
            memberPageSize = 1,
            onResolutionProgress = cachedProgress::add
        )

        assertEquals(1, cached.clusters.size)
        assertTrue(cachedProgress.isEmpty())
    }

    @Test
    fun sameResolutionFilterUsesPersistedDimensionsAcrossMemberPages() {
        val matchingFiles = listOf(
            videoFile("resolution-match-a.mp4"),
            videoFile("resolution-match-b.mp4")
        )
        val differentFiles = listOf(
            videoFile("resolution-different-a.mp4"),
            videoFile("resolution-different-b.mp4")
        )
        (matchingFiles + differentFiles).forEach { file ->
            database.fileCacheDao().upsert(entity(file))
        }
        val repository = repository(
            signatures = mapOf(
                matchingFiles[0].absolutePath to "matching-resolution",
                matchingFiles[1].absolutePath to "matching-resolution",
                differentFiles[0].absolutePath to "different-resolution",
                differentFiles[1].absolutePath to "different-resolution"
            ),
            dimensions = mapOf(
                matchingFiles[0].absolutePath to MediaDimensions(1920, 1080),
                matchingFiles[1].absolutePath to MediaDimensions(1920, 1080),
                differentFiles[0].absolutePath to MediaDimensions(1920, 1080),
                differentFiles[1].absolutePath to MediaDimensions(1280, 720)
            )
        )
        val setting = repository.createExactThumbnailSetting(
            mediaScope = SimilarityMediaScope.Video,
            minSizeBytes = 1L,
            step = exactStep(width = 2, height = 2),
            enabled = true
        )
        repository.runSettingMaintenance(
            settingId = setting.settingId,
            rebuild = true,
            shouldContinue = { true },
            onProgress = {}
        )
        val definition = ResultsFilterDefinition(
            clusters = listOf(
                ResultsFilterCluster(
                    id = "cluster_resolution",
                    name = "Same resolution",
                    rules = listOf(
                        ResultsFilterRule(
                            id = "rule_resolution",
                            target = ResultsFilterTarget.SameResolution
                        )
                    )
                )
            )
        )

        val filtered = loadFilteredSimilarityClustersPage(
            repository = repository,
            settingId = setting.settingId,
            sortColumn = SimilarityClusterSortColumn.FileCount,
            sortDirection = SortDirection.Desc,
            definition = definition,
            startOffset = 0,
            minMatches = 10,
            sourcePageSize = 1,
            memberPageSize = 1
        )

        assertEquals(1, filtered.clusters.size)
        assertEquals(
            matchingFiles.map { file -> file.normalizedPath() },
            repository.listClusterMembers(filtered.clusters.single().clusterId)
                .map { member -> member.metadata.normalizedPath }
        )
        assertEquals(
            listOf(1920 to 1080, 1920 to 1080),
            repository.listClusterMembers(filtered.clusters.single().clusterId)
                .map { member -> member.metadata.widthPixels to member.metadata.heightPixels }
        )
    }

    @Test
    fun sameResolutionFilterBackfillsUncheckedDimensionsWithoutMaintenance() {
        val first = videoFile("filter-backfill-a.mp4")
        val second = videoFile("filter-backfill-b.mp4")
        listOf(first, second).forEach { file -> database.fileCacheDao().upsert(entity(file)) }
        val initialRepository = repository(
            signatures = mapOf(
                first.absolutePath to "same",
                second.absolutePath to "same"
            )
        )
        val setting = initialRepository.createExactThumbnailSetting(
            mediaScope = SimilarityMediaScope.Video,
            minSizeBytes = 1L,
            step = exactStep(width = 2, height = 2),
            enabled = true
        )
        initialRepository.runSettingMaintenance(
            settingId = setting.settingId,
            rebuild = true,
            shouldContinue = { true },
            onProgress = {}
        )
        val similarityDao = database.similaritySettingsDao()
        listOf(first, second).forEach { file ->
            val stored = requireNotNull(similarityDao.getSettingFile(setting.settingId, fileId(file)))
            similarityDao.upsertSettingFiles(
                listOf(
                    stored.copy(
                        widthPixels = null,
                        heightPixels = null,
                        dimensionsChecked = false
                    )
                )
            )
        }
        val filteringRepository = repository(
            dimensions = mapOf(
                first.absolutePath to MediaDimensions(1920, 1080),
                second.absolutePath to MediaDimensions(1920, 1080)
            )
        )
        val definition = ResultsFilterDefinition(
            clusters = listOf(
                ResultsFilterCluster(
                    id = "cluster_resolution_backfill",
                    name = "Same resolution",
                    rules = listOf(
                        ResultsFilterRule(
                            id = "rule_resolution_backfill",
                            target = ResultsFilterTarget.SameResolution
                        )
                    )
                )
            )
        )
        val progress = mutableListOf<SimilarityFilterResolutionProgress>()

        val filtered = loadFilteredSimilarityClustersPage(
            repository = filteringRepository,
            settingId = setting.settingId,
            sortColumn = SimilarityClusterSortColumn.FileCount,
            sortDirection = SortDirection.Desc,
            definition = definition,
            startOffset = 0,
            minMatches = 10,
            sourcePageSize = 1,
            memberPageSize = 1,
            onResolutionProgress = progress::add
        )

        assertEquals(1, filtered.clusters.size)
        assertEquals(0, progress.first().processed)
        assertEquals(2, progress.first().total)
        assertEquals(2, progress.last().processed)
        assertEquals(2, progress.last().total)
        assertEquals(SimilarityMemberResolutionKind.Dimensions, progress.last().kind)
        assertEquals(second.absolutePath, progress.last().currentPath)
        listOf(first, second).forEach { file ->
            val stored = requireNotNull(similarityDao.getSettingFile(setting.settingId, fileId(file)))
            assertEquals(1920, stored.widthPixels)
            assertEquals(1080, stored.heightPixels)
            assertTrue(stored.dimensionsChecked)
        }
    }

    @Test
    fun maintenanceBackfillsMigratedDimensionsWithoutRecalculatingFreshFeatures() {
        val first = videoFile("backfill-a.mp4")
        val second = videoFile("backfill-b.mp4")
        listOf(first, second).forEach { file -> database.fileCacheDao().upsert(entity(file)) }
        val initialRepository = repository(
            signatures = mapOf(
                first.absolutePath to "same",
                second.absolutePath to "same"
            )
        )
        val setting = initialRepository.createExactThumbnailSetting(
            mediaScope = SimilarityMediaScope.Video,
            minSizeBytes = 1L,
            step = exactStep(width = 2, height = 2),
            enabled = true
        )
        initialRepository.runSettingMaintenance(
            settingId = setting.settingId,
            rebuild = true,
            shouldContinue = { true },
            onProgress = {}
        )
        val similarityDao = database.similaritySettingsDao()
        listOf(first, second).forEach { file ->
            val stored = requireNotNull(similarityDao.getSettingFile(setting.settingId, fileId(file)))
            similarityDao.upsertSettingFiles(
                listOf(
                    stored.copy(
                        widthPixels = null,
                        heightPixels = null,
                        dimensionsChecked = false
                    )
                )
            )
        }
        val backfillRepository = repository(
            signatures = emptyMap(),
            dimensions = mapOf(
                first.absolutePath to MediaDimensions(3840, 2160),
                second.absolutePath to MediaDimensions(3840, 2160)
            )
        )

        backfillRepository.runSettingMaintenance(
            settingId = setting.settingId,
            rebuild = false,
            shouldContinue = { true },
            onProgress = {}
        )

        val cluster = backfillRepository.listClusters(setting.settingId).single()
        assertEquals(
            listOf(3840 to 2160, 3840 to 2160),
            backfillRepository.listClusterMembers(cluster.clusterId)
                .map { member -> member.metadata.widthPixels to member.metadata.heightPixels }
        )
        listOf(first, second).forEach { file ->
            val stored = requireNotNull(similarityDao.getSettingFile(setting.settingId, fileId(file)))
            assertEquals("ready", stored.status)
            assertTrue(stored.dimensionsChecked)
        }
    }

    @Test
    fun incrementalMaintenanceDoesNotRetryAnUnchangedUndecodableVideo() {
        val file = videoFile("undecodable.mp4")
        database.fileCacheDao().upsert(entity(file))
        val extractor = CountingNullSignatureExtractor()
        val repository = SimilaritySettingsRepository(
            database = database,
            fileDao = database.fileCacheDao(),
            similarityDao = database.similaritySettingsDao(),
            frameSignatureExtractor = extractor,
            durationExtractor = FakeDurationExtractor(emptyMap()),
            mediaDimensionsExtractor = FakeMediaDimensionsExtractor(emptyMap())
        )
        val setting = repository.createExactThumbnailSetting(
            mediaScope = SimilarityMediaScope.Video,
            minSizeBytes = 1L,
            step = exactStep(width = 1, height = 1),
            enabled = true
        )

        val initial = repository.runSettingMaintenance(
            settingId = setting.settingId,
            rebuild = true,
            shouldContinue = { true },
            onProgress = {}
        )
        val incremental = repository.runSettingMaintenance(
            settingId = setting.settingId,
            rebuild = false,
            shouldContinue = { true },
            onProgress = {}
        )

        assertEquals(1, initial.skippedCount)
        assertEquals(1, incremental.skippedCount)
        assertEquals(1, extractor.extractionCalls.get())

        repository.runSettingMaintenance(
            settingId = setting.settingId,
            rebuild = true,
            shouldContinue = { true },
            onProgress = {}
        )

        assertEquals(2, extractor.extractionCalls.get())
    }

    @Test
    fun incrementalMaintenanceRetriesLegacySkippedVideoWithBoundedSampling() {
        val file = videoFile("legacy-skipped.mp4")
        database.fileCacheDao().upsert(entity(file))
        val extractor = CountingNullSignatureExtractor()
        val repository = SimilaritySettingsRepository(
            database = database,
            fileDao = database.fileCacheDao(),
            similarityDao = database.similaritySettingsDao(),
            frameSignatureExtractor = extractor,
            durationExtractor = FakeDurationExtractor(emptyMap()),
            mediaDimensionsExtractor = FakeMediaDimensionsExtractor(emptyMap())
        )
        val setting = repository.createExactThumbnailSetting(
            mediaScope = SimilarityMediaScope.Video,
            minSizeBytes = 1L,
            step = exactStep(width = 1, height = 1),
            enabled = true
        )
        database.similaritySettingsDao().upsertSettingFiles(
            listOf(
                SimilaritySettingFileEntity(
                    settingId = setting.settingId,
                    fileId = fileId(file),
                    sizeBytes = file.length(),
                    lastModifiedMillis = file.lastModified(),
                    status = "skipped",
                    widthPixels = null,
                    heightPixels = null,
                    dimensionsChecked = false,
                    durationChecked = false,
                    updatedAtMillis = 1L
                )
            )
        )

        val summary = repository.runSettingMaintenance(
            settingId = setting.settingId,
            rebuild = false,
            shouldContinue = { true },
            onProgress = {}
        )

        assertEquals(1, summary.skippedCount)
        assertEquals(1, extractor.extractionCalls.get())
        assertEquals(
            "skipped-v2",
            requireNotNull(
                database.similaritySettingsDao().getSettingFile(setting.settingId, fileId(file))
            ).status
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

        val progressUpdates = mutableListOf<SimilarityClearProgress>()
        val summary = repository.clearSettingResults(
            settingId = setting.settingId,
            mode = SimilarityClearMode.Standard,
            shouldContinue = { true },
            onProgress = progressUpdates::add
        )

        assertTrue(repository.listClusters(setting.settingId).isEmpty())
        assertEquals(setting.settingId, repository.listSettings().first { it.settingId == setting.settingId }.settingId)
        assertFalse(summary.cancelled)
        assertEquals(summary.total, summary.processed)
        assertEquals(0, summary.remaining)
        assertEquals(
            listOf(
                SimilarityClearPhase.Preparing,
                SimilarityClearPhase.Clusters,
                SimilarityClearPhase.FilesAndFeatures,
                SimilarityClearPhase.History
            ),
            progressUpdates.map { progress -> progress.phase }
        )
        assertTrue(progressUpdates.zipWithNext().all { (firstProgress, secondProgress) ->
            secondProgress.processed >= firstProgress.processed
        })
        assertEquals(0, similarityRowsForSetting(setting.settingId))
    }

    @Test
    fun incrementalClearCommitsBoundedClusterMemberBatchesAndResumesRemainingRows() {
        val repository = repository()
        val setting = repository.createExactThumbnailSetting(
            mediaScope = SimilarityMediaScope.Video,
            minSizeBytes = 1L,
            step = exactStep(width = 1, height = 1),
            enabled = true
        )
        val dao = database.similaritySettingsDao()
        val paths = (0 until 205).map { index -> "/virtual/clear-$index.mp4" }
        val orphanDurationPath = "/virtual/orphan-duration.mp4"
        (paths + orphanDurationPath).forEach { path ->
            database.fileCacheDao().upsert(
                CachedFileEntity(
                    normalizedPath = path,
                    path = path,
                    sizeBytes = 10L,
                    lastModifiedMillis = 1L,
                    hashHex = null
                )
            )
        }
        val fileIdsByPath = (paths + orphanDurationPath).associateWith { path ->
            requireNotNull(database.fileCacheDao().getByNormalizedPath(path)).fileId
        }
        dao.upsertSettingFiles(
            paths.map { path ->
                SimilaritySettingFileEntity(
                    settingId = setting.settingId,
                    fileId = requireNotNull(fileIdsByPath[path]),
                    sizeBytes = 10L,
                    lastModifiedMillis = 1L,
                    status = "ready",
                    widthPixels = null,
                    heightPixels = null,
                    dimensionsChecked = false,
                    durationChecked = false,
                    updatedAtMillis = 1L
                )
            }
        )
        dao.upsertExactThumbnailFeatures(
            paths.map { path ->
                SimilarityExactThumbnailFeatureEntity(
                    settingId = setting.settingId,
                    fileId = requireNotNull(fileIdsByPath[path]),
                    thumbnailHash = StoredHash.fromExternalString("same")
                )
            }
        )
        dao.upsertDurationFeatures(
            listOf(
                SimilarityDurationFeatureEntity(
                    settingId = setting.settingId,
                    fileId = requireNotNull(fileIdsByPath[orphanDurationPath]),
                    durationMillis = 1_000L
                )
            )
        )
        val clusterId = dao.insertCluster(
            SimilarityClusterEntity(
                settingId = setting.settingId,
                clusterKey = "large-cluster",
                fileCount = paths.size,
                totalBytes = paths.size * 10L,
                updatedAtMillis = 1L
            )
        )
        dao.upsertClusterMembers(
            paths.mapIndexed { index, path ->
                SimilarityClusterMemberEntity(
                    clusterId = clusterId,
                    fileId = requireNotNull(fileIdsByPath[path]),
                    position = index
                )
            }
        )
        dao.insertMaintenanceRun(
            SimilarityMaintenanceRunEntity(
                settingId = setting.settingId,
                startedAtMillis = 1L,
                finishedAtMillis = 2L,
                candidateCount = paths.size,
                processedCount = paths.size,
                skippedCount = 0,
                clusterCount = 1,
                duplicateFileCount = paths.size,
                cancelled = false
            )
        )
        var keepRunning = true
        val firstProgress = mutableListOf<SimilarityClearProgress>()

        val cancelled = repository.clearSettingResults(
            settingId = setting.settingId,
            mode = SimilarityClearMode.Incremental,
            shouldContinue = { keepRunning },
            onProgress = { progress ->
                firstProgress += progress
                if (progress.phase == SimilarityClearPhase.ClusterMembers && progress.processed > 0) {
                    keepRunning = false
                }
            }
        )

        assertTrue(cancelled.cancelled)
        assertEquals(100, cancelled.processed)
        assertEquals(100, firstProgress.last().processed)
        assertEquals(105, dao.countClusterMembersForSetting(setting.settingId))
        assertEquals(
            paths.drop(100).map { path -> requireNotNull(fileIdsByPath[path]) },
            dao.listClusterMemberIdsForClear(clusterId, limit = paths.size)
        )
        val remainingCluster = requireNotNull(repository.getCluster(setting.settingId, clusterId))
        assertEquals(105, remainingCluster.fileCount)
        assertEquals(1_050L, remainingCluster.totalBytes)
        assertTrue(cancelled.remaining > 0)

        val resumedProgress = mutableListOf<SimilarityClearProgress>()
        val completed = repository.clearSettingResults(
            settingId = setting.settingId,
            mode = SimilarityClearMode.Incremental,
            shouldContinue = { true },
            onProgress = resumedProgress::add
        )

        assertFalse(completed.cancelled)
        assertEquals(completed.total, completed.processed)
        assertEquals(0, completed.remaining)
        assertEquals(0, similarityRowsForSetting(setting.settingId))
        assertTrue(repository.listSettings().any { candidate -> candidate.settingId == setting.settingId })
        assertEquals(
            3,
            resumedProgress.count { progress -> progress.phase == SimilarityClearPhase.FilesAndFeatures }
        )
        assertTrue(resumedProgress.any { progress -> progress.phase == SimilarityClearPhase.OrphanFeatures })
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
        durations: Map<String, Long> = emptyMap(),
        dimensions: Map<String, MediaDimensions> = emptyMap()
    ): SimilaritySettingsRepository {
        return SimilaritySettingsRepository(
            database = database,
            fileDao = database.fileCacheDao(),
            similarityDao = database.similaritySettingsDao(),
            frameSignatureExtractor = FakeSignatureExtractor(signatures),
            durationExtractor = FakeDurationExtractor(durations),
            mediaDimensionsExtractor = FakeMediaDimensionsExtractor(dimensions)
        )
    }

    private fun similarityRowsForSetting(settingId: Long): Int {
        val dao = database.similaritySettingsDao()
        return dao.countSettingFiles(settingId) +
            dao.countExactThumbnailFeatures(settingId) +
            dao.countDurationFeatures(settingId) +
            dao.countClustersForSetting(settingId) +
            dao.countClusterMembersForSetting(settingId) +
            dao.countMaintenanceRunsForSetting(settingId)
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

    private fun fileId(file: File): Long {
        return requireNotNull(database.fileCacheDao().getByNormalizedPath(file.normalizedPath())).fileId
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

    private fun waitForThreadState(thread: Thread, expected: Thread.State) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (thread.state != expected && System.nanoTime() < deadline) {
            Thread.yield()
        }
        assertEquals(expected, thread.state)
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

private class CountingNullSignatureExtractor : VideoFrameSignatureExtractor {
    val extractionCalls = AtomicInteger(0)

    override fun signature(
        file: File,
        mediaScope: SimilarityMediaScope,
        step: ExactThumbnailHashStep,
        shouldContinue: () -> Boolean
    ): String? {
        extractionCalls.incrementAndGet()
        return null
    }
}

private class FakeDurationExtractor(
    private val durationsByPath: Map<String, Long>
) : VideoDurationExtractor {
    override fun durationMillis(file: File, shouldContinue: () -> Boolean): Long? {
        return durationsByPath[file.absolutePath]
    }
}

private class FakeMediaDimensionsExtractor(
    private val dimensionsByPath: Map<String, MediaDimensions>
) : MediaDimensionsExtractor {
    override fun dimensions(
        file: File,
        mediaScope: SimilarityMediaScope,
        shouldContinue: () -> Boolean
    ): MediaDimensions? {
        return dimensionsByPath[file.absolutePath]
    }
}

private class BlockingConcurrencySignatureExtractor : VideoFrameSignatureExtractor {
    val firstExtractionEntered = CountDownLatch(1)
    val releaseFirstExtraction = CountDownLatch(1)
    val activeExtractions = AtomicInteger(0)
    val maxConcurrentExtractions = AtomicInteger(0)

    override fun signature(
        file: File,
        mediaScope: SimilarityMediaScope,
        step: ExactThumbnailHashStep,
        shouldContinue: () -> Boolean
    ): String? {
        val active = activeExtractions.incrementAndGet()
        maxConcurrentExtractions.updateAndGet { previous -> maxOf(previous, active) }
        return try {
            if (firstExtractionEntered.count > 0L) {
                firstExtractionEntered.countDown()
                releaseFirstExtraction.await(5, TimeUnit.SECONDS)
            }
            "same"
        } finally {
            activeExtractions.decrementAndGet()
        }
    }
}

private class BlockingParallelSignatureExtractor(expectedConcurrent: Int) : VideoFrameSignatureExtractor {
    val expectedWorkersEntered = CountDownLatch(expectedConcurrent)
    val releaseWorkers = CountDownLatch(1)
    val maxConcurrentExtractions = AtomicInteger(0)
    val extractionCalls = AtomicInteger(0)
    private val activeExtractions = AtomicInteger(0)

    override fun signature(
        file: File,
        mediaScope: SimilarityMediaScope,
        step: ExactThumbnailHashStep,
        shouldContinue: () -> Boolean
    ): String? {
        extractionCalls.incrementAndGet()
        val active = activeExtractions.incrementAndGet()
        maxConcurrentExtractions.updateAndGet { previous -> maxOf(previous, active) }
        expectedWorkersEntered.countDown()
        return try {
            releaseWorkers.await(5, TimeUnit.SECONDS)
            if (shouldContinue()) "same" else null
        } finally {
            activeExtractions.decrementAndGet()
        }
    }
}

private class BlockingCancellationSignatureExtractor : VideoFrameSignatureExtractor {
    val workerEntered = CountDownLatch(1)
    val releaseWorkers = CountDownLatch(1)

    override fun signature(
        file: File,
        mediaScope: SimilarityMediaScope,
        step: ExactThumbnailHashStep,
        shouldContinue: () -> Boolean
    ): String? {
        workerEntered.countDown()
        releaseWorkers.await(5, TimeUnit.SECONDS)
        return if (shouldContinue()) "same" else null
    }
}

private const val THUMBNAIL_TEST_HASH =
    "f95cabe9951dcab34f51672a22fc4045c14ed62fc263e671a12f796654053744"
private const val THUMBNAIL_UNIQUE_TEST_HASH =
    "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f"
