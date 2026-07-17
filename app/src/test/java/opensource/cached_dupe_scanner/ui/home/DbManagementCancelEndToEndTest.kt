package opensource.cached_dupe_scanner.ui.home

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.junit.Assert.assertTrue
import opensource.cached_dupe_scanner.cache.CacheDatabase
import opensource.cached_dupe_scanner.core.FileMetadata
import opensource.cached_dupe_scanner.core.ScanResult
import opensource.cached_dupe_scanner.notifications.TaskNotificationController
import opensource.cached_dupe_scanner.storage.AppSettingsStore
import opensource.cached_dupe_scanner.storage.ClearCacheSummary
import opensource.cached_dupe_scanner.storage.RebuildGroupsPhase
import opensource.cached_dupe_scanner.storage.RebuildGroupsProgress
import opensource.cached_dupe_scanner.storage.RebuildGroupsSummary
import opensource.cached_dupe_scanner.storage.ResultsDbRepository
import opensource.cached_dupe_scanner.storage.ScanHistoryRepository
import opensource.cached_dupe_scanner.tasks.TaskCoordinator
import opensource.cached_dupe_scanner.ui.components.TaskBannerStack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DbManagementCancelEndToEndTest {
    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var database: CacheDatabase
    private lateinit var tempFile: File
    private lateinit var appScope: CoroutineScope

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, CacheDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        tempFile = File.createTempFile("cached-dupe-scanner", ".rehash").apply {
            writeText("content")
        }
        appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    }

    @After
    fun tearDown() {
        appScope.cancel()
        tempFile.delete()
        database.close()
    }

    @Test
    fun cancellingMaintenanceClearsRunningUi() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val enteredHash = CountDownLatch(1)
        val releaseHash = CountDownLatch(1)
        val settingsStore = AppSettingsStore(context)
        val historyRepo = ScanHistoryRepository(
            dao = database.fileCacheDao(),
            settingsStore = settingsStore,
            groupDao = database.duplicateGroupDao(),
            database = database,
            hashFile = { _, shouldContinue ->
                enteredHash.countDown()
                releaseHash.await(5, TimeUnit.SECONDS)
                assertTrue(!shouldContinue())
                null
            }
        )
        historyRepo.recordScan(
            ScanResult(
                scannedAtMillis = 1L,
                files = listOf(
                    FileMetadata(
                        path = tempFile.absolutePath,
                        normalizedPath = tempFile.absolutePath,
                        sizeBytes = tempFile.length(),
                        lastModifiedMillis = tempFile.lastModified(),
                        hashHex = null
                    )
                ),
                duplicateGroups = emptyList()
            )
        )
        val resultsRepo = ResultsDbRepository(database.fileCacheDao(), database.duplicateGroupDao())
        val uiState = DbManagementUiState()
        val taskCoordinator = TaskCoordinator()
        val notificationController = TaskNotificationController(context)

        composeRule.setContent {
            DbManagementHarness(
                historyRepo = historyRepo,
                resultsRepo = resultsRepo,
                uiState = uiState,
                appScope = appScope,
                taskCoordinator = taskCoordinator,
                notificationController = notificationController
            )
        }

        composeRule.runOnIdle {
            startDbMaintenanceTask(
                historyRepo = historyRepo,
                uiState = uiState,
                appScope = appScope,
                taskCoordinator = taskCoordinator,
                notificationController = notificationController,
                deleteMissing = false,
                rehashStale = false,
                rehashMissing = true,
                onMaintenanceApplied = {},
                refreshOverview = {}
            )
        }

        assertTrue(enteredHash.await(5, TimeUnit.SECONDS))

        composeRule.onNodeWithText("Cancel running task").fetchSemanticsNode()
        composeRule.runOnIdle {
            assertTrue(taskCoordinator.requestCancel(opensource.cached_dupe_scanner.tasks.TaskArea.Db))
        }
        releaseHash.countDown()

        composeRule.waitUntil(5_000) {
            !taskCoordinator.isAreaBusy(opensource.cached_dupe_scanner.tasks.TaskArea.Db)
        }

        assertTrue(
            composeRule.onAllNodesWithText("Cancel running task").fetchSemanticsNodes().isEmpty()
        )
        assertTrue(
            composeRule.onAllNodesWithText("Idle").fetchSemanticsNodes().isNotEmpty()
        )
    }

    @Test
    fun maintenanceScopesAreMutuallyExclusive() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        composeRule.setContent {
            DbManagementHarness(
                historyRepo = ScanHistoryRepository(
                    dao = database.fileCacheDao(),
                    settingsStore = AppSettingsStore(context),
                    groupDao = database.duplicateGroupDao(),
                    database = database
                ),
                resultsRepo = ResultsDbRepository(
                    database.fileCacheDao(),
                    database.duplicateGroupDao()
                ),
                uiState = DbManagementUiState(),
                appScope = appScope,
                taskCoordinator = TaskCoordinator(),
                notificationController = TaskNotificationController(context)
            )
        }

        val allFiles = composeRule.onNodeWithTag("db-maintenance-scope:AllCachedFiles")
        val resultGroups = composeRule.onNodeWithTag("db-maintenance-scope:DuplicateResultGroups")
        val similarityGroups = composeRule.onNodeWithTag("db-maintenance-scope:SimilarityGroups")
        allFiles.assertIsSelected()

        similarityGroups.performScrollTo().performClick().assertIsSelected()
        allFiles.assertIsNotSelected()
        resultGroups.assertIsNotSelected()

        resultGroups.performClick().assertIsSelected()
        allFiles.assertIsNotSelected()
        similarityGroups.assertIsNotSelected()
    }

    @Test
    fun cancellingRebuildGroupsClearsRunningUi() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val settingsStore = AppSettingsStore(context)
        val historyRepo = ScanHistoryRepository(
            dao = database.fileCacheDao(),
            settingsStore = settingsStore,
            groupDao = database.duplicateGroupDao(),
            database = database
        )
        val resultsRepo = ResultsDbRepository(database.fileCacheDao(), database.duplicateGroupDao())
        val uiState = DbManagementUiState()
        val taskCoordinator = TaskCoordinator()
        val notificationController = TaskNotificationController(context)
        val enteredTask = CountDownLatch(1)
        val releaseTask = CountDownLatch(1)

        composeRule.setContent {
            DbManagementHarness(
                historyRepo = historyRepo,
                resultsRepo = resultsRepo,
                uiState = uiState,
                appScope = appScope,
                taskCoordinator = taskCoordinator,
                notificationController = notificationController
            )
        }

        composeRule.runOnIdle {
            startRebuildGroupsTask(
                uiState = uiState,
                appScope = appScope,
                taskCoordinator = taskCoordinator,
                notificationController = notificationController,
                onMaintenanceApplied = {},
                refreshOverview = {},
                runRebuildGroups = { shouldContinue, _ ->
                    enteredTask.countDown()
                    releaseTask.await(5, TimeUnit.SECONDS)
                    assertTrue(!shouldContinue())
                    RebuildGroupsSummary(total = 7, processed = 3, cancelled = true)
                }
            )
        }

        assertTrue(enteredTask.await(5, TimeUnit.SECONDS))
        composeRule.onNodeWithText("Rebuilding groups").fetchSemanticsNode()
        composeRule.runOnIdle {
            assertTrue(taskCoordinator.requestCancel(opensource.cached_dupe_scanner.tasks.TaskArea.Db))
        }
        releaseTask.countDown()

        composeRule.waitUntil(5_000) {
            !taskCoordinator.isAreaBusy(opensource.cached_dupe_scanner.tasks.TaskArea.Db)
        }

        assertTrue(
            composeRule.onAllNodesWithText("Rebuilding groups").fetchSemanticsNodes().isEmpty()
        )
    }

    @Test
    fun rebuildGroupsProgressAppearsWithCurrentRepairPath() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val settingsStore = AppSettingsStore(context)
        val historyRepo = ScanHistoryRepository(
            dao = database.fileCacheDao(),
            settingsStore = settingsStore,
            groupDao = database.duplicateGroupDao(),
            database = database
        )
        val resultsRepo = ResultsDbRepository(database.fileCacheDao(), database.duplicateGroupDao())
        val uiState = DbManagementUiState()
        val taskCoordinator = TaskCoordinator()
        val notificationController = TaskNotificationController(context)
        val enteredTask = CountDownLatch(1)
        val releaseTask = CountDownLatch(1)

        composeRule.setContent {
            DbManagementHarness(
                historyRepo = historyRepo,
                resultsRepo = resultsRepo,
                uiState = uiState,
                appScope = appScope,
                taskCoordinator = taskCoordinator,
                notificationController = notificationController
            )
        }

        composeRule.runOnIdle {
            startRebuildGroupsTask(
                uiState = uiState,
                appScope = appScope,
                taskCoordinator = taskCoordinator,
                notificationController = notificationController,
                onMaintenanceApplied = {},
                refreshOverview = {},
                runRebuildGroups = { shouldContinue, onProgress ->
                    onProgress(
                        RebuildGroupsProgress(
                            total = 2,
                            processed = 1,
                            phase = RebuildGroupsPhase.RepairingMissingHashes,
                            currentPath = "/storage/emulated/0/Download/missing.mp4"
                        )
                    )
                    enteredTask.countDown()
                    releaseTask.await(5, TimeUnit.SECONDS)
                    assertTrue(!shouldContinue())
                    RebuildGroupsSummary(
                        total = 2,
                        processed = 1,
                        cancelled = true,
                        phase = RebuildGroupsPhase.RepairingMissingHashes
                    )
                }
            )
        }

        composeRule.waitUntil(5_000) {
            enteredTask.count == 0L &&
                taskCoordinator.activeTask(opensource.cached_dupe_scanner.tasks.TaskArea.Db)
                    ?.detail == "Repairing missing hashes 1/2 before rebuilding groups."
        }
        assertTrue(
            composeRule.onAllNodesWithText("Repairing missing hashes 1/2 before rebuilding groups.")
                .fetchSemanticsNodes()
                .isNotEmpty()
        )
        composeRule.onNodeWithText("Current: /storage/emulated/0/Download/missing.mp4").fetchSemanticsNode()

        composeRule.runOnIdle {
            assertTrue(taskCoordinator.requestCancel(opensource.cached_dupe_scanner.tasks.TaskArea.Db))
        }
        releaseTask.countDown()
        composeRule.waitUntil(5_000) {
            !taskCoordinator.isAreaBusy(opensource.cached_dupe_scanner.tasks.TaskArea.Db)
        }
    }

    @Test
    fun cancellingClearCacheClearsRunningUi() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val settingsStore = AppSettingsStore(context)
        val historyRepo = ScanHistoryRepository(
            dao = database.fileCacheDao(),
            settingsStore = settingsStore,
            groupDao = database.duplicateGroupDao(),
            database = database
        )
        val resultsRepo = ResultsDbRepository(database.fileCacheDao(), database.duplicateGroupDao())
        val uiState = DbManagementUiState()
        val taskCoordinator = TaskCoordinator()
        val notificationController = TaskNotificationController(context)
        val enteredTask = CountDownLatch(1)
        val releaseTask = CountDownLatch(1)

        composeRule.setContent {
            DbManagementHarness(
                historyRepo = historyRepo,
                resultsRepo = resultsRepo,
                uiState = uiState,
                appScope = appScope,
                taskCoordinator = taskCoordinator,
                notificationController = notificationController
            )
        }

        composeRule.runOnIdle {
            startClearCacheTask(
                uiState = uiState,
                appScope = appScope,
                taskCoordinator = taskCoordinator,
                notificationController = notificationController,
                onCacheCleared = {},
                refreshOverview = {},
                runClearAll = { shouldContinue, _ ->
                    enteredTask.countDown()
                    releaseTask.await(5, TimeUnit.SECONDS)
                    assertTrue(!shouldContinue())
                    ClearCacheSummary(
                        total = 9,
                        processed = 4,
                        clearedFiles = 4,
                        clearedGroups = 0,
                        cancelled = true
                    )
                }
            )
        }

        assertTrue(enteredTask.await(5, TimeUnit.SECONDS))
        composeRule.onNodeWithText("Clearing cached results").fetchSemanticsNode()
        composeRule.runOnIdle {
            assertTrue(taskCoordinator.requestCancel(opensource.cached_dupe_scanner.tasks.TaskArea.Db))
        }
        releaseTask.countDown()

        composeRule.waitUntil(5_000) {
            !taskCoordinator.isAreaBusy(opensource.cached_dupe_scanner.tasks.TaskArea.Db)
        }

        assertTrue(
            composeRule.onAllNodesWithText("Clearing cached results").fetchSemanticsNodes().isEmpty()
        )
    }
}

@Composable
private fun DbManagementHarness(
    historyRepo: ScanHistoryRepository,
    resultsRepo: ResultsDbRepository,
    uiState: DbManagementUiState,
    appScope: CoroutineScope,
    taskCoordinator: TaskCoordinator,
    notificationController: TaskNotificationController
) {
    Box {
        DbManagementScreen(
            historyRepo = historyRepo,
            resultsRepo = resultsRepo,
            uiState = uiState,
            appScope = appScope,
            taskCoordinator = taskCoordinator,
            notificationController = notificationController,
            onMaintenanceApplied = {},
            onCacheCleared = {},
            onBack = {}
        )
        TaskBannerStack(
            tasks = taskCoordinator.activeTasks.toList(),
            onOpenTask = {},
            onCancelTask = { task -> taskCoordinator.requestCancel(task.area) }
        )
    }
}
