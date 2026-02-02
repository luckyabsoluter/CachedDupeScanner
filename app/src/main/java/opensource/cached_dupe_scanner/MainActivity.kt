package opensource.cached_dupe_scanner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import opensource.cached_dupe_scanner.ui.home.DashboardScreen
import opensource.cached_dupe_scanner.ui.home.FilesScreen
import opensource.cached_dupe_scanner.ui.home.PermissionScreen
import opensource.cached_dupe_scanner.ui.home.ResultsScreen
import opensource.cached_dupe_scanner.ui.home.ReportsScreen
import opensource.cached_dupe_scanner.ui.home.ScanCommandScreen
import opensource.cached_dupe_scanner.ui.home.SettingsScreen
import opensource.cached_dupe_scanner.ui.home.TargetsScreen
import opensource.cached_dupe_scanner.ui.results.ScanUiState
import opensource.cached_dupe_scanner.storage.ScanHistoryRepository
import opensource.cached_dupe_scanner.storage.AppSettingsStore
import opensource.cached_dupe_scanner.storage.ScanReportRepository
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
        enableEdgeToEdge()
        setContent {
            CachedDupeScannerTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    val state = remember { mutableStateOf<ScanUiState>(ScanUiState.Idle) }
                    val pendingScan = remember { mutableStateOf<ScanResult?>(null) }
                    val clearRequested = remember { mutableStateOf(false) }
                    val deletedPaths = remember { mutableStateOf(setOf<String>()) }
                    val displayResult = remember { mutableStateOf<ScanResult?>(null) }
                    val sortSettingsVersion = remember { mutableStateOf(0) }
                    val filesClearVersion = remember { mutableStateOf(0) }
                    val filesRefreshVersion = remember { mutableStateOf(0) }
                    val targetsVersion = remember { mutableStateOf(0) }
                    val reportsRefreshVersion = remember { mutableStateOf(0) }
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
                                CacheMigrations.MIGRATION_6_7
                            )
                            .build()
                    }
                    val historyRepo = remember { ScanHistoryRepository(database.fileCacheDao(), settingsStore) }
                    val reportRepo = remember { ScanReportRepository(database.scanReportDao()) }

                    LaunchedEffect(Unit) {
                        if (state.value is ScanUiState.Idle) {
                            val stored = withContext(Dispatchers.IO) {
                                historyRepo.loadMergedHistory()
                            }
                            if (stored != null) {
                                state.value = ScanUiState.Success(stored)
                            }
                        }
                    }

                    LaunchedEffect(pendingScan.value) {
                        val scan = pendingScan.value ?: return@LaunchedEffect
                        Log.d("MainActivity", "Scan complete callback received")
                        pendingScan.value = null
                        val merged = withContext(Dispatchers.IO) {
                            Log.d("MainActivity", "Persisting scan to DB")
                            historyRepo.recordScan(scan)
                            Log.d("MainActivity", "Reloading merged history")
                            val mergedOrScan = historyRepo.loadMergedHistory() ?: scan
                            Log.d("MainActivity", "Merged history loaded")
                            mergedOrScan
                        }
                        Log.d("MainActivity", "Merged history ready")
                        state.value = ScanUiState.Success(merged)
                        deletedPaths.value = emptySet()
                        filesRefreshVersion.value += 1
                        navigateTo(backStack, screenCache, Screen.Results)
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
                                onOpenSettings = { navigateTo(backStack, screenCache, Screen.Settings) },
                                onOpenReports = { navigateTo(backStack, screenCache, Screen.Reports) },
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
                            Screen.Files -> FilesScreen(
                                historyRepo = historyRepo,
                                clearVersion = filesClearVersion.value,
                                refreshVersion = filesRefreshVersion.value,
                                onBack = { pop(backStack) },
                                modifier = screenModifier
                            )
                            Screen.ScanCommand -> ScanCommandScreen(
                                state = state,
                                onScanComplete = { pendingScan.value = it },
                                onScanCancelled = restoreLastResult,
                                reportRepo = reportRepo,
                                settingsStore = settingsStore,
                                targetsVersion = targetsVersion.value,
                                onReportSaved = { reportsRefreshVersion.value += 1 },
                                onBack = { pop(backStack) },
                                modifier = screenModifier
                            )
                            Screen.Results -> ResultsScreen(
                                state = state,
                                displayResult = displayResult.value,
                                onBackToDashboard = { goDashboard(backStack, screenCache) },
                                onOpenGroup = { index ->
                                    navigateTo(backStack, screenCache, Screen.ResultsDetail(index))
                                },
                                deletedPaths = deletedPaths.value,
                                onDeleteFile = { file ->
                                    scope.launch {
                                        withContext(Dispatchers.IO) {
                                            historyRepo.deleteByNormalizedPath(file.normalizedPath)
                                        }
                                    }
                                    deletedPaths.value = deletedPaths.value + file.normalizedPath
                                },
                                onSortChanged = { sortSettingsVersion.value++ },
                                onClearResults = {
                                    pendingScan.value = null
                                    clearRequested.value = true
                                },
                                settingsStore = settingsStore,
                                modifier = screenModifier
                            )
                            is Screen.ResultsDetail -> ResultsScreen(
                                state = state,
                                displayResult = displayResult.value,
                                onBackToDashboard = { pop(backStack) },
                                onOpenGroup = null,
                                deletedPaths = deletedPaths.value,
                                onDeleteFile = { file ->
                                    scope.launch {
                                        withContext(Dispatchers.IO) {
                                            historyRepo.deleteByNormalizedPath(file.normalizedPath)
                                        }
                                    }
                                    deletedPaths.value = deletedPaths.value + file.normalizedPath
                                },
                                onSortChanged = { sortSettingsVersion.value++ },
                                onClearResults = {
                                    pendingScan.value = null
                                    clearRequested.value = true
                                },
                                settingsStore = settingsStore,
                                selectedGroupIndex = screen.index,
                                modifier = screenModifier
                            )
                            Screen.Settings -> SettingsScreen(
                                settingsStore = settingsStore,
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
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScreenStack(
    screens: List<Screen>,
    current: Screen,
    modifier: Modifier = Modifier,
    content: @Composable (Screen) -> Unit
) {
    Box(modifier = modifier) {
        content(current)
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
    data object ScanCommand : Screen()
    data object Results : Screen()
    data class ResultsDetail(val index: Int) : Screen()
    data object Settings : Screen()
    data object Reports : Screen()
    data class ReportDetail(val id: String) : Screen()
}