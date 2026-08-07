package opensource.cached_dupe_scanner.ui.home

import android.content.Context
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.unit.dp
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import opensource.cached_dupe_scanner.cache.CacheDatabase
import opensource.cached_dupe_scanner.cache.CachedFileEntity
import opensource.cached_dupe_scanner.storage.AppSettingsStore
import opensource.cached_dupe_scanner.storage.PagedFileRepository
import opensource.cached_dupe_scanner.storage.ScanHistoryRepository
import opensource.cached_dupe_scanner.storage.TrashController
import opensource.cached_dupe_scanner.storage.TrashRepository
import org.junit.After
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

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, CacheDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        settingsStore = AppSettingsStore(context)
    }

    @After
    fun tearDown() {
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
}
