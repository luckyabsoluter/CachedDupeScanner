package opensource.cached_dupe_scanner.ui.home

import android.content.Context
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import coil.ImageLoader
import opensource.cached_dupe_scanner.core.FileMetadata
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DuplicateGroupDetailContentTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun longPressingMemberEntersSelectionModeWithThatMemberSelected() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val first = file("/storage/emulated/0/Documents/a.txt")
        val second = file("/storage/emulated/0/Documents/b.txt")

        composeRule.setContent {
            EagerDuplicateGroupDetailContent(
                title = "Group detail",
                memberCount = 2,
                totalBytes = 20L,
                summaryLines = emptyList(),
                members = listOf(first, second),
                deletedPaths = emptySet(),
                imageLoader = ImageLoader.Builder(context).build(),
                keepLoadedThumbnailsInMemory = false,
                rememberedPreviewCache = mutableStateMapOf<String, ImageBitmap>(),
                previewMemoryKey = "group:test",
                previewHeight = 100.dp,
                onDeleteFile = null
            )
        }

        composeRule.onNodeWithTag("duplicate-member:${first.normalizedPath}")
            .performTouchInput { longClick() }

        composeRule.onNodeWithText("1 selected").assertExists()
        composeRule.onNodeWithText("Delete selected").assertExists()
    }

    private fun file(path: String): FileMetadata {
        return FileMetadata(
            path = path,
            normalizedPath = path,
            sizeBytes = 10L,
            lastModifiedMillis = 1L
        )
    }
}
