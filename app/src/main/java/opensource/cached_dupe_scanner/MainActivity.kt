package opensource.cached_dupe_scanner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.ScrollState
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import opensource.cached_dupe_scanner.ui.home.DashboardScreen
import opensource.cached_dupe_scanner.ui.home.PermissionScreen
import opensource.cached_dupe_scanner.ui.home.ResultsScreen
import opensource.cached_dupe_scanner.ui.home.ScanCommandScreen
import opensource.cached_dupe_scanner.ui.home.SettingsScreen
import opensource.cached_dupe_scanner.ui.home.TargetsScreen
import opensource.cached_dupe_scanner.ui.results.ScanUiState
import opensource.cached_dupe_scanner.storage.ScanResultStore
import opensource.cached_dupe_scanner.storage.ScanHistoryRepository
import opensource.cached_dupe_scanner.storage.AppSettingsStore
import opensource.cached_dupe_scanner.cache.CacheDatabase
import opensource.cached_dupe_scanner.cache.CacheMigrations
import androidx.room.Room
import opensource.cached_dupe_scanner.core.ScanResult
import opensource.cached_dupe_scanner.ui.theme.CachedDupeScannerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CachedDupeScannerTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    val screen = remember { mutableStateOf(Screen.Dashboard) }
                    val state = remember { mutableStateOf<ScanUiState>(ScanUiState.Idle) }
                    val pendingScan = remember { mutableStateOf<ScanResult?>(null) }
                    val clearRequested = remember { mutableStateOf(false) }
                    val context = LocalContext.current
                    val resultStore = remember { ScanResultStore(context) }
                    val settingsStore = remember { AppSettingsStore(context) }
                    val dashboardScroll = rememberSaveable(saver = ScrollState.Saver) { ScrollState(0) }
                    val permissionScroll = rememberSaveable(saver = ScrollState.Saver) { ScrollState(0) }
                    val targetsScroll = rememberSaveable(saver = ScrollState.Saver) { ScrollState(0) }
                    val scanCommandScroll = rememberSaveable(saver = ScrollState.Saver) { ScrollState(0) }
                    val resultsScroll = rememberSaveable(saver = ScrollState.Saver) { ScrollState(0) }
                    val settingsScroll = rememberSaveable(saver = ScrollState.Saver) { ScrollState(0) }
                    val historyRepo = remember {
                        val db = Room.databaseBuilder(context, CacheDatabase::class.java, "scan-cache.db")
                            .addMigrations(CacheMigrations.MIGRATION_1_3, CacheMigrations.MIGRATION_2_3)
                            .build()
                        ScanHistoryRepository(db.fileCacheDao(), settingsStore)
                    }

                    BackHandler(enabled = screen.value != Screen.Dashboard) {
                        screen.value = Screen.Dashboard
                    }

                    LaunchedEffect(Unit) {
                        if (state.value is ScanUiState.Idle) {
                            val stored = withContext(Dispatchers.IO) {
                                historyRepo.loadMergedHistory()
                                    ?: resultStore.load()
                            }
                            if (stored != null) {
                                state.value = ScanUiState.Success(stored)
                            }
                        }
                    }

                    LaunchedEffect(pendingScan.value) {
                        val scan = pendingScan.value ?: return@LaunchedEffect
                        val merged = withContext(Dispatchers.IO) {
                            historyRepo.recordScan(scan)
                            historyRepo.loadMergedHistory() ?: scan
                        }
                        state.value = ScanUiState.Success(merged)
                        resultStore.save(merged)
                        screen.value = Screen.Results
                        pendingScan.value = null
                    }

                    LaunchedEffect(clearRequested.value) {
                        if (!clearRequested.value) return@LaunchedEffect
                        withContext(Dispatchers.IO) {
                            historyRepo.clearAll()
                        }
                        resultStore.clear()
                        state.value = ScanUiState.Idle
                        clearRequested.value = false
                    }

                    val contentModifier = Modifier
                        .fillMaxSize()
                        .systemBarsPadding()

                    when (screen.value) {
                        Screen.Dashboard -> {
                            DashboardScreen(
                                onOpenPermission = { screen.value = Screen.Permission },
                                onOpenTargets = { screen.value = Screen.Targets },
                                onOpenScanCommand = { screen.value = Screen.ScanCommand },
                                onOpenResults = { screen.value = Screen.Results },
                                onOpenSettings = { screen.value = Screen.Settings },
                                scrollState = dashboardScroll,
                                modifier = contentModifier
                            )
                        }
                        Screen.Permission -> {
                            PermissionScreen(
                                onBack = { screen.value = Screen.Dashboard },
                                scrollState = permissionScroll,
                                modifier = contentModifier
                            )
                        }
                        Screen.Targets -> {
                            TargetsScreen(
                                onBack = { screen.value = Screen.Dashboard },
                                scrollState = targetsScroll,
                                modifier = contentModifier
                            )
                        }
                        Screen.ScanCommand -> {
                            ScanCommandScreen(
                                state = state,
                                onScanComplete = {
                                    pendingScan.value = it
                                },
                                settingsStore = settingsStore,
                                onBack = { screen.value = Screen.Dashboard },
                                scrollState = scanCommandScroll,
                                modifier = contentModifier
                            )
                        }
                        Screen.Results -> {
                            ResultsScreen(
                                state = state,
                                onBackToDashboard = { screen.value = Screen.Dashboard },
                                onClearResults = {
                                    pendingScan.value = null
                                    clearRequested.value = true
                                },
                                settingsStore = settingsStore,
                                scrollState = resultsScroll,
                                modifier = contentModifier
                            )
                        }
                        Screen.Settings -> {
                            SettingsScreen(
                                settingsStore = settingsStore,
                                onBack = { screen.value = Screen.Dashboard },
                                scrollState = settingsScroll,
                                modifier = contentModifier
                            )
                        }
                    }
                }
            }
        }
    }
}

private enum class Screen {
    Dashboard,
    Permission,
    Targets,
    ScanCommand,
    Results,
    Settings
}