package opensource.cached_dupe_scanner.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import opensource.cached_dupe_scanner.cache.CacheDatabaseStartupPlan
import opensource.cached_dupe_scanner.cache.CacheDatabaseStartupProgress
import opensource.cached_dupe_scanner.ui.theme.CachedDupeScannerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CacheDatabaseStartupGateTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun currentDatabaseOpensWithoutRenderingDatabaseStartupScreen() {
        val inspectResult = CompletableDeferred<CacheDatabaseStartupPlan>()
        val openStarted = CompletableDeferred<Unit>()
        val releaseOpen = CompletableDeferred<Unit>()
        composeRule.setContent {
            val database = cacheDatabaseStartupGate(
                inspect = { inspectResult.await() },
                openDatabase = { _, _ ->
                    openStarted.complete(Unit)
                    releaseOpen.await()
                    "database"
                },
                onClose = {}
            )
            if (database != null) Text("Main content")
        }

        composeRule.onNodeWithText("Checking database").assertDoesNotExist()
        composeRule.onNodeWithText("Database upgrade required").assertDoesNotExist()

        inspectResult.complete(CacheDatabaseStartupPlan.OpenCurrent(existingVersion = 24))
        composeRule.waitUntil(timeoutMillis = 5_000) { openStarted.isCompleted }

        composeRule.onNodeWithText("Opening database").assertDoesNotExist()
        composeRule.onNodeWithText("Upgrading database").assertDoesNotExist()
        composeRule.onNodeWithText("Database upgrade required").assertDoesNotExist()
        composeRule.onNodeWithText("Main content", useUnmergedTree = true).assertDoesNotExist()

        releaseOpen.complete(Unit)
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Main content", useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        composeRule.onNodeWithText("Main content", useUnmergedTree = true).assertExists()
    }

    @Test
    fun currentDatabaseOpenFailureIsNotReportedAsUpgradeFailure() {
        composeRule.setContent {
            cacheDatabaseStartupGate(
                inspect = {
                    CacheDatabaseStartupPlan.OpenCurrent(existingVersion = 24)
                },
                openDatabase = { _, _ -> error("Cannot open current database") },
                onClose = {}
            )
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Database startup failed")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeRule.onNodeWithText("Database startup failed").assertExists()
        composeRule.onNodeWithText("The database could not be opened.").assertExists()
        composeRule.onNodeWithText("Database upgrade failed").assertDoesNotExist()
        composeRule.onNodeWithText("Retry").assertExists()
    }

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
                    onProgress(
                        CacheDatabaseStartupProgress(
                            stage = "Preserving similarity results",
                            processed = 20L,
                            total = 100L
                        )
                    )
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
        composeRule.onNodeWithText("Speed:", substring = true).assertExists()
        composeRule.onNodeWithText("Elapsed:", substring = true).assertExists()
        composeRule.onNodeWithText("Remaining:", substring = true).assertExists()
        composeRule.onNodeWithText("Upgrade database").assertDoesNotExist()
        composeRule.onNodeWithText("Main content", useUnmergedTree = true).assertDoesNotExist()

        releaseUpgrade.complete(Unit)
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Main content", useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        composeRule.onNodeWithText("Main content", useUnmergedTree = true).assertExists()
        assertEquals(1, openCalls.get())
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun databaseUpgradeGatePaintsDarkThemeBackground() {
        val plan = CacheDatabaseStartupPlan.UpgradeRequired(
            fromVersion = 21,
            toVersion = 24,
            recoveryRequired = true
        )
        composeRule.setContent {
            CachedDupeScannerTheme(darkTheme = true, dynamicColor = false) {
                cacheDatabaseStartupGate(
                    inspect = { plan },
                    openDatabase = { _, _ -> error("Upgrade must wait for user action") },
                    onClose = {}
                )
            }
        }
        composeRule.onNodeWithText("Database upgrade required").assertExists()

        lateinit var bitmap: Bitmap
        composeRule.runOnIdle {
            val activity = ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(Stage.RESUMED)
                .single()
            val content = activity.findViewById<View>(android.R.id.content)
            bitmap = Bitmap.createBitmap(content.width, content.height, Bitmap.Config.ARGB_8888)
            content.draw(Canvas(bitmap))
        }
        val background = Color(bitmap.getPixel(1, 1))
        bitmap.recycle()

        assertEquals(1f, background.alpha, 0.001f)
        assertTrue(background.luminance() < 0.2f)
    }
}
