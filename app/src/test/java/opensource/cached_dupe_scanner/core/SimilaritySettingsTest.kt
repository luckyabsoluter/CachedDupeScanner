package opensource.cached_dupe_scanner.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SimilaritySettingsTest {
    @Test
    fun exactThumbnailSettingIdentityChangesWithResize() {
        val twoByTwo = exactThumbnailSettingDraft(
            mediaScope = SimilarityMediaScope.Video,
            minSizeBytes = DEFAULT_SIMILARITY_MIN_SIZE_BYTES,
            step = ExactThumbnailHashStep(
                frameSeconds = listOf(0, 1, 10),
                resizeWidthPx = 2,
                resizeHeightPx = 2,
                quantizationLevels = 16,
                grayscale = false
            )
        )
        val threeByThree = exactThumbnailSettingDraft(
            mediaScope = SimilarityMediaScope.Video,
            minSizeBytes = DEFAULT_SIMILARITY_MIN_SIZE_BYTES,
            step = ExactThumbnailHashStep(
                frameSeconds = listOf(0, 1, 10),
                resizeWidthPx = 3,
                resizeHeightPx = 3,
                quantizationLevels = 16,
                grayscale = false
            )
        )

        assertNotEquals(twoByTwo.paramsJson, threeByThree.paramsJson)
        assertNotEquals(twoByTwo.paramsHash, threeByThree.paramsHash)
    }

    @Test
    fun sameExactThumbnailParamsProduceStableIdentity() {
        val first = exactThumbnailSettingDraft(
            mediaScope = SimilarityMediaScope.Video,
            minSizeBytes = DEFAULT_SIMILARITY_MIN_SIZE_BYTES,
            step = exactStep(grayscale = true)
        )
        val second = exactThumbnailSettingDraft(
            mediaScope = SimilarityMediaScope.Video,
            minSizeBytes = DEFAULT_SIMILARITY_MIN_SIZE_BYTES,
            step = exactStep(grayscale = true)
        )

        assertEquals(first.paramsJson, second.paramsJson)
        assertEquals(first.paramsHash, second.paramsHash)
    }

    @Test
    fun settingDisplayNameFallsBackWhenBlank() {
        assertEquals(
            "Custom similarity",
            normalizedSimilaritySettingDisplayName("  Custom similarity  ", "Fallback")
        )
        assertEquals(
            "Fallback",
            normalizedSimilaritySettingDisplayName("   ", "Fallback")
        )
        assertEquals(
            "Similarity",
            normalizedSimilaritySettingDisplayName(null, "   ")
        )
    }

    @Test
    fun durationToleranceIdentityChangesWithTolerance() {
        val oneSecond = durationToleranceSettingDraft(
            minSizeBytes = DEFAULT_SIMILARITY_MIN_SIZE_BYTES,
            step = DurationToleranceStep(toleranceSeconds = 1)
        )
        val twoSeconds = durationToleranceSettingDraft(
            minSizeBytes = DEFAULT_SIMILARITY_MIN_SIZE_BYTES,
            step = DurationToleranceStep(toleranceSeconds = 2)
        )

        assertNotEquals(oneSecond.paramsHash, twoSeconds.paramsHash)
    }

    @Test
    fun defaultSettingsExposeExecutableMethodsOnly() {
        assertEquals(
            listOf(
                SIMILARITY_METHOD_EXACT_THUMBNAIL,
                SIMILARITY_METHOD_EXACT_THUMBNAIL,
                SIMILARITY_METHOD_DURATION_TOLERANCE,
                SIMILARITY_METHOD_DURATION_NEIGHBOR_LIST
            ),
            defaultSimilaritySettingDrafts().map { it.methodId }
        )
    }

    @Test
    fun exactThumbnailSettingIdentityNormalizesEquivalentInputs() {
        val normalized = exactThumbnailSettingDraft(
            mediaScope = SimilarityMediaScope.Video,
            minSizeBytes = 0L,
            step = ExactThumbnailHashStep(
                frameSeconds = listOf(0, 2),
                resizeWidthPx = 1,
                resizeHeightPx = 1,
                quantizationLevels = 2,
                grayscale = false
            )
        )
        val unnormalized = exactThumbnailSettingDraft(
            mediaScope = SimilarityMediaScope.Video,
            minSizeBytes = -10L,
            step = ExactThumbnailHashStep(
                frameSeconds = listOf(-3, 0, 2, 2),
                resizeWidthPx = 0,
                resizeHeightPx = -4,
                quantizationLevels = 1,
                grayscale = false
            )
        )

        assertEquals(normalized.minSizeBytes, unnormalized.minSizeBytes)
        assertEquals(normalized.paramsJson, unnormalized.paramsJson)
        assertEquals(normalized.paramsHash, unnormalized.paramsHash)
    }

    @Test
    fun durationSettingIdentityNormalizesNegativeValues() {
        val normalizedTolerance = durationToleranceSettingDraft(
            minSizeBytes = 0L,
            step = DurationToleranceStep(toleranceMillis = 0L)
        )
        val unnormalizedTolerance = durationToleranceSettingDraft(
            minSizeBytes = -1L,
            step = DurationToleranceStep(toleranceMillis = -1L)
        )
        val normalizedNeighbor = durationNeighborListSettingDraft(
            minSizeBytes = 0L,
            step = DurationNeighborListStep(toleranceMillis = 0L)
        )
        val unnormalizedNeighbor = durationNeighborListSettingDraft(
            minSizeBytes = -1L,
            step = DurationNeighborListStep(toleranceMillis = -1L)
        )

        assertEquals(normalizedTolerance.minSizeBytes, unnormalizedTolerance.minSizeBytes)
        assertEquals(normalizedTolerance.paramsJson, unnormalizedTolerance.paramsJson)
        assertEquals(normalizedTolerance.paramsHash, unnormalizedTolerance.paramsHash)
        assertEquals(normalizedNeighbor.minSizeBytes, unnormalizedNeighbor.minSizeBytes)
        assertEquals(normalizedNeighbor.paramsJson, unnormalizedNeighbor.paramsJson)
        assertEquals(normalizedNeighbor.paramsHash, unnormalizedNeighbor.paramsHash)
    }

    @Test
    fun paramsParsingNormalizesStoredValues() {
        val exact = exactThumbnailStepFromParams(
            "{" +
                "\"frameSeconds\":[-1,0,0]," +
                "\"resizeWidthPx\":0," +
                "\"resizeHeightPx\":-3," +
                "\"quantizationLevels\":1," +
                "\"grayscale\":true" +
                "}"
        )
        val tolerance = durationToleranceStepFromParams("{\"toleranceMillis\":-1}")
        val neighbor = durationNeighborListStepFromParams("{\"toleranceMillis\":-1}")

        assertEquals(listOf(0), exact.frameSeconds)
        assertEquals(1, exact.resizeWidthPx)
        assertEquals(1, exact.resizeHeightPx)
        assertEquals(2, exact.quantizationLevels)
        assertEquals(0L, tolerance.toleranceMillis)
        assertEquals(0L, neighbor.toleranceMillis)
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
    fun durationSignaturesStoreToleranceAndObservedRange() {
        val tolerance = DurationToleranceStep(toleranceSeconds = 2)
        val neighbor = DurationNeighborListStep(toleranceSeconds = 2)

        assertEquals(2_000L, durationToleranceMillis(tolerance))
        assertEquals(2_000L, durationNeighborToleranceMillis(neighbor))
        assertEquals(
            "duration-v1:2000:10000-11500",
            buildDurationToleranceSignature(10_000L, 11_500L, tolerance)
        )
        assertEquals(
            "duration-neighbor-list-v1:2000:0000000010000-0000000011500",
            buildDurationNeighborListSignature(10_000L, 11_500L, neighbor)
        )
    }

    private fun exactStep(grayscale: Boolean): ExactThumbnailHashStep {
        return ExactThumbnailHashStep(
            frameSeconds = listOf(0, 1, 10),
            resizeWidthPx = 1,
            resizeHeightPx = 1,
            quantizationLevels = 16,
            grayscale = grayscale
        )
    }
}
