package opensource.cached_dupe_scanner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.ScrollState
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavType
import android.net.Uri
import opensource.cached_dupe_scanner.ui.home.DashboardScreen
import opensource.cached_dupe_scanner.ui.home.PermissionScreen
import opensource.cached_dupe_scanner.ui.home.ResultsScreen
import opensource.cached_dupe_scanner.ui.home.ReportsScreen
import opensource.cached_dupe_scanner.ui.home.ScanCommandScreen
import opensource.cached_dupe_scanner.ui.home.SettingsScreen
import opensource.cached_dupe_scanner.ui.home.TargetsScreen
import opensource.cached_dupe_scanner.ui.results.ScanUiState
import opensource.cached_dupe_scanner.storage.ScanResultStore
import opensource.cached_dupe_scanner.storage.ScanHistoryRepository
import opensource.cached_dupe_scanner.storage.AppSettingsStore
import opensource.cached_dupe_scanner.storage.ScanReportStore
import opensource.cached_dupe_scanner.cache.CacheDatabase
import opensource.cached_dupe_scanner.cache.CacheMigrations
import androidx.room.Room
import opensource.cached_dupe_scanner.core.ScanResult
import opensource.cached_dupe_scanner.ui.theme.CachedDupeScannerTheme
import opensource.cached_dupe_scanner.ui.navigation.AppRoutes

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
                    val context = LocalContext.current
                    val resultStore = remember { ScanResultStore(context) }
                    val settingsStore = remember { AppSettingsStore(context) }
                    val reportStore = remember { ScanReportStore(context) }
                    val scope = rememberCoroutineScope()
                    val navController = rememberNavController()
                    val dashboardScroll = rememberSaveable(saver = ScrollState.Saver) { ScrollState(0) }
                    val permissionScroll = rememberSaveable(saver = ScrollState.Saver) { ScrollState(0) }
                    val targetsScroll = rememberSaveable(saver = ScrollState.Saver) { ScrollState(0) }
                    val scanCommandScroll = rememberSaveable(saver = ScrollState.Saver) { ScrollState(0) }
                    val resultsScroll = rememberSaveable(saver = ScrollState.Saver) { ScrollState(0) }
                    val settingsScroll = rememberSaveable(saver = ScrollState.Saver) { ScrollState(0) }
                    val reportsScroll = rememberSaveable(saver = ScrollState.Saver) { ScrollState(0) }
                    val historyRepo = remember {
                        val db = Room.databaseBuilder(context, CacheDatabase::class.java, "scan-cache.db")
                            .addMigrations(CacheMigrations.MIGRATION_1_3, CacheMigrations.MIGRATION_2_3)
                            .build()
                        ScanHistoryRepository(db.fileCacheDao(), settingsStore)
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
                        navController.navigate(AppRoutes.Results) {
                            launchSingleTop = true
                        }
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

                    val navModifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                    val screenModifier = Modifier.fillMaxSize()

                    val restoreLastResult: () -> Unit = {
                        scope.launch {
                            val stored = withContext(Dispatchers.IO) {
                                historyRepo.loadMergedHistory() ?: resultStore.load()
                            }
                            if (stored != null) {
                                state.value = ScanUiState.Success(stored)
                            } else {
                                state.value = ScanUiState.Idle
                            }
                        }
                        Unit
                    }

                    NavHost(
                        navController = navController,
                        startDestination = AppRoutes.Dashboard,
                        modifier = navModifier,
                        enterTransition = { fadeIn(animationSpec = tween(100)) },
                        exitTransition = { fadeOut(animationSpec = tween(100)) },
                        popEnterTransition = { fadeIn(animationSpec = tween(100)) },
                        popExitTransition = { fadeOut(animationSpec = tween(100)) }
                    ) {
                        composable(AppRoutes.Dashboard) {
                            DashboardScreen(
                                onOpenPermission = { navController.navigate(AppRoutes.Permission) },
                                onOpenTargets = { navController.navigate(AppRoutes.Targets) },
                                onOpenScanCommand = { navController.navigate(AppRoutes.ScanCommand) },
                                onOpenResults = { navController.navigate(AppRoutes.Results) },
                                onOpenSettings = { navController.navigate(AppRoutes.Settings) },
                                onOpenReports = { navController.navigate(AppRoutes.Reports) },
                                scrollState = dashboardScroll,
                                modifier = screenModifier
                            )
                        }
                        composable(AppRoutes.Permission) {
                            PermissionScreen(
                                onBack = { navController.popBackStack() },
                                scrollState = permissionScroll,
                                modifier = screenModifier
                            )
                        }
                        composable(AppRoutes.Targets) {
                            TargetsScreen(
                                onBack = { navController.popBackStack() },
                                scrollState = targetsScroll,
                                modifier = screenModifier
                            )
                        }
                        composable(AppRoutes.ScanCommand) {
                            ScanCommandScreen(
                                state = state,
                                onScanComplete = { pendingScan.value = it },
                                onScanCancelled = restoreLastResult,
                                reportStore = reportStore,
                                settingsStore = settingsStore,
                                onBack = { navController.popBackStack() },
                                scrollState = scanCommandScroll,
                                modifier = screenModifier
                            )
                        }
                        composable(AppRoutes.Results) {
                            ResultsScreen(
                                state = state,
                                onBackToDashboard = {
                                    navController.popBackStack(AppRoutes.Dashboard, false)
                                },
                                onOpenGroup = { index ->
                                    navController.navigate("results/detail/$index")
                                },
                                onClearResults = {
                                    pendingScan.value = null
                                    clearRequested.value = true
                                },
                                settingsStore = settingsStore,
                                scrollState = resultsScroll,
                                modifier = screenModifier
                            )
                        }
                        composable(
                            route = AppRoutes.ResultsDetail,
                            arguments = listOf(navArgument("index") { type = NavType.IntType })
                        ) { backStackEntry ->
                            val index = backStackEntry.arguments?.getInt("index") ?: 0
                            ResultsScreen(
                                state = state,
                                onBackToDashboard = { navController.popBackStack() },
                                onOpenGroup = null,
                                onClearResults = {
                                    pendingScan.value = null
                                    clearRequested.value = true
                                },
                                settingsStore = settingsStore,
                                scrollState = resultsScroll,
                                selectedGroupIndex = index,
                                modifier = screenModifier
                            )
                        }
                        composable(AppRoutes.Settings) {
                            SettingsScreen(
                                settingsStore = settingsStore,
                                onBack = { navController.popBackStack() },
                                scrollState = settingsScroll,
                                modifier = screenModifier
                            )
                        }
                        composable(AppRoutes.Reports) {
                            ReportsScreen(
                                reportStore = reportStore,
                                onBack = { navController.popBackStack() },
                                onOpenReport = { id ->
                                    navController.navigate("reports/detail/${Uri.encode(id)}")
                                },
                                scrollState = reportsScroll,
                                modifier = screenModifier
                            )
                        }
                        composable(
                            route = AppRoutes.ReportDetail,
                            arguments = listOf(navArgument("id") { type = NavType.StringType })
                        ) { backStackEntry ->
                            val id = backStackEntry.arguments?.getString("id") ?: ""
                            ReportsScreen(
                                reportStore = reportStore,
                                onBack = { navController.popBackStack() },
                                onOpenReport = null,
                                selectedReportId = id,
                                scrollState = reportsScroll,
                                modifier = screenModifier
                            )
                        }
                    }
                }
            }
        }
    }
}