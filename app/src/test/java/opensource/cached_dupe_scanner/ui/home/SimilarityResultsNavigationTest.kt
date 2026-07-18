package opensource.cached_dupe_scanner.ui.home

import android.content.Context
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext
import opensource.cached_dupe_scanner.cache.CacheDatabase
import opensource.cached_dupe_scanner.cache.CachedFileEntity
import opensource.cached_dupe_scanner.core.ExactThumbnailHashStep
import opensource.cached_dupe_scanner.core.MediaDimensions
import opensource.cached_dupe_scanner.core.MediaDimensionsExtractor
import opensource.cached_dupe_scanner.core.PathNormalizer
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
import opensource.cached_dupe_scanner.tasks.TaskArea
import opensource.cached_dupe_scanner.tasks.TaskCoordinator
import opensource.cached_dupe_scanner.tasks.TaskKind
import opensource.cached_dupe_scanner.tasks.TaskStatus
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
        appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
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
        val settingName = fixture.repository.listSettings()
            .single { setting -> setting.settingId == fixture.settingId }
            .displayName
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

        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText(settingName)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        scrollUntilText("Open results")
        composeRule.onNodeWithText("Open results").performClick()

        composeRule.runOnIdle {
            assertEquals(fixture.settingId, openedSettingId)
        }
    }

    @Test
    fun settingDetailIncrementalClearRunsTrackedTaskAndKeepsSetting() {
        val fixture = createSimilarityFixture()
        val taskCoordinator = TaskCoordinator()
        val settingName = fixture.repository.listSettings()
            .first { setting -> setting.settingId == fixture.settingId }
            .displayName

        composeRule.setContent {
            SimilaritySettingDetailScreen(
                repository = fixture.repository,
                appScope = appScope,
                taskCoordinator = taskCoordinator,
                notificationController = TaskNotificationController(context),
                settingId = fixture.settingId,
                refreshVersion = 0,
                onChanged = {},
                onBack = {},
                onOpenGroups = {},
                modifier = Modifier.height(1_200.dp)
            )
        }

        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText(settingName)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        scrollUntilTag(
            listTag = "similarity-setting-detail-list",
            targetTag = "similarity-incremental-clear"
        )
        composeRule.onNodeWithTag("similarity-incremental-clear")
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithText("Incrementally clear these results?").assertExists()
        composeRule.onNodeWithText("Clear incrementally").performClick()

        composeRule.waitUntil(5_000) {
            taskCoordinator.terminalSummary(TaskArea.Similarity)?.status == TaskStatus.Completed
        }
        composeRule.runOnIdle {
            val terminal = requireNotNull(taskCoordinator.terminalSummary(TaskArea.Similarity))
            assertEquals(TaskKind.SimilarityClear, terminal.kind)
            assertEquals(0, fixture.repository.getClusterSummary(fixture.settingId).clusterCount)
            assertTrue(fixture.repository.listSettings().any { setting -> setting.settingId == fixture.settingId })
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
        composeRule.onNodeWithText("Rule 1 - File name")
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithText("Member match").performScrollTo().fetchSemanticsNode()
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
    fun groupsScreenShowsProgressWhileFilterMetadataIsRecalculated() {
        val fixture = createSimilarityFixture()
        val similarityDao = database.similaritySettingsDao()
        listOf(fixture.firstFile, fixture.secondFile).forEach { file ->
            val fileId = requireNotNull(
                database.fileCacheDao().getByNormalizedPath(file.normalizedPathForTest())
            ).fileId
            val stored = requireNotNull(similarityDao.getSettingFile(fixture.settingId, fileId))
            similarityDao.upsertSettingFiles(
                listOf(
                    stored.copy(
                        widthPixels = null,
                        heightPixels = null,
                        dimensionsChecked = false
                    )
                )
            )
        }
        val dimensionsExtractor = BlockingMediaDimensionsExtractor()
        val filteringRepository = SimilaritySettingsRepository(
            database = database,
            fileDao = database.fileCacheDao(),
            similarityDao = similarityDao,
            frameSignatureExtractor = FakeSignatureExtractor(emptyMap()),
            durationExtractor = FakeDurationExtractor(emptyMap()),
            mediaDimensionsExtractor = dimensionsExtractor
        )
        val settingsStore = AppSettingsStore(context)
        settingsStore.setSimilarityFilterDefinitionJson(
            resultsFilterDefinitionToJson(
                ResultsFilterDefinition(
                    clusters = listOf(
                        createResultsFilterCluster().copy(
                            rules = listOf(createResultsFilterRule(ResultsFilterTarget.SameResolution))
                        )
                    )
                )
            )
        )

        composeRule.setContent {
            SimilaritySettingGroupsScreen(
                repository = filteringRepository,
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

        try {
            composeRule.waitUntil(5_000) {
                dimensionsExtractor.firstExtractionEntered.count == 0L
            }
            composeRule.onNodeWithTag("similarity-filter-resolution-progress").assertExists()
            composeRule.onNodeWithText("Recalculating filter metadata").assertExists()
            composeRule.onNodeWithText("Media dimensions: 0/2").assertExists()
            composeRule.onNodeWithText("Speed:", substring = true).assertExists()
            composeRule.onNodeWithText("Elapsed:", substring = true).assertExists()
            composeRule.onNodeWithText("Remaining:", substring = true).assertExists()
            composeRule.onNodeWithText(fixture.firstFile.absolutePath).assertExists()
        } finally {
            dimensionsExtractor.releaseFirstExtraction.countDown()
        }

        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("similarity-filter-resolution-progress")
                .fetchSemanticsNodes()
                .isEmpty()
        }
        composeRule.onNodeWithTag("similarity-cluster:${fixture.clusterId}").assertExists()
    }

    @Test
    fun similarityFilterEditorUsesOneDurationInputWithAUnitSelector() {
        var editedRule: ResultsFilterRule? = null

        composeRule.setContent {
            val definition = remember {
                mutableStateOf(
                    ResultsFilterDefinition(
                        clusters = listOf(
                            createResultsFilterCluster().copy(
                                rules = listOf(
                                    createResultsFilterRule(ResultsFilterTarget.DurationFromAverage)
                                )
                            )
                        )
                    )
                )
            }
            SimilarityFilterScreen(
                definition = definition.value,
                onDefinitionChange = { updated ->
                    definition.value = updated
                    editedRule = updated.clusters.single().rules.single()
                },
                onBack = {},
                onApply = {}
            )
        }

        scrollUntilText("Rule 1 - All near average duration")
        composeRule.onNodeWithText("Rule 1 - All near average duration")
            .performScrollTo()
            .performClick()
        scrollUntilText("Tolerance")
        composeRule.onNodeWithTag("duration-average-tolerance")
            .performScrollTo()
            .performTextInput("375")
        composeRule.onNodeWithText("ms")
            .performScrollTo()
            .performClick()

        composeRule.runOnIdle {
            assertEquals(ResultsFilterTarget.DurationFromAverage, editedRule?.target)
            assertEquals("", editedRule?.durationToleranceSeconds)
            assertEquals("375", editedRule?.durationToleranceMilliseconds)
            assertEquals(375L, editedRule?.durationToleranceMillis())
        }
        assertTrue(
            composeRule.onAllNodesWithTag("duration-average-seconds")
                .fetchSemanticsNodes()
                .isEmpty()
        )
        assertTrue(
            composeRule.onAllNodesWithTag("duration-average-milliseconds")
                .fetchSemanticsNodes()
                .isEmpty()
        )
    }

    @Test
    fun deleteKeepsMemorySnapshotAndMaintenanceReentryUsesPersistedState() {
        val fixture = createSimilarityFixture()
        val taskCoordinator = TaskCoordinator()

        composeRule.setContent {
            SimilarityDeleteNavigationHarness(
                fixture = fixture,
                settingsStore = AppSettingsStore(context),
                taskCoordinator = taskCoordinator,
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
        val resultsBackNodes = composeRule.onAllNodesWithContentDescription("Back")
        resultsBackNodes[resultsBackNodes.fetchSemanticsNodes().lastIndex].performClick()

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
    fun bulkDeleteKeepsColoredParentSnapshotUntilResultsAreReopened() {
        val fixture = createSimilarityFixture()
        val taskCoordinator = TaskCoordinator()
        val resultsOpen = mutableStateOf(true)

        composeRule.setContent {
            SimilarityDeleteNavigationHarness(
                fixture = fixture,
                settingsStore = AppSettingsStore(context),
                taskCoordinator = taskCoordinator,
                resultsOpenState = resultsOpen,
                modifier = Modifier.height(1_200.dp)
            )
        }

        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("similarity-cluster:${fixture.clusterId}")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeRule.onNodeWithContentDescription("Menu").performClick()
        composeRule.onNodeWithText("Bulk delete").performClick()
        composeRule.onNodeWithText("Keep by modified time").performClick()
        composeRule.onNodeWithText("Build preview").performClick()
        scrollUntilListIndex("bulk-delete-keep-modified-list", 8)
        composeRule.onNodeWithText("1 groups and 1 files are ready.").fetchSemanticsNode()
        composeRule.onNodeWithText("Delete matching files").performClick()
        composeRule.onNodeWithText("Delete").performClick()

        composeRule.waitUntil(5_000) {
            taskCoordinator.terminalSummary(TaskArea.Trash) != null
        }
        composeRule.waitForIdle()
        val terminal = requireNotNull(taskCoordinator.terminalSummary(TaskArea.Trash))
        assertEquals(terminal.detail, TaskStatus.Completed, terminal.status)
        assertTrue(fixture.secondFile.exists().not())
        assertTrue(fixture.firstFile.exists())
        assertEquals(0, fixture.repository.getClusterSummary(fixture.settingId).clusterCount)

        composeRule.onNodeWithTag("bulk-delete-keep-modified-list")
            .performScrollToIndex(3)
        composeRule.onNodeWithText("Back").performClick()
        val catalogBackNodes = composeRule.onAllNodesWithContentDescription("Back")
        catalogBackNodes[catalogBackNodes.fetchSemanticsNodes().lastIndex].performClick()
        composeRule.waitForIdle()

        val clusterNode = composeRule.onNodeWithTag("similarity-cluster:${fixture.clusterId}")
        clusterNode.fetchSemanticsNode()
        composeRule.waitUntil(5_000) {
            clusterNode.fetchSemanticsNode().config[SemanticsProperties.StateDescription] ==
                "Contains deleted files"
        }

        composeRule.runOnIdle { resultsOpen.value = false }
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
        taskCoordinator: TaskCoordinator,
        resultsOpenState: MutableState<Boolean>? = null,
        modifier: Modifier
    ) {
        val rememberedResultsOpen = remember { mutableStateOf(true) }
        val resultsOpen = resultsOpenState ?: rememberedResultsOpen
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
                onBulkDeleteFile = { file ->
                    val moved = withContext(Dispatchers.IO) {
                        fixture.trashController.moveToTrash(file.normalizedPath).success
                    }
                    if (moved) {
                        deletedPaths.value = deletedPaths.value + file.normalizedPath
                    }
                    moved
                },
                taskScope = appScope,
                taskCoordinator = taskCoordinator,
                notificationController = remember { TaskNotificationController(context) },
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

    @Test
    fun durationBulkDeleteScreenAppliesLongestAndNewestSelections() {
        val fixture = createSimilarityFixture(
            durationsByName = mapOf(
                "first.mp4" to 10_000L,
                "second.mp4" to 20_000L
            )
        )

        composeRule.setContent {
            SimilarityDeleteNavigationHarness(
                fixture = fixture,
                settingsStore = AppSettingsStore(context),
                taskCoordinator = TaskCoordinator(),
                modifier = Modifier.height(1_200.dp)
            )
        }

        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("similarity-cluster:${fixture.clusterId}")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeRule.onNodeWithContentDescription("Menu").performClick()
        composeRule.onNodeWithText("Bulk delete").performClick()
        composeRule.onNodeWithTag("bulk-delete-catalog-list")
            .performScrollToIndex(4)
        composeRule.onNodeWithText("Keep by video duration").performClick()
        composeRule.onNodeWithText("Duration to keep").fetchSemanticsNode()
        composeRule.onNodeWithText("Equal-duration fallback").fetchSemanticsNode()

        composeRule.onNodeWithText("Keep longest").performClick()
        composeRule.onNodeWithText("Keep newest").performClick()

        composeRule.onNodeWithText(
            "The longest-duration video survives. Equal durations keep the newest modified file."
        ).fetchSemanticsNode()
        composeRule.onNodeWithTag("bulk-delete-keep-duration-list")
            .performScrollToIndex(3)
        composeRule.onNodeWithText("Build preview").performClick()
        scrollUntilListIndex("bulk-delete-keep-duration-list", 7)

        composeRule.onNodeWithText(
            "Keep: 20s | ${fixture.secondFile.normalizedPathForTest()}"
        ).fetchSemanticsNode()
        composeRule.onNodeWithText(
            "Delete: 10s | ${fixture.firstFile.normalizedPathForTest()}"
        ).fetchSemanticsNode()
    }

    @Test
    fun textBulkDeleteScreenBuildsExactKeepCountPreview() {
        val fixture = createSimilarityFixture()

        composeRule.setContent {
            SimilarityDeleteNavigationHarness(
                fixture = fixture,
                settingsStore = AppSettingsStore(context),
                taskCoordinator = TaskCoordinator(),
                modifier = Modifier.height(1_200.dp)
            )
        }

        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("similarity-cluster:${fixture.clusterId}")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeRule.onNodeWithContentDescription("Menu").performClick()
        composeRule.onNodeWithText("Bulk delete").performClick()
        composeRule.onNodeWithText("Keep by text match").performClick()
        composeRule.onNodeWithText("Keep match").performClick()
        composeRule.onNodeWithText("Keep N").performClick()
        composeRule.onNodeWithTag("bulk-delete-text-keep-count")
            .performTextReplacement("1")
        composeRule.onNodeWithTag("bulk-delete-text-phrase")
            .performTextInput("first")
        composeRule.onNodeWithTag("bulk-delete-keep-text-list")
            .performScrollToIndex(3)
        composeRule.onNodeWithText("Build preview").performClick()
        scrollUntilListIndex("bulk-delete-keep-text-list", 7)

        composeRule.onNodeWithText(
            "Keep: ${fixture.firstFile.normalizedPathForTest()}"
        ).fetchSemanticsNode()
        composeRule.onNodeWithText(
            "Delete: ${fixture.secondFile.normalizedPathForTest()}"
        ).fetchSemanticsNode()
    }

    private fun createSimilarityFixture(
        firstContents: String = "video",
        secondContents: String = "video",
        durationsByName: Map<String, Long> = emptyMap()
    ): SimilarityFixture {
        val first = videoFile("first.mp4", firstContents)
        val second = videoFile("second.mp4", secondContents)
        check(first.setLastModified(1_000L))
        check(second.setLastModified(2_000L))
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
            durationExtractor = FakeDurationExtractor(durationsByName)
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
            secondFile = second,
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
        return PathNormalizer.normalize(absolutePath)
    }

    private fun clearSettings() {
        context.getSharedPreferences("cached_dupe_scanner", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    private fun scrollUntilText(text: String, substring: Boolean = false) {
        repeat(16) {
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
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText(text, substring = substring)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    private fun scrollUntilTag(listTag: String, targetTag: String) {
        composeRule.onNodeWithTag(listTag)
            .performScrollToNode(hasTestTag(targetTag))
    }

    private fun scrollUntilListIndex(tag: String, index: Int) {
        composeRule.waitUntil(10_000) {
            runCatching {
                composeRule.onNodeWithTag(tag).performScrollToIndex(index)
            }.isSuccess
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
        val secondFile: File,
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

    private class FakeDurationExtractor(
        private val durationsByName: Map<String, Long>
    ) : VideoDurationExtractor {
        override fun durationMillis(
            file: File,
            shouldContinue: () -> Boolean
        ): Long? {
            return durationsByName[file.name]
        }
    }

    private class BlockingMediaDimensionsExtractor : MediaDimensionsExtractor {
        val firstExtractionEntered = CountDownLatch(1)
        val releaseFirstExtraction = CountDownLatch(1)

        override fun dimensions(
            file: File,
            mediaScope: SimilarityMediaScope,
            shouldContinue: () -> Boolean
        ): MediaDimensions? {
            firstExtractionEntered.countDown()
            check(releaseFirstExtraction.await(5, TimeUnit.SECONDS))
            return MediaDimensions(widthPixels = 1920, heightPixels = 1080)
        }
    }
}
