package opensource.cached_dupe_scanner.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.RuntimeEnvironment
import opensource.cached_dupe_scanner.notifications.TaskNotificationController
import opensource.cached_dupe_scanner.storage.TrashRunSummary
import opensource.cached_dupe_scanner.tasks.TaskArea
import opensource.cached_dupe_scanner.tasks.TaskCoordinator

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TrashCancelEndToEndTest {
    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var appScope: CoroutineScope

    @Before
    fun setUp() {
        appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    }

    @After
    fun tearDown() {
        TrashTaskHarnessState.currentJobState = null
        appScope.cancel()
    }

    @Test
    fun cancellingEmptyTrashClearsRunningUi() {
        val context = RuntimeEnvironment.getApplication()
        val taskCoordinator = TaskCoordinator()
        val notificationController = TaskNotificationController(context)
        val enteredTask = AtomicBoolean(false)

        composeRule.setContent {
            TrashTaskHarness(taskCoordinator = taskCoordinator)
        }

        composeRule.runOnIdle {
            startEmptyTrashTask(
                scope = appScope,
                taskCoordinator = taskCoordinator,
                notificationController = notificationController,
                onJobChanged = TrashTaskHarnessState::updateJob,
                resetAndLoad = {},
                runEmptyTrash = { shouldContinue, _ ->
                    enteredTask.set(true)
                    while (shouldContinue()) {
                        Thread.sleep(10)
                    }
                    TrashRunSummary(
                        total = 6,
                        processed = 2,
                        deleted = 2,
                        failed = 0,
                        cancelled = true,
                        currentPath = "blocked-entry"
                    )
                }
            )
        }

        composeRule.waitUntil(5_000) { enteredTask.get() }
        composeRule.onNodeWithText("Emptying trash").fetchSemanticsNode()
        composeRule.runOnIdle {
            assertTrue(taskCoordinator.requestCancel(TaskArea.Trash))
        }

        composeRule.waitUntil(5_000) {
            !taskCoordinator.isAreaBusy(TaskArea.Trash)
        }
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("Idle").fetchSemanticsNodes().isNotEmpty()
        }

        assertTrue(
            composeRule.onAllNodesWithText("Emptying trash").fetchSemanticsNodes().isEmpty()
        )
    }
}

private object TrashTaskHarnessState {
    var currentJobState: ((Job?) -> Unit)? = null

    fun updateJob(job: Job?) {
        currentJobState?.invoke(job)
    }
}

@Composable
private fun TrashTaskHarness(taskCoordinator: TaskCoordinator) {
    val currentJob = remember { mutableStateOf<Job?>(null) }
    TrashTaskHarnessState.currentJobState = { job -> currentJob.value = job }
    val activeTask = taskCoordinator.activeTask(TaskArea.Trash)
    val isBusy = activeTask != null || currentJob.value != null

    if (activeTask != null) {
        androidx.compose.material3.Text(activeTask.title)
    }
    androidx.compose.material3.Text(if (isBusy) "Busy" else "Idle")
}
