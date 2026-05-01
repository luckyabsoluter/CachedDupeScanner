package opensource.cached_dupe_scanner.ui.home

import opensource.cached_dupe_scanner.core.SimilarityMediaScope
import opensource.cached_dupe_scanner.cache.SimilarityClusterEntity
import opensource.cached_dupe_scanner.cache.SimilarityExperimentRunEntity
import opensource.cached_dupe_scanner.core.ExactThumbnailHashStep
import opensource.cached_dupe_scanner.core.DurationToleranceStep
import opensource.cached_dupe_scanner.core.SimilarityExperimentSpec
import opensource.cached_dupe_scanner.core.buildThumbnailSignature
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SimilarityExperimentsScreenTest {
    @Test
    fun minSizeInputConvertsSelectedUnitToBytes() {
        assertEquals(0L, parsedMinSizeBytes("", SimilaritySizeUnit.MB))
        assertEquals(0L, parsedMinSizeBytes("0", SimilaritySizeUnit.MB))
        assertEquals(100L * 1024L * 1024L, parsedMinSizeBytes("100", SimilaritySizeUnit.MB))
        assertEquals(100L * 1024L, parsedMinSizeBytes("100", SimilaritySizeUnit.KB))
    }

    @Test
    fun sizeUnitCyclesFromDefaultMbToGb() {
        assertEquals(SimilaritySizeUnit.GB, SimilaritySizeUnit.MB.next())
    }

    @Test
    fun frameSecondsInputKeepsEditableCommaSeparatedSeconds() {
        assertEquals("0, 1, 10", sanitizeFrameSecondsInput("0, 1, 10s"))
        assertEquals(listOf(0, 1, 10), parsedFrameSeconds("0, 1, 10, 10"))
    }

    @Test
    fun exactThumbnailStepUsesEditableParameters() {
        val step = parsedExactThumbnailStep(
            frameSecondsInput = "0,1,10",
            resizeWidthInput = "2",
            resizeHeightInput = "3",
            quantizationEnabled = true,
            quantizationInput = "8",
            grayscale = true
        )

        assertEquals(listOf(0, 1, 10), step.frameSeconds)
        assertEquals(2, step.resizeWidthPx)
        assertEquals(3, step.resizeHeightPx)
        assertEquals(8, step.quantizationLevels)
        assertTrue(step.grayscale)
    }

    @Test
    fun exactThumbnailStepCanDisableQuantization() {
        val step = parsedExactThumbnailStep(
            frameSecondsInput = "0",
            resizeWidthInput = "1",
            resizeHeightInput = "1",
            quantizationEnabled = false,
            quantizationInput = "16",
            grayscale = false
        )

        assertEquals(null, step.quantizationLevels)
    }

    @Test
    fun generatedExperimentIdChangesWithRuntimeParameters() {
        val color = parsedExactThumbnailStep(
            frameSecondsInput = "0",
            resizeWidthInput = "1",
            resizeHeightInput = "1",
            quantizationEnabled = false,
            quantizationInput = "16",
            grayscale = false
        )
        val gray = color.copy(grayscale = true)

        assertFalse(
            exactThumbnailExperimentForRun(SimilarityMediaScope.Video, 0L, color).id ==
                exactThumbnailExperimentForRun(SimilarityMediaScope.Video, 0L, gray).id
        )
    }

    @Test
    fun generatedExperimentIdChangesWithMediaScope() {
        val step = parsedExactThumbnailStep(
            frameSecondsInput = "0",
            resizeWidthInput = "1",
            resizeHeightInput = "1",
            quantizationEnabled = false,
            quantizationInput = "16",
            grayscale = false
        )

        assertFalse(
            exactThumbnailExperimentForRun(SimilarityMediaScope.Video, 0L, step).id ==
            exactThumbnailExperimentForRun(SimilarityMediaScope.Image, 0L, step).id
        )
    }

    @Test
    fun selectedSimilarityRunUsesPreferredExperimentOrLatest() {
        val latest = run("latest")
        val older = run("older")

        assertEquals(older, selectedSimilarityRun(listOf(latest, older), "older"))
        assertEquals(latest, selectedSimilarityRun(listOf(latest, older), "missing"))
        assertEquals(null, selectedSimilarityRun(emptyList(), "missing"))
    }

    @Test
    fun selectedSimilarityExperimentTemplateUsesPreferredTemplateOrFirst() {
        val first = exactTemplate("first")
        val second = exactTemplate("second")

        assertEquals(second, selectedSimilarityExperimentTemplate(listOf(first, second), "second"))
        assertEquals(first, selectedSimilarityExperimentTemplate(listOf(first, second), "missing"))
        assertEquals(null, selectedSimilarityExperimentTemplate(emptyList(), "missing"))
    }

    @Test
    fun executableExactThumbnailStepRequiresSingleExactStep() {
        val exact = ExactThumbnailHashStep(
            frameSeconds = listOf(0),
            resizeWidthPx = 1,
            resizeHeightPx = 1,
            quantizationLevels = null,
            grayscale = false
        )

        assertEquals(exact, executableExactThumbnailStep(exactTemplate("exact", exact)))
        assertEquals(
            null,
            executableExactThumbnailStep(
                SimilarityExperimentSpec(
                    id = "duration",
                    name = "Duration",
                    description = "Duration",
                    defaultMinSizeBytes = 0L,
                    mediaScope = SimilarityMediaScope.Video,
                    steps = listOf(DurationToleranceStep(toleranceSeconds = 1))
                )
            )
        )
    }

    @Test
    fun defaultSizeInputKeepsMbDefaultWhenDivisible() {
        assertEquals(
            SimilaritySizeInput(input = "100", unit = SimilaritySizeUnit.MB),
            defaultSizeInputForUnit(100L * 1024L * 1024L, SimilaritySizeUnit.MB)
        )
        assertEquals(
            SimilaritySizeInput(input = "123", unit = SimilaritySizeUnit.B),
            defaultSizeInputForUnit(123L, SimilaritySizeUnit.MB)
        )
    }

    @Test
    fun exactThumbnailClusterExplanationParsesExactHashSignature() {
        val signature = buildThumbnailSignature(
            mediaScope = SimilarityMediaScope.Video,
            step = ExactThumbnailHashStep(
                frameSeconds = listOf(0, 1, 10),
                resizeWidthPx = 1,
                resizeHeightPx = 1,
                quantizationLevels = 16,
                grayscale = true
            ),
            frameSignatures = listOf("a", "b", "c")
        )

        val explanation = exactThumbnailClusterExplanation(signature)

        assertEquals("video", explanation?.mediaScope)
        assertEquals("gray", explanation?.colorMode)
        assertEquals("1x1", explanation?.resize)
        assertEquals("q16", explanation?.quantization)
        assertEquals(listOf("0", "1", "10"), explanation?.frameSeconds)
        assertEquals(listOf("a", "b", "c"), explanation?.sampleSignatures)
        assertEquals(
            "Exact hash: Video, 0s, 1s, 10s, 1x1, grayscale, 16 levels",
            explanation?.let(::exactHashClusterSummary)
        )
    }

    @Test
    fun exactThumbnailClusterExplanationIgnoresNonExactSignatures() {
        assertEquals(null, exactThumbnailClusterExplanation("duration-v1:1000"))
    }

    @Test
    fun similarityClusterDetailLinesExplainExactHashGroupingOnlyWhenAvailable() {
        val exactSignature = buildThumbnailSignature(
            mediaScope = SimilarityMediaScope.Image,
            step = ExactThumbnailHashStep(
                frameSeconds = listOf(0),
                resizeWidthPx = 2,
                resizeHeightPx = 2,
                quantizationLevels = null,
                grayscale = false
            ),
            frameSignatures = listOf("ff00aa")
        )
        val exactCluster = cluster(signature = exactSignature)
        val exactLines = similarityClusterDetailLines(
            cluster = exactCluster,
            exactHashExplanation = exactThumbnailClusterExplanation(exactSignature)
        )

        assertTrue(exactLines.any { it == "Group rule: exact thumbnail hash equality" })
        assertTrue(exactLines.any { it == "Why included: every member produced the same exact thumbnail signature." })
        assertTrue(exactLines.any { it == "Samples: image pixels" })
        assertTrue(exactLines.any { it == "Quantization: raw pixels" })

        val genericLines = similarityClusterDetailLines(
            cluster = cluster(signature = "duration-v1:1000"),
            exactHashExplanation = null
        )
        assertFalse(genericLines.any { it.startsWith("Group rule:") })
    }

    @Test
    fun screenStartsWithNewExperimentAndRunListBeforeDrillingIntoDetails() {
        val content = sourceText("SimilarityExperimentsScreen.kt")

        assertTrue(content.contains("private enum class SimilarityExperimentPane"))
        assertTrue(content.contains("SimilarityExperimentPane.List ->"))
        assertTrue(content.contains("Text(\"New experiment\")"))
        assertTrue(content.contains("onSelectRun = ::openRunPane"))
        assertTrue(content.contains("SimilarityExperimentPane.Create ->"))
        assertTrue(content.contains("SimilarityExperimentPane.RunDetail ->"))
        assertTrue(content.contains("StoredSimilarityResultsCard("))
    }

    private fun cluster(signature: String): SimilarityClusterEntity {
        return SimilarityClusterEntity(
            experimentId = "experiment",
            signature = signature,
            fileCount = 2,
            totalBytes = 10L,
            memberNormalizedPathsText = "/a\n/b",
            updatedAtMillis = 1L
        )
    }

    private fun run(id: String): SimilarityExperimentRunEntity {
        return SimilarityExperimentRunEntity(
            experimentId = id,
            experimentName = id,
            startedAtMillis = 1L,
            finishedAtMillis = 2L,
            candidateCount = 3,
            processedCount = 3,
            skippedCount = 0,
            clusterCount = 1,
            duplicateFileCount = 2
        )
    }

    private fun exactTemplate(
        id: String,
        step: ExactThumbnailHashStep = ExactThumbnailHashStep(
            frameSeconds = listOf(0),
            resizeWidthPx = 1,
            resizeHeightPx = 1,
            quantizationLevels = null,
            grayscale = false
        )
    ): SimilarityExperimentSpec {
        return SimilarityExperimentSpec(
            id = id,
            name = id,
            description = id,
            defaultMinSizeBytes = 100L * 1024L * 1024L,
            mediaScope = SimilarityMediaScope.Video,
            steps = listOf(step)
        )
    }

    private fun sourceText(fileName: String): String {
        val projectDir = File(System.getProperty("user.dir") ?: ".")
        val sourceFile = sequenceOf(
            File(projectDir, "app/src/main/java/opensource/cached_dupe_scanner/ui/home/$fileName"),
            File(projectDir.parentFile ?: projectDir, "app/src/main/java/opensource/cached_dupe_scanner/ui/home/$fileName")
        ).firstOrNull { it.exists() }

        assertTrue("$fileName should exist", sourceFile != null)
        return sourceFile!!.readText()
    }
}
