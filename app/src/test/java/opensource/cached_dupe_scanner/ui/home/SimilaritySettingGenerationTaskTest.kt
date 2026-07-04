package opensource.cached_dupe_scanner.ui.home

import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import opensource.cached_dupe_scanner.notifications.TaskNotificationController
import opensource.cached_dupe_scanner.storage.SimilarityMaintenanceProgress
import opensource.cached_dupe_scanner.storage.SimilarityMaintenanceSummary
import opensource.cached_dupe_scanner.tasks.TaskArea
import opensource.cached_dupe_scanner.tasks.TaskCoordinator
import opensource.cached_dupe_scanner.tasks.TaskKind
import opensource.cached_dupe_scanner.tasks.TaskStatus
import opensource.cached_dupe_scanner.ui.home.similarity.runScanIntegratedSimilarityGeneration
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
        assertEquals(
            "Processed 4/10 • Cluster candidates 2 • Skipped 1 • Exact 2x2",
            taskCoordinator.activeTask(TaskArea.Similarity)?.detail
        )
        val activeTask = taskCoordinator.activeTask(TaskArea.Similarity)
        assertEquals(TaskKind.SimilarityGeneration, activeTask?.kind)
        assertEquals("/storage/emulated/0/DCIM/sample.mp4", activeTask?.currentPath)

        releaseRun.countDown()
        assertTrue(finished.await(5, TimeUnit.SECONDS))
        assertFalse(taskCoordinator.isAreaBusy(TaskArea.Similarity))

        val terminal = taskCoordinator.terminalSummary(TaskArea.Similarity)
        assertEquals(TaskStatus.Completed, terminal?.status)
        assertEquals("Similarity update complete", terminal?.title)
        assertTrue(statuses.any { status -> status.contains("Update complete: 2 clusters, 5 files.") })
    }

    @Test
    fun scanIntegratedSimilarityGenerationUpdatesScanTaskAndWaitsForCompletion() = runBlocking {
        val taskCoordinator = TaskCoordinator()
        val notificationController = TaskNotificationController(RuntimeEnvironment.getApplication())
        val enteredRun = CountDownLatch(1)
        val releaseRun = CountDownLatch(1)
        val finished = CountDownLatch(1)
        val finishedFlag = AtomicBoolean(false)
        taskCoordinator.tryStart(
            area = TaskArea.Scan,
            kind = TaskKind.ScanTarget,
            title = "Scanning files",
            detail = "Saving cache"
        )

        val job = launch(Dispatchers.Default) {
            val ran = runScanIntegratedSimilarityGeneration(
                hasEnabledSettings = { true },
                taskCoordinator = taskCoordinator,
                notificationController = notificationController,
                shouldContinue = { taskCoordinator.isAreaBusy(TaskArea.Scan) },
                onFinished = {
                    finishedFlag.set(true)
                    finished.countDown()
                },
                runGeneration = { shouldContinue, onProgress ->
                    assertTrue(shouldContinue())
                    onProgress(
                        SimilarityMaintenanceProgress(
                            total = 2,
                            processed = 1,
                            skipped = 0,
                            clusterCandidates = 1,
                            currentPath = "/storage/emulated/0/DCIM/scan.mp4",
                            settingName = "Scan generated"
                        )
                    )
                    enteredRun.countDown()
                    assertTrue(releaseRun.await(5, TimeUnit.SECONDS))
                    SimilarityMaintenanceSummary(
                        settingCount = 1,
                        candidateCount = 2,
                        processedCount = 2,
                        skippedCount = 0,
                        clusterCount = 1,
                        duplicateFileCount = 2,
                        cancelled = false
                    )
                }
            )
            assertTrue(ran)
        }

        assertTrue(enteredRun.await(5, TimeUnit.SECONDS))
        assertTrue(taskCoordinator.isAreaBusy(TaskArea.Scan))
        assertFalse(taskCoordinator.isAreaBusy(TaskArea.Similarity))
        assertEquals(
            "Generating similarity • 1/2 • Cluster candidates 1 • Skipped 0 • Scan generated",
            taskCoordinator.activeTask(TaskArea.Scan)?.detail
        )
        assertEquals("/storage/emulated/0/DCIM/scan.mp4", taskCoordinator.activeTask(TaskArea.Scan)?.currentPath)
        assertFalse(finishedFlag.get())

        releaseRun.countDown()
        job.join()
        assertTrue(finished.await(5, TimeUnit.SECONDS))
        assertTrue(finishedFlag.get())
        assertTrue(taskCoordinator.isAreaBusy(TaskArea.Scan))
    }

    @Test
    fun scanIntegratedSimilarityGenerationSkipsWhenNoSettingIsEnabled() = runBlocking {
        val taskCoordinator = TaskCoordinator()
        val notificationController = TaskNotificationController(RuntimeEnvironment.getApplication())
        var generationRan = false

        val ran = runScanIntegratedSimilarityGeneration(
            hasEnabledSettings = { false },
            taskCoordinator = taskCoordinator,
            notificationController = notificationController,
            shouldContinue = { true },
            onFinished = {},
            runGeneration = { _, _ ->
                generationRan = true
                SimilarityMaintenanceSummary(
                    settingCount = 0,
                    candidateCount = 0,
                    processedCount = 0,
                    skippedCount = 0,
                    clusterCount = 0,
                    duplicateFileCount = 0,
                    cancelled = false
                )
            }
        )

        assertFalse(ran)
        assertFalse(generationRan)
    }

    @Test
    fun cancellingSettingGenerationClearsSharedTask() {
        val taskCoordinator = TaskCoordinator()
        val notificationController = TaskNotificationController(RuntimeEnvironment.getApplication())
        val enteredRun = CountDownLatch(1)
        val releaseRun = CountDownLatch(1)
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
                assertTrue(releaseRun.await(5, TimeUnit.SECONDS))
                assertFalse(shouldContinue())
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
        releaseRun.countDown()
        assertTrue(finished.await(5, TimeUnit.SECONDS))

        val terminal = taskCoordinator.terminalSummary(TaskArea.Similarity)
        assertEquals(TaskStatus.Cancelled, terminal?.status)
        assertEquals("Similarity rebuild cancelled", terminal?.title)
        assertFalse(taskCoordinator.isAreaBusy(TaskArea.Similarity))
    }
}
