package opensource.cached_dupe_scanner

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.toMutableStateList
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import opensource.cached_dupe_scanner.cache.CacheDatabase
import opensource.cached_dupe_scanner.cache.CacheStore
import opensource.cached_dupe_scanner.cache.buildCacheDatabase
import opensource.cached_dupe_scanner.core.ResultSortKey
import opensource.cached_dupe_scanner.core.ScanResult
import opensource.cached_dupe_scanner.core.ScanResultViewFilter
import opensource.cached_dupe_scanner.core.SortDirection
import opensource.cached_dupe_scanner.engine.IncrementalScanner
import opensource.cached_dupe_scanner.notifications.TaskNotificationController
import opensource.cached_dupe_scanner.storage.AppSettingsStore
import opensource.cached_dupe_scanner.storage.PagedFileRepository
import opensource.cached_dupe_scanner.storage.ResultsDbRepository
import opensource.cached_dupe_scanner.storage.ScanHistoryRepository
import opensource.cached_dupe_scanner.storage.ScanReportRepository
import opensource.cached_dupe_scanner.storage.SimilaritySettingsRepository
import opensource.cached_dupe_scanner.storage.TrashController
import opensource.cached_dupe_scanner.storage.TrashRepository
import opensource.cached_dupe_scanner.tasks.TaskArea
import opensource.cached_dupe_scanner.tasks.TaskCoordinator
import opensource.cached_dupe_scanner.ui.components.MemoryUsageOverlay
import opensource.cached_dupe_scanner.ui.components.TaskBannerStack
import opensource.cached_dupe_scanner.ui.components.Spacing
import opensource.cached_dupe_scanner.ui.home.AboutScreen
import opensource.cached_dupe_scanner.ui.home.DashboardScreen
import opensource.cached_dupe_scanner.ui.home.DbManagementScreen
import opensource.cached_dupe_scanner.ui.home.DbManagementUiState
import opensource.cached_dupe_scanner.ui.home.FilesScreenDb
import opensource.cached_dupe_scanner.ui.home.PermissionScreen
import opensource.cached_dupe_scanner.ui.home.ReportsScreen
import opensource.cached_dupe_scanner.ui.home.ResultsScreenDb
import opensource.cached_dupe_scanner.ui.home.ScanCommandScreen
import opensource.cached_dupe_scanner.ui.home.SettingsScreen
import opensource.cached_dupe_scanner.ui.home.SimilarityClusterDetailScreen
import opensource.cached_dupe_scanner.ui.home.SimilarityDurationSettingScreen
import opensource.cached_dupe_scanner.ui.home.SimilarityExactThumbnailSettingScreen
import opensource.cached_dupe_scanner.ui.home.SimilaritySettingCreateScreen
import opensource.cached_dupe_scanner.ui.home.SimilaritySettingDetailScreen
import opensource.cached_dupe_scanner.ui.home.SimilaritySettingResultsScreen
import opensource.cached_dupe_scanner.ui.home.SimilaritySettingsScreen
import opensource.cached_dupe_scanner.ui.home.TargetsScreen
import opensource.cached_dupe_scanner.ui.home.TrashScreen
import opensource.cached_dupe_scanner.ui.home.similarity.runScanIntegratedSimilarityGeneration
import opensource.cached_dupe_scanner.ui.results.ScanUiState
import opensource.cached_dupe_scanner.ui.theme.CachedDupeScannerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        updateSystemBars()
        setContent {
            CachedDupeScannerTheme {
                val state = remember { mutableStateOf<ScanUiState>(ScanUiState.Idle) }
                val deletedPaths = remember { mutableStateOf(setOf<String>()) }
                val displayResult = remember { mutableStateOf<ScanResult?>(null) }
                val sortSettingsVersion = remember { mutableStateOf(0) }
                val filesClearVersion = remember { mutableStateOf(0) }
                val filesRefreshVersion = remember { mutableStateOf(0) }
                val targetsVersion = remember { mutableStateOf(0) }
                val reportsRefreshVersion = remember { mutableStateOf(0) }
                val resultsRefreshVersion = remember { mutableStateOf(0) }
                val similarityRefreshVersion = remember { mutableStateOf(0) }
                val settingsVersion = remember { mutableStateOf(0) }
                val selectedResultsGroupIndex = rememberSaveable { mutableStateOf<Int?>(null) }
                val similarityShowVideoPreviews = rememberSaveable { mutableStateOf(false) }
                val similarityShowVideoPreviewDurations = rememberSaveable { mutableStateOf(false) }
                val similarityShowVideoPreviewResolutions = rememberSaveable { mutableStateOf(false) }
                val context = LocalContext.current
                val settingsStore = remember { AppSettingsStore(context) }
                val settingsSnapshot = remember(settingsVersion.value) { settingsStore.load() }
                val rememberedThumbnailCache = remember { mutableStateMapOf<String, ImageBitmap>() }
                val rememberedVideoPreviewCache = remember { mutableStateMapOf<String, ImageBitmap>() }
                val scope = rememberCoroutineScope()
                val dbManagementUiState = remember { DbManagementUiState() }
                val screenCache = rememberSaveable(saver = ScreenBackStackSaver) {
                    mutableStateListOf(Screen.Dashboard)
                }
                val backStack = rememberSaveable(saver = ScreenBackStackSaver) {
                    mutableStateListOf(Screen.Dashboard)
                }
                val taskCoordinator = remember { AppWorkScopes.taskCoordinator(context) }
                val notificationController = remember { AppWorkScopes.notificationController(context) }
                val notificationPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission(),
                    onResult = {}
                )
                LaunchedEffect(Unit) {
                    if (
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.POST_NOTIFICATIONS
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
                val database = remember { buildCacheDatabase(context) }
                val scanCacheStore = remember { CacheStore(database.fileCacheDao()) }
                val scanner = remember { IncrementalScanner(scanCacheStore) }
                val similarityRepo = remember {
                    SimilaritySettingsRepository(
                        database = database,
                        fileDao = database.fileCacheDao(),
                        similarityDao = database.similaritySettingsDao()
                    )
                }
                val historyRepo = remember {
                    ScanHistoryRepository(
                        dao = database.fileCacheDao(),
                        settingsStore = settingsStore,
                        groupDao = database.duplicateGroupDao(),
                        database = database,
                        cacheMutationObserver = similarityRepo
                    )
                }
                val reportRepo = remember { ScanReportRepository(database.scanReportDao()) }
                val trashRepo = remember { TrashRepository(database.trashDao()) }
                val trashController = remember { TrashController(context, database, historyRepo, trashRepo) }
                val resultsRepo = remember { ResultsDbRepository(database.fileCacheDao(), database.duplicateGroupDao()) }
                val fileRepo = remember { PagedFileRepository(database.fileCacheDao()) }

                LaunchedEffect(Unit) {
                    // DB-backed screens load data on demand; avoid pulling the full cache into RAM on startup.
                }

                LaunchedEffect(settingsSnapshot.keepLoadedThumbnailsInMemory) {
                    if (!settingsSnapshot.keepLoadedThumbnailsInMemory) {
                        rememberedThumbnailCache.clear()
                    }
                }

                LaunchedEffect(settingsSnapshot.keepLoadedVideoPreviewsInMemory) {
                    if (!settingsSnapshot.keepLoadedVideoPreviewsInMemory) {
                        rememberedVideoPreviewCache.clear()
                    }
                }

                suspend fun runScanSimilarityIfNeeded() {
                    runScanIntegratedSimilarityGeneration(
                        repository = similarityRepo,
                        taskCoordinator = taskCoordinator,
                        notificationController = notificationController,
                        shouldContinue = { taskCoordinator.isAreaBusy(TaskArea.Scan) },
                        onFinished = {}
                    )
                }

                suspend fun handleScanComplete(scan: ScanResult) {
                    Log.d("MainActivity", "Scan complete callback received")
                    val persisted = runCatching {
                        withContext(Dispatchers.IO) {
                            Log.d("MainActivity", "Persisting scan to DB")
                            historyRepo.recordScan(scan)
                        }
                    }.onFailure { error ->
                        Log.e("MainActivity", "Failed to persist scan results", error)
                    }
                    if (persisted.isSuccess) {
                        runScanSimilarityIfNeeded()
                    }
                    state.value = ScanUiState.Success(scan)
                    deletedPaths.value = emptySet()
                    filesRefreshVersion.value += 1
                    selectedResultsGroupIndex.value = null
                    resultsRefreshVersion.value += 1
                    similarityRefreshVersion.value += 1
                }

                fun refreshSimilarityFromCache(onComplete: (() -> Unit)? = null) {
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            similarityRepo.generateEnabledResults(
                                shouldContinue = { true },
                                onProgress = {}
                            )
                        }
                        similarityRefreshVersion.value += 1
                        onComplete?.invoke()
                    }
                }

                LaunchedEffect(state.value, sortSettingsVersion.value) {
                    val current = state.value
                    if (current is ScanUiState.Success) {
                        val settings = settingsStore.load()
                        val sortKey = runCatching { ResultSortKey.valueOf(settings.resultSortKey) }
                            .getOrDefault(ResultSortKey.Count)
                        val sortDir = runCatching { SortDirection.valueOf(settings.resultSortDirection) }
                            .getOrDefault(SortDirection.Desc)
                        val base = ScanResult(
                            scannedAtMillis = current.result.scannedAtMillis,
                            files = current.result.files,
                            duplicateGroups = emptyList()
                        )
                        val filtered = withContext(Dispatchers.Default) {
                            ScanResultViewFilter.filterForDisplay(
                                result = base,
                                hideZeroSizeInResults = settings.hideZeroSizeInResults,
                                sortKey = sortKey,
                                sortDirection = sortDir
                            )
                        }
                        displayResult.value = filtered
                    } else {
                        displayResult.value = null
                    }
                }

                val restoreLastResult: () -> Unit = {
                    if (state.value !is ScanUiState.Success) {
                        state.value = ScanUiState.Idle
                    }
                }

                BackHandler {
                    val current = backStack.lastOrNull()
                    if (current == Screen.Results && selectedResultsGroupIndex.value != null) {
                        selectedResultsGroupIndex.value = null
                        return@BackHandler
                    }
                    if (backStack.size > 1) {
                        pop(backStack)
                    } else {
                        finish()
                    }
                }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    val screenModifier = Modifier.fillMaxSize()

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        ScreenStack(
                            screens = screenCache,
                            current = backStack.last(),
                            modifier = Modifier.fillMaxSize()
                        ) { screen ->
                            when (screen) {
                            Screen.Dashboard -> DashboardScreen(
                                onOpenPermission = { navigateTo(backStack, screenCache, Screen.Permission) },
                                onOpenTargets = { navigateTo(backStack, screenCache, Screen.Targets) },
                                onOpenScanCommand = { navigateTo(backStack, screenCache, Screen.ScanCommand) },
                                onOpenResults = { navigateTo(backStack, screenCache, Screen.Results) },
                                onOpenFiles = { navigateTo(backStack, screenCache, Screen.Files) },
                                onOpenTrash = { navigateTo(backStack, screenCache, Screen.Trash) },
                                onOpenDbManagement = { navigateTo(backStack, screenCache, Screen.DbManagement) },
                                onOpenSimilaritySettings = {
                                    navigateTo(backStack, screenCache, Screen.SimilaritySettings)
                                },
                                onOpenSettings = { navigateTo(backStack, screenCache, Screen.Settings) },
                                onOpenReports = { navigateTo(backStack, screenCache, Screen.Reports) },
                                onOpenAbout = { navigateTo(backStack, screenCache, Screen.About) },
                                modifier = screenModifier
                            )

                            Screen.Permission -> PermissionScreen(
                                onBack = { pop(backStack) },
                                modifier = screenModifier
                            )

                            Screen.Targets -> TargetsScreen(
                                onBack = { pop(backStack) },
                                onTargetsChanged = { targetsVersion.value += 1 },
                                modifier = screenModifier
                            )

                            Screen.Files -> FilesScreenDb(
                                fileRepo = fileRepo,
                                trashController = trashController,
                                settingsStore = settingsStore,
                                keepLoadedThumbnailsInMemory = settingsSnapshot.keepLoadedThumbnailsInMemory,
                                keepLoadedVideoPreviewsInMemory = settingsSnapshot.keepLoadedVideoPreviewsInMemory,
                                snapVideoPreviewFramesToWidth = settingsSnapshot.snapVideoPreviewFramesToWidth,
                                videoPreviewLineCount = settingsSnapshot.videoPreviewLineCount,
                                thumbnailSizeScale = settingsSnapshot.thumbnailSizePercent / 100f,
                                videoPreviewSizeScale = settingsSnapshot.videoPreviewSizePercent / 100f,
                                rememberedThumbnailCache = rememberedThumbnailCache,
                                rememberedVideoPreviewCache = rememberedVideoPreviewCache,
                                clearVersion = filesClearVersion.value,
                                refreshVersion = filesRefreshVersion.value,
                                onFilesChanged = {
                                    resultsRefreshVersion.value += 1
                                    refreshSimilarityFromCache()
                                },
                                onBack = { pop(backStack) },
                                modifier = screenModifier
                            )

                            Screen.DbManagement -> DbManagementScreen(
                                historyRepo = historyRepo,
                                resultsRepo = resultsRepo,
                                uiState = dbManagementUiState,
                                appScope = AppWorkScopes.taskScope,
                                taskCoordinator = taskCoordinator,
                                notificationController = notificationController,
                                onMaintenanceApplied = {
                                    refreshSimilarityFromCache {
                                        filesRefreshVersion.value += 1
                                        resultsRefreshVersion.value += 1
                                    }
                                },
                                onCacheCleared = {
                                    state.value = ScanUiState.Idle
                                    deletedPaths.value = emptySet()
                                    displayResult.value = null
                                    filesClearVersion.value += 1
                                    filesRefreshVersion.value += 1
                                    resultsRefreshVersion.value += 1
                                    refreshSimilarityFromCache()
                                    selectedResultsGroupIndex.value = null
                                },
                                onBack = { pop(backStack) },
                                modifier = screenModifier
                            )

                            Screen.ScanCommand -> ScanCommandScreen(
                                state = state,
                                onScanComplete = { handleScanComplete(it) },
                                onScanCancelled = restoreLastResult,
                                reportRepo = reportRepo,
                                settingsStore = settingsStore,
                                targetsVersion = targetsVersion.value,
                                scanScope = AppWorkScopes.scanScope,
                                onReportSaved = { reportsRefreshVersion.value += 1 },
                                taskCoordinator = taskCoordinator,
                                notificationController = notificationController,
                                onBack = { pop(backStack) },
                                scanner = scanner,
                                modifier = screenModifier
                            )

                            Screen.Results -> ResultsScreenDb(
                                resultsRepo = resultsRepo,
                                settingsStore = settingsStore,
                                keepLoadedThumbnailsInMemory = settingsSnapshot.keepLoadedThumbnailsInMemory,
                                thumbnailSizeScale = settingsSnapshot.thumbnailSizePercent / 100f,
                                rememberedPreviewCache = rememberedThumbnailCache,
                                deletedPaths = deletedPaths.value,
                                onDeleteFile = { file ->
                                    if (taskCoordinator.isAreaBusy(TaskArea.Trash)) {
                                        return@ResultsScreenDb false
                                    }
                                    val ok = withContext(Dispatchers.IO) {
                                        trashController.moveToTrash(file.normalizedPath).success
                                    }
                                    if (ok) {
                                        deletedPaths.value = deletedPaths.value + file.normalizedPath
                                    }
                                    ok
                                },
                                onBulkDeleteFile = { file ->
                                    val ok = withContext(Dispatchers.IO) {
                                        trashController.moveToTrash(file.normalizedPath).success
                                    }
                                    if (ok) {
                                        deletedPaths.value = deletedPaths.value + file.normalizedPath
                                    }
                                    ok
                                },
                                taskScope = AppWorkScopes.taskScope,
                                taskCoordinator = taskCoordinator,
                                notificationController = notificationController,
                                onBack = {
                                    if (selectedResultsGroupIndex.value != null) {
                                        selectedResultsGroupIndex.value = null
                                    } else {
                                        goDashboard(backStack, screenCache)
                                    }
                                },
                                onOpenGroup = { index ->
                                    selectedResultsGroupIndex.value = index
                                },
                                refreshVersion = resultsRefreshVersion.value,
                                selectedGroupIndex = selectedResultsGroupIndex.value,
                                modifier = screenModifier
                            )

                            Screen.Settings -> SettingsScreen(
                                settingsStore = settingsStore,
                                onBack = { pop(backStack) },
                                onSettingsChanged = { settingsVersion.value += 1 },
                                modifier = screenModifier
                            )

                            Screen.SimilaritySettings -> SimilaritySettingsScreen(
                                repository = similarityRepo,
                                refreshVersion = similarityRefreshVersion.value,
                                onBack = { pop(backStack) },
                                onCreateSetting = {
                                    navigateTo(backStack, screenCache, Screen.SimilaritySettingCreate)
                                },
                                onOpenSetting = { settingId ->
                                    navigateTo(backStack, screenCache, Screen.SimilaritySettingDetail(settingId))
                                },
                                modifier = screenModifier
                            )

                            Screen.SimilaritySettingCreate -> SimilaritySettingCreateScreen(
                                onBack = { pop(backStack) },
                                onOpenExactThumbnail = {
                                    navigateTo(backStack, screenCache, Screen.SimilarityExactThumbnailSetting)
                                },
                                onOpenDurationTolerance = {
                                    navigateTo(backStack, screenCache, Screen.SimilarityDurationToleranceSetting)
                                },
                                onOpenDurationNeighbor = {
                                    navigateTo(backStack, screenCache, Screen.SimilarityDurationNeighborSetting)
                                },
                                modifier = screenModifier
                            )

                            Screen.SimilarityExactThumbnailSetting -> SimilarityExactThumbnailSettingScreen(
                                repository = similarityRepo,
                                onChanged = { similarityRefreshVersion.value += 1 },
                                onCreated = { settingId ->
                                    navigateTo(backStack, screenCache, Screen.SimilaritySettingDetail(settingId))
                                },
                                onBack = { pop(backStack) },
                                modifier = screenModifier
                            )

                            Screen.SimilarityDurationToleranceSetting -> SimilarityDurationSettingScreen(
                                repository = similarityRepo,
                                neighborList = false,
                                onChanged = { similarityRefreshVersion.value += 1 },
                                onCreated = { settingId ->
                                    navigateTo(backStack, screenCache, Screen.SimilaritySettingDetail(settingId))
                                },
                                onBack = { pop(backStack) },
                                modifier = screenModifier
                            )

                            Screen.SimilarityDurationNeighborSetting -> SimilarityDurationSettingScreen(
                                repository = similarityRepo,
                                neighborList = true,
                                onChanged = { similarityRefreshVersion.value += 1 },
                                onCreated = { settingId ->
                                    navigateTo(backStack, screenCache, Screen.SimilaritySettingDetail(settingId))
                                },
                                onBack = { pop(backStack) },
                                modifier = screenModifier
                            )

                            is Screen.SimilaritySettingDetail -> SimilaritySettingDetailScreen(
                                repository = similarityRepo,
                                appScope = AppWorkScopes.taskScope,
                                taskCoordinator = taskCoordinator,
                                notificationController = notificationController,
                                settingId = screen.settingId,
                                refreshVersion = similarityRefreshVersion.value,
                                onChanged = { similarityRefreshVersion.value += 1 },
                                onBack = { pop(backStack) },
                                onOpenGroups = { settingId ->
                                    navigateTo(
                                        backStack,
                                        screenCache,
                                        Screen.SimilaritySettingGroups(settingId)
                                    )
                                },
                                modifier = screenModifier
                            )

                            is Screen.SimilaritySettingGroups -> SimilaritySettingResultsScreen(
                                repository = similarityRepo,
                                settingsStore = settingsStore,
                                keepLoadedThumbnailsInMemory = settingsSnapshot.keepLoadedThumbnailsInMemory,
                                keepLoadedVideoPreviewsInMemory = settingsSnapshot.keepLoadedVideoPreviewsInMemory,
                                snapVideoPreviewFramesToWidth = settingsSnapshot.snapVideoPreviewFramesToWidth,
                                videoPreviewLineCount = settingsSnapshot.videoPreviewLineCount,
                                thumbnailSizeScale = settingsSnapshot.thumbnailSizePercent / 100f,
                                videoPreviewSizeScale = settingsSnapshot.videoPreviewSizePercent / 100f,
                                rememberedPreviewCache = rememberedThumbnailCache,
                                rememberedVideoPreviewCache = rememberedVideoPreviewCache,
                                showFullPaths = settingsSnapshot.showFullPaths,
                                showVideoPreviews = similarityShowVideoPreviews.value,
                                showVideoPreviewDurations = similarityShowVideoPreviewDurations.value,
                                showVideoPreviewResolutions = similarityShowVideoPreviewResolutions.value,
                                onShowVideoPreviewsChange = { similarityShowVideoPreviews.value = it },
                                onShowVideoPreviewDurationsChange = {
                                    similarityShowVideoPreviewDurations.value = it
                                },
                                onShowVideoPreviewResolutionsChange = {
                                    similarityShowVideoPreviewResolutions.value = it
                                },
                                deletedPaths = deletedPaths.value,
                                onDeleteFile = { file ->
                                    if (taskCoordinator.isAreaBusy(TaskArea.Trash)) {
                                        return@SimilaritySettingResultsScreen false
                                    }
                                    val ok = withContext(Dispatchers.IO) {
                                        trashController.moveToTrash(file.normalizedPath).success
                                    }
                                    if (ok) {
                                        deletedPaths.value = deletedPaths.value + file.normalizedPath
                                    }
                                    ok
                                },
                                settingId = screen.settingId,
                                refreshVersion = similarityRefreshVersion.value,
                                onBack = { pop(backStack) },
                                modifier = screenModifier
                            )

                            is Screen.SimilarityClusterDetail -> SimilarityClusterDetailScreen(
                                repository = similarityRepo,
                                settingsStore = settingsStore,
                                keepLoadedThumbnailsInMemory = settingsSnapshot.keepLoadedThumbnailsInMemory,
                                keepLoadedVideoPreviewsInMemory = settingsSnapshot.keepLoadedVideoPreviewsInMemory,
                                snapVideoPreviewFramesToWidth = settingsSnapshot.snapVideoPreviewFramesToWidth,
                                videoPreviewLineCount = settingsSnapshot.videoPreviewLineCount,
                                thumbnailSizeScale = settingsSnapshot.thumbnailSizePercent / 100f,
                                videoPreviewSizeScale = settingsSnapshot.videoPreviewSizePercent / 100f,
                                rememberedPreviewCache = rememberedThumbnailCache,
                                rememberedVideoPreviewCache = rememberedVideoPreviewCache,
                                showFullPaths = settingsSnapshot.showFullPaths,
                                showVideoPreviews = similarityShowVideoPreviews.value,
                                showVideoPreviewDurations = similarityShowVideoPreviewDurations.value,
                                showVideoPreviewResolutions = similarityShowVideoPreviewResolutions.value,
                                onShowVideoPreviewsChange = { similarityShowVideoPreviews.value = it },
                                onShowVideoPreviewDurationsChange = {
                                    similarityShowVideoPreviewDurations.value = it
                                },
                                onShowVideoPreviewResolutionsChange = {
                                    similarityShowVideoPreviewResolutions.value = it
                                },
                                deletedPaths = deletedPaths.value,
                                onDeleteFile = { file ->
                                    if (taskCoordinator.isAreaBusy(TaskArea.Trash)) {
                                        return@SimilarityClusterDetailScreen false
                                    }
                                    val ok = withContext(Dispatchers.IO) {
                                        trashController.moveToTrash(file.normalizedPath).success
                                    }
                                    if (ok) {
                                        deletedPaths.value = deletedPaths.value + file.normalizedPath
                                    }
                                    ok
                                },
                                settingId = screen.settingId,
                                clusterId = screen.clusterId,
                                onBack = { pop(backStack) },
                                modifier = screenModifier
                            )

                            Screen.About -> AboutScreen(
                                onBack = { pop(backStack) },
                                modifier = screenModifier
                            )

                            Screen.Reports -> ReportsScreen(
                                reportRepo = reportRepo,
                                refreshVersion = reportsRefreshVersion.value,
                                onBack = { pop(backStack) },
                                onOpenReport = { id ->
                                    navigateTo(backStack, screenCache, Screen.ReportDetail(id))
                                },
                                modifier = screenModifier
                            )

                            is Screen.ReportDetail -> ReportsScreen(
                                reportRepo = reportRepo,
                                refreshVersion = reportsRefreshVersion.value,
                                onBack = { pop(backStack) },
                                onOpenReport = null,
                                selectedReportId = screen.id,
                                modifier = screenModifier
                            )

                            Screen.Trash -> TrashScreen(
                                trashRepo = trashRepo,
                                trashController = trashController,
                                appScope = AppWorkScopes.taskScope,
                                taskCoordinator = taskCoordinator,
                                notificationController = notificationController,
                                onTrashChanged = { restoredOriginalPath ->
                                    if (restoredOriginalPath != null) {
                                        deletedPaths.value = deletedPaths.value - restoredOriginalPath
                                    }
                                    refreshSimilarityFromCache {
                                        filesRefreshVersion.value += 1
                                        resultsRefreshVersion.value += 1
                                    }
                                },
                                onBack = { pop(backStack) },
                                modifier = screenModifier
                            )
                        }
                    }

                        TaskBannerStack(
                            tasks = taskCoordinator.activeTasks.toList(),
                            onOpenTask = { task ->
                                navigateTo(backStack, screenCache, screenForTaskArea(task.area))
                            },
                            onCancelTask = { task ->
                                taskCoordinator.requestCancel(task.area)
                            },
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = Spacing.itemGap)
                        )

                        if (settingsSnapshot.showMemoryOverlay) {
                            MemoryUsageOverlay()
                        }
                    }
                }
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateSystemBars()
    }

    private fun updateSystemBars() {
        val isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        val statusStyle = if (isDark) {
            SystemBarStyle.dark(Color.TRANSPARENT)
        } else {
            SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        }
        val navStyle = if (isDark) {
            SystemBarStyle.dark(Color.TRANSPARENT)
        } else {
            SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        }
        enableEdgeToEdge(statusBarStyle = statusStyle, navigationBarStyle = navStyle)
    }
}

