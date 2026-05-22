package opensource.cached_dupe_scanner.ui.home

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import opensource.cached_dupe_scanner.core.FileMetadata
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FileDetailsDialogTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun fileDetailsShowsHashValueWhenPresent() {
        composeRule.setContent {
            FileDetailsDialogUi(
                file = file(hashHex = "abc123"),
                showName = true,
                onOpen = {},
                onDeleteRequest = {},
                onDismiss = {}
            )
        }

        composeRule.onNodeWithText("Hash: abc123").assertExists()
    }

    @Test
    fun fileDetailsShowsNoHashWhenMissing() {
        composeRule.setContent {
            FileDetailsDialogUi(
                file = file(hashHex = null),
                showName = true,
                onOpen = {},
                onDeleteRequest = {},
                onDismiss = {}
            )
        }

        composeRule.onNodeWithText("Hash: No hash").assertExists()
    }

    @Test
    fun fileDetailsShowsNoHashWhenBlank() {
        composeRule.setContent {
            FileDetailsDialogUi(
                file = file(hashHex = " "),
                showName = true,
                onOpen = {},
                onDeleteRequest = {},
                onDismiss = {}
            )
        }

        composeRule.onNodeWithText("Hash: No hash").assertExists()
    }

    private fun file(hashHex: String?): FileMetadata {
        return FileMetadata(
            path = "/storage/emulated/0/DCIM/a.jpg",
            normalizedPath = "/storage/emulated/0/DCIM/a.jpg",
            sizeBytes = 10L,
            lastModifiedMillis = 1_700_000_000_000L,
            hashHex = hashHex
        )
    }
}
