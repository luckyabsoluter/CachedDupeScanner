package opensource.cached_dupe_scanner.ui.home

import android.content.Context
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import opensource.cached_dupe_scanner.core.MAX_SCAN_WORKER_COUNT
import opensource.cached_dupe_scanner.storage.AppSettingsStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsScreenInteractionTest {
    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        clearSettings()
    }

    @After
    fun tearDown() {
        clearSettings()
    }

    @Test
    fun scanWorkerInputPersistsAppliedCount() {
        val store = AppSettingsStore(context)
        val current = store.load().scanWorkerCount
        val target = if (current < MAX_SCAN_WORKER_COUNT) current + 1 else current - 1
        var settingsChanged = 0
        composeRule.setContent {
            SettingsScreen(
                settingsStore = store,
                onBack = {},
                onSettingsChanged = { settingsChanged += 1 },
                modifier = Modifier.height(1_000.dp)
            )
        }

        composeRule.onNodeWithTag("scan-worker-count-input")
            .performTextReplacement(target.toString())
        composeRule.onNodeWithTag("scan-worker-count-apply").performClick()

        composeRule.runOnIdle {
            assertEquals(target, store.load().scanWorkerCount)
            assertEquals(1, settingsChanged)
        }
    }

    private fun clearSettings() {
        context.getSharedPreferences("cached_dupe_scanner", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }
}