@Composable
private fun ScreenStack(
    screens: List<Screen>,
    current: Screen,
    modifier: Modifier = Modifier,
    content: @Composable (Screen) -> Unit
) {
    val stateHolder: SaveableStateHolder = rememberSaveableStateHolder()
    val stateKey = remember(current) { current.toString() }
    Box(modifier = modifier) {
        stateHolder.SaveableStateProvider(stateKey) {
            content(current)
        }
    }
}

private fun navigateTo(stack: MutableList<Screen>, cache: MutableList<Screen>, screen: Screen) {
    if (cache.none { it == screen }) {
        cache.add(screen)
    }
    if (stack.lastOrNull() != screen) {
        stack.add(screen)
    }
}

private fun pop(stack: MutableList<Screen>) {
    if (stack.size > 1) {
        stack.removeAt(stack.lastIndex)
    }
}

private fun goDashboard(stack: MutableList<Screen>, cache: MutableList<Screen>) {
    if (cache.none { it == Screen.Dashboard }) {
        cache.add(Screen.Dashboard)
    }
    stack.clear()
    stack.add(Screen.Dashboard)
}

private fun screenForTaskArea(area: TaskArea): Screen {
    return when (area) {
        TaskArea.Scan -> Screen.ScanCommand
        TaskArea.Db -> Screen.DbManagement
        TaskArea.Trash -> Screen.Trash
        TaskArea.Similarity -> Screen.SimilaritySettings
    }
}

