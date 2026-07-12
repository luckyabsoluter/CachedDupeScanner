package opensource.cached_dupe_scanner.ui.home

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Assert.assertEquals
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
}
