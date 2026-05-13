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
class ConfirmationDialogTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun confirmationDialogShowsTextAndInvokesActions() {
        var confirmed = 0
        var dismissed = 0
        composeRule.setContent {
            ConfirmationDialog(
                title = "Remove item?",
                text = "This removes the selected item.",
                confirmText = "Remove",
                onConfirm = { confirmed += 1 },
                onDismissRequest = { dismissed += 1 }
            )
        }

        composeRule.onNodeWithText("Remove item?").assertExists()
        composeRule.onNodeWithText("This removes the selected item.").assertExists()
        composeRule.onNodeWithText("Remove").performClick()
        composeRule.onNodeWithText("Cancel").performClick()

        assertEquals(1, confirmed)
        assertEquals(1, dismissed)
    }

    @Test
    fun confirmationDialogUsesCustomDismissText() {
        var dismissed = 0
        composeRule.setContent {
            ConfirmationDialog(
                title = "Close dialog?",
                text = "This closes the dialog.",
                confirmText = "Close",
                dismissText = "Keep open",
                onConfirm = {},
                onDismissRequest = { dismissed += 1 }
            )
        }

        composeRule.onNodeWithText("Keep open").performClick()

        assertEquals(1, dismissed)
    }
}
