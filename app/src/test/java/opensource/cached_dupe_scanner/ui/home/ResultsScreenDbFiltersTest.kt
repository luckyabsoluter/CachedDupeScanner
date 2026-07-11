package opensource.cached_dupe_scanner.ui.home

import opensource.cached_dupe_scanner.cache.DuplicateGroupEntity
import opensource.cached_dupe_scanner.core.FileMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResultsScreenDbFiltersTest {
    @Test
    fun durationAverageTargetIsLimitedToSimilarityFilters() {
        assertTrue(SIMILARITY_FILTER_TARGETS.contains(ResultsFilterTarget.DurationFromAverage))
        assertTrue(SIMILARITY_FILTER_TARGETS.contains(ResultsFilterTarget.SameResolution))
        assertFalse(RESULT_FILTER_TARGETS.contains(ResultsFilterTarget.DurationFromAverage))
        assertFalse(RESULT_FILTER_TARGETS.contains(ResultsFilterTarget.SameResolution))
        assertFalse(FILE_FILTER_TARGETS.contains(ResultsFilterTarget.DurationFromAverage))
        assertFalse(FILE_FILTER_TARGETS.contains(ResultsFilterTarget.SameResolution))
    }

    @Test
    fun matchesResultsFilterUsesGroupItemCountRule() {
        val definition = ResultsFilterDefinition(
            clusters = listOf(
                ResultsFilterCluster(
                    id = "cluster_1",
                    name = "Count",
                    rules = listOf(
                        ResultsFilterRule(
                            id = "rule_1",
                            target = ResultsFilterTarget.GroupItemCount,
                            countOperator = ResultsFilterCountOperator.AtLeast,
                            value = "3"
                        )
                    )
                )
            )
        )

        assertTrue(
            matchesResultsFilter(
                definition = definition,
                group = group(fileCount = 3),
                members = listOf(file("/dupes/a.jpg"))
            )
        )
        assertFalse(
            matchesResultsFilter(
                definition = definition,
                group = group(fileCount = 2),
                members = listOf(file("/dupes/a.jpg"))
            )
        )
    }

    @Test
    fun matchesResultsFilterUsesFileNameAndFolderRulesCaseInsensitively() {
        val definition = ResultsFilterDefinition(
            clusters = listOf(
                ResultsFilterCluster(
                    id = "cluster_1",
                    name = "Names",
                    mode = ResultsFilterClusterMode.All,
                    rules = listOf(
                        ResultsFilterRule(
                            id = "rule_1",
                            target = ResultsFilterTarget.FileName,
                            textOperator = ResultsFilterTextOperator.StartsWith,
                            value = "img_"
                        ),
                        ResultsFilterRule(
                            id = "rule_2",
                            target = ResultsFilterTarget.FolderPath,
                            textOperator = ResultsFilterTextOperator.Contains,
                            value = "camera"
                        )
                    )
                )
            )
        )

        val members = listOf(
            file("/storage/DCIM/Camera/IMG_1001.JPG"),
            file("/storage/Download/backup.jpg")
        )

        assertTrue(
            matchesResultsFilter(
                definition = definition,
                group = group(fileCount = members.size),
                members = members
            )
        )
    }

    @Test
    fun matchesResultsFilterHonorsClusterAnyModeAndSkipsDisabledClusters() {
        val definition = ResultsFilterDefinition(
            clusters = listOf(
                ResultsFilterCluster(
                    id = "cluster_1",
                    name = "Disabled",
                    enabled = false,
                    rules = listOf(
                        ResultsFilterRule(
                            id = "rule_1",
                            target = ResultsFilterTarget.FileName,
                            value = "never"
                        )
                    )
                ),
                ResultsFilterCluster(
                    id = "cluster_2",
                    name = "Flexible",
                    mode = ResultsFilterClusterMode.Any,
                    rules = listOf(
                        ResultsFilterRule(
                            id = "rule_2",
                            target = ResultsFilterTarget.FileName,
                            textOperator = ResultsFilterTextOperator.Equals,
                            value = "cover.jpg"
                        ),
                        ResultsFilterRule(
                            id = "rule_3",
                            target = ResultsFilterTarget.FolderPath,
                            textOperator = ResultsFilterTextOperator.EndsWith,
                            value = "albums"
                        )
                    )
                )
            )
        )

        val members = listOf(
            file("/media/music/albums/track01.flac"),
            file("/media/music/singles/demo.flac")
        )

        assertTrue(
            matchesResultsFilter(
                definition = definition,
                group = group(fileCount = members.size),
                members = members
            )
        )
    }

    @Test
    fun matchesResultsFilterUsesSameFolderRule() {
        val definition = ResultsFilterDefinition(
            clusters = listOf(
                ResultsFilterCluster(
                    id = "cluster_1",
                    name = "Same folder",
                    rules = listOf(
                        ResultsFilterRule(
                            id = "rule_1",
                            target = ResultsFilterTarget.SameFolder
                        )
                    )
                )
            )
        )

        assertTrue(
            matchesResultsFilter(
                definition = definition,
                group = group(fileCount = 2),
                members = listOf(
                    file("/storage/camera/a.jpg"),
                    file("/storage/camera/b.jpg")
                )
            )
        )
        assertFalse(
            matchesResultsFilter(
                definition = definition,
                group = group(fileCount = 2),
                members = listOf(
                    file("/storage/camera/a.jpg"),
                    file("/storage/screenshots/b.jpg")
                )
            )
        )
    }

    @Test
    fun matchesResultsFilterUsesSameFileSizeRule() {
        val definition = ResultsFilterDefinition(
            clusters = listOf(
                ResultsFilterCluster(
                    id = "cluster_1",
                    name = "Same size",
                    rules = listOf(
                        ResultsFilterRule(
                            id = "rule_1",
                            target = ResultsFilterTarget.SameFileSize
                        )
                    )
                )
            )
        )
        val sameSize = listOf(
            file("/storage/camera/a.jpg", sizeBytes = 10L),
            file("/storage/camera/b.jpg", sizeBytes = 10L)
        )
        val differentSizes = listOf(
            file("/storage/camera/a.jpg", sizeBytes = 10L),
            file("/storage/camera/b.jpg", sizeBytes = 11L)
        )

        assertTrue(matchesResultsFilter(definition, group(fileCount = 2), sameSize))
        assertFalse(matchesResultsFilter(definition, group(fileCount = 2), differentSizes))
        assertFalse(matchesResultsFilter(definition, group(fileCount = 0), emptyList()))
    }

    @Test
    fun similarityFilterRequiresEveryMemberToHaveTheSameResolution() {
        val definition = ResultsFilterDefinition(
            clusters = listOf(
                ResultsFilterCluster(
                    id = "cluster_resolution",
                    name = "Same resolution",
                    rules = listOf(
                        ResultsFilterRule(
                            id = "rule_resolution",
                            target = ResultsFilterTarget.SameResolution
                        )
                    )
                )
            )
        )
        val sameResolution = listOf(
            file("/videos/a.mp4", widthPixels = 1920, heightPixels = 1080),
            file("/videos/b.mp4", widthPixels = 1920, heightPixels = 1080)
        )

        assertTrue(
            matchesResultsFilter(
                definition = definition,
                group = group(fileCount = sameResolution.size),
                members = sameResolution,
                supportedTargets = SIMILARITY_FILTER_TARGETS
            )
        )
        assertFalse(
            matchesResultsFilter(
                definition = definition,
                group = group(fileCount = 2),
                members = sameResolution.dropLast(1) +
                    file("/videos/b.mp4", widthPixels = 1280, heightPixels = 1080),
                supportedTargets = SIMILARITY_FILTER_TARGETS
            )
        )
        assertFalse(
            matchesResultsFilter(
                definition = definition,
                group = group(fileCount = 2),
                members = sameResolution.dropLast(1) +
                    file("/videos/b.mp4", widthPixels = 1920, heightPixels = 1200),
                supportedTargets = SIMILARITY_FILTER_TARGETS
            )
        )
        assertFalse(
            matchesResultsFilter(
                definition = definition,
                group = group(fileCount = 2),
                members = sameResolution.dropLast(1) + file("/videos/unknown.mp4"),
                supportedTargets = SIMILARITY_FILTER_TARGETS
            )
        )
    }

    @Test
    fun similarityFilterRequiresEveryDurationWithinToleranceOfExactAverage() {
        val definition = durationAverageDefinition(seconds = "0", milliseconds = "100")
        val withinTolerance = listOf(
            file("/videos/a.mp4", durationMillis = 10_000L),
            file("/videos/b.mp4", durationMillis = 10_100L),
            file("/videos/c.mp4", durationMillis = 10_200L)
        )
        val outsideTolerance = withinTolerance.dropLast(1) +
            file("/videos/c.mp4", durationMillis = 10_201L)

        assertTrue(
            matchesResultsFilter(
                definition = definition,
                group = group(fileCount = withinTolerance.size),
                members = withinTolerance,
                supportedTargets = SIMILARITY_FILTER_TARGETS
            )
        )
        assertFalse(
            matchesResultsFilter(
                definition = definition,
                group = group(fileCount = outsideTolerance.size),
                members = outsideTolerance,
                supportedTargets = SIMILARITY_FILTER_TARGETS
            )
        )
        assertFalse(
            matchesResultsFilter(
                definition = definition,
                group = group(fileCount = 2),
                members = listOf(
                    file("/videos/a.mp4", durationMillis = 10_000L),
                    file("/videos/unknown.mp4", durationMillis = null)
                ),
                supportedTargets = SIMILARITY_FILTER_TARGETS
            )
        )
    }

    @Test
    fun pagedSimilarityFilterAccumulatesAverageAcrossEveryMemberPage() {
        val definition = durationAverageDefinition(seconds = "1", milliseconds = "250")
        val members = listOf(
            file("/videos/a.mp4", durationMillis = 8_750L),
            file("/videos/b.mp4", durationMillis = 10_000L),
            file("/videos/c.mp4", durationMillis = 11_250L)
        )

        assertTrue(
            matchesResultsFilterPagedMembers(
                definition = definition,
                group = group(fileCount = members.size),
                supportedTargets = SIMILARITY_FILTER_TARGETS,
                memberPages = { pagedMembers(members, pageSize = 1) }
            )
        )
        assertFalse(
            matchesResultsFilterPagedMembers(
                definition = durationAverageDefinition(seconds = "1", milliseconds = "249"),
                group = group(fileCount = members.size),
                supportedTargets = SIMILARITY_FILTER_TARGETS,
                memberPages = { pagedMembers(members, pageSize = 1) }
            )
        )
    }

    @Test
    fun durationAverageToleranceParsesSecondsAndMillisecondsSafely() {
        assertEquals(
            1_250L,
            ResultsFilterRule(
                id = "rule_1",
                target = ResultsFilterTarget.DurationFromAverage,
                durationToleranceSeconds = "1",
                durationToleranceMilliseconds = "250"
            ).durationToleranceMillis()
        )
        assertEquals(
            2_375L,
            ResultsFilterRule(
                id = "rule_2",
                target = ResultsFilterTarget.DurationFromAverage,
                durationToleranceMilliseconds = "2375"
            ).durationToleranceMillis()
        )
        assertEquals(
            null,
            ResultsFilterRule(
                id = "rule_3",
                target = ResultsFilterTarget.DurationFromAverage,
                durationToleranceSeconds = "1",
                durationToleranceMilliseconds = "1000"
            ).durationToleranceMillis()
        )
        assertEquals(
            null,
            ResultsFilterRule(
                id = "rule_4",
                target = ResultsFilterTarget.DurationFromAverage
            ).durationToleranceMillis()
        )
    }

    @Test
    fun durationAverageEditorConvertsLegacyCompositeValuesToOneMillisecondInput() {
        val legacyRule = ResultsFilterRule(
            id = "rule_legacy",
            target = ResultsFilterTarget.DurationFromAverage,
            durationToleranceSeconds = "2",
            durationToleranceMilliseconds = "375"
        )

        assertEquals(ResultsFilterDurationUnit.Milliseconds, legacyRule.durationToleranceUnit())
        assertEquals("2375", legacyRule.durationToleranceInput())
        assertEquals(
            ResultsFilterRule(
                id = "rule_legacy",
                target = ResultsFilterTarget.DurationFromAverage,
                durationToleranceMilliseconds = "2500"
            ),
            legacyRule.withDurationToleranceInput("2500")
        )
    }

    @Test
    fun matchesResultsFilterUsesModifiedTimeRule() {
        val definition = ResultsFilterDefinition(
            clusters = listOf(
                ResultsFilterCluster(
                    id = "cluster_1",
                    name = "Recent",
                    rules = listOf(
                        ResultsFilterRule(
                            id = "rule_1",
                            target = ResultsFilterTarget.ModifiedTime,
                            timeOperator = ResultsFilterTimeOperator.OnOrAfter,
                            value = "2026-04-20"
                        )
                    )
                )
            )
        )

        assertTrue(
            matchesResultsFilter(
                definition = definition,
                group = group(fileCount = 2),
                members = listOf(
                    file("/storage/camera/old.jpg", modified = 1_776_643_199_999L),
                    file("/storage/camera/new.jpg", modified = 1_776_643_200_000L)
                )
            )
        )
        assertFalse(
            matchesResultsFilter(
                definition = definition,
                group = group(fileCount = 1),
                members = listOf(file("/storage/camera/old.jpg", modified = 1_776_643_199_999L))
            )
        )
    }

    @Test
    fun pagedFilterMatchesFullFilterAcrossClusterModes() {
        val definition = ResultsFilterDefinition(
            clusters = listOf(
                ResultsFilterCluster(
                    id = "cluster_1",
                    name = "All rules",
                    mode = ResultsFilterClusterMode.All,
                    rules = listOf(
                        ResultsFilterRule(
                            id = "rule_1",
                            target = ResultsFilterTarget.GroupItemCount,
                            countOperator = ResultsFilterCountOperator.AtLeast,
                            value = "3"
                        ),
                        ResultsFilterRule(
                            id = "rule_2",
                            target = ResultsFilterTarget.FileName,
                            textOperator = ResultsFilterTextOperator.Contains,
                            value = "target"
                        )
                    )
                ),
                ResultsFilterCluster(
                    id = "cluster_2",
                    name = "Any rules",
                    mode = ResultsFilterClusterMode.Any,
                    rules = listOf(
                        ResultsFilterRule(
                            id = "rule_3",
                            target = ResultsFilterTarget.FolderPath,
                            textOperator = ResultsFilterTextOperator.Contains,
                            value = "missing"
                        ),
                        ResultsFilterRule(
                            id = "rule_4",
                            target = ResultsFilterTarget.ModifiedTime,
                            timeOperator = ResultsFilterTimeOperator.OnOrAfter,
                            value = "100"
                        )
                    )
                )
            )
        )
        val members = listOf(
            file("/library/a/first.jpg", modified = 1L),
            file("/library/b/target.jpg", modified = 10L),
            file("/library/c/third.jpg", modified = 100L)
        )

        assertEquals(
            matchesResultsFilter(definition, group(fileCount = members.size), members),
            pagedFilterResult(definition, group(fileCount = members.size), members, pageSize = 1)
        )
    }

    @Test
    fun pagedFilterEvaluatesSameFolderAcrossPages() {
        val definition = ResultsFilterDefinition(
            clusters = listOf(
                ResultsFilterCluster(
                    id = "cluster_1",
                    name = "Same folder",
                    rules = listOf(ResultsFilterRule(id = "rule_1", target = ResultsFilterTarget.SameFolder))
                )
            )
        )
        val sameFolder = listOf(file("/same/a.jpg"), file("/same/b.jpg"), file("/same/c.jpg"))
        val differentFolders = listOf(file("/same/a.jpg"), file("/other/b.jpg"), file("/same/c.jpg"))

        assertEquals(
            matchesResultsFilter(definition, group(fileCount = sameFolder.size), sameFolder),
            pagedFilterResult(definition, group(fileCount = sameFolder.size), sameFolder, pageSize = 1)
        )
        assertEquals(
            matchesResultsFilter(definition, group(fileCount = differentFolders.size), differentFolders),
            pagedFilterResult(definition, group(fileCount = differentFolders.size), differentFolders, pageSize = 1)
        )
    }

    @Test
    fun pagedFilterEvaluatesSameFileSizeAcrossPages() {
        val definition = ResultsFilterDefinition(
            clusters = listOf(
                ResultsFilterCluster(
                    id = "cluster_1",
                    name = "Same size",
                    rules = listOf(
                        ResultsFilterRule(id = "rule_1", target = ResultsFilterTarget.SameFileSize)
                    )
                )
            )
        )
        val sameSize = listOf(
            file("/same/a.jpg", sizeBytes = 20L),
            file("/other/b.jpg", sizeBytes = 20L),
            file("/third/c.jpg", sizeBytes = 20L)
        )
        val differentSizes = sameSize.dropLast(1) + file("/third/c.jpg", sizeBytes = 21L)

        assertTrue(pagedFilterResult(definition, group(fileCount = 3), sameSize, pageSize = 1))
        assertFalse(pagedFilterResult(definition, group(fileCount = 3), differentSizes, pageSize = 1))
    }

    @Test
    fun pagedSimilarityFilterEvaluatesResolutionAcrossEveryMemberPage() {
        val definition = ResultsFilterDefinition(
            clusters = listOf(
                ResultsFilterCluster(
                    id = "cluster_resolution",
                    name = "Same resolution",
                    rules = listOf(
                        ResultsFilterRule(id = "rule_resolution", target = ResultsFilterTarget.SameResolution)
                    )
                )
            )
        )
        val sameResolution = listOf(
            file("/videos/a.mp4", widthPixels = 1920, heightPixels = 1080),
            file("/videos/b.mp4", widthPixels = 1920, heightPixels = 1080),
            file("/videos/c.mp4", widthPixels = 1920, heightPixels = 1080)
        )
        val differentResolution = sameResolution.dropLast(1) +
            file("/videos/c.mp4", widthPixels = 1920, heightPixels = 1200)

        assertTrue(
            matchesResultsFilterPagedMembers(
                definition = definition,
                group = group(fileCount = sameResolution.size),
                supportedTargets = SIMILARITY_FILTER_TARGETS,
                memberPages = { pagedMembers(sameResolution, pageSize = 1) }
            )
        )
        assertFalse(
            matchesResultsFilterPagedMembers(
                definition = definition,
                group = group(fileCount = differentResolution.size),
                supportedTargets = SIMILARITY_FILTER_TARGETS,
                memberPages = { pagedMembers(differentResolution, pageSize = 1) }
            )
        )
    }

    @Test
    fun pagedGroupCountFilterDoesNotLoadMembers() {
        val definition = ResultsFilterDefinition(
            clusters = listOf(
                ResultsFilterCluster(
                    id = "cluster_1",
                    name = "Count",
                    rules = listOf(
                        ResultsFilterRule(
                            id = "rule_1",
                            target = ResultsFilterTarget.GroupItemCount,
                            countOperator = ResultsFilterCountOperator.Equals,
                            value = "2"
                        )
                    )
                )
            )
        )
        var loaderCalled = false

        val matched = matchesResultsFilterPagedMembers(
            definition = definition,
            group = group(fileCount = 2),
            memberPages = {
                loaderCalled = true
                sequenceOf(listOf(file("/unexpected/a.jpg")))
            }
        )

        assertTrue(matched)
        assertFalse(loaderCalled)
    }


    @Test
    fun modifiedTimeRuleMatchesWholeUtcDate() {
        val parsed = parseResultsFilterTimeValue("2026-04-20")

        assertTrue(parsed != null)
        assertTrue(
            matchesTimeOperator(
                sourceMillis = 1_776_643_200_000L,
                expected = parsed!!,
                operator = ResultsFilterTimeOperator.OnDate
            )
        )
        assertTrue(
            matchesTimeOperator(
                sourceMillis = 1_776_729_599_999L,
                expected = parsed,
                operator = ResultsFilterTimeOperator.OnDate
            )
        )
        assertFalse(
            matchesTimeOperator(
                sourceMillis = 1_776_729_600_000L,
                expected = parsed,
                operator = ResultsFilterTimeOperator.OnDate
            )
        )
    }

    @Test
    fun modifiedTimeRuleMatchesEnteredUtcTimePrecision() {
        val parsedMinute = parseResultsFilterTimeValue("2026-04-20 13:45")
        val parsedSecond = parseResultsFilterTimeValue("2026-04-20 13:45:30")

        assertTrue(parsedMinute != null)
        assertTrue(parsedSecond != null)
        assertTrue(
            matchesTimeOperator(
                sourceMillis = 1_776_692_700_000L,
                expected = parsedMinute!!,
                operator = ResultsFilterTimeOperator.OnDate
            )
        )
        assertFalse(
            matchesTimeOperator(
                sourceMillis = 1_776_692_760_000L,
                expected = parsedMinute,
                operator = ResultsFilterTimeOperator.OnDate
            )
        )
        assertTrue(
            matchesTimeOperator(
                sourceMillis = 1_776_692_730_000L,
                expected = parsedSecond!!,
                operator = ResultsFilterTimeOperator.OnDate
            )
        )
        assertFalse(
            matchesTimeOperator(
                sourceMillis = 1_776_692_731_000L,
                expected = parsedSecond,
                operator = ResultsFilterTimeOperator.OnDate
            )
        )
    }

    @Test
    fun hasActiveRulesIgnoresIncompleteOrDisabledRules() {
        val definition = ResultsFilterDefinition(
            clusters = listOf(
                ResultsFilterCluster(
                    id = "cluster_1",
                    name = "Draft",
                    rules = listOf(
                        ResultsFilterRule(
                            id = "rule_1",
                            enabled = false,
                            target = ResultsFilterTarget.FileName,
                            value = "sample"
                        ),
                        ResultsFilterRule(
                            id = "rule_2",
                            target = ResultsFilterTarget.GroupItemCount,
                            value = ""
                        )
                    )
                )
            )
        )

        assertFalse(definition.hasActiveRules())
        assertEquals("No filters", summarizeResultsFilter(definition))
    }

    @Test
    fun hasActiveRulesTreatsSameFolderRuleAsConfiguredWithoutValue() {
        val definition = ResultsFilterDefinition(
            clusters = listOf(
                ResultsFilterCluster(
                    id = "cluster_1",
                    name = "Same folder",
                    rules = listOf(
                        ResultsFilterRule(
                            id = "rule_1",
                            target = ResultsFilterTarget.SameFolder
                        )
                    )
                )
            )
        )

        assertTrue(definition.hasActiveRules())
        assertEquals("1 active rule", summarizeResultsFilter(definition))
    }

    @Test
    fun summarizeResultsFilterReportsActiveClusterAndRuleCounts() {
        val definition = ResultsFilterDefinition(
            clusters = listOf(
                ResultsFilterCluster(
                    id = "cluster_1",
                    name = "One",
                    rules = listOf(
                        ResultsFilterRule(
                            id = "rule_1",
                            target = ResultsFilterTarget.FileName,
                            value = "sample"
                        ),
                        ResultsFilterRule(
                            id = "rule_2",
                            target = ResultsFilterTarget.FolderPath,
                            value = "camera"
                        )
                    )
                ),
                ResultsFilterCluster(
                    id = "cluster_2",
                    name = "Two",
                    rules = listOf(
                        ResultsFilterRule(
                            id = "rule_3",
                            target = ResultsFilterTarget.GroupItemCount,
                            value = "4"
                        )
                    )
                )
            )
        )

        assertEquals("2 clusters · 3 rules", summarizeResultsFilter(definition))
    }

    @Test
    fun fileNameAndFolderHelpersNormalizeSeparators() {
        assertEquals("image.png", fileNameFromPath("C:\\Users\\test\\image.png"))
        assertEquals("C:/Users/test", folderPathFromPath("C:\\Users\\test\\image.png"))
    }

    @Test
    fun resultsFilterDefinitionJsonRoundTripPreservesConfiguredRules() {
        val definition = ResultsFilterDefinition(
            clusters = listOf(
                ResultsFilterCluster(
                    id = "cluster_4",
                    name = "Saved",
                    enabled = true,
                    mode = ResultsFilterClusterMode.Any,
                    rules = listOf(
                        ResultsFilterRule(
                            id = "rule_7",
                            enabled = true,
                            target = ResultsFilterTarget.GroupItemCount,
                            countOperator = ResultsFilterCountOperator.AtMost,
                            value = "9"
                        ),
                        ResultsFilterRule(
                            id = "rule_8",
                            enabled = false,
                            target = ResultsFilterTarget.FileName,
                            textOperator = ResultsFilterTextOperator.EndsWith,
                            value = ".jpg"
                        ),
                        ResultsFilterRule(
                            id = "rule_9",
                            target = ResultsFilterTarget.ModifiedTime,
                            timeOperator = ResultsFilterTimeOperator.OnOrBefore,
                            value = "2026-04-20 12:30"
                        ),
                        ResultsFilterRule(
                            id = "rule_10",
                            target = ResultsFilterTarget.SameFileSize
                        ),
                        ResultsFilterRule(
                            id = "rule_11",
                            target = ResultsFilterTarget.DurationFromAverage,
                            durationToleranceSeconds = "2",
                            durationToleranceMilliseconds = "375"
                        ),
                        ResultsFilterRule(
                            id = "rule_12",
                            target = ResultsFilterTarget.SameResolution
                        )
                    )
                )
            )
        )

        val restored = resultsFilterDefinitionFromJson(
            resultsFilterDefinitionToJson(definition)
        )

        assertEquals(definition, restored)
    }

    @Test
    fun resultsFilterDefinitionFromJsonReturnsEmptyWhenJsonIsInvalid() {
        val restored = resultsFilterDefinitionFromJson("{invalid")

        assertEquals(ResultsFilterDefinition(), restored)
    }

    @Test
    fun resultsFilterDefinitionFromJsonAdvancesIdsBeyondPersistedOnes() {
        resultsFilterDefinitionFromJson(
            resultsFilterDefinitionToJson(
                ResultsFilterDefinition(
                    clusters = listOf(
                        ResultsFilterCluster(
                            id = "cluster_12",
                            name = "Saved",
                            rules = listOf(
                                ResultsFilterRule(
                                    id = "rule_21",
                                    target = ResultsFilterTarget.FileName,
                                    value = "keep"
                                )
                            )
                        )
                    )
                )
            )
        )

        val cluster = createResultsFilterCluster()
        val rule = createResultsFilterRule()

        assertTrue(cluster.id.startsWith("cluster_"))
        assertTrue(rule.id.startsWith("rule_"))
        assertTrue(cluster.id.substringAfterLast('_').toLong() > 12L)
        assertTrue(rule.id.substringAfterLast('_').toLong() > 21L)
    }

    private fun pagedFilterResult(
        definition: ResultsFilterDefinition,
        group: DuplicateGroupEntity,
        members: List<FileMetadata>,
        pageSize: Int
    ): Boolean {
        return matchesResultsFilterPagedMembers(
            definition = definition,
            group = group,
            memberPages = { pagedMembers(members, pageSize) }
        )
    }

    private fun pagedMembers(
        members: List<FileMetadata>,
        pageSize: Int
    ): Sequence<List<FileMetadata>> {
        return sequence {
            var offset = 0
            while (offset < members.size) {
                val page = members.drop(offset).take(pageSize)
                if (page.isEmpty()) break
                yield(page)
                offset += page.size
            }
        }
    }

    private fun file(
        path: String,
        modified: Long = 1L,
        sizeBytes: Long = 10L,
        durationMillis: Long? = null,
        widthPixels: Int? = null,
        heightPixels: Int? = null
    ): FileMetadata {
        return FileMetadata(
            path = path,
            normalizedPath = path,
            sizeBytes = sizeBytes,
            lastModifiedMillis = modified,
            hashHex = "hash",
            durationMillis = durationMillis,
            widthPixels = widthPixels,
            heightPixels = heightPixels
        )
    }

    private fun durationAverageDefinition(
        seconds: String,
        milliseconds: String
    ): ResultsFilterDefinition {
        return ResultsFilterDefinition(
            clusters = listOf(
                ResultsFilterCluster(
                    id = "cluster_duration",
                    name = "Duration average",
                    rules = listOf(
                        ResultsFilterRule(
                            id = "rule_duration",
                            target = ResultsFilterTarget.DurationFromAverage,
                            durationToleranceSeconds = seconds,
                            durationToleranceMilliseconds = milliseconds
                        )
                    )
                )
            )
        )
    }

    private fun group(fileCount: Int): DuplicateGroupEntity {
        return DuplicateGroupEntity(
            sizeBytes = 10L,
            hashHex = "hash",
            fileCount = fileCount,
            totalBytes = 10L * fileCount,
            updatedAtMillis = 1L
        )
    }
}
