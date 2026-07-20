package opensource.cached_dupe_scanner.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SimilaritySignatureExplanationTest {
    @Test
    fun exactThumbnailBuilderHashesCanonicalPixelPayload() {
        val hash = buildThumbnailSignature(
            mediaScope = SimilarityMediaScope.Video,
            step = ExactThumbnailHashStep(
                frameSeconds = listOf(0, 1),
                resizeWidthPx = 2,
                resizeHeightPx = 1,
                quantizationLevels = 16,
                grayscale = false
            ),
            frameSignatures = listOf("0f0f0f,000000", "ffffff,101010")
        )

        assertEquals(
            "f95cabe9951dcab34f51672a22fc4045c14ed62fc263e671a12f796654053744",
            hash
        )
    }

    @Test
    fun exactThumbnailExplanationParsesHashedClusterKeyWithoutPixelPayload() {
        val step = ExactThumbnailHashStep(
            frameSeconds = listOf(0, 1),
            resizeWidthPx = 2,
            resizeHeightPx = 1,
            quantizationLevels = 16,
            grayscale = false
        )
        val hash = "f95cabe9951dcab34f51672a22fc4045c14ed62fc263e671a12f796654053744"
        val clusterKey = buildThumbnailHashClusterKey(
            mediaScope = SimilarityMediaScope.Video,
            step = step,
            thumbnailHashHex = hash
        )

        val explanation = requireNotNull(exactThumbnailClusterExplanation(clusterKey))

        assertEquals("video", explanation.mediaScope)
        assertEquals("color", explanation.colorMode)
        assertEquals("2x1", explanation.resize)
        assertEquals("q16", explanation.quantization)
        assertEquals(listOf("0", "1"), explanation.frameSeconds)
        assertEquals(emptyList<String>(), explanation.sampleSignatures)
        assertEquals(hash, explanation.thumbnailHashHex)
    }

    @Test
    fun durationExplanationsParseBuiltSignatures() {
        val tolerance = DurationToleranceStep(toleranceMillis = 500L)
        val neighbor = DurationNeighborListStep(toleranceMillis = 750L)

        val durationExplanation = requireNotNull(
            durationClusterExplanation(
                buildDurationToleranceSignature(
                    minDurationMillis = 1_000L,
                    maxDurationMillis = 1_500L,
                    step = tolerance
                )
            )
        )
        val neighborExplanation = requireNotNull(
            durationNeighborClusterExplanation(
                buildDurationNeighborListSignature(
                    minDurationMillis = 2_000L,
                    maxDurationMillis = 3_250L,
                    step = neighbor
                )
            )
        )

        assertEquals(500L, durationExplanation.toleranceMillis)
        assertEquals(1_000L, durationExplanation.minDurationMillis)
        assertEquals(1_500L, durationExplanation.maxDurationMillis)
        assertEquals(750L, neighborExplanation.toleranceMillis)
        assertEquals(2_000L, neighborExplanation.minDurationMillis)
        assertEquals(3_250L, neighborExplanation.maxDurationMillis)
    }

    @Test
    fun durationNeighborExplanationAcceptsLegacyPrefix() {
        val explanation = requireNotNull(
            durationNeighborClusterExplanation("duration-neighbor-v1:500:1000-1500")
        )

        assertEquals(500L, explanation.toleranceMillis)
        assertEquals(1_000L, explanation.minDurationMillis)
        assertEquals(1_500L, explanation.maxDurationMillis)
    }

    @Test
    fun explanationsRejectUnrelatedSignatures() {
        assertNull(exactThumbnailClusterExplanation("duration-v1:500:1000-1500"))
        assertNull(durationClusterExplanation("duration-neighbor-list-v1:500:1000-1500"))
        assertNull(durationNeighborClusterExplanation("duration-v1:500:1000-1500"))
    }
}
