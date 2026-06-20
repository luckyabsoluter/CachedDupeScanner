package opensource.cached_dupe_scanner.ui.home

import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import opensource.cached_dupe_scanner.notifications.TaskNotificationController
import opensource.cached_dupe_scanner.storage.SimilarityMaintenanceProgress
import opensource.cached_dupe_scanner.storage.SimilarityMaintenanceSummary
import opensource.cached_dupe_scanner.tasks.TaskArea
import opensource.cached_dupe_scanner.tasks.TaskCoordinator
import opensource.cached_dupe_scanner.tasks.TaskKind
import opensource.cached_dupe_scanner.tasks.TaskStatus
import opensource.cached_dupe_scanner.ui.home.similarity.startSimilaritySettingGenerationTask
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
class SimilaritySettingGenerationTaskTest {
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
    fun settingGenerationTaskPublishesProgress() {
        val taskCoordinator = TaskCoordinator()
        val notificationController = TaskNotificationController(RuntimeEnvironment.getApplication())
        val statuses = Collections.synchronizedList(mutableListOf<String>())
        val progressPublished = CountDownLatch(1)
        val releaseRun = CountDownLatch(1)
        val finished = CountDownLatch(1)

        val started = startSimilaritySettingGenerationTask(
            rebuild = false,
            scope = appScope,
            taskCoordinator = taskCoordinator,
            notificationController = notificationController,
            onStatusText = { status -> statuses.add(status) },
            onFinished = { finished.countDown() },
            runGeneration = { _, onProgress ->
                onProgress(
                    SimilarityMaintenanceProgress(
                        total = 10,
                        processed = 4,
                        skipped = 1,
                        clusterCandidates = 2,
                        currentPath = "/storage/emulated/0/DCIM/sample.mp4",
                        settingName = "Exact 2x2"
                    )
                )
                progressPublished.countDown()
                assertTrue(releaseRun.await(5, TimeUnit.SECONDS))
                SimilarityMaintenanceSummary(
                    settingCount = 1,
                    candidateCount = 10,
                    processedCount = 10,
                    skippedCount = 1,
                    clusterCount = 2,
                    duplicateFileCount = 5,
                    cancelled = false
                )
            }
        )

        assertTrue(started)
        assertTrue(progressPublished.await(5, TimeUnit.SECONDS))
        waitUntil {
            taskCoordinator.activeTask(TaskArea.Similarity)?.detail ==
                "Processed 4/10 • Cluster candidates 2 • Skipped 1 • Exact 2x2"
        }
        val activeTask = taskCoordinator.activeTask(TaskArea.Similarity)
        assertEquals(TaskKind.SimilarityGeneration, activeTask?.kind)
        assertEquals("/storage/emulated/0/DCIM/sample.mp4", activeTask?.currentPath)

        releaseRun.countDown()
        assertTrue(finished.await(5, TimeUnit.SECONDS))
        waitUntil { !taskCoordinator.isAreaBusy(TaskArea.Similarity) }

        val terminal = taskCoordinator.terminalSummary(TaskArea.Similarity)
        assertEquals(TaskStatus.Completed, terminal?.status)
        assertEquals("Similarity update complete", terminal?.title)
        assertTrue(statuses.any { status -> status.contains("Update complete: 2 clusters, 5 files.") })
    }

    @Test
    fun cancellingSettingGenerationClearsSharedTask() {
        val taskCoordinator = TaskCoordinator()
        val notificationController = TaskNotificationController(RuntimeEnvironment.getApplication())
        val enteredRun = CountDownLatch(1)
        val finished = CountDownLatch(1)

        startSimilaritySettingGenerationTask(
            rebuild = true,
            scope = appScope,
            taskCoordinator = taskCoordinator,
            notificationController = notificationController,
            onStatusText = {},
            onFinished = { finished.countDown() },
            runGeneration = { shouldContinue, _ ->
                enteredRun.countDown()
                while (shouldContinue()) {
                    Thread.sleep(10)
                }
                SimilarityMaintenanceSummary(
                    settingCount = 1,
                    candidateCount = 8,
                    processedCount = 3,
                    skippedCount = 1,
                    clusterCount = 1,
                    duplicateFileCount = 2,
                    cancelled = true
                )
            }
        )

        assertTrue(enteredRun.await(5, TimeUnit.SECONDS))
        assertTrue(taskCoordinator.requestCancel(TaskArea.Similarity))
        waitUntil { !taskCoordinator.isAreaBusy(TaskArea.Similarity) }
        assertTrue(finished.await(5, TimeUnit.SECONDS))

        val terminal = taskCoordinator.terminalSummary(TaskArea.Similarity)
        assertEquals(TaskStatus.Cancelled, terminal?.status)
        assertEquals("Similarity rebuild cancelled", terminal?.title)
        assertFalse(taskCoordinator.isAreaBusy(TaskArea.Similarity))
    }
}

private fun waitUntil(condition: () -> Boolean) {
    val deadline = System.currentTimeMillis() + 5_000L
    while (System.currentTimeMillis() < deadline) {
        if (condition()) return
        Thread.sleep(10)
    }
    assertTrue("Condition was not met before timeout", condition())
}
