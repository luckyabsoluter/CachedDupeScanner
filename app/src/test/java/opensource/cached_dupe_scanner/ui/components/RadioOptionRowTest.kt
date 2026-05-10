package opensource.cached_dupe_scanner.ui.components

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RadioOptionRowTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun radioOptionRowShowsLabelAndInvokesSelection() {
        var selected = "A"
        var clicked: String? = null
        composeRule.setContent {
            RadioOptionRow(
                option = "B",
                selected = selected,
                label = "Option B",
                onSelect = { option -> clicked = option }
            )
        }

        composeRule.onNodeWithText("Option B").assertExists()
        composeRule.onNodeWithText("Option B").performClick()

        assertEquals("A", selected)
        assertEquals("B", clicked)
    }
}
