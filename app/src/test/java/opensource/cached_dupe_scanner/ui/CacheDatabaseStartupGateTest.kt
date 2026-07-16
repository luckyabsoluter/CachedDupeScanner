package opensource.cached_dupe_scanner.ui

import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import opensource.cached_dupe_scanner.cache.CacheDatabaseStartupPlan
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CacheDatabaseStartupGateTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun upgradeRequiresUserActionAndBlocksMainContentUntilCompletion() {
        val openCalls = AtomicInteger(0)
        val releaseUpgrade = CompletableDeferred<Unit>()
        val plan = CacheDatabaseStartupPlan.UpgradeRequired(
            fromVersion = 21,
            toVersion = 24,
            recoveryRequired = true
        )
        composeRule.setContent {
            val database = cacheDatabaseStartupGate(
                inspect = { plan },
                openDatabase = { _, onProgress ->
                    openCalls.incrementAndGet()
                    onProgress("Preserving similarity results")
                    releaseUpgrade.await()
                    "database"
                },
                onClose = {}
            )
            if (database != null) Text("Main content")
        }

        composeRule.onNodeWithText("Database upgrade required").assertExists()
        composeRule.onNodeWithText("Version 21 to 24").assertExists()
        composeRule.onNodeWithText("Main content", useUnmergedTree = true).assertDoesNotExist()
        assertEquals(0, openCalls.get())

        composeRule.onNodeWithText("Upgrade database").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { openCalls.get() == 1 }

        composeRule.onNodeWithText("Upgrading database").assertExists()
        composeRule.onNodeWithText("Preserving similarity results").assertExists()
        composeRule.onNodeWithText("Upgrade database").assertDoesNotExist()
        composeRule.onNodeWithText("Main content", useUnmergedTree = true).assertDoesNotExist()

        releaseUpgrade.complete(Unit)
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Main content", useUnmergedTree = true).assertExists()
        assertEquals(1, openCalls.get())
    }
}
