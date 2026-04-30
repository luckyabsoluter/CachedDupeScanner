package opensource.cached_dupe_scanner.storage

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import opensource.cached_dupe_scanner.cache.CacheDatabase
import opensource.cached_dupe_scanner.cache.CachedFileEntity
import opensource.cached_dupe_scanner.core.ExactThumbnailHashStep
import opensource.cached_dupe_scanner.core.SimilarityExperimentSpec
import opensource.cached_dupe_scanner.core.SimilarityMediaScope
import opensource.cached_dupe_scanner.core.VideoFrameSignatureExtractor
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class SimilarityExperimentRepositoryTest {
    private lateinit var database: CacheDatabase
    private lateinit var tempDir: File

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, CacheDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        tempDir = createTempDir(prefix = "similarity-experiment")
    }

    @After
    fun teardown() {
        database.close()
        tempDir.deleteRecursively()
    }

    @Test
    fun exactThumbnailRunPersistsOnlyDuplicateSignatureClusters() {
        val first = videoFile("a.mp4")
        val second = videoFile("b.mp4")
        val unique = videoFile("c.mp4")
        val tooSmall = videoFile("small.mp4")
        val minSizeBytes = 10L
        database.fileCacheDao().upsert(entity(first, sizeBytes = minSizeBytes))
        database.fileCacheDao().upsert(entity(second, sizeBytes = minSizeBytes + 1L))
        database.fileCacheDao().upsert(entity(unique, sizeBytes = minSizeBytes + 2L))
        database.fileCacheDao().upsert(entity(tooSmall, sizeBytes = minSizeBytes - 1L))

        val repository = SimilarityExperimentRepository(
            database = database,
            fileDao = database.fileCacheDao(),
            experimentDao = database.similarityExperimentDao(),
            frameSignatureExtractor = FakeSignatureExtractor(
                mapOf(
                    first.absolutePath to "same",
                    second.absolutePath to "same",
                    unique.absolutePath to "unique"
                )
            )
        )
        val experiment = SimilarityExperimentSpec(
            id = "custom",
            name = "Custom",
            description = "Custom test",
            defaultMinSizeBytes = minSizeBytes,
            mediaScope = SimilarityMediaScope.Video,
            steps = listOf(exactStep())
        )

        val summary = repository.runExactThumbnailHashExperiment(
            request = SimilarityExperimentRunRequest(
                experiment = experiment,
                mediaScope = SimilarityMediaScope.Video,
                minSizeBytes = minSizeBytes,
                exactThumbnailStep = exactStep()
            ),
            shouldContinue = { true },
            onProgress = {}
        )

        assertEquals(3, summary.candidateCount)
        assertEquals(3, summary.processedCount)
        assertEquals(1, summary.clusterCount)
        assertEquals(2, summary.duplicateFileCount)
        assertEquals(1, repository.listRuns().size)
        val clusters = repository.listClusters("custom")
        assertEquals(1, clusters.size)
        assertEquals(2, clusters.single().fileCount)
        assertEquals(
            listOf(first, second).map { it.absolutePath.replace('\\', '/').lowercase() },
            clusters.single().memberNormalizedPathsText.lineSequence().toList()
        )
        assertEquals(
            listOf(first, second).map { it.absolutePath.replace('\\', '/').lowercase() },
            repository.listClusterMembers(clusters.single()).map { it.normalizedPath }
        )
    }

    @Test
    fun exactThumbnailRunCanUseImageCandidates() {
        val first = videoFile("a.jpg")
        val second = videoFile("b.png")
        val video = videoFile("c.mp4")
        val minSizeBytes = 10L
        database.fileCacheDao().upsert(entity(first, sizeBytes = minSizeBytes))
        database.fileCacheDao().upsert(entity(second, sizeBytes = minSizeBytes + 1L))
        database.fileCacheDao().upsert(entity(video, sizeBytes = minSizeBytes + 2L))

        val repository = SimilarityExperimentRepository(
            database = database,
            fileDao = database.fileCacheDao(),
            experimentDao = database.similarityExperimentDao(),
            frameSignatureExtractor = FakeSignatureExtractor(
                mapOf(
                    first.absolutePath to "same-image",
                    second.absolutePath to "same-image",
                    video.absolutePath to "video"
                )
            )
        )
        val experiment = SimilarityExperimentSpec(
            id = "custom-image",
            name = "Custom image",
            description = "Custom image test",
            defaultMinSizeBytes = minSizeBytes,
            mediaScope = SimilarityMediaScope.Image,
            steps = listOf(exactStep())
        )

        val summary = repository.runExactThumbnailHashExperiment(
            request = SimilarityExperimentRunRequest(
                experiment = experiment,
                mediaScope = SimilarityMediaScope.Image,
                minSizeBytes = minSizeBytes,
                exactThumbnailStep = exactStep()
            ),
            shouldContinue = { true },
            onProgress = {}
        )

        assertEquals(2, summary.candidateCount)
        assertEquals(1, summary.clusterCount)
        assertEquals(2, summary.duplicateFileCount)
    }

    private fun videoFile(name: String): File {
        val file = File(tempDir, name)
        file.writeText("video")
        return file
    }

    private fun entity(file: File, sizeBytes: Long): CachedFileEntity {
        return CachedFileEntity(
            normalizedPath = file.absolutePath.replace('\\', '/').lowercase(),
            path = file.absolutePath,
            sizeBytes = sizeBytes,
            lastModifiedMillis = 1L,
            hashHex = null
        )
    }

    private fun exactStep(): ExactThumbnailHashStep {
        return ExactThumbnailHashStep(
            frameSeconds = listOf(0, 1, 10),
            resizeWidthPx = 1,
            resizeHeightPx = 1,
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
