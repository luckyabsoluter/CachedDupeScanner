package opensource.cached_dupe_scanner.ui.home

import android.content.Context
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
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
import kotlinx.coroutines.withContext
import opensource.cached_dupe_scanner.cache.CacheDatabase
import opensource.cached_dupe_scanner.cache.CachedFileEntity
import opensource.cached_dupe_scanner.core.ExactThumbnailHashStep
import opensource.cached_dupe_scanner.core.SimilarityMediaScope
import opensource.cached_dupe_scanner.core.VideoDurationExtractor
import opensource.cached_dupe_scanner.core.VideoFrameSignatureExtractor
import opensource.cached_dupe_scanner.notifications.TaskNotificationController
import opensource.cached_dupe_scanner.storage.AppSettingsStore
import opensource.cached_dupe_scanner.storage.ScanHistoryRepository
import opensource.cached_dupe_scanner.storage.SimilaritySettingsRepository
import opensource.cached_dupe_scanner.storage.StorageRootProvider
import opensource.cached_dupe_scanner.storage.StorageRootResolver
import opensource.cached_dupe_scanner.storage.TrashController
import opensource.cached_dupe_scanner.storage.TrashRepository
import opensource.cached_dupe_scanner.tasks.TaskCoordinator
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
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
                deletedPaths = emptySet(),
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

    @Test
    fun groupsScreenAppliesStoredMemberFilterAndOpensSharedEditor() {
        val fixture = createSimilarityFixture()
        val settingsStore = AppSettingsStore(context)
        val definition = ResultsFilterDefinition(
            clusters = listOf(
                createResultsFilterCluster().copy(
                    rules = listOf(
                        createResultsFilterRule(ResultsFilterTarget.FileName).copy(
                            textOperator = ResultsFilterTextOperator.Equals,
                            value = "not-present.mp4"
                        )
                    )
                )
            )
        )
        settingsStore.setSimilarityFilterDefinitionJson(resultsFilterDefinitionToJson(definition))

        composeRule.setContent {
            SimilaritySettingGroupsScreen(
                repository = fixture.repository,
                settingsStore = settingsStore,
                keepLoadedThumbnailsInMemory = false,
                thumbnailSizeScale = 1f,
                rememberedPreviewCache = mutableStateMapOf(),
                showFullPaths = false,
                deletedPaths = emptySet(),
                settingId = fixture.settingId,
                refreshVersion = 0,
                onBack = {},
                onOpenCluster = { _, _ -> },
                modifier = Modifier.height(1_200.dp)
            )
        }

        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("No similarity groups match", substring = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        assertTrue(
            composeRule.onAllNodesWithTag("similarity-cluster:${fixture.clusterId}")
                .fetchSemanticsNodes()
                .isEmpty()
        )
        composeRule.onNodeWithContentDescription("Menu").performClick()
        composeRule.onNodeWithText("Filters (1)").performClick()
        composeRule.onNodeWithText("Similarity filters").fetchSemanticsNode()
    }

    @Test
    fun groupsScreenAppliesSameFileSizeFilterToStoredMembers() {
        val fixture = createSimilarityFixture(secondContents = "longer-video-content")
        val settingsStore = AppSettingsStore(context)
        val definition = ResultsFilterDefinition(
            clusters = listOf(
                createResultsFilterCluster().copy(
                    rules = listOf(createResultsFilterRule(ResultsFilterTarget.SameFileSize))
                )
            )
        )
        settingsStore.setSimilarityFilterDefinitionJson(resultsFilterDefinitionToJson(definition))

        composeRule.setContent {
            SimilaritySettingGroupsScreen(
                repository = fixture.repository,
                settingsStore = settingsStore,
                keepLoadedThumbnailsInMemory = false,
                thumbnailSizeScale = 1f,
                rememberedPreviewCache = mutableStateMapOf(),
                showFullPaths = false,
                deletedPaths = emptySet(),
                settingId = fixture.settingId,
                refreshVersion = 0,
                onBack = {},
                onOpenCluster = { _, _ -> },
                modifier = Modifier.height(1_200.dp)
            )
        }

        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("No similarity groups match", substring = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        assertTrue(
            composeRule.onAllNodesWithTag("similarity-cluster:${fixture.clusterId}")
                .fetchSemanticsNodes()
                .isEmpty()
        )
    }

    @Test
    fun deleteKeepsMemorySnapshotAndMaintenanceReentryUsesPersistedState() {
        val fixture = createSimilarityFixture()

        composeRule.setContent {
            SimilarityDeleteNavigationHarness(
                fixture = fixture,
                settingsStore = AppSettingsStore(context),
                modifier = Modifier.height(1_200.dp)
            )
        }

        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("2 files", substring = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeRule.onNodeWithTag("similarity-cluster:${fixture.clusterId}").performClick()

        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("Similarity group detail")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        scrollSimilarityDetailToMember(fixture.clusterId, fixture.firstFile.normalizedPathForTest())
        composeRule.onNodeWithTag(
            "similarity-member:${fixture.firstFile.normalizedPathForTest()}"
        ).performClick()
        composeRule.onNodeWithText("Delete").performClick()
        composeRule.onNodeWithText("Move").performClick()

        composeRule.waitUntil(5_000) { !fixture.firstFile.exists() }
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("File details")
                .fetchSemanticsNodes()
                .isEmpty()
        }
        composeRule.onNodeWithText(fixture.firstFile.name).fetchSemanticsNode()
        assertTrue(
            composeRule.onAllNodesWithText("Missing", substring = true)
                .fetchSemanticsNodes()
                .isEmpty()
        )

        returnFromSimilarityDetail(fixture.clusterId)

        composeRule.waitForIdle()
        val clusterNode = composeRule.onNodeWithTag("similarity-cluster:${fixture.clusterId}")
        clusterNode.fetchSemanticsNode()
        composeRule.waitUntil(5_000) {
            clusterNode.fetchSemanticsNode().config[SemanticsProperties.StateDescription] ==
                "Contains deleted files"
        }
        assertEquals(0, fixture.repository.getClusterSummary(fixture.settingId).clusterCount)

        clusterNode.performClick()
        scrollSimilarityDetailToMember(fixture.clusterId, fixture.firstFile.normalizedPathForTest())
        composeRule.onNodeWithTag(
            "similarity-member:${fixture.firstFile.normalizedPathForTest()}"
        ).fetchSemanticsNode()
        assertTrue(
            composeRule.onAllNodesWithText("Missing", substring = true)
                .fetchSemanticsNodes()
                .isEmpty()
        )

        returnFromSimilarityDetail(fixture.clusterId)
        composeRule.onNodeWithContentDescription("Back").performClick()

        val maintenance = fixture.historyRepository.runMaintenance(
            deleteMissing = true,
            rehashStale = false,
            rehashMissing = false,
            shouldContinue = { true },
            onProgress = {}
        )
        assertEquals(1, maintenance.total)
        assertEquals(1, maintenance.processed)
        assertEquals(0, maintenance.deleted)
        fixture.repository.generateEnabledResults(shouldContinue = { true }, onProgress = {})
        assertEquals(0, fixture.repository.getClusterSummary(fixture.settingId).clusterCount)

        composeRule.onNodeWithText("Reopen similarity results").performClick()

        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("No similarity groups found", substring = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        assertTrue(
            composeRule.onAllNodesWithTag("similarity-cluster:${fixture.clusterId}")
                .fetchSemanticsNodes()
                .isEmpty()
        )
    }

    @Test
    fun videoPreviewToggleKeepsDetailMenuOpen() {
        val fixture = createSimilarityFixture()
        val previewEnabled = mutableStateOf(false)
        val thumbnailCache = mutableStateMapOf<String, ImageBitmap>()
        val videoPreviewCache = mutableStateMapOf<String, ImageBitmap>()

        composeRule.setContent {
            SimilarityClusterDetailScreen(
                repository = fixture.repository,
                settingsStore = AppSettingsStore(context),
                keepLoadedThumbnailsInMemory = false,
                keepLoadedVideoPreviewsInMemory = false,
                snapVideoPreviewFramesToWidth = false,
                videoPreviewLineCount = 1,
                thumbnailSizeScale = 1f,
                videoPreviewSizeScale = 1f,
                rememberedPreviewCache = thumbnailCache,
                rememberedVideoPreviewCache = videoPreviewCache,
                showFullPaths = false,
                showVideoPreviews = previewEnabled.value,
                showVideoPreviewDurations = false,
                showVideoPreviewResolutions = false,
                onShowVideoPreviewsChange = { enabled -> previewEnabled.value = enabled },
                onShowVideoPreviewDurationsChange = {},
                onShowVideoPreviewResolutionsChange = {},
                deletedPaths = emptySet(),
                onDeleteFile = null,
                settingId = fixture.settingId,
                clusterId = fixture.clusterId,
                onBack = {},
                modifier = Modifier.height(1_200.dp)
            )
        }

        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("2 files", substring = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeRule.onNodeWithContentDescription("Menu").performClick()
        composeRule.onNodeWithText("Video preview").performClick()

        composeRule.runOnIdle { assertTrue(previewEnabled.value) }
        composeRule.onNodeWithText("Video duration").assertExists()
    }

    @Composable
    private fun SimilarityDeleteNavigationHarness(
        fixture: SimilarityFixture,
        settingsStore: AppSettingsStore,
        modifier: Modifier
    ) {
        val resultsOpen = remember { mutableStateOf(true) }
        val deletedPaths = remember { mutableStateOf<Set<String>>(emptySet()) }
        val thumbnailCache = remember { mutableStateMapOf<String, ImageBitmap>() }
        val videoPreviewCache = remember { mutableStateMapOf<String, ImageBitmap>() }

        if (resultsOpen.value) {
            SimilaritySettingResultsScreen(
                repository = fixture.repository,
                settingsStore = settingsStore,
                keepLoadedThumbnailsInMemory = false,
                keepLoadedVideoPreviewsInMemory = false,
                snapVideoPreviewFramesToWidth = false,
                videoPreviewLineCount = 1,
                thumbnailSizeScale = 1f,
                videoPreviewSizeScale = 1f,
                rememberedPreviewCache = thumbnailCache,
                rememberedVideoPreviewCache = videoPreviewCache,
                showFullPaths = false,
                showVideoPreviews = false,
                showVideoPreviewDurations = false,
                showVideoPreviewResolutions = false,
                onShowVideoPreviewsChange = {},
                onShowVideoPreviewDurationsChange = {},
                onShowVideoPreviewResolutionsChange = {},
                deletedPaths = deletedPaths.value,
                onDeleteFile = { file ->
                    val moved = withContext(Dispatchers.IO) {
                        fixture.trashController.moveToTrash(file.normalizedPath).success
                    }
                    if (moved) {
                        deletedPaths.value = deletedPaths.value + file.normalizedPath
                    }
                    moved
                },
                settingId = fixture.settingId,
                refreshVersion = 0,
                onBack = { resultsOpen.value = false },
                modifier = modifier
            )
        } else {
            Button(onClick = { resultsOpen.value = true }) {
                androidx.compose.material3.Text("Reopen similarity results")
            }
        }
    }

    private fun createSimilarityFixture(
        firstContents: String = "video",
        secondContents: String = "video"
    ): SimilarityFixture {
        val first = videoFile("first.mp4", firstContents)
        val second = videoFile("second.mp4", secondContents)
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
        val historyRepository = ScanHistoryRepository(
            dao = database.fileCacheDao(),
            settingsStore = AppSettingsStore(context),
            groupDao = database.duplicateGroupDao(),
            database = database,
            cacheMutationObserver = repository
        )
        val trashController = TrashController(
            context = context,
            database = database,
            historyRepo = historyRepository,
            trashRepo = TrashRepository(database.trashDao()),
            storageRootProvider = object : StorageRootProvider {
                override fun resolve(context: Context, absolutePath: String): StorageRootResolver.Root {
                    return StorageRootResolver.Root(tempDir.absolutePath)
                }
            }
        )
        return SimilarityFixture(
            repository = repository,
            historyRepository = historyRepository,
            settingId = setting.settingId,
            clusterId = cluster!!.clusterId,
            firstFile = first,
            trashController = trashController
        )
    }

    private fun videoFile(name: String, contents: String): File {
        val file = File(tempDir, name)
        file.writeText(contents)
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

    private fun scrollSimilarityDetailToMember(clusterId: Long, normalizedPath: String) {
        composeRule.onNodeWithTag("similarity-detail-list:$clusterId")
            .performScrollToIndex(3)
        composeRule.onNodeWithTag("similarity-member:$normalizedPath").fetchSemanticsNode()
    }

    private fun returnFromSimilarityDetail(clusterId: Long) {
        composeRule.onNodeWithTag("similarity-detail-list:$clusterId")
            .performScrollToIndex(0)
        val backNodes = composeRule.onAllNodesWithContentDescription("Back")
        val detailBackIndex = backNodes.fetchSemanticsNodes().lastIndex
        backNodes[detailBackIndex].performClick()
        composeRule.waitForIdle()
    }

    private data class SimilarityFixture(
        val repository: SimilaritySettingsRepository,
        val historyRepository: ScanHistoryRepository,
        val settingId: Long,
        val clusterId: Long,
        val firstFile: File,
        val trashController: TrashController
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
