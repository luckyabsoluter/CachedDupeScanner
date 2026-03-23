package opensource.cached_dupe_scanner.ui.home

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
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
        val enteredHash = AtomicBoolean(false)
        val settingsStore = AppSettingsStore(context)
        val historyRepo = ScanHistoryRepository(
            dao = database.fileCacheDao(),
            settingsStore = settingsStore,
            groupDao = database.duplicateGroupDao(),
            database = database,
            hashFile = { _, shouldContinue ->
                enteredHash.set(true)
                while (shouldContinue()) {
                    Thread.sleep(10)
                }
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

        composeRule.waitUntil(5_000) { enteredHash.get() }

        composeRule.onNodeWithText("Cancel running task").fetchSemanticsNode()
        composeRule.runOnIdle {
            assertTrue(taskCoordinator.requestCancel(opensource.cached_dupe_scanner.tasks.TaskArea.Db))
        }

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
        val enteredTask = AtomicBoolean(false)

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
                    enteredTask.set(true)
                    while (shouldContinue()) {
                        Thread.sleep(10)
                    }
                    RebuildGroupsSummary(total = 7, processed = 3, cancelled = true)
                }
            )
        }

        composeRule.waitUntil(5_000) { enteredTask.get() }
        composeRule.onNodeWithText("Rebuilding groups").fetchSemanticsNode()
        composeRule.runOnIdle {
            assertTrue(taskCoordinator.requestCancel(opensource.cached_dupe_scanner.tasks.TaskArea.Db))
        }

        composeRule.waitUntil(5_000) {
            !taskCoordinator.isAreaBusy(opensource.cached_dupe_scanner.tasks.TaskArea.Db)
        }

        assertTrue(
            composeRule.onAllNodesWithText("Rebuilding groups").fetchSemanticsNodes().isEmpty()
        )
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
        val enteredTask = AtomicBoolean(false)

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
                    enteredTask.set(true)
                    while (shouldContinue()) {
                        Thread.sleep(10)
                    }
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

        composeRule.waitUntil(5_000) { enteredTask.get() }
        composeRule.onNodeWithText("Clearing cached results").fetchSemanticsNode()
        composeRule.runOnIdle {
            assertTrue(taskCoordinator.requestCancel(opensource.cached_dupe_scanner.tasks.TaskArea.Db))
        }

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
