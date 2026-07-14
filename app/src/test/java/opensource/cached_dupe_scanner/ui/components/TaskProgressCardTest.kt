package opensource.cached_dupe_scanner.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import kotlin.math.roundToInt
import opensource.cached_dupe_scanner.tasks.TaskArea
import opensource.cached_dupe_scanner.tasks.TaskKind
import opensource.cached_dupe_scanner.tasks.TaskSnapshot
import opensource.cached_dupe_scanner.tasks.TaskStatus
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
class TaskProgressCardTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun progressFractionUsesProcessedAndTotal() {
        val task = taskSnapshot(processed = 25, total = 100, indeterminate = false)

        assertEquals(0.25f, task.progressFraction(), 0.001f)
    }

    @Test
    fun progressFractionClampsInvalidValues() {
        val overProcessed = taskSnapshot(processed = 150, total = 100, indeterminate = false)
        val missingTotal = taskSnapshot(processed = 50, total = null, indeterminate = false)
        val negativeProcessed = taskSnapshot(processed = -1, total = 100, indeterminate = false)
        val zeroTotal = taskSnapshot(processed = 1, total = 0, indeterminate = false)

        assertEquals(1f, overProcessed.progressFraction(), 0.001f)
        assertEquals(0f, missingTotal.progressFraction(), 0.001f)
        assertEquals(0f, negativeProcessed.progressFraction(), 0.001f)
        assertEquals(0f, zeroTotal.progressFraction(), 0.001f)
    }

    @Test
    fun incompleteLargeProgressDoesNotRoundUpToComplete() {
        val task = taskSnapshot(
            processed = Int.MAX_VALUE - 1,
            total = Int.MAX_VALUE,
            indeterminate = false
        )

        assertTrue(task.progressFraction() < 1f)
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun progressEndpointsUseExpectedRightEdgeColors() {
        val activeColor = Color.Red
        val trackColor = Color.Green
        val task = mutableStateOf(taskSnapshot(processed = 0, total = 100, indeterminate = false))
        composeRule.setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = activeColor,
                    secondaryContainer = trackColor
                )
            ) {
                TaskProgressCard(
                    task = task.value,
                    onCancel = {},
                    showCancelWhenDisabled = false
                )
            }
        }

        val indicator = composeRule.onNode(
            matcher = SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo),
            useUnmergedTree = true
        )
        val indicatorBounds = indicator.fetchSemanticsNode().boundsInRoot
        fun rightEdgeColor(): Color {
            lateinit var bitmap: Bitmap
            composeRule.runOnIdle {
                val activity = ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(Stage.RESUMED)
                    .single()
                val content = activity.findViewById<View>(android.R.id.content)
                bitmap = Bitmap.createBitmap(content.width, content.height, Bitmap.Config.ARGB_8888)
                content.draw(Canvas(bitmap))
            }
            val sampleX = (indicatorBounds.right - 2f).roundToInt().coerceIn(0, bitmap.width - 1)
            val sampleY = indicatorBounds.center.y.roundToInt().coerceIn(0, bitmap.height - 1)
            return Color(bitmap.getPixel(sampleX, sampleY)).also { bitmap.recycle() }
        }

        val emptyRightEdge = rightEdgeColor()
        composeRule.runOnIdle { task.value = task.value.copy(processed = 50) }
        val partialRightEdge = rightEdgeColor()
        composeRule.runOnIdle { task.value = task.value.copy(processed = 100) }
        val completeRightEdge = rightEdgeColor()

        assertTrue(emptyRightEdge.green > emptyRightEdge.red)
        assertTrue(partialRightEdge.green > partialRightEdge.red)
        assertTrue(completeRightEdge.red > completeRightEdge.green)
    }

    @Test
    fun taskProgressCardShowsDeterminateTaskTextAndCancel() {
        composeRule.setContent {
            TaskProgressCard(
                task = taskSnapshot(
                    title = "Scanning",
                    detail = "Hashing files",
                    processed = 1,
                    total = 2,
                    indeterminate = false
                ),
                onCancel = {},
                cancelText = "Stop scan"
            )
        }

        composeRule.onNodeWithText("Scanning").assertExists()
        composeRule.onNodeWithText("Hashing files").assertExists()
        composeRule.onNodeWithText("Stop scan").assertExists()
    }

    @Test
    fun taskProgressCardShowsIndeterminateTaskWithoutTotal() {
        composeRule.setContent {
            TaskProgressCard(
                task = taskSnapshot(
                    title = "Cleaning",
                    detail = "Checking cache",
                    processed = null,
                    total = null,
                    indeterminate = true
                ),
                onCancel = {}
            )
        }

        composeRule.onNodeWithText("Cleaning").assertExists()
        composeRule.onNodeWithText("Checking cache").assertExists()
    }

    @Test
    fun taskProgressCardShowsCustomCurrentPathAndExtraContent() {
        composeRule.setContent {
            TaskProgressCard(
                task = taskSnapshot(currentPath = "root/file.txt"),
                onCancel = {},
                currentPathText = { path -> "Current: $path" },
                extraContent = { Text("Scanned: 1 / 2") }
            )
        }

        composeRule.onNodeWithText("Current: root/file.txt").assertExists()
        composeRule.onNodeWithText("Scanned: 1 / 2").assertExists()
    }

    @Test
    fun taskProgressCardCanHideDisabledCancelButton() {
        composeRule.setContent {
            TaskProgressCard(
                task = taskSnapshot(isCancellable = false),
                onCancel = {},
                showCancelWhenDisabled = false
            )
        }

        assertEquals(0, composeRule.onAllNodesWithText("Cancel").fetchSemanticsNodes().size)
    }

    @Test
    fun taskProgressCardInvokesCancel() {
        var cancelled = false
        composeRule.setContent {
            TaskProgressCard(
                task = taskSnapshot(),
                onCancel = { cancelled = true },
                cancelText = "Cancel running task"
            )
        }

        composeRule.onNodeWithText("Cancel running task").performClick()

        assertEquals(true, cancelled)
    }

    private fun taskSnapshot(
        title: String = "Task",
        detail: String = "Running",
        currentPath: String? = null,
        processed: Int? = 1,
        total: Int? = 2,
        indeterminate: Boolean = false,
        isCancellable: Boolean = true
    ): TaskSnapshot {
        return TaskSnapshot(
            area = TaskArea.Scan,
            kind = TaskKind.ScanTarget,
            title = title,
            detail = detail,
            currentPath = currentPath,
            processed = processed,
            total = total,
            indeterminate = indeterminate,
            startedAt = 123,
            isCancellable = isCancellable,
            status = TaskStatus.Running
        )
    }
}
