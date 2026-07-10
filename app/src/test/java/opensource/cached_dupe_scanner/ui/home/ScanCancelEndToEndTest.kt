package opensource.cached_dupe_scanner.ui.home

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
import opensource.cached_dupe_scanner.cache.CacheDatabase
import opensource.cached_dupe_scanner.cache.CacheStore
import opensource.cached_dupe_scanner.engine.FileHasher
import opensource.cached_dupe_scanner.engine.FileWalker
import opensource.cached_dupe_scanner.engine.IncrementalScanner
import opensource.cached_dupe_scanner.notifications.TaskNotificationController
import opensource.cached_dupe_scanner.storage.AppSettingsStore
import opensource.cached_dupe_scanner.storage.ScanReportRepository
import opensource.cached_dupe_scanner.storage.ScanTargetStore
import opensource.cached_dupe_scanner.tasks.TaskCoordinator
import opensource.cached_dupe_scanner.ui.results.ScanUiState

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ScanCancelEndToEndTest {
    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var reportDatabase: CacheDatabase
    private lateinit var scanDatabase: CacheDatabase
    private lateinit var targetDir: File
    private lateinit var appScope: CoroutineScope

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        reportDatabase = Room.inMemoryDatabaseBuilder(context, CacheDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        scanDatabase = Room.inMemoryDatabaseBuilder(context, CacheDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        targetDir = createTempDirectory(prefix = "cached-dupe-scan").toFile()
        File(targetDir, "a.bin").writeText("aa")
        File(targetDir, "b.bin").writeText("bb")
        ScanTargetStore(context).saveTargets(
            listOf(opensource.cached_dupe_scanner.storage.ScanTarget(id = "scan-target", path = targetDir.absolutePath))
        )
        appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    }

    @After
    fun tearDown() {
        appScope.cancel()
        targetDir.deleteRecursively()
        val context = ApplicationProvider.getApplicationContext<Context>()
        ScanTargetStore(context).saveTargets(emptyList())
        reportDatabase.close()
        scanDatabase.close()
    }

    @Test
    fun cancellingScanClearsRunningUiImmediately() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val enteredHash = CountDownLatch(1)
        val releaseHash = CountDownLatch(1)
        val scanner = IncrementalScanner(
            cacheStore = CacheStore(scanDatabase.fileCacheDao()),
            fileHasher = BlockingHasher(enteredHash, releaseHash),
            fileWalker = FileWalker()
        )
        val state = mutableStateOf<ScanUiState>(ScanUiState.Idle)
        val taskCoordinator = TaskCoordinator()
        val notificationController = TaskNotificationController(context)

        composeRule.setContent {
            ScanCommandScreen(
                state = state,
                onScanComplete = {},
                onScanCancelled = {},
                reportRepo = ScanReportRepository(reportDatabase.scanReportDao()),
                settingsStore = AppSettingsStore(context),
                targetsVersion = 0,
                scanScope = appScope,
                onReportSaved = {},
                taskCoordinator = taskCoordinator,
                notificationController = notificationController,
                onBack = {},
                scanner = scanner
            )
        }

        try {
            composeRule.onNodeWithText("Scan target").performClick()

            assertTrue(enteredHash.await(5, TimeUnit.SECONDS))
            composeRule.onNodeWithText("Stop scan").fetchSemanticsNode()
            composeRule.runOnIdle {
                assertTrue(taskCoordinator.requestCancel(opensource.cached_dupe_scanner.tasks.TaskArea.Scan))
            }

            composeRule.waitUntil(5_000) {
                composeRule.onAllNodesWithText("Stop scan").fetchSemanticsNodes().isEmpty()
            }
            assertTrue(!taskCoordinator.isAreaBusy(opensource.cached_dupe_scanner.tasks.TaskArea.Scan))
        } finally {
            releaseHash.countDown()
        }
    }

    private class BlockingHasher(
        private val enteredHash: CountDownLatch,
        private val releaseHash: CountDownLatch
    ) : FileHasher {
        override fun hash(file: File, shouldContinue: () -> Boolean): String? {
            enteredHash.countDown()
            releaseHash.await(5, TimeUnit.SECONDS)
            return if (shouldContinue()) "hash-${file.name}" else null
        }
    }
}
