package opensource.cached_dupe_scanner.ui.home.similarity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SimilaritySettingInputsTest {
    @Test
    fun exactThumbnailInputPreservesCustomSettings() {
        val step = parsedExactThumbnailStep(
            frameSecondsInput = "10, 0, 10, 3",
            resizeWidthInput = "2",
            resizeHeightInput = "3",
            quantizationEnabled = true,
            quantizationInput = "32",
            grayscale = true
        )

        assertEquals(listOf(10, 0, 3), step.frameSeconds)
        assertEquals(2, step.resizeWidthPx)
        assertEquals(3, step.resizeHeightPx)
        assertEquals(32, step.quantizationLevels)
        assertEquals(true, step.grayscale)
    }

    @Test
    fun exactThumbnailInputCanDisableQuantization() {
        val step = parsedExactThumbnailStep(
            frameSecondsInput = "0",
            resizeWidthInput = "1",
            resizeHeightInput = "1",
            quantizationEnabled = false,
            quantizationInput = "16",
            grayscale = false
        )

        assertNull(step.quantizationLevels)
    }

    @Test
    fun durationInputPreservesUnitChoice() {
        assertEquals(250L, parsedDurationToleranceStep("250", SimilarityTimeUnit.MS).toleranceMillis)
        assertEquals(2_000L, parsedDurationNeighborListStep("2", SimilarityTimeUnit.S).toleranceMillis)
        assertEquals(60_000L, parsedDurationToleranceStep("1", SimilarityTimeUnit.MIN).toleranceMillis)
    }

    @Test
    fun sizeInputPreservesUnitChoice() {
        assertEquals(512L, parsedMinSizeBytes("512", SimilaritySizeUnit.B))
        assertEquals(3L * 1024L * 1024L, parsedMinSizeBytes("3", SimilaritySizeUnit.MB))
    }
}
