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
import opensource.cached_dupe_scanner.storage.SimilarityClusterMember
import opensource.cached_dupe_scanner.storage.SimilarityExperimentRunRequest
import opensource.cached_dupe_scanner.ui.home.similarity.SimilaritySizeInput
import opensource.cached_dupe_scanner.ui.home.similarity.SimilaritySizeUnit
import opensource.cached_dupe_scanner.ui.home.similarity.SimilarityRunRequestBuildResult
import opensource.cached_dupe_scanner.ui.home.similarity.SimilarityRunRequestDraft
import opensource.cached_dupe_scanner.ui.home.similarity.SimilarityTimeInput
import opensource.cached_dupe_scanner.ui.home.similarity.SimilarityTimeUnit
import opensource.cached_dupe_scanner.ui.home.similarity.buildSimilarityRunRequest
import opensource.cached_dupe_scanner.ui.home.similarity.defaultSizeInputForUnit
import opensource.cached_dupe_scanner.ui.home.similarity.defaultTimeInputForUnit
import opensource.cached_dupe_scanner.ui.home.similarity.durationNeighborListExperimentForRun
import opensource.cached_dupe_scanner.ui.home.similarity.durationToleranceExperimentForRun
import opensource.cached_dupe_scanner.ui.home.similarity.exactThumbnailExperimentForRun
import opensource.cached_dupe_scanner.ui.home.similarity.executableDurationNeighborListStep
import opensource.cached_dupe_scanner.ui.home.similarity.executableDurationToleranceStep
import opensource.cached_dupe_scanner.ui.home.similarity.executableExactThumbnailStep
import opensource.cached_dupe_scanner.ui.home.similarity.executableTemplateKind
import opensource.cached_dupe_scanner.ui.home.similarity.parsedDurationNeighborListStep
import opensource.cached_dupe_scanner.ui.home.similarity.parsedDurationToleranceMillis
import opensource.cached_dupe_scanner.ui.home.similarity.parsedDurationToleranceStep
import opensource.cached_dupe_scanner.ui.home.similarity.parsedExactThumbnailStep
import opensource.cached_dupe_scanner.ui.home.similarity.parsedFrameSeconds
import opensource.cached_dupe_scanner.ui.home.similarity.parsedMinSizeBytes
import opensource.cached_dupe_scanner.ui.home.similarity.sanitizeFrameSecondsInput
import opensource.cached_dupe_scanner.ui.home.similarity.selectedSimilarityExperimentTemplate
import opensource.cached_dupe_scanner.ui.home.similarity.selectedSimilarityRun
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
    fun sizeUnitsExposeMenuOptions() {
        assertEquals(
            listOf("B", "KB", "MB", "GB"),
            SimilaritySizeUnit.entries.map { it.label }
        )
    }

    @Test
    fun timeUnitsExposeMenuOptions() {
        assertEquals(
            listOf("s", "ms", "min"),
            SimilarityTimeUnit.entries.map { it.label }
        )
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
        assertEquals(
            500L,
            parsedDurationToleranceStep(
                input = "500",
                unit = SimilarityTimeUnit.MS
            ).toleranceMillis
        )
        assertEquals(
            250L,
            parsedDurationNeighborListStep(
                input = "250",
                unit = SimilarityTimeUnit.MS
            ).toleranceMillis
        )
        assertEquals(120_000L, parsedDurationToleranceMillis("2", SimilarityTimeUnit.MIN))
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
    fun runFactoryBuildsExactThumbnailRequest() {
        val template = exactTemplate("exact")

        val result = buildSimilarityRunRequest(
            template = template,
            draft = SimilarityRunRequestDraft(
                mediaScope = SimilarityMediaScope.Image,
                minSizeBytes = 1024L,
                exactThumbnailStep = ExactThumbnailHashStep(
                    frameSeconds = listOf(0, 2),
                    resizeWidthPx = 2,
                    resizeHeightPx = 3,
                    quantizationLevels = null,
                    grayscale = true
                )
            )
        )

        val request = assertRequest(result)
        assertEquals(SimilarityMediaScope.Image, request.mediaScope)
        assertEquals(1024L, request.minSizeBytes)
        assertEquals(listOf(0, 2), request.exactThumbnailStep?.frameSeconds)
        assertEquals(null, request.durationToleranceStep)
        assertEquals(null, request.durationNeighborListStep)
    }

    @Test
    fun runFactoryRejectsExactThumbnailRequestWithoutFrames() {
        val template = exactTemplate("exact")

        val result = buildSimilarityRunRequest(
            template = template,
            draft = SimilarityRunRequestDraft(
                mediaScope = SimilarityMediaScope.Video,
                minSizeBytes = 0L,
                exactThumbnailStep = ExactThumbnailHashStep(
                    frameSeconds = emptyList(),
                    resizeWidthPx = 1,
                    resizeHeightPx = 1,
                    quantizationLevels = null,
                    grayscale = false
                )
            )
        )

        assertEquals(
            SimilarityRunRequestBuildResult.Invalid("Add at least one frame timestamp."),
            result
        )
    }

    @Test
    fun runFactoryBuildsDurationToleranceRequest() {
        val template = SimilarityExperimentSpec(
            id = "duration",
            name = "Duration",
            description = "Duration",
            defaultMinSizeBytes = 0L,
            mediaScope = SimilarityMediaScope.Video,
            steps = listOf(DurationToleranceStep(toleranceMillis = 500L))
        )

        val result = buildSimilarityRunRequest(
            template = template,
            draft = SimilarityRunRequestDraft(
                mediaScope = SimilarityMediaScope.Image,
                minSizeBytes = 2048L,
                durationToleranceStep = DurationToleranceStep(toleranceMillis = 250L)
            )
        )

        val request = assertRequest(result)
        assertEquals(SimilarityMediaScope.Video, request.mediaScope)
        assertEquals(2048L, request.minSizeBytes)
        assertEquals(250L, request.durationToleranceStep?.toleranceMillis)
        assertEquals(null, request.exactThumbnailStep)
        assertEquals(null, request.durationNeighborListStep)
    }

    @Test
    fun runFactoryBuildsDurationNeighborRequest() {
        val template = SimilarityExperimentSpec(
            id = "duration-neighbor",
            name = "Duration neighbor",
            description = "Duration neighbor",
            defaultMinSizeBytes = 0L,
            mediaScope = SimilarityMediaScope.Video,
            steps = listOf(DurationNeighborListStep(toleranceMillis = 500L))
        )

        val result = buildSimilarityRunRequest(
            template = template,
            draft = SimilarityRunRequestDraft(
                mediaScope = SimilarityMediaScope.Image,
                minSizeBytes = 4096L,
                durationNeighborListStep = DurationNeighborListStep(toleranceMillis = 750L)
            )
        )

        val request = assertRequest(result)
        assertEquals(SimilarityMediaScope.Video, request.mediaScope)
        assertEquals(4096L, request.minSizeBytes)
        assertEquals(750L, request.durationNeighborListStep?.toleranceMillis)
        assertEquals(null, request.exactThumbnailStep)
        assertEquals(null, request.durationToleranceStep)
    }

    @Test
    fun runFactoryRejectsMethodologyOnlyTemplate() {
        val template = SimilarityExperimentSpec(
            id = "methodology",
            name = "Methodology",
            description = "Methodology",
            defaultMinSizeBytes = 0L,
            mediaScope = SimilarityMediaScope.Video,
            steps = listOf(
                DurationToleranceStep(toleranceMillis = 500L),
                ExactThumbnailHashStep(
                    frameSeconds = listOf(0),
                    resizeWidthPx = 1,
                    resizeHeightPx = 1,
                    quantizationLevels = null,
                    grayscale = false
                )
            )
        )

        val result = buildSimilarityRunRequest(
            template = template,
            draft = SimilarityRunRequestDraft(
                mediaScope = SimilarityMediaScope.Video,
                minSizeBytes = 0L
            )
        )

        assertEquals(
            SimilarityRunRequestBuildResult.Invalid("Selected experiment template is not directly runnable."),
            result
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
    fun durationNeighborTimeInputUsesClusterSignatureBeforeRunId() {
        val run = run("video-duration-neighbor-104857600-2000")
        val clusters = listOf(
            cluster(signature = "duration-neighbor-list-v1:1000:0000000010000-0000000010750")
        )

        assertEquals(
            SimilarityTimeInput(input = "1", unit = SimilarityTimeUnit.S),
            durationNeighborTimeInputForRun(run = run, clusters = clusters)
        )
        assertEquals(
            SimilarityTimeInput(input = "2", unit = SimilarityTimeUnit.S),
            durationNeighborTimeInputForRun(run = run, clusters = emptyList())
        )
        assertEquals(
            SimilarityTimeInput(input = "500", unit = SimilarityTimeUnit.MS),
            durationNeighborTimeInputForClusters(
                listOf(cluster(signature = "duration-neighbor-list-v1:500:0000000010000-0000000010750"))
            )
        )
        assertEquals(null, durationNeighborTimeInputForRun(run = run("exact"), clusters = emptyList()))
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
        assertEquals(
            "10.750s · /b.mp4",
            durationNeighborVideoTitle(
                member = SimilarityClusterMember(
                    metadata = file("/b.mp4"),
                    durationMillis = 10_750L
                ),
                showFullPaths = true
            )
        )
    }

    @Test
    fun screenStartsWithNewExperimentAndRunListBeforeDrillingIntoDetails() {
        val content = sourceText("SimilarityExperimentsScreen.kt")
        val taskRunnerContent = sourceText("similarity/SimilarityTaskRunner.kt")

        assertTrue(content.contains("private enum class SimilarityExperimentPane"))
        assertTrue(content.contains("SimilarityExperimentPane.List ->"))
        assertTrue(content.contains("Text(\"New experiment\")"))
        assertTrue(content.contains("onSelectRun = ::openRunPane"))
        assertTrue(content.contains("SimilarityExperimentPane.Create ->"))
        assertTrue(content.contains("SimilarityExperimentPane.TemplateDetail ->"))
        assertTrue(content.contains("onSelectExperiment = ::openTemplateDetailPane"))
        assertTrue(content.contains("SimilarityExperimentPane.RunDetail ->"))
        assertTrue(content.contains("DurationToleranceRunCard("))
        assertTrue(content.contains("buildSimilarityRunRequest("))
        assertTrue(content.contains("SimilarityRunRequestDraft("))
        assertFalse(content.contains("repository.runDurationToleranceExperiment("))
        assertFalse(content.contains("repository.runDurationNeighborListExperiment("))
        assertTrue(taskRunnerContent.contains("repository.runDurationToleranceExperiment("))
        assertTrue(taskRunnerContent.contains("repository.runDurationNeighborListExperiment("))
        assertTrue(content.contains("repository.rebuildDurationNeighborListFromStoredDurations("))
        assertTrue(content.contains("toleranceUnit = durationToleranceUnit"))
        assertTrue(content.contains("sortingEnabled = durationNeighborExplanation == null"))
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
    fun durationNeighborRunDetailUsesFlatVideoListInsteadOfClusterCards() {
        val content = sourceText("SimilarityExperimentsScreen.kt")
        val durationNeighborBranch = sourceSection(
            content = content,
            start = "} else if (selectedRunIsDurationNeighbor) {",
            end = "} else if (clusters.isEmpty()) {"
        )

        assertTrue(durationNeighborBranch.contains("items("))
        assertTrue(durationNeighborBranch.contains("items = durationNeighborMembers"))
        assertTrue(durationNeighborBranch.contains("DurationNeighborVideoCard("))
        assertTrue(durationNeighborBranch.contains("selectedDurationNeighborFile = member.metadata"))
        assertFalse(durationNeighborBranch.contains("SimilarityClusterCard("))
        assertFalse(durationNeighborBranch.contains("selectedClusterKey"))
        assertTrue(content.contains("DURATION_NEIGHBOR_MEMBER_PAGE_SIZE = 200"))
        assertTrue(content.contains("DURATION_NEIGHBOR_MEMBER_LOAD_MORE_BUFFER = 50"))
        assertTrue(content.contains("fun loadMoreDurationNeighborMembers("))
        assertTrue(content.contains("durationNeighborSortDirection"))
        assertTrue(content.contains("DurationNeighborSortDirectionCard("))
        assertTrue(content.contains("onDirectionChange = ::applyDurationNeighborSortDirection"))
        assertTrue(content.contains("offset = durationNeighborMemberNextOffset"))
        assertTrue(content.contains("durationNeighborMemberNextOffset = nextDurationNeighborMemberNextOffset"))
        assertTrue(content.contains("limit = DURATION_NEIGHBOR_MEMBER_PAGE_SIZE"))
        assertTrue(content.contains("direction = durationNeighborSortDirection"))
        assertTrue(content.contains("direction = direction"))
        assertTrue(content.contains("RadioOptionRow("))
        assertTrue(content.contains("option = SortDirection.Asc"))
        assertTrue(content.contains("option = SortDirection.Desc"))
        assertFalse(content.contains("nextClusters.flatMap { cluster -> repository.listClusterMemberRows(cluster) }"))
        assertTrue(content.contains(".distinctBy { member -> member.metadata.normalizedPath }"))
        assertTrue(content.contains("Card(\n        onClick = onOpen"))
        assertTrue(content.contains("FileDetailsDialogWithDeleteConfirm("))
        assertTrue(content.contains("DurationNeighborStoredRebuildCard("))
        assertTrue(content.contains("toleranceUnit = durationRebuildToleranceUnit"))
        assertTrue(content.contains("countDurationCandidates("))
    }

    @Test
    fun similarityClusterRunDetailExposesClusterSortOptions() {
        val content = sourceText("SimilarityExperimentsScreen.kt")
        val runDetail = sourceSection(
            content = content,
            start = "SimilarityExperimentPane.RunDetail -> {",
            end = "@Composable\nprivate fun SimilarityExperimentLazyPane"
        )
        val header = sourceSection(
            content = content,
            start = "private fun StoredSimilarityResultsHeader(",
            end = "@Composable\nprivate fun SimilarityClusterLoadingIndicator"
        )

        assertTrue(content.contains("similarityClusterSortKey"))
        assertTrue(content.contains("similarityClusterSortDirection"))
        assertTrue(runDetail.contains("clusterSortKey = similarityClusterSortKey"))
        assertTrue(runDetail.contains("clusterSortDirection = similarityClusterSortDirection"))
        assertTrue(runDetail.contains("onApplyClusterSort = ::applySimilarityClusterSort"))
        assertTrue(header.contains("SimilarityClusterSortButton("))
        assertTrue(header.contains("!isDurationNeighborList"))
        assertTrue(content.contains("private fun SimilarityClusterSortButton("))
        assertTrue(content.contains("Cluster sort options"))
        assertTrue(content.contains("SimilarityClusterSortKey.FileCount"))
        assertTrue(content.contains("SimilarityClusterSortKey.TotalSize"))
        assertTrue(content.contains("sortKey = similarityClusterSortKey"))
        assertTrue(content.contains("direction = similarityClusterSortDirection"))
    }

    @Test
    fun similarityClusterDetailLoadsMembersLazilyByPage() {
        val content = sourceText("SimilarityExperimentsScreen.kt")
        val detail = sourceSection(
            content = content,
            start = "private fun SimilarityClusterDetailScreen(",
            end = "@Composable\nprivate fun ExactHashReductionPreviewCard"
        )

        assertTrue(content.contains("SIMILARITY_CLUSTER_DETAIL_MEMBER_PAGE_SIZE = 200"))
        assertTrue(detail.contains("offset = if (reset) 0 else currentMembers.size"))
        assertTrue(detail.contains("limit = SIMILARITY_CLUSTER_DETAIL_MEMBER_PAGE_SIZE"))
        assertTrue(detail.contains("shouldTriggerDetailAutoLoad("))
        assertTrue(detail.contains("loadClusterMemberPage(reset = false)"))
        assertFalse(detail.contains("repository.listClusterMemberRows(cluster = cluster)"))
    }

    @Test
    fun similarityClusterDetailKeepsLazySelectAllLongPressBehavior() {
        val content = sourceText("SimilarityExperimentsScreen.kt")
        val detailScreen = sourceSection(
            content = content,
            start = "private fun SimilarityClusterDetailScreen(",
            end = "@Composable\nprivate fun ExactHashReductionPreviewCard"
        )

        assertTrue(detailScreen.contains("rememberLazyDetailSelectionState(previewMemoryKey)"))
        assertTrue(detailScreen.contains("lazySelection.statusText("))
        assertTrue(detailScreen.contains("Select all includes not-loaded files in delete queries."))
        assertTrue(detailScreen.contains("lazySelection.isSelectAllMode"))
        assertTrue(detailScreen.contains("while (offset < cluster.fileCount)"))
        assertTrue(detailScreen.contains("repository.listClusterMemberRows("))
        assertTrue(detailScreen.contains("offset = offset"))
        assertTrue(detailScreen.contains("lazySelection.selectedLoadedFilesForDelete("))
        assertFalse(detailScreen.contains("DuplicateGroupDetailContent("))
        assertFalse(detailScreen.contains("val isSelectAllMode = remember"))
        assertFalse(detailScreen.contains("val deselectedPathsInSelectAll = remember"))
    }

    @Test
    fun similarityClusterDetailRendersMembersAsLazyItems() {
        val content = sourceText("SimilarityExperimentsScreen.kt")
        val detailContent = sourceSection(
            content = content,
            start = "private fun LazyListScope.SimilarityClusterDetailContent(",
            end = "@Composable\n@OptIn(ExperimentalFoundationApi::class)\nprivate fun SimilarityClusterMemberCard("
        )

        assertTrue(detailContent.contains("items(\n        items = displayedMembers,"))
        assertTrue(detailContent.contains("key = { file -> \"similarity-cluster-detail-member:${'$'}{file.normalizedPath}\" }"))
        assertTrue(detailContent.contains("SimilarityClusterMemberCard("))
        assertFalse(detailContent.contains("displayedMembers.forEach { file ->"))
    }

    @Test
    fun similarityClusterDetailOffersVideoTimelinePreviewOptionForVideoMembers() {
        val content = sourceText("SimilarityExperimentsScreen.kt")
        val detailScreen = sourceSection(
            content = content,
            start = "private fun SimilarityClusterDetailScreen(",
            end = "@OptIn(ExperimentalFoundationApi::class)\nprivate fun LazyListScope.SimilarityClusterDetailContent("
        )
        val detailContent = sourceSection(
            content = content,
            start = "private fun LazyListScope.SimilarityClusterDetailContent(",
            end = "@Composable\n@OptIn(ExperimentalFoundationApi::class)\nprivate fun SimilarityClusterMemberCard("
        )
        val memberCard = sourceSection(
            content = content,
            start = "private fun SimilarityClusterMemberCard(",
            end = "@Composable\nprivate fun SimilarityClusterDetailDialogs("
        )
        val videoPreview = sourceSection(
            content = content,
            start = "private fun SimilarityClusterMemberVideoPreview(",
            end = "@Composable\nprivate fun ExactHashReductionPreviewCard"
        )
        val mainActivity = projectSourceText("app/src/main/java/opensource/cached_dupe_scanner/MainActivity.kt")

        assertTrue(detailScreen.contains("rememberedVideoPreviewCache: MutableMap<String, ImageBitmap>"))
        assertTrue(detailScreen.contains("videoPreviewFrameHeight: Dp"))
        assertTrue(detailScreen.contains("val showVideoPreviews = remember(clusterKey) { mutableStateOf(false) }"))
        assertTrue(detailScreen.contains("val videoPreviewMenuExpanded = remember(clusterKey) { mutableStateOf(false) }"))
        assertTrue(detailScreen.contains("val hasVideoMembers = members.any"))
        assertTrue(detailScreen.contains("actions = {"))
        assertTrue(detailScreen.contains("if (hasVideoMembers) {"))
        assertTrue(detailScreen.contains("IconButton(onClick = { videoPreviewMenuExpanded.value = true })"))
        assertTrue(detailScreen.contains("Icon(Icons.Filled.MoreVert, contentDescription = \"Menu\")"))
        assertTrue(detailScreen.contains("expanded = videoPreviewMenuExpanded.value"))
        assertTrue(detailScreen.contains("Text(\"Video preview\")"))
        assertTrue(detailScreen.contains("checked = showVideoPreviews.value"))
        assertTrue(detailScreen.contains("showVideoPreviews.value = !showVideoPreviews.value"))
        assertTrue(detailScreen.contains("showVideoPreviews = showVideoPreviews.value && hasVideoMembers"))
        assertFalse(detailScreen.contains("showVideoPreviewOption = hasVideoMembers"))
        assertFalse(detailScreen.contains("onToggleVideoPreviews = { enabled -> showVideoPreviews.value = enabled }"))
        assertTrue(detailScreen.contains("rememberedVideoPreviewCache = rememberedVideoPreviewCache"))
        assertTrue(memberCard.contains("val isVideo = isVideoFile(file.normalizedPath)"))
        assertTrue(detailContent.contains("showVideoPreviews: Boolean"))
        assertFalse(detailContent.contains("showVideoPreviewOption: Boolean"))
        assertFalse(detailContent.contains("onToggleVideoPreviews: (Boolean) -> Unit"))
        assertFalse(detailContent.contains("Text(\"Video preview\")"))
        assertFalse(detailContent.contains(".clickable { onToggleVideoPreviews(!showVideoPreviews) }"))
        assertTrue(memberCard.contains("SimilarityClusterMemberVideoPreview("))
        assertTrue(memberCard.contains("visible = showVideoPreviews && showMemberThumbnails && isVideo && !isDeleted"))
        assertTrue(videoPreview.contains("VideoTimelinePreviewStrip("))
        assertTrue(videoPreview.contains("rememberedPreviewCache = rememberedVideoPreviewCache"))
        assertTrue(videoPreview.contains("keepLoadedInMemory = keepLoadedVideoPreviewsInMemory"))
        assertTrue(videoPreview.contains("snapToFillWidth = snapVideoPreviewFramesToWidth"))
        assertTrue(videoPreview.contains("lineCount = videoPreviewLineCount"))
        assertTrue(videoPreview.contains("frameHeight = videoPreviewFrameHeight"))
        assertTrue(mainActivity.contains("rememberedVideoPreviewCache = rememberedVideoPreviewCache"))
        assertTrue(mainActivity.contains("keepLoadedVideoPreviewsInMemory = settingsSnapshot.keepLoadedVideoPreviewsInMemory"))
    }

    @Test
    fun similarityClusterDetailPlacesVideoTimelineBelowMemberRow() {
        val content = sourceText("SimilarityExperimentsScreen.kt")
        val memberCard = sourceSection(
            content = content,
            start = "private fun SimilarityClusterMemberCard(",
            end = "@Composable\nprivate fun SimilarityClusterDetailDialogs("
        )
        val textColumn = sourceSection(
            content = memberCard,
            start = "Column(modifier = Modifier.fillMaxWidth()) {",
            end = "SimilarityClusterMemberVideoPreview("
        )

        assertTrue(memberCard.contains("Column(\n            modifier = Modifier\n                .padding(10.dp)\n                .fillMaxWidth()"))
        assertTrue(memberCard.contains("Row(\n                modifier = Modifier.fillMaxWidth(),"))
        assertTrue(memberCard.contains("SimilarityClusterMemberVideoPreview("))
        assertTrue(memberCard.contains("visible = showVideoPreviews && showMemberThumbnails && isVideo && !isDeleted"))
        assertFalse(textColumn.contains("VideoTimelinePreviewStrip("))
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
    fun similarityResultLazyLoadingUsesNeighborListBufferAndWaitsForActivePageLoad() {
        assertFalse(
            shouldLoadMoreSimilarityResults(
                isRunDetailPane = true,
                isDurationNeighborList = false,
                lastVisibleItemIndex = 37,
                totalItemsCount = 50,
                clustersLoading = true,
                clustersExhausted = false,
                durationNeighborMembersLoading = false,
                durationNeighborMembersExhausted = true
            )
        )
        assertTrue(
            shouldLoadMoreSimilarityResults(
                isRunDetailPane = true,
                isDurationNeighborList = false,
                lastVisibleItemIndex = 38,
                totalItemsCount = 50,
                clustersLoading = false,
                clustersExhausted = false,
                durationNeighborMembersLoading = false,
                durationNeighborMembersExhausted = true
            )
        )
        assertFalse(
            shouldLoadMoreSimilarityResults(
                isRunDetailPane = true,
                isDurationNeighborList = true,
                lastVisibleItemIndex = 100,
                totalItemsCount = 150,
                clustersLoading = false,
                clustersExhausted = true,
                durationNeighborMembersLoading = true,
                durationNeighborMembersExhausted = false
            )
        )
        assertTrue(
            shouldLoadMoreSimilarityResults(
                isRunDetailPane = true,
                isDurationNeighborList = true,
                lastVisibleItemIndex = 100,
                totalItemsCount = 150,
                clustersLoading = false,
                clustersExhausted = true,
                durationNeighborMembersLoading = false,
                durationNeighborMembersExhausted = false
            )
        )
        assertFalse(
            shouldLoadMoreSimilarityResults(
                isRunDetailPane = true,
                isDurationNeighborList = true,
                lastVisibleItemIndex = 99,
                totalItemsCount = 150,
                clustersLoading = false,
                clustersExhausted = true,
                durationNeighborMembersLoading = false,
                durationNeighborMembersExhausted = false
            )
        )
        assertFalse(
            shouldLoadMoreSimilarityResults(
                isRunDetailPane = true,
                isDurationNeighborList = false,
                lastVisibleItemIndex = 10,
                totalItemsCount = 50,
                clustersLoading = false,
                clustersExhausted = false,
                durationNeighborMembersLoading = false,
                durationNeighborMembersExhausted = true
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

    private fun assertRequest(
        result: SimilarityRunRequestBuildResult
    ): SimilarityExperimentRunRequest {
        assertTrue(result is SimilarityRunRequestBuildResult.Valid)
        return (result as SimilarityRunRequestBuildResult.Valid).request
    }

    private fun sourceText(fileName: String): String {
        return projectSourceText("app/src/main/java/opensource/cached_dupe_scanner/ui/home/$fileName")
    }

    private fun projectSourceText(relativePath: String): String {
        val projectDir = File(System.getProperty("user.dir") ?: ".")
        val sourceFile = sequenceOf(
            File(projectDir, relativePath),
            File(projectDir.parentFile ?: projectDir, relativePath)
        ).firstOrNull { it.exists() }

        assertTrue("$relativePath should exist", sourceFile != null)
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
