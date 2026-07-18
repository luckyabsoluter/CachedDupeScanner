package opensource.cached_dupe_scanner.ui.home

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
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
    @Config(sdk = [34], qualifiers = "w600dp-h3000dp")
    fun collapsedClustersRestoreWhenFilterStateOwnerIsRecreated() {
        val initial = ResultsFilterDefinition(
            clusters = listOf(
                ResultsFilterCluster(
                    id = "cluster_1",
                    name = "Names",
                    rules = listOf(
                        ResultsFilterRule(
                            id = "rule_1",
                            target = ResultsFilterTarget.FileName,
                            value = "sample"
                        )
                    )
                ),
                ResultsFilterCluster(
                    id = "cluster_2",
                    name = "Folders",
                    rules = listOf(
                        ResultsFilterRule(
                            id = "rule_2",
                            target = ResultsFilterTarget.FolderPath,
                            value = "/archive"
                        )
                    )
                )
            )
        )
        val stateOwnerVersion = mutableStateOf(0)
        val persistedCollapsedClusterIds = mutableStateOf<Set<String>>(emptySet())
        composeRule.setContent {
            key(stateOwnerVersion.value) {
                val filterScreenOpen = remember { mutableStateOf(true) }
                val clusterExpansionState = rememberFilterClusterExpansionState(
                    initialCollapsedClusterIds = persistedCollapsedClusterIds.value,
                    onCollapsedClusterIdsChange = { collapsedClusterIds ->
                        persistedCollapsedClusterIds.value = collapsedClusterIds
                    }
                )
                if (filterScreenOpen.value) {
                    ResultsFilterScreen(
                        definition = initial,
                        onDefinitionChange = {},
                        onBack = { filterScreenOpen.value = false },
                        onApply = {},
                        clusterExpansionState = clusterExpansionState
                    )
                } else {
                    Button(
                        onClick = { filterScreenOpen.value = true },
                        modifier = Modifier.testTag("reopen-filter")
                    ) {
                        Text("Reopen filter")
                    }
                }
            }
        }

        composeRule.onNodeWithTag("filter-cluster:cluster_1").performClick()
        composeRule.onNodeWithTag("filter-cluster:cluster_2").performClick()
        composeRule.onNodeWithContentDescription("Expand cluster 1").assertExists()
        composeRule.onNodeWithContentDescription("Expand cluster 2").assertExists()
        composeRule.runOnIdle {
            assertEquals(setOf("cluster_1", "cluster_2"), persistedCollapsedClusterIds.value)
        }

        composeRule.onNodeWithText("Cancel").performScrollTo().performClick()
        composeRule.onNodeWithTag("reopen-filter").performClick()

        composeRule.onNodeWithContentDescription("Expand cluster 1").assertExists()
        composeRule.onNodeWithContentDescription("Expand cluster 2").assertExists()

        composeRule.runOnIdle { stateOwnerVersion.value += 1 }

        composeRule.onNodeWithContentDescription("Expand cluster 1").assertExists()
        composeRule.onNodeWithContentDescription("Expand cluster 2").assertExists()
        composeRule.onNodeWithTag("filter-cluster:cluster_1").performClick()
        composeRule.runOnIdle {
            assertEquals(setOf("cluster_2"), persistedCollapsedClusterIds.value)
        }
    }

    @Test
    @Config(sdk = [34], qualifiers = "w600dp-h3000dp")
    fun clusterCanCollapseAndRestoreItsExpandedRule() {
        val initial = ResultsFilterDefinition(
            clusters = listOf(
                ResultsFilterCluster(
                    id = "cluster_1",
                    name = "Paths",
                    mode = ResultsFilterClusterMode.All,
                    rules = listOf(
                        ResultsFilterRule(
                            id = "rule_1",
                            target = ResultsFilterTarget.FileName,
                            value = "sample"
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

        composeRule.onNodeWithContentDescription("Collapse cluster 1").assertExists()
        composeRule.onNodeWithText("Cluster 1 - Paths").assertExists()
        composeRule.onNodeWithText("1 rule | Match all").assertExists()
        composeRule.onNodeWithTag("filter-cluster-enabled:cluster_1").performClick()
        composeRule.onNodeWithContentDescription("Collapse cluster 1").assertExists()
        composeRule.onNodeWithTag("filter-rule:rule_1").performClick()
        composeRule.onNodeWithText("File name text").assertExists()

        composeRule.onNodeWithTag("filter-cluster:cluster_1").performClick()

        composeRule.onNodeWithContentDescription("Expand cluster 1").assertExists()
        assertTrue(
            composeRule.onAllNodesWithTag("filter-rule:rule_1")
                .fetchSemanticsNodes()
                .isEmpty()
        )
        assertTrue(composeRule.onAllNodesWithText("File name text").fetchSemanticsNodes().isEmpty())

        composeRule.onNodeWithTag("filter-cluster:cluster_1").performClick()

        composeRule.onNodeWithContentDescription("Collapse cluster 1").assertExists()
        composeRule.onNodeWithTag("filter-rule:rule_1").assertExists()
        composeRule.onNodeWithText("File name text").assertExists()
    }

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
    fun targetChoicesOpenOnlyAfterClickingCurrentTarget() {
        val initial = ResultsFilterDefinition(
            clusters = listOf(
                ResultsFilterCluster(
                    id = "cluster_1",
                    name = "Target menu",
                    rules = listOf(
                        ResultsFilterRule(
                            id = "rule_1",
                            target = ResultsFilterTarget.FileName,
                            value = "sample"
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
        assertTrue(composeRule.onAllNodesWithText("Group count").fetchSemanticsNodes().isEmpty())

        composeRule.onNodeWithText("File name").performClick()
        composeRule.onNodeWithText("Group count").performClick()

        composeRule.runOnIdle {
            val rule = updated.clusters.single().rules.single()
            assertEquals(ResultsFilterTarget.GroupItemCount, rule.target)
            assertEquals("", rule.value)
        }
        composeRule.onNodeWithText("Item count").fetchSemanticsNode()
        assertTrue(composeRule.onAllNodesWithText("File name").fetchSemanticsNodes().isEmpty())
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
