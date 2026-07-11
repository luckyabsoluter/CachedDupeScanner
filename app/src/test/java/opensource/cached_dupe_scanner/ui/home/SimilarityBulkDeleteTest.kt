package opensource.cached_dupe_scanner.ui.home

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import opensource.cached_dupe_scanner.cache.CacheDatabase
import opensource.cached_dupe_scanner.cache.CachedFileEntity
import opensource.cached_dupe_scanner.core.ExactThumbnailHashStep
import opensource.cached_dupe_scanner.core.MediaDimensions
import opensource.cached_dupe_scanner.core.MediaDimensionsExtractor
import opensource.cached_dupe_scanner.core.PathNormalizer
import opensource.cached_dupe_scanner.core.SimilarityMediaScope
import opensource.cached_dupe_scanner.core.VideoDurationExtractor
import opensource.cached_dupe_scanner.core.VideoFrameSignatureExtractor
import opensource.cached_dupe_scanner.storage.AppSettingsStore
import opensource.cached_dupe_scanner.storage.ScanHistoryRepository
import opensource.cached_dupe_scanner.storage.SimilaritySettingsRepository
import opensource.cached_dupe_scanner.storage.StorageRootProvider
import opensource.cached_dupe_scanner.storage.StorageRootResolver
import opensource.cached_dupe_scanner.storage.TrashController
import opensource.cached_dupe_scanner.storage.TrashRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SimilarityBulkDeleteTest {
    private lateinit var context: Context
    private lateinit var database: CacheDatabase
    private lateinit var tempDir: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, CacheDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        tempDir = File(
            File(requireNotNull(System.getProperty("user.dir")), "build/test-temp"),
            "similarity-bulk-delete-${UUID.randomUUID()}"
        )
        tempDir.mkdirs()
    }

    @After
    fun tearDown() {
        database.close()
        tempDir.deleteRecursively()
    }

    @Test
    fun filteredExecutionMovesFilesAndDoesNotSkipClustersRemovedByEarlierPages() = runBlocking {
        val fixture = createFixture()
        val sameSizeFilter = ResultsFilterDefinition(
            clusters = listOf(
                createResultsFilterCluster().copy(
                    rules = listOf(createResultsFilterRule(ResultsFilterTarget.SameFileSize))
                )
            )
        )
        val operations = SimilarityBulkDeleteOperations(
            repository = fixture.repository,
            settingId = fixture.settingId,
            totalGroupCount = 3,
            sourcePageSize = 1
        )

        val preview = operations.buildKeepModifiedPreview(
            filterDefinition = sameSizeFilter,
            keepNewest = false,
            onProgress = {}
        )

        assertEquals(3, preview.totalGroupCount)
        assertEquals(2, preview.filterMatchedGroupCount)
        assertEquals(2, preview.candidateGroupCount)
        assertEquals(2, preview.candidateFileCount)
        assertFalse(operations.hasSnapshotChanged(preview))

        val progress = mutableListOf<ResultsBulkDeleteExecutionProgress>()
        val outcome = operations.executeKeepModified(
            preview = preview,
            filterDefinition = sameSizeFilter,
            keepNewest = false,
            onDeleteFile = { file ->
                fixture.trashController.moveToTrash(file.normalizedPath).success
            },
            onProgress = progress::add
        )

        assertEquals(2, outcome.successCount)
        assertTrue(outcome.failedPaths.isEmpty())
        assertEquals(fixture.eligibleClusterIds, outcome.touchedSourceIds)
        assertEquals(2, progress.last().processed)
        assertEquals(2, progress.last().total)
        assertTrue(fixture.alphaNewer.exists().not())
        assertTrue(fixture.gammaNewer.exists().not())
        assertTrue(fixture.alphaOlder.exists())
        assertTrue(fixture.gammaOlder.exists())
        assertTrue(fixture.ineligibleFiles.all(File::exists))
        assertEquals(1, fixture.repository.getClusterSummary(fixture.settingId).clusterCount)
        assertEquals(
            listOf("different-size"),
            fixture.repository.listClusters(fixture.settingId).map { cluster -> cluster.clusterKey }
        )
    }

    @Test
    fun canonicalClusterMutationInvalidatesBuiltPreview() = runBlocking {
        val fixture = createFixture()
        val operations = SimilarityBulkDeleteOperations(
            repository = fixture.repository,
            settingId = fixture.settingId,
            totalGroupCount = 3,
            sourcePageSize = 1
        )
        val preview = operations.buildKeepModifiedPreview(
            filterDefinition = ResultsFilterDefinition(),
            keepNewest = false,
            onProgress = {}
        )

        fixture.historyRepository.deleteByNormalizedPath(fixture.alphaNewer.normalizedPath())

        assertTrue(operations.hasSnapshotChanged(preview))
        assertEquals(2, fixture.repository.getClusterSummary(fixture.settingId).clusterCount)
    }

    @Test
    fun sameResolutionFilterRestrictsBulkDeletePreview() = runBlocking {
        val fixture = createFixture()
        val filter = ResultsFilterDefinition(
            clusters = listOf(
                createResultsFilterCluster().copy(
                    rules = listOf(createResultsFilterRule(ResultsFilterTarget.SameResolution))
                )
            )
        )
        val operations = SimilarityBulkDeleteOperations(
            repository = fixture.repository,
            settingId = fixture.settingId,
            totalGroupCount = 3,
            sourcePageSize = 1
        )

        val preview = operations.buildKeepModifiedPreview(
            filterDefinition = filter,
            keepNewest = false,
            onProgress = {}
        )

        assertEquals(3, preview.totalGroupCount)
        assertEquals(2, preview.filterMatchedGroupCount)
        assertEquals(2, preview.candidateGroupCount)
        assertEquals(2, preview.candidateFileCount)
    }

    private fun createFixture(): Fixture {
        val alphaOlder = videoFile("alpha-older.mp4", "same", 1_000L)
        val alphaNewer = videoFile("alpha-newer.mp4", "same", 2_000L)
        val differentSmall = videoFile("different-small.mp4", "x", 1_000L)
        val differentLarge = videoFile("different-large.mp4", "different-length", 2_000L)
        val gammaOlder = videoFile("gamma-older.mp4", "equal", 1_000L)
        val gammaNewer = videoFile("gamma-newer.mp4", "equal", 2_000L)
        val files = listOf(
            alphaOlder,
            alphaNewer,
            differentSmall,
            differentLarge,
            gammaOlder,
            gammaNewer
        )
        files.forEach { file -> database.fileCacheDao().upsert(file.entity()) }
        val signatures = mapOf(
            alphaOlder.absolutePath to "alpha",
            alphaNewer.absolutePath to "alpha",
            differentSmall.absolutePath to "different-size",
            differentLarge.absolutePath to "different-size",
            gammaOlder.absolutePath to "gamma",
            gammaNewer.absolutePath to "gamma"
        )
        val dimensions = mapOf(
            alphaOlder.absolutePath to MediaDimensions(1920, 1080),
            alphaNewer.absolutePath to MediaDimensions(1920, 1080),
            differentSmall.absolutePath to MediaDimensions(1920, 1080),
            differentLarge.absolutePath to MediaDimensions(1280, 720),
            gammaOlder.absolutePath to MediaDimensions(1280, 720),
            gammaNewer.absolutePath to MediaDimensions(1280, 720)
        )
        val repository = SimilaritySettingsRepository(
            database = database,
            fileDao = database.fileCacheDao(),
            similarityDao = database.similaritySettingsDao(),
            frameSignatureExtractor = FakeSignatureExtractor(signatures),
            durationExtractor = FakeDurationExtractor(),
            mediaDimensionsExtractor = FakeMediaDimensionsExtractor(dimensions)
        )
        val setting = repository.createExactThumbnailSetting(
            mediaScope = SimilarityMediaScope.Video,
            minSizeBytes = 1L,
            step = ExactThumbnailHashStep(
                frameSeconds = listOf(0),
                resizeWidthPx = 1,
                resizeHeightPx = 1,
                quantizationLevels = 16,
                grayscale = false
            ),
            enabled = true
        )
        repository.runSettingMaintenance(
            settingId = setting.settingId,
            rebuild = true,
            shouldContinue = { true },
            onProgress = {}
        )
        val clustersByKey = repository.listClusters(setting.settingId).associateBy { cluster -> cluster.clusterKey }
        val historyRepository = ScanHistoryRepository(
            dao = database.fileCacheDao(),
            settingsStore = AppSettingsStore(context),
            groupDao = database.duplicateGroupDao(),
            database = database,
            cacheMutationObserver = repository
        )
        val trashController = TrashController(
            context = context,
            database = database,
            historyRepo = historyRepository,
            trashRepo = TrashRepository(database.trashDao()),
            storageRootProvider = object : StorageRootProvider {
                override fun resolve(context: Context, absolutePath: String): StorageRootResolver.Root {
                    return StorageRootResolver.Root(tempDir.absolutePath)
                }
            }
        )
        return Fixture(
            repository = repository,
            historyRepository = historyRepository,
            trashController = trashController,
            settingId = setting.settingId,
            eligibleClusterIds = setOf(
                requireNotNull(clustersByKey["alpha"]).clusterId,
                requireNotNull(clustersByKey["gamma"]).clusterId
            ),
            alphaOlder = alphaOlder,
            alphaNewer = alphaNewer,
            gammaOlder = gammaOlder,
            gammaNewer = gammaNewer,
            ineligibleFiles = listOf(differentSmall, differentLarge)
        )
    }

    private fun videoFile(name: String, contents: String, modifiedMillis: Long): File {
        return File(tempDir, name).apply {
            writeText(contents)
            check(setLastModified(modifiedMillis))
        }
    }

    private fun File.entity(): CachedFileEntity {
        return CachedFileEntity(
            normalizedPath = normalizedPath(),
            path = absolutePath,
            sizeBytes = length(),
            lastModifiedMillis = lastModified(),
            hashHex = null
        )
    }

    private fun File.normalizedPath(): String {
        return PathNormalizer.normalize(absolutePath)
    }

    private data class Fixture(
        val repository: SimilaritySettingsRepository,
        val historyRepository: ScanHistoryRepository,
        val trashController: TrashController,
        val settingId: Long,
        val eligibleClusterIds: Set<Long>,
        val alphaOlder: File,
        val alphaNewer: File,
        val gammaOlder: File,
        val gammaNewer: File,
        val ineligibleFiles: List<File>
    )

    private class FakeSignatureExtractor(
        private val signatures: Map<String, String>
    ) : VideoFrameSignatureExtractor {
        override fun signature(
            file: File,
            mediaScope: SimilarityMediaScope,
            step: ExactThumbnailHashStep,
            shouldContinue: () -> Boolean
        ): String? {
            return signatures[file.absolutePath]
        }
    }

    private class FakeDurationExtractor : VideoDurationExtractor {
        override fun durationMillis(file: File, shouldContinue: () -> Boolean): Long? = null
    }

    private class FakeMediaDimensionsExtractor(
        private val dimensions: Map<String, MediaDimensions>
    ) : MediaDimensionsExtractor {
        override fun dimensions(
            file: File,
            mediaScope: SimilarityMediaScope,
            shouldContinue: () -> Boolean
        ): MediaDimensions? {
            return dimensions[file.absolutePath]
        }
    }
}
