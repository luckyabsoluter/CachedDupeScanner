package opensource.cached_dupe_scanner.ui.home

import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import opensource.cached_dupe_scanner.notifications.TaskNotificationController
import opensource.cached_dupe_scanner.storage.SimilarityClearMode
import opensource.cached_dupe_scanner.storage.SimilarityClearPhase
import opensource.cached_dupe_scanner.storage.SimilarityClearProgress
import opensource.cached_dupe_scanner.storage.SimilarityClearSummary
import opensource.cached_dupe_scanner.tasks.TaskArea
import opensource.cached_dupe_scanner.tasks.TaskCoordinator
import opensource.cached_dupe_scanner.tasks.TaskKind
import opensource.cached_dupe_scanner.tasks.TaskStatus
import opensource.cached_dupe_scanner.ui.home.similarity.startSimilaritySettingClearTask
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SimilaritySettingClearTaskTest {
    private lateinit var appScope: CoroutineScope

    @Before
    fun setUp() {
        appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    @After
    fun tearDown() {
        appScope.cancel()
    }

    @Test
    fun clearTaskPublishesLinearProgressAndCompletion() {
        val taskCoordinator = TaskCoordinator()
        val notificationController = TaskNotificationController(RuntimeEnvironment.getApplication())
        val progressPublished = CountDownLatch(1)
        val releaseRun = CountDownLatch(1)
        val finished = CountDownLatch(1)

        val started = startSimilaritySettingClearTask(
            mode = SimilarityClearMode.Standard,
            scope = appScope,
            taskCoordinator = taskCoordinator,
            notificationController = notificationController,
            onStatusText = {},
            onFinished = { finished.countDown() },
            runClear = { _, onProgress ->
                onProgress(
                    SimilarityClearProgress(
                        total = 12,
                        processed = 4,
                        remaining = 8,
                        phase = SimilarityClearPhase.Clusters
                    )
                )
                progressPublished.countDown()
                assertTrue(releaseRun.await(5, TimeUnit.SECONDS))
                SimilarityClearSummary(
                    total = 12,
                    processed = 12,
                    remaining = 0,
                    cancelled = false
                )
            }
        )

        assertTrue(started)
        assertTrue(progressPublished.await(5, TimeUnit.SECONDS))
        val active = taskCoordinator.activeTask(TaskArea.Similarity)
        assertEquals(TaskKind.SimilarityClear, active?.kind)
        assertEquals(4, active?.processed)
        assertEquals(12, active?.total)
        assertEquals("Clearing clusters • 4/12 • Remaining 8", active?.detail)

        releaseRun.countDown()
        assertTrue(finished.await(5, TimeUnit.SECONDS))
        val terminal = taskCoordinator.terminalSummary(TaskArea.Similarity)
        assertEquals(TaskStatus.Completed, terminal?.status)
        assertEquals("Similarity results cleared", terminal?.title)
        assertEquals(12, terminal?.processed)
        assertEquals(12, terminal?.total)
    }

    @Test
    fun clearCancellationKeepsTaskActiveUntilCommittedBatchStops() {
        val taskCoordinator = TaskCoordinator()
        val notificationController = TaskNotificationController(RuntimeEnvironment.getApplication())
        val enteredRun = CountDownLatch(1)
        val releaseRun = CountDownLatch(1)
        val finished = CountDownLatch(1)

        startSimilaritySettingClearTask(
            mode = SimilarityClearMode.Incremental,
            scope = appScope,
            taskCoordinator = taskCoordinator,
            notificationController = notificationController,
            onStatusText = {},
            onFinished = { finished.countDown() },
            runClear = { shouldContinue, _ ->
                enteredRun.countDown()
                assertTrue(releaseRun.await(5, TimeUnit.SECONDS))
                assertFalse(shouldContinue())
                SimilarityClearSummary(
                    total = 300,
                    processed = 100,
                    remaining = 200,
                    cancelled = true
                )
            }
        )

        assertTrue(enteredRun.await(5, TimeUnit.SECONDS))
        assertTrue(taskCoordinator.requestCancel(TaskArea.Similarity))
        assertTrue(taskCoordinator.isAreaBusy(TaskArea.Similarity))
        assertEquals(
            "Stopping after the current committed batch.",
            taskCoordinator.activeTask(TaskArea.Similarity)?.detail
        )

        releaseRun.countDown()
        assertTrue(finished.await(5, TimeUnit.SECONDS))
        val terminal = taskCoordinator.terminalSummary(TaskArea.Similarity)
        assertEquals(TaskStatus.Cancelled, terminal?.status)
        assertEquals(100, terminal?.processed)
        assertEquals(300, terminal?.total)
        assertFalse(taskCoordinator.isAreaBusy(TaskArea.Similarity))
    }

    @Test
    fun clearFailureReportsRemainingDataAndRefreshesScreen() {
        val taskCoordinator = TaskCoordinator()
        val notificationController = TaskNotificationController(RuntimeEnvironment.getApplication())
        val statuses = Collections.synchronizedList(mutableListOf<String>())
        val finished = CountDownLatch(1)

        startSimilaritySettingClearTask(
            mode = SimilarityClearMode.Standard,
            scope = appScope,
            taskCoordinator = taskCoordinator,
            notificationController = notificationController,
            onStatusText = statuses::add,
            onFinished = { finished.countDown() },
            runClear = { _, onProgress ->
                onProgress(
                    SimilarityClearProgress(
                        total = 20,
                        processed = 5,
                        remaining = 15,
                        phase = SimilarityClearPhase.Clusters
                    )
                )
                error("database full")
            }
        )

        assertTrue(finished.await(5, TimeUnit.SECONDS))
        val terminal = taskCoordinator.terminalSummary(TaskArea.Similarity)
        assertEquals(TaskStatus.Failed, terminal?.status)
        assertEquals(5, terminal?.processed)
        assertEquals(20, terminal?.total)
        assertTrue(terminal?.detail?.contains("Incremental clear") == true)
        assertTrue(statuses.any { status -> status.contains("Incremental clear") })
    }
}
