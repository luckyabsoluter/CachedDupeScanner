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
import opensource.cached_dupe_scanner.core.SimilarityMediaScope
import opensource.cached_dupe_scanner.core.VideoDurationExtractor
import opensource.cached_dupe_scanner.core.VideoFrameSignatureExtractor
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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
        val history = ScanHistoryRepository(
            dao = database.fileCacheDao(),
            settingsStore = AppSettingsStore(ApplicationProvider.getApplicationContext()),
            groupDao = database.duplicateGroupDao(),
            database = database,
            cacheMutationObserver = repository
        )

        history.deleteByNormalizedPath(second.normalizedPath())

        assertTrue(repository.listClusters(setting.settingId).isEmpty())
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
