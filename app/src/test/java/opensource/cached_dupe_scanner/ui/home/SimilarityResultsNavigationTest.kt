package opensource.cached_dupe_scanner.ui.home

import android.content.Context
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import opensource.cached_dupe_scanner.cache.CacheDatabase
import opensource.cached_dupe_scanner.cache.CachedFileEntity
import opensource.cached_dupe_scanner.core.ExactThumbnailHashStep
import opensource.cached_dupe_scanner.core.SimilarityMediaScope
import opensource.cached_dupe_scanner.core.VideoDurationExtractor
import opensource.cached_dupe_scanner.core.VideoFrameSignatureExtractor
import opensource.cached_dupe_scanner.notifications.TaskNotificationController
import opensource.cached_dupe_scanner.storage.AppSettingsStore
import opensource.cached_dupe_scanner.storage.SimilaritySettingsRepository
import opensource.cached_dupe_scanner.tasks.TaskCoordinator
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SimilarityResultsNavigationTest {
    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var database: CacheDatabase
    private lateinit var tempDir: File
    private lateinit var appScope: CoroutineScope
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        clearSettings()
        database = Room.inMemoryDatabaseBuilder(context, CacheDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        tempDir = File(
            File(requireNotNull(System.getProperty("user.dir")), "build/test-temp"),
            "similarity-results-navigation-${UUID.randomUUID()}"
        )
        tempDir.mkdirs()
        appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    }

    @After
    fun tearDown() {
        appScope.cancel()
        database.close()
        tempDir.deleteRecursively()
        clearSettings()
    }

    @Test
    fun settingDetailOpenResultsInvokesGroupsRoute() {
        val fixture = createSimilarityFixture()
        var openedSettingId: Long? = null

        composeRule.setContent {
            SimilaritySettingDetailScreen(
                repository = fixture.repository,
                appScope = appScope,
                taskCoordinator = TaskCoordinator(),
                notificationController = TaskNotificationController(context),
                settingId = fixture.settingId,
                refreshVersion = 0,
                onChanged = {},
                onBack = {},
                onOpenGroups = { settingId -> openedSettingId = settingId },
                modifier = Modifier.height(1_200.dp)
            )
        }

        scrollUntilText("Open results")
        composeRule.onNodeWithText("Open results").performClick()

        composeRule.runOnIdle {
            assertEquals(fixture.settingId, openedSettingId)
        }
    }

    @Test
    fun groupsScreenOpensClusterDetailRoute() {
        val fixture = createSimilarityFixture()
        var openedSettingId: Long? = null
        var openedClusterId: Long? = null
        val previewCache = mutableStateMapOf<String, ImageBitmap>()

        composeRule.setContent {
            SimilaritySettingGroupsScreen(
                repository = fixture.repository,
                settingsStore = AppSettingsStore(context),
                keepLoadedThumbnailsInMemory = false,
                thumbnailSizeScale = 1f,
                rememberedPreviewCache = previewCache,
                showFullPaths = false,
                settingId = fixture.settingId,
                refreshVersion = 0,
                onBack = {},
                onOpenCluster = { settingId, clusterId ->
                    openedSettingId = settingId
                    openedClusterId = clusterId
                },
                modifier = Modifier.height(1_200.dp)
            )
        }

        scrollUntilText("2 files", substring = true)
        composeRule.onNodeWithTag("similarity-cluster:${fixture.clusterId}").performClick()

        composeRule.runOnIdle {
            assertEquals(fixture.settingId, openedSettingId)
            assertEquals(fixture.clusterId, openedClusterId)
        }
    }

    private fun createSimilarityFixture(): SimilarityFixture {
        val first = videoFile("first.mp4")
        val second = videoFile("second.mp4")
        database.fileCacheDao().upsert(entity(first))
        database.fileCacheDao().upsert(entity(second))
        val repository = SimilaritySettingsRepository(
            database = database,
            fileDao = database.fileCacheDao(),
            similarityDao = database.similaritySettingsDao(),
            frameSignatureExtractor = FakeSignatureExtractor(
                mapOf(
                    first.absolutePath to "same-signature",
                    second.absolutePath to "same-signature"
                )
            ),
            durationExtractor = FakeDurationExtractor()
        )
        val setting = repository.createExactThumbnailSetting(
            mediaScope = SimilarityMediaScope.Video,
            minSizeBytes = 1L,
            step = ExactThumbnailHashStep(
                frameSeconds = listOf(0),
                resizeWidthPx = 1,
                resizeHeightPx = 1,
                quantizationLevels = 16,
                grayscale = false
            ),
            enabled = true
        )

        repository.runSettingMaintenance(
            settingId = setting.settingId,
            rebuild = true,
            shouldContinue = { true },
            onProgress = {}
        )
        val cluster = repository.listClusters(setting.settingId).singleOrNull()
        assertNotNull(cluster)
        return SimilarityFixture(
            repository = repository,
            settingId = setting.settingId,
            clusterId = cluster!!.clusterId
        )
    }

    private fun videoFile(name: String): File {
        val file = File(tempDir, name)
        file.writeText("video")
        return file
    }

    private fun entity(file: File): CachedFileEntity {
        return CachedFileEntity(
            normalizedPath = file.normalizedPathForTest(),
            path = file.absolutePath,
            sizeBytes = file.length(),
            lastModifiedMillis = file.lastModified(),
            hashHex = null
        )
    }

    private fun File.normalizedPathForTest(): String {
        return absolutePath.replace('\\', '/').lowercase()
    }

    private fun clearSettings() {
        context.getSharedPreferences("cached_dupe_scanner", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    private fun scrollUntilText(text: String, substring: Boolean = false) {
        repeat(8) {
            composeRule.waitForIdle()
            if (
                composeRule.onAllNodesWithText(text, substring = substring)
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            ) {
                return
            }
            composeRule.onRoot().performTouchInput { swipeUp() }
        }
        composeRule.waitUntil(1_000) {
            composeRule.onAllNodesWithText(text, substring = substring)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    private data class SimilarityFixture(
        val repository: SimilaritySettingsRepository,
        val settingId: Long,
        val clusterId: Long
    )

    private class FakeSignatureExtractor(
        private val signatures: Map<String, String>
    ) : VideoFrameSignatureExtractor {
        override fun signature(
            file: File,
            mediaScope: SimilarityMediaScope,
            step: ExactThumbnailHashStep,
            shouldContinue: () -> Boolean
        ): String? {
            return signatures[file.absolutePath]
        }
    }

    private class FakeDurationExtractor : VideoDurationExtractor {
        override fun durationMillis(
            file: File,
            shouldContinue: () -> Boolean
        ): Long? {
            return null
        }
    }
}