private val ScreenBackStackSaver = Saver<SnapshotStateList<Screen>, ArrayList<String>>(
    save = { stack ->
        ArrayList(stack.map { screen -> screen.toSaveToken() })
    },
    restore = { tokens ->
        restoreScreenStack(tokens).toMutableStateList()
    }
)

internal fun restoreScreenStack(tokens: List<String>): List<Screen> {
    val restored = tokens.mapNotNull { token -> Screen.fromSaveToken(token) }
    if (restored.isEmpty()) {
        return listOf(Screen.Dashboard)
    }
    if (restored.first() == Screen.Dashboard) {
        return restored
    }
    return listOf(Screen.Dashboard) + restored
}

internal sealed class Screen {
    data object Dashboard : Screen()
    data object Permission : Screen()
    data object Targets : Screen()
    data object Files : Screen()
    data object Trash : Screen()
    data object DbManagement : Screen()
    data object ScanCommand : Screen()
    data object Results : Screen()
    data object Settings : Screen()
    data object SimilaritySettings : Screen()
    data object SimilaritySettingCreate : Screen()
    data object SimilarityExactThumbnailSetting : Screen()
    data object SimilarityDurationToleranceSetting : Screen()
    data object SimilarityDurationNeighborSetting : Screen()
    data class SimilaritySettingDetail(val settingId: Long) : Screen()
    data class SimilaritySettingGroups(val settingId: Long) : Screen()
    data class SimilarityClusterDetail(val settingId: Long, val clusterId: Long) : Screen()
    data object About : Screen()
    data object Reports : Screen()
    data class ReportDetail(val id: String) : Screen()

