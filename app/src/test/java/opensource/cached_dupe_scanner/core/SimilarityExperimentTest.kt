package opensource.cached_dupe_scanner.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SimilarityExperimentTest {
    @Test
    fun exactThumbnailExperimentUsesLargeVideoScopeAndRequestedColorMode() {
        val experiment = exactThumbnailHashExperiment(grayscale = true)

        assertEquals("video-thumbnail-exact-grayscale", experiment.id)
        assertEquals(DEFAULT_SIMILARITY_EXPERIMENT_MIN_SIZE_BYTES, experiment.defaultMinSizeBytes)
        assertTrue(experiment.accepts("/storage/video.MP4", 101L * 1024L * 1024L))
        assertFalse(experiment.accepts("/storage/video.MP4", 99L * 1024L * 1024L))
        assertFalse(experiment.accepts("/storage/video.MP4", 99L * 1024L * 1024L, minSizeBytes = 100L * 1024L * 1024L))
        assertFalse(experiment.accepts("/storage/image.jpg", 101L * 1024L * 1024L))

        val step = experiment.steps.single() as ExactThumbnailHashStep
        assertEquals(listOf(0, 1, 10), step.frameSeconds)
        assertEquals(1, step.resizeWidthPx)
        assertEquals(1, step.resizeHeightPx)
        assertEquals(16, step.quantizationLevels)
        assertTrue(step.grayscale)
    }

    @Test
    fun defaultExperimentsExposeIndependentAndStagedPipelines() {
        val experiments = defaultSimilarityExperimentSpecs()

        assertEquals(
            listOf(
                "video-thumbnail-exact-color",
                "video-thumbnail-exact-grayscale",
                "video-duration-tolerance",
                "video-duration-neighbor-list",
                "video-duration-phash",
                "video-thumbnail-phash-staged"
            ),
            experiments.map { it.id }
        )
        assertTrue(
            experiments.any { experiment ->
                experiment.steps.singleOrNull() is DurationToleranceStep
            }
        )
        assertTrue(
            experiments.any { experiment ->
                experiment.steps.singleOrNull() is DurationNeighborListStep
            }
        )
        assertTrue(
            experiments.any { experiment ->
                experiment.steps.any { it is DurationToleranceStep } &&
                    experiment.steps.any { it is PerceptualHashRefinementStep }
            }
        )
        assertTrue(
            experiments.any { experiment ->
                experiment.steps.any { it is ExactThumbnailHashStep } &&
                    experiment.steps.any { it is PerceptualHashRefinementStep }
            }
        )
    }

    @Test
    fun videoPathDetectionMatchesCommonVideoExtensionsOnly() {
        assertTrue(isVideoPath("/movies/sample.mkv"))
        assertTrue(isVideoPath("/movies/sample.webm"))
        assertFalse(isVideoPath("/movies/sample.png"))
        assertFalse(isVideoPath("/movies/sample.mp4.backup"))
    }

    @Test
    fun imagePathDetectionMatchesCommonImageExtensionsOnly() {
        assertTrue(isImagePath("/images/sample.jpg"))
        assertTrue(isImagePath("/images/sample.PNG"))
        assertFalse(isImagePath("/images/sample.mp4"))
        assertFalse(isImagePath("/images/sample.jpg.backup"))
    }

    @Test
    fun videoFrameSignatureMarksDisabledQuantizationAsRaw() {
        val signature = buildVideoFrameSignature(
            step = ExactThumbnailHashStep(
                frameSeconds = listOf(0),
                resizeWidthPx = 1,
                resizeHeightPx = 1,
                quantizationLevels = null,
                grayscale = false
            ),
            frameSignatures = listOf("ff00aa")
        )

        assertTrue(signature.contains(":raw:"))
    }

    @Test
    fun durationToleranceSignatureStoresToleranceAndObservedRange() {
        val step = DurationToleranceStep(toleranceSeconds = 2)

        assertEquals(2_000L, durationToleranceMillis(step))
        assertEquals(
            "duration-v1:2000:10000-11500",
            buildDurationToleranceSignature(
                minDurationMillis = 10_000L,
                maxDurationMillis = 11_500L,
                step = step
            )
        )
    }

    @Test
    fun durationNeighborSignatureStoresSortableObservedRange() {
        val step = DurationNeighborListStep(toleranceSeconds = 2)

        assertEquals(2_000L, durationNeighborToleranceMillis(step))
        assertEquals(
            "duration-neighbor-list-v1:2000:0000000010000-0000000011500",
            buildDurationNeighborListSignature(
                minDurationMillis = 10_000L,
                maxDurationMillis = 11_500L,
                step = step
            )
        )
    }
}
