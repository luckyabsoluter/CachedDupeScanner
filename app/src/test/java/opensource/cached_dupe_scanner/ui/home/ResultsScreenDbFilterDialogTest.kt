package opensource.cached_dupe_scanner.ui.home

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ResultsScreenDbFilterDialogTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun resultMemberRuleBranchesToAnyOrAllMembers() {
        val initial = ResultsFilterDefinition(
            clusters = listOf(
                ResultsFilterCluster(
                    id = "cluster_1",
                    name = "Names",
                    rules = listOf(
                        ResultsFilterRule(
                            id = "rule_1",
                            target = ResultsFilterTarget.FileName,
                            value = "keep"
                        )
                    )
                )
            )
        )
        var updated = initial
        composeRule.setContent {
            val definition = remember { mutableStateOf(initial) }
            ResultsFilterScreen(
                definition = definition.value,
                onDefinitionChange = { value ->
                    definition.value = value
                    updated = value
                },
                onBack = {},
                onApply = {}
            )
        }

        composeRule.onNodeWithTag("filter-rule:rule_1")
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithText("Member match").performScrollTo()
        composeRule.onNodeWithText("Any member").fetchSemanticsNode()
        composeRule.onNodeWithText("All members").performScrollTo().performClick()

        composeRule.runOnIdle {
            assertEquals(
                ResultsFilterMemberMatchMode.All,
                updated.clusters.single().rules.single().memberMatchMode
            )
        }
    }

    @Test
    @Config(sdk = [34], qualifiers = "w600dp-h3000dp")
    fun clusterRulesRenderAsSeparateLabeledSections() {
        val initial = ResultsFilterDefinition(
            clusters = listOf(
                ResultsFilterCluster(
                    id = "cluster_1",
                    name = "Paths",
                    rules = listOf(
                        ResultsFilterRule(
                            id = "rule_1",
                            target = ResultsFilterTarget.FileName,
                            value = "sample"
                        ),
                        ResultsFilterRule(
                            id = "rule_2",
                            target = ResultsFilterTarget.FolderPath,
                            value = "/archive"
                        )
                    )
                )
            )
        )
        composeRule.setContent {
            ResultsFilterScreen(
                definition = initial,
                onDefinitionChange = {},
                onBack = {},
                onApply = {}
            )
        }

        composeRule.onNodeWithText("Rule 1 - File name").fetchSemanticsNode()
        composeRule.onNodeWithText("Rule 2 - Folder").fetchSemanticsNode()
        val firstBounds = composeRule.onNodeWithTag("filter-rule-container:rule_1")
            .fetchSemanticsNode()
            .boundsInRoot
        val secondBounds = composeRule.onNodeWithTag("filter-rule-container:rule_2")
            .fetchSemanticsNode()
            .boundsInRoot

        assertTrue(firstBounds.height > 0f)
        assertTrue(secondBounds.height > 0f)
        assertTrue(firstBounds.bottom < secondBounds.top)
    }

    @Test
    @Config(sdk = [34], qualifiers = "w600dp-h3000dp")
    fun clusterRulesExpandOneEditorAtATime() {
        val initial = ResultsFilterDefinition(
            clusters = listOf(
                ResultsFilterCluster(
                    id = "cluster_1",
                    name = "Paths",
                    rules = listOf(
                        ResultsFilterRule(
                            id = "rule_1",
                            target = ResultsFilterTarget.FileName,
                            value = "sample"
                        ),
                        ResultsFilterRule(
                            id = "rule_2",
                            target = ResultsFilterTarget.FolderPath,
                            value = "/archive"
                        )
                    )
                )
            )
        )
        composeRule.setContent {
            ResultsFilterScreen(
                definition = initial,
                onDefinitionChange = {},
                onBack = {},
                onApply = {}
            )
        }

        assertTrue(composeRule.onAllNodesWithText("File name text").fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithText("Folder text").fetchSemanticsNodes().isEmpty())

        composeRule.onNodeWithTag("filter-rule-enabled:rule_1").performClick()
        assertTrue(composeRule.onAllNodesWithText("File name text").fetchSemanticsNodes().isEmpty())

        composeRule.onNodeWithTag("filter-rule:rule_1").performClick()
        composeRule.onNodeWithText("File name text").fetchSemanticsNode()
        assertTrue(composeRule.onAllNodesWithText("Folder text").fetchSemanticsNodes().isEmpty())

        composeRule.onNodeWithTag("filter-rule:rule_2").performClick()
        assertTrue(composeRule.onAllNodesWithText("File name text").fetchSemanticsNodes().isEmpty())
        composeRule.onNodeWithText("Folder text").fetchSemanticsNode()
    }
}