    fun toSaveToken(): String {
        return when (this) {
            Dashboard -> "dashboard"
            Permission -> "permission"
            Targets -> "targets"
            Files -> "files"
            Trash -> "trash"
            DbManagement -> "db-management"
            ScanCommand -> "scan-command"
            Results -> "results"
            Settings -> "settings"
            SimilaritySettings -> "similarity-settings"
            SimilaritySettingCreate -> "similarity-setting-create"
            SimilarityExactThumbnailSetting -> "similarity-setting-exact-thumbnail"
            SimilarityDurationToleranceSetting -> "similarity-setting-duration-tolerance"
            SimilarityDurationNeighborSetting -> "similarity-setting-duration-neighbor"
            is SimilaritySettingDetail -> "similarity-setting-detail:$settingId"
            is SimilaritySettingGroups -> "similarity-setting-groups:$settingId"
            is SimilarityClusterDetail -> "similarity-cluster-detail:$settingId:$clusterId"
            About -> "about"
            Reports -> "reports"
            is ReportDetail -> "report-detail:$id"
        }
    }

    companion object {
        fun fromSaveToken(token: String): Screen? {
            return when {
                token == "dashboard" -> Dashboard
                token == "permission" -> Permission
                token == "targets" -> Targets
                token == "files" -> Files
                token == "trash" -> Trash
                token == "db-management" -> DbManagement
                token == "scan-command" -> ScanCommand
                token == "results" -> Results
                token == "settings" -> Settings
                token == "similarity-settings" -> SimilaritySettings
                token == "similarity-maintenance" -> SimilaritySettings
                token == "similarity-setting-create" -> SimilaritySettingCreate
                token == "similarity-setting-exact-thumbnail" -> SimilarityExactThumbnailSetting
                token == "similarity-setting-duration-tolerance" -> SimilarityDurationToleranceSetting
                token == "similarity-setting-duration-neighbor" -> SimilarityDurationNeighborSetting
                token.startsWith("similarity-setting-detail:") -> {
                    token.substringAfter("similarity-setting-detail:")
                        .toLongOrNull()
                        ?.let { settingId -> SimilaritySettingDetail(settingId) }
                }
                token.startsWith("similarity-setting-groups:") -> {
                    token.substringAfter("similarity-setting-groups:")
                        .toLongOrNull()
                        ?.let { settingId -> SimilaritySettingGroups(settingId) }
                }
                token.startsWith("similarity-cluster-detail:") -> {
                    val ids = token.substringAfter("similarity-cluster-detail:").split(':')
                    val settingId = ids.getOrNull(0)?.toLongOrNull()
                    val clusterId = ids.getOrNull(1)?.toLongOrNull()
                    if (settingId != null && clusterId != null) {
                        SimilarityClusterDetail(settingId = settingId, clusterId = clusterId)
                    } else {
                        null
                    }
                }
                token == "about" -> About
                token == "reports" -> Reports
                token.startsWith("report-detail:") -> ReportDetail(token.removePrefix("report-detail:"))
                else -> null
            }
        }
    }
}
