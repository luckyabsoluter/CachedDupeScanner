package opensource.cached_dupe_scanner.storage

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.util.UUID
import opensource.cached_dupe_scanner.cache.CacheDatabase
import opensource.cached_dupe_scanner.cache.CachedFileEntity
import opensource.cached_dupe_scanner.core.DurationToleranceStep
import opensource.cached_dupe_scanner.core.ExactThumbnailHashStep
import opensource.cached_dupe_scanner.core.MediaDimensions
import opensource.cached_dupe_scanner.core.MediaDimensionsExtractor
import opensource.cached_dupe_scanner.core.PathNormalizer
import opensource.cached_dupe_scanner.core.SimilarityMediaScope
import opensource.cached_dupe_scanner.core.VideoDurationExtractor
import opensource.cached_dupe_scanner.core.VideoFrameSignatureExtractor
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TrashSimilarityRestoreTest {
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
            "trash-similarity-restore-${UUID.randomUUID()}"
        ).apply { mkdirs() }
    }

    @After
    fun tearDown() {
        database.close()
        tempDir.deleteRecursively()
    }

    @Test
    fun restoreRebuildsEnabledAndPausedSimilarityFromOnlyRestoredPath() {
        val older = videoFile("older.mp4", modifiedMillis = 1_000L)
        val newer = videoFile("newer.mp4", modifiedMillis = 2_000L)
        val unrelated = videoFile("unrelated.mp4", modifiedMillis = 3_000L)
        listOf(older, newer, unrelated).forEach { file ->
            database.fileCacheDao().upsert(file.toEntity())
        }
        val signatureRequests = mutableListOf<String>()
        val durationRequests = mutableListOf<String>()
        val dimensionRequests = mutableListOf<String>()
        val repository = SimilaritySettingsRepository(
            database = database,
            fileDao = database.fileCacheDao(),
            similarityDao = database.similaritySettingsDao(),
            frameSignatureExtractor = RecordingSignatureExtractor(
                signatures = mapOf(
                    older.absolutePath to "same",
                    newer.absolutePath to "same",
                    unrelated.absolutePath to "unrelated"
                ),
                requests = signatureRequests
            ),
            durationExtractor = RecordingDurationExtractor(
                durations = mapOf(
                    older.absolutePath to 10_000L,
                    newer.absolutePath to 10_100L,
                    unrelated.absolutePath to 30_000L
                ),
                requests = durationRequests
            ),
            mediaDimensionsExtractor = RecordingDimensionsExtractor(dimensionRequests)
        )
        val exactSetting = repository.createExactThumbnailSetting(
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
        val durationSetting = repository.createDurationToleranceSetting(
            minSizeBytes = 1L,
            step = DurationToleranceStep(toleranceMillis = 250L),
            enabled = true
        )
        listOf(exactSetting.settingId, durationSetting.settingId).forEach { settingId ->
            repository.runSettingMaintenance(
                settingId = settingId,
                rebuild = true,
                shouldContinue = { true },
                onProgress = {}
            )
        }
        repository.setEnabled(durationSetting.settingId, enabled = false)
        signatureRequests.clear()
        durationRequests.clear()
        dimensionRequests.clear()

        val historyRepository = ScanHistoryRepository(
            dao = database.fileCacheDao(),
            settingsStore = AppSettingsStore(context),
            groupDao = database.duplicateGroupDao(),
            database = database,
            cacheMutationObserver = repository
        )
        val trashRepository = TrashRepository(database.trashDao())
        val trashController = TrashController(
            context = context,
            database = database,
            historyRepo = historyRepository,
            trashRepo = trashRepository,
            storageRootProvider = object : StorageRootProvider {
                override fun resolve(context: Context, absolutePath: String): StorageRootResolver.Root {
                    return StorageRootResolver.Root(tempDir.absolutePath)
                }
            }
        )

        val move = trashController.moveToTrash(newer.normalizedPath())
        val entry = requireNotNull(move.entry)
        assertTrue(move.success)
        assertFalse(newer.exists())
        assertNull(database.fileCacheDao().getByNormalizedPath(newer.normalizedPath()))
        assertTrue(repository.listClusters(exactSetting.settingId).isEmpty())
        assertTrue(repository.listClusters(durationSetting.settingId).isEmpty())

        val restore = trashController.restoreFromTrash(entry)
        assertEquals(TrashController.RestoreResult.Success, restore)
        assertTrue(newer.exists())
        assertNotNull(database.fileCacheDao().getByNormalizedPath(newer.normalizedPath()))
        assertNull(trashRepository.getById(entry.id))
        assertEquals(
            2,
            repository.refreshRestoredFileResults(
                normalizedPath = newer.normalizedPath(),
                shouldContinue = { true }
            )
        )

        assertEquals(listOf(newer.absolutePath), signatureRequests)
        assertEquals(listOf(newer.absolutePath), durationRequests)
        assertEquals(listOf(newer.absolutePath, newer.absolutePath), dimensionRequests)
        val expectedPaths = setOf(older.normalizedPath(), newer.normalizedPath())
        val exactMembers = repository.listClusters(exactSetting.settingId)
            .single()
            .let { cluster -> repository.listClusterMembers(cluster.clusterId) }
        val durationMembers = repository.listClusters(durationSetting.settingId)
            .single()
            .let { cluster -> repository.listClusterMembers(cluster.clusterId) }
        assertEquals(expectedPaths, exactMembers.map { member -> member.metadata.normalizedPath }.toSet())
        assertEquals(expectedPaths, durationMembers.map { member -> member.metadata.normalizedPath }.toSet())
    }

    private fun videoFile(name: String, modifiedMillis: Long): File {
        return File(tempDir, name).apply {
            writeBytes(byteArrayOf(1))
            check(setLastModified(modifiedMillis))
        }
    }

    private fun File.toEntity(): CachedFileEntity {
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

    private class RecordingSignatureExtractor(
        private val signatures: Map<String, String>,
        private val requests: MutableList<String>
    ) : VideoFrameSignatureExtractor {
        override fun signature(
            file: File,
            mediaScope: SimilarityMediaScope,
            step: ExactThumbnailHashStep,
            shouldContinue: () -> Boolean
        ): String? {
            requests += file.absolutePath
            return signatures[file.absolutePath]
        }
    }

    private class RecordingDurationExtractor(
        private val durations: Map<String, Long>,
        private val requests: MutableList<String>
    ) : VideoDurationExtractor {
        override fun durationMillis(file: File, shouldContinue: () -> Boolean): Long? {
            requests += file.absolutePath
            return durations[file.absolutePath]
        }
    }

    private class RecordingDimensionsExtractor(
        private val requests: MutableList<String>
    ) : MediaDimensionsExtractor {
        override fun dimensions(
            file: File,
            mediaScope: SimilarityMediaScope,
            shouldContinue: () -> Boolean
        ): MediaDimensions {
            requests += file.absolutePath
            return MediaDimensions(widthPixels = 1920, heightPixels = 1080)
        }
    }
}
