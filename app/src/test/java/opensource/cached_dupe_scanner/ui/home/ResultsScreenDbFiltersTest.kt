package opensource.cached_dupe_scanner.ui.home

import opensource.cached_dupe_scanner.cache.DuplicateGroupEntity
import opensource.cached_dupe_scanner.core.FileMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResultsScreenDbFiltersTest {
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

    private fun file(
        path: String,
        modified: Long = 1L
    ): FileMetadata {
        return FileMetadata(
            path = path,
            normalizedPath = path,
            sizeBytes = 10L,
            lastModifiedMillis = modified,
            hashHex = "hash"
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
