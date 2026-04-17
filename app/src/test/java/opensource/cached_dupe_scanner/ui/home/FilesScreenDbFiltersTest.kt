package opensource.cached_dupe_scanner.ui.home

import opensource.cached_dupe_scanner.core.FileMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FilesScreenDbFiltersTest {
    @Test
    fun matchesFileFilterUsesFileNameAndFolderRules() {
        val definition = ResultsFilterDefinition(
            clusters = listOf(
                ResultsFilterCluster(
                    id = "cluster_1",
                    name = "Keep camera png",
                    mode = ResultsFilterClusterMode.All,
                    rules = listOf(
                        ResultsFilterRule(
                            id = "rule_1",
                            target = ResultsFilterTarget.FileName,
                            textOperator = ResultsFilterTextOperator.EndsWith,
                            value = ".png"
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

        assertTrue(matchesFileFilter(definition, file("/storage/DCIM/Camera/IMG_0001.PNG")))
        assertFalse(matchesFileFilter(definition, file("/storage/Download/IMG_0001.PNG")))
    }

    @Test
    fun fileFilterSummaryIgnoresUnsupportedTargets() {
        val definition = ResultsFilterDefinition(
            clusters = listOf(
                ResultsFilterCluster(
                    id = "cluster_1",
                    name = "Mixed",
                    rules = listOf(
                        ResultsFilterRule(
                            id = "rule_1",
                            target = ResultsFilterTarget.GroupItemCount,
                            value = "2"
                        ),
                        ResultsFilterRule(
                            id = "rule_2",
                            target = ResultsFilterTarget.FileName,
                            value = "keep"
                        )
                    )
                )
            )
        )

        assertTrue(definition.hasActiveRules(FILE_FILTER_TARGETS))
        assertEquals("1 active rule", summarizeResultsFilter(definition, FILE_FILTER_TARGETS))
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
}
