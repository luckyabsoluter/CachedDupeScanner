package opensource.cached_dupe_scanner.ui.home

import opensource.cached_dupe_scanner.core.SimilarityMediaScope
import opensource.cached_dupe_scanner.cache.SimilarityClusterEntity
import opensource.cached_dupe_scanner.cache.SimilarityExperimentRunEntity
import opensource.cached_dupe_scanner.core.DurationNeighborListStep
import opensource.cached_dupe_scanner.core.ExactThumbnailHashStep
import opensource.cached_dupe_scanner.core.DurationToleranceStep
import opensource.cached_dupe_scanner.core.FileMetadata
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
    fun durationToleranceStepUsesEditableSeconds() {
        assertEquals(2, parsedDurationToleranceStep("2").toleranceSeconds)
        assertEquals(0, parsedDurationToleranceStep("0").toleranceSeconds)
        assertEquals(1, parsedDurationToleranceStep("").toleranceSeconds)
        assertEquals(2, parsedDurationNeighborListStep("2").toleranceSeconds)
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
        assertFalse(
            durationToleranceExperimentForRun(0L, DurationToleranceStep(toleranceSeconds = 1)).id ==
                durationToleranceExperimentForRun(0L, DurationToleranceStep(toleranceSeconds = 2)).id
        )
        assertFalse(
            durationNeighborListExperimentForRun(0L, DurationNeighborListStep(toleranceSeconds = 1)).id ==
                durationNeighborListExperimentForRun(0L, DurationNeighborListStep(toleranceSeconds = 2)).id
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
    fun executableDurationToleranceStepRequiresSingleDurationStep() {
        val duration = DurationToleranceStep(toleranceSeconds = 2)
        val durationTemplate = SimilarityExperimentSpec(
            id = "duration",
            name = "Duration",
            description = "Duration",
            defaultMinSizeBytes = 0L,
            mediaScope = SimilarityMediaScope.Video,
            steps = listOf(duration)
        )

        assertEquals(duration, executableDurationToleranceStep(durationTemplate))
        assertEquals("Executable duration experiment", executableTemplateKind(durationTemplate))
        assertEquals(null, executableDurationToleranceStep(exactTemplate("exact")))
    }

    @Test
    fun executableDurationNeighborListStepRequiresSingleNeighborStep() {
        val duration = DurationNeighborListStep(toleranceSeconds = 2)
        val durationTemplate = SimilarityExperimentSpec(
            id = "duration-neighbor",
            name = "Duration neighbor",
            description = "Duration neighbor",
            defaultMinSizeBytes = 0L,
            mediaScope = SimilarityMediaScope.Video,
            steps = listOf(duration)
        )

        assertEquals(duration, executableDurationNeighborListStep(durationTemplate))
        assertEquals("Executable duration neighbor-list experiment", executableTemplateKind(durationTemplate))
        assertEquals(null, executableDurationNeighborListStep(exactTemplate("exact")))
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
    fun durationNeighborClusterExplanationParsesSortedDurationRange() {
        val explanation = durationNeighborClusterExplanation(
            "duration-neighbor-list-v1:1000:0000000010000-0000000010750"
        )
        val legacyExplanation = durationNeighborClusterExplanation(
            "duration-neighbor-v1:1000:0000000010000-0000000010750"
        )

        assertEquals(1_000L, explanation?.toleranceMillis)
        assertEquals(10_000L, explanation?.minDurationMillis)
        assertEquals(10_750L, explanation?.maxDurationMillis)
        assertEquals(explanation, legacyExplanation)
        assertEquals(
            "Duration neighbor list: 10s - 10.750s, tolerance 1s",
            explanation?.let(::durationNeighborClusterSummary)
        )
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

        val durationNeighborLines = similarityClusterDetailLines(
            cluster = cluster(signature = "duration-neighbor-list-v1:1000:0000000010000-0000000010750"),
            exactHashExplanation = null,
            durationNeighborExplanation = durationNeighborClusterExplanation(
                "duration-neighbor-list-v1:1000:0000000010000-0000000010750"
            )
        )
        assertTrue(durationNeighborLines.any { it == "List rule: duration-sorted neighbor filter" })
        assertTrue(durationNeighborLines.any { it == "Order: sorted by extracted video duration" })
    }

    @Test
    fun exactHashReductionSamplesConvertStoredSignaturesToPreviewColors() {
        val explanation = ExactThumbnailClusterExplanation(
            mediaScope = "video",
            colorMode = "color",
            resize = "1x1",
            quantization = "q16",
            frameSeconds = listOf("0", "1", "10"),
            sampleSignatures = listOf("000", "f80", "fff")
        )

        val samples = exactHashReductionSamples(explanation)

        assertEquals("Reduced frame 0s", samples[0].label)
        assertEquals(1, samples[0].width)
        assertEquals(1, samples[0].height)
        assertEquals(ExactHashReductionColor(red = 0, green = 0, blue = 0), samples[0].colors.single())
        assertEquals(ExactHashReductionColor(red = 255, green = 136, blue = 0), samples[1].colors.single())
        assertEquals(ExactHashReductionColor(red = 255, green = 255, blue = 255), samples[2].colors.single())
    }

    @Test
    fun exactHashReductionSamplesConvertRawGrayscaleImageSignature() {
        val explanation = ExactThumbnailClusterExplanation(
            mediaScope = "image",
            colorMode = "gray",
            resize = "1x1",
            quantization = "raw",
            frameSeconds = emptyList(),
            sampleSignatures = listOf("80")
        )

        val samples = exactHashReductionSamples(explanation)

        assertEquals("Reduced image", samples.single().label)
        assertEquals(ExactHashReductionColor(red = 128, green = 128, blue = 128), samples.single().colors.single())
    }

    @Test
    fun exactHashReductionSamplesPreserveTwoByTwoPreviewGrid() {
        val explanation = ExactThumbnailClusterExplanation(
            mediaScope = "image",
            colorMode = "color",
            resize = "2x2",
            quantization = "q16",
            frameSeconds = emptyList(),
            sampleSignatures = listOf("000,f80,08f,fff")
        )

        val sample = exactHashReductionSamples(explanation).single()

        assertEquals(2, sample.width)
        assertEquals(2, sample.height)
        assertEquals(
            listOf(
                ExactHashReductionColor(red = 0, green = 0, blue = 0),
                ExactHashReductionColor(red = 255, green = 136, blue = 0),
                ExactHashReductionColor(red = 0, green = 136, blue = 255),
                ExactHashReductionColor(red = 255, green = 255, blue = 255)
            ),
            sample.colors
        )
    }

    @Test
    fun similarityClusterPreviewLineTextsCompactMembersWithoutGrowingCards() {
        val lines = similarityClusterPreviewLineTexts(
            members = listOf(
                file("/c.mp4"),
                file("/a.mp4"),
                file("/e.mp4"),
                file("/b.mp4"),
                file("/d.mp4")
            ),
            showFullPaths = true,
            itemsPerLine = 2,
            maxItems = 4
        )

        assertEquals(
            listOf(
                "/a.mp4  •  /b.mp4",
                "/c.mp4  •  /d.mp4"
            ),
            lines
        )
        assertEquals(4, similarityClusterPreviewDisplayCount(List(5) { index -> file("/$index.mp4") }))

        val orderedLines = similarityClusterPreviewLineTexts(
            members = listOf(file("/b.mp4"), file("/a.mp4")),
            showFullPaths = true,
            preserveOrder = true
        )
        assertEquals(listOf("/b.mp4  •  /a.mp4"), orderedLines)

        val durationLines = similarityClusterPreviewLineTexts(
            members = listOf(file("/b.mp4"), file("/a.mp4")),
            showFullPaths = true,
            durationMillisByNormalizedPath = mapOf(
                "/a.mp4" to 10_000L,
                "/b.mp4" to 10_750L
            ),
            preserveOrder = true
        )
        assertEquals(listOf("10.750s · /b.mp4  •  10s · /a.mp4"), durationLines)
    }

    @Test
    fun screenStartsWithNewExperimentAndRunListBeforeDrillingIntoDetails() {
        val content = sourceText("SimilarityExperimentsScreen.kt")

        assertTrue(content.contains("private enum class SimilarityExperimentPane"))
        assertTrue(content.contains("SimilarityExperimentPane.List ->"))
        assertTrue(content.contains("Text(\"New experiment\")"))
        assertTrue(content.contains("onSelectRun = ::openRunPane"))
        assertTrue(content.contains("SimilarityExperimentPane.Create ->"))
        assertTrue(content.contains("SimilarityExperimentPane.TemplateDetail ->"))
        assertTrue(content.contains("onSelectExperiment = ::openTemplateDetailPane"))
        assertTrue(content.contains("SimilarityExperimentPane.RunDetail ->"))
        assertTrue(content.contains("DurationToleranceRunCard("))
        assertTrue(content.contains("repository.runDurationToleranceExperiment("))
        assertTrue(content.contains("repository.runDurationNeighborListExperiment("))
        assertTrue(content.contains("sortMembersByPath = durationNeighborExplanation == null"))
        assertTrue(content.contains("StoredSimilarityResultsHeader("))
        assertTrue(content.contains("items("))
        assertTrue(content.contains("SimilarityClusterLoadingIndicator("))
        assertTrue(content.contains("Reduction preview"))
        assertTrue(content.contains(".background("))
        assertTrue(content.contains("ExactHashReductionSampleGrid("))
        assertTrue(content.contains("SimilarityClusterMemberPreviewLines("))

        val createPane = sourceSection(
            content = content,
            start = "SimilarityExperimentPane.Create -> {",
            end = "SimilarityExperimentPane.TemplateDetail -> {"
        )
        val templateDetailPane = sourceSection(
            content = content,
            start = "SimilarityExperimentPane.TemplateDetail -> {",
            end = "SimilarityExperimentPane.RunDetail -> {"
        )
        assertTrue(createPane.contains("ExperimentTemplatesHeader("))
        assertTrue(createPane.contains("ExperimentTemplateCard("))
        assertFalse(createPane.contains("ExactThumbnailRunCard("))
        assertTrue(templateDetailPane.contains("ExactThumbnailRunCard("))
        assertTrue(templateDetailPane.contains("DurationToleranceRunCard("))
    }

    @Test
    fun similarityExperimentMainPaneUsesLazyListScrollbarAndLoadIndicator() {
        val content = sourceText("SimilarityExperimentsScreen.kt")
        val mainScreen = sourceSection(
            content = content,
            start = "fun SimilarityExperimentsScreen(",
            end = "@Composable\nprivate fun ExperimentRunsHeader"
        )

        assertTrue(mainScreen.contains("rememberLazyListState()"))
        assertTrue(mainScreen.contains("SimilarityExperimentLazyPane("))
        assertTrue(mainScreen.contains("LazyColumn("))
        assertTrue(mainScreen.contains("VerticalLazyScrollbar("))
        assertTrue(mainScreen.contains("TopRightLoadIndicator("))
        assertTrue(mainScreen.contains("similarityClusterLoadIndicatorText("))
        assertTrue(mainScreen.contains("items = clusters"))
        assertFalse(mainScreen.contains(".verticalScroll("))
        assertFalse(mainScreen.contains("VerticalScrollbar("))
    }

    @Test
    fun similarityClusterLoadIndicatorTextTracksVisibleLazyCluster() {
        assertEquals(
            "Loading 0/12 clusters",
            similarityClusterLoadIndicatorText(
                isRunDetailPane = true,
                totalClusterCount = 12,
                loadedClusterCount = 0,
                topVisibleItemIndex = 0,
                clustersLoading = true
            )
        )
        assertEquals(
            "3/8/12 (37%/66%)",
            similarityClusterLoadIndicatorText(
                isRunDetailPane = true,
                totalClusterCount = 12,
                loadedClusterCount = 8,
                topVisibleItemIndex = SIMILARITY_RUN_DETAIL_CLUSTER_FIRST_ITEM_INDEX + 2,
                clustersLoading = false
            )
        )
        assertEquals(
            null,
            similarityClusterLoadIndicatorText(
                isRunDetailPane = false,
                totalClusterCount = 12,
                loadedClusterCount = 8,
                topVisibleItemIndex = 0,
                clustersLoading = false
            )
        )
    }

    @Test
    fun memberThumbnailsAreEnabledOnlyForSimilarityClusterDetails() {
        val detailContent = sourceText("DuplicateGroupDetailContent.kt")
        val similarityContent = sourceText("SimilarityExperimentsScreen.kt")
        val resultsContent = sourceText("ResultsScreen.kt")

        assertTrue(detailContent.contains("showMemberThumbnails: Boolean = false"))
        assertTrue(detailContent.contains("contentDescription = \"Member thumbnail\""))
        assertFalse(detailContent.contains("MemberThumbnailGrid("))
        assertTrue(similarityContent.contains("showMemberThumbnails = true"))
        assertFalse(resultsContent.contains("showMemberThumbnails = true"))
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

    private fun file(path: String): FileMetadata {
        return FileMetadata(
            path = path,
            normalizedPath = path,
            sizeBytes = 10L,
            lastModifiedMillis = 1L,
            hashHex = null
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

    private fun sourceSection(
        content: String,
        start: String,
        end: String
    ): String {
        assertTrue("source should contain $start", content.contains(start))
        assertTrue("source should contain $end", content.contains(end))
        return content.substringAfter(start).substringBefore(end)
    }
}
