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

    private fun file(path: String): FileMetadata {
        return FileMetadata(
            path = path,
            normalizedPath = path,
            sizeBytes = 10L,
            lastModifiedMillis = 1L,
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
