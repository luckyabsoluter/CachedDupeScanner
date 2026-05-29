package opensource.cached_dupe_scanner.ui.components

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OptionButtonGridTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun optionButtonGridRowsChunksOptionsIntoTwoColumnRows() {
        val rows = optionButtonGridRows(listOf("A", "B", "C", "D", "E"))

        assertEquals(
            listOf(listOf("A", "B"), listOf("C", "D"), listOf("E")),
            rows
        )
    }

    @Test
    fun optionButtonGridRowsUsesCustomColumnCount() {
        val rows = optionButtonGridRows(listOf("A", "B", "C", "D", "E"), columns = 3)

        assertEquals(
            listOf(listOf("A", "B", "C"), listOf("D", "E")),
            rows
        )
    }

    @Test
    fun optionButtonGridRowsRejectsNonPositiveColumnCount() {
        assertThrows(IllegalArgumentException::class.java) {
            optionButtonGridRows(listOf("A"), columns = 0)
        }
    }

    @Test
    fun optionButtonGridShowsOptionsAndInvokesSelection() {
        var selected = "One"
        var clicked: String? = null
        composeRule.setContent {
            OptionButtonGrid(
                options = listOf("One", "Two", "Three"),
                selected = selected,
                label = { it },
                onSelect = { option ->
                    clicked = option
                    selected = option
                }
            )
        }

        composeRule.onNodeWithText("One").assertExists()
        composeRule.onNodeWithText("Two").assertExists()
        composeRule.onNodeWithText("Three").assertExists()

        composeRule.onNodeWithText("Two").performClick()

        assertEquals("Two", clicked)
        assertEquals("Two", selected)
    }
}
