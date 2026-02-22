package opensource.cached_dupe_scanner

import android.os.Bundle
import android.content.res.Configuration
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.Composable
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import opensource.cached_dupe_scanner.ui.home.DashboardScreen
import opensource.cached_dupe_scanner.ui.home.DbManagementScreen
import opensource.cached_dupe_scanner.ui.home.FilesScreenDb
import opensource.cached_dupe_scanner.ui.home.AboutScreen
import opensource.cached_dupe_scanner.ui.home.PermissionScreen
import opensource.cached_dupe_scanner.ui.home.ResultsScreenDb
import opensource.cached_dupe_scanner.ui.home.ReportsScreen
import opensource.cached_dupe_scanner.ui.home.ScanCommandScreen
import opensource.cached_dupe_scanner.ui.home.SettingsScreen
import opensource.cached_dupe_scanner.ui.home.TargetsScreen
import opensource.cached_dupe_scanner.ui.home.TrashScreen
import opensource.cached_dupe_scanner.ui.results.ScanUiState
import opensource.cached_dupe_scanner.storage.ScanHistoryRepository
import opensource.cached_dupe_scanner.storage.AppSettingsStore
import opensource.cached_dupe_scanner.storage.ResultsDbRepository
import opensource.cached_dupe_scanner.storage.PagedFileRepository
import opensource.cached_dupe_scanner.storage.ScanReportRepository
import opensource.cached_dupe_scanner.storage.TrashController
import opensource.cached_dupe_scanner.storage.TrashRepository
import opensource.cached_dupe_scanner.cache.CacheDatabase
import opensource.cached_dupe_scanner.cache.CacheMigrations
import androidx.room.Room
import opensource.cached_dupe_scanner.core.ScanResult
import opensource.cached_dupe_scanner.core.ScanResultViewFilter
import opensource.cached_dupe_scanner.core.ResultSortKey
import opensource.cached_dupe_scanner.core.SortDirection
import opensource.cached_dupe_scanner.ui.theme.CachedDupeScannerTheme
import android.util.Log

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        updateSystemBars()
        setContent {
            CachedDupeScannerTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    val state = remember { mutableStateOf<ScanUiState>(ScanUiState.Idle) }
                    val clearRequested = remember { mutableStateOf(false) }
                    val deletedPaths = remember { mutableStateOf(setOf<String>()) }
                    val displayResult = remember { mutableStateOf<ScanResult?>(null) }
                    val sortSettingsVersion = remember { mutableStateOf(0) }
                    val filesClearVersion = remember { mutableStateOf(0) }
                    val filesRefreshVersion = remember { mutableStateOf(0) }
                    val targetsVersion = remember { mutableStateOf(0) }
                    val reportsRefreshVersion = remember { mutableStateOf(0) }
                    val resultsRefreshVersion = remember { mutableStateOf(0) }
                    val selectedResultsGroupIndex = rememberSaveable { mutableStateOf<Int?>(null) }
                    val context = LocalContext.current
                    val settingsStore = remember { AppSettingsStore(context) }
                    val scope = rememberCoroutineScope()
                    val screenCache = remember { mutableStateListOf<Screen>(Screen.Dashboard) }
                    val backStack = remember { mutableStateListOf<Screen>(Screen.Dashboard) }
                    val database = remember {
                        Room.databaseBuilder(context, CacheDatabase::class.java, "scan-cache.db")
                            .addMigrations(
                                CacheMigrations.MIGRATION_1_3,
                                CacheMigrations.MIGRATION_2_3,
                                CacheMigrations.MIGRATION_3_4,
                                CacheMigrations.MIGRATION_4_5,
                                CacheMigrations.MIGRATION_5_6,
                                CacheMigrations.MIGRATION_6_7,
                                CacheMigrations.MIGRATION_7_8,
                                CacheMigrations.MIGRATION_8_9,
                                CacheMigrations.MIGRATION_9_10,
                                CacheMigrations.MIGRATION_10_11,
                                CacheMigrations.MIGRATION_11_12
                            )
                            .build()
                    }
                    val historyRepo = remember {
                        ScanHistoryRepository(
                            dao = database.fileCacheDao(),
                            settingsStore = settingsStore,
                            groupDao = database.duplicateGroupDao()
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

                    fun handleScanComplete(scan: ScanResult) {
                        Log.d("MainActivity", "Scan complete callback received")
                        state.value = ScanUiState.Success(scan)
                        deletedPaths.value = emptySet()
                        filesRefreshVersion.value += 1
                        selectedResultsGroupIndex.value = null
                        scope.launch {
                            runCatching {
                                withContext(Dispatchers.IO) {
                                    Log.d("MainActivity", "Persisting scan to DB")
                                    historyRepo.recordScan(scan)
                                }
                            }.onFailure { error ->
                                Log.e("MainActivity", "Failed to persist scan results", error)
                            }
                            resultsRefreshVersion.value += 1
                            navigateTo(backStack, screenCache, Screen.Results)
                        }
                    }

                    LaunchedEffect(clearRequested.value) {
                        if (!clearRequested.value) return@LaunchedEffect
                        withContext(Dispatchers.IO) {
                            historyRepo.clearAll()
                        }
                        state.value = ScanUiState.Idle
                        deletedPaths.value = emptySet()
                        displayResult.value = null
                        filesClearVersion.value += 1
                        filesRefreshVersion.value += 1
                        resultsRefreshVersion.value += 1
                        selectedResultsGroupIndex.value = null
                        clearRequested.value = false
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

                    val navModifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                    val screenModifier = Modifier.fillMaxSize()

                    val restoreLastResult: () -> Unit = {
                        scope.launch {
                            val stored = withContext(Dispatchers.IO) {
                                historyRepo.loadMergedHistory()
                            }
                            if (stored != null) {
                                state.value = ScanUiState.Success(stored)
                            } else {
                                state.value = ScanUiState.Idle
                            }
                        }
                        Unit
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

                    ScreenStack(
                        screens = screenCache,
                        current = backStack.last(),
                        modifier = navModifier
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
                                clearVersion = filesClearVersion.value,
                                refreshVersion = filesRefreshVersion.value,
                                onBack = { pop(backStack) },
                                modifier = screenModifier
                            )
                            Screen.DbManagement -> DbManagementScreen(
                                historyRepo = historyRepo,
                                onMaintenanceApplied = {
                                    filesRefreshVersion.value += 1
                                    resultsRefreshVersion.value += 1
                                },
                                onClearAll = { clearRequested.value = true },
                                clearVersion = filesClearVersion.value,
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
                                scanScope = scope,
                                onReportSaved = { reportsRefreshVersion.value += 1 },
                                onBack = { pop(backStack) },
                                modifier = screenModifier
                            )
                            Screen.Results -> ResultsScreenDb(
                                resultsRepo = resultsRepo,
                                settingsStore = settingsStore,
                                deletedPaths = deletedPaths.value,
                                onDeleteFile = { file ->
                                    val ok = withContext(Dispatchers.IO) {
                                        trashController.moveToTrash(file.normalizedPath).success
                                    }
                                    if (ok) {
                                        deletedPaths.value = deletedPaths.value + file.normalizedPath
                                    }
                                    ok
                                },
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
                                onBack = { pop(backStack) },
                                modifier = screenModifier
                            )
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

private sealed class Screen {
    data object Dashboard : Screen()
    data object Permission : Screen()
    data object Targets : Screen()
    data object Files : Screen()
    data object Trash : Screen()
    data object DbManagement : Screen()
    data object ScanCommand : Screen()
    data object Results : Screen()
    data object Settings : Screen()
    data object About : Screen()
    data object Reports : Screen()
    data class ReportDetail(val id: String) : Screen()
}
