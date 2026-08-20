package opensource.cached_dupe_scanner.ui.home

import android.content.Context
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import opensource.cached_dupe_scanner.cache.CacheDatabase
import opensource.cached_dupe_scanner.cache.CachedFileEntity
import opensource.cached_dupe_scanner.storage.AppSettingsStore
import opensource.cached_dupe_scanner.storage.PagedFileRepository
import opensource.cached_dupe_scanner.storage.ScanHistoryRepository
import opensource.cached_dupe_scanner.storage.TrashController
import opensource.cached_dupe_scanner.storage.TrashRepository
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FilesScreenDbNavigationTest {
    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var database: CacheDatabase
    private lateinit var settingsStore: AppSettingsStore
    private lateinit var executedQueries: CopyOnWriteArrayList<String>
    private val cachedFilesCountQueryLatch = AtomicReference<CountDownLatch?>(null)

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        executedQueries = CopyOnWriteArrayList()
        database = Room.inMemoryDatabaseBuilder(context, CacheDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryCallback(
                { sqlQuery, _ ->
                    executedQueries += sqlQuery
                    if (isCachedFilesCountQuery(sqlQuery)) {
                        cachedFilesCountQueryLatch.get()?.countDown()
                    }
                },
                { command -> command.run() }
            )
            .build()
        settingsStore = AppSettingsStore(context)
        settingsStore.setFilesFilterDefinitionJson("")
        settingsStore.setFilesSortKey(PagedFileRepository.SortKey.Name.name)
        settingsStore.setFilesSortDirection(PagedFileRepository.SortDirection.Asc.name)
    }

    @After
    fun tearDown() {
        cachedFilesCountQueryLatch.set(null)
        database.close()
    }

    @Test
    fun firstCompositionLoadsCachedFilesInsteadOfClearingTheInitialRequest() {
        val normalizedPath = "C:/cached/files/initial-entry.txt"
        database.fileCacheDao().upsert(
            CachedFileEntity(
                normalizedPath = normalizedPath,
                path = normalizedPath,
                sizeBytes = 17L,
                lastModifiedMillis = 123L,
                hashHex = null
            )
        )
        val historyRepository = ScanHistoryRepository(
            dao = database.fileCacheDao(),
            settingsStore = settingsStore,
            groupDao = database.duplicateGroupDao(),
            database = database
        )
        val trashRepository = TrashRepository(database.trashDao())
        val trashController = TrashController(
            context = context,
            database = database,
            historyRepo = historyRepository,
            trashRepo = trashRepository
        )

        composeRule.setContent {
            FilesScreenDb(
                fileRepo = PagedFileRepository(database),
                trashController = trashController,
                settingsStore = settingsStore,
                keepLoadedThumbnailsInMemory = false,
                keepLoadedVideoPreviewsInMemory = false,
                snapVideoPreviewFramesToWidth = false,
                videoPreviewLineCount = 1,
                thumbnailSizeScale = 1f,
                videoPreviewSizeScale = 1f,
                rememberedThumbnailCache = mutableStateMapOf(),
                rememberedVideoPreviewCache = mutableStateMapOf(),
                clearVersion = 0,
                refreshVersion = 0,
                onBack = {},
                modifier = Modifier.height(1_200.dp)
            )
        }

        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText(normalizedPath)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    @Test
    fun refreshWaitsUntilTheFileFilterScreenClosesBeforeQueryingAgain() {
        val refreshVersion = mutableIntStateOf(0)
        val refreshQueryStarted = CountDownLatch(1)
        val refreshedPath = "C:/cached/files/refreshed-entry.txt"
        val historyRepository = ScanHistoryRepository(
            dao = database.fileCacheDao(),
            settingsStore = settingsStore,
            groupDao = database.duplicateGroupDao(),
            database = database
        )
        val trashController = TrashController(
            context = context,
            database = database,
            historyRepo = historyRepository,
            trashRepo = TrashRepository(database.trashDao())
        )

        composeRule.setContent {
            FilesScreenDb(
                fileRepo = PagedFileRepository(database),
                trashController = trashController,
                settingsStore = settingsStore,
                keepLoadedThumbnailsInMemory = false,
                keepLoadedVideoPreviewsInMemory = false,
                snapVideoPreviewFramesToWidth = false,
                videoPreviewLineCount = 1,
                thumbnailSizeScale = 1f,
                videoPreviewSizeScale = 1f,
                rememberedThumbnailCache = mutableStateMapOf(),
                rememberedVideoPreviewCache = mutableStateMapOf(),
                clearVersion = 0,
                refreshVersion = refreshVersion.intValue,
                onBack = {},
                modifier = Modifier.height(1_200.dp)
            )
        }

        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("No files in cache.")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeRule.onNodeWithContentDescription("Menu").performClick()
        composeRule.onNodeWithText("Filter").performClick()
        composeRule.onNodeWithText("File filters").assertExists()
        database.fileCacheDao().upsert(
            CachedFileEntity(
                normalizedPath = refreshedPath,
                path = refreshedPath,
                sizeBytes = 23L,
                lastModifiedMillis = 456L,
                hashHex = null
            )
        )
        executedQueries.clear()
        cachedFilesCountQueryLatch.set(refreshQueryStarted)

        composeRule.runOnIdle {
            refreshVersion.intValue += 1
        }
        composeRule.waitForIdle()

        val queriedWhileFilterOpen = refreshQueryStarted.await(500, TimeUnit.MILLISECONDS)

        composeRule.onNodeWithContentDescription("Back").performClick()
        assertTrue(refreshQueryStarted.await(5, TimeUnit.SECONDS))
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText(refreshedPath)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        cachedFilesCountQueryLatch.set(null)
        assertFalse(queriedWhileFilterOpen)
    }

    private fun isCachedFilesCountQuery(sql: String): Boolean {
        return sql.contains("SELECT COUNT(*) FROM cached_files", ignoreCase = true)
    }
}
