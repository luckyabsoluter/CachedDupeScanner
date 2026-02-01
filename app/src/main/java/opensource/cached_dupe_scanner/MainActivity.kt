package opensource.cached_dupe_scanner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import opensource.cached_dupe_scanner.ui.home.DashboardScreen
import opensource.cached_dupe_scanner.ui.home.PermissionScreen
import opensource.cached_dupe_scanner.ui.home.ResultsScreen
import opensource.cached_dupe_scanner.ui.home.ScanCommandScreen
import opensource.cached_dupe_scanner.ui.home.TargetsScreen
import opensource.cached_dupe_scanner.ui.results.ScanUiState
import opensource.cached_dupe_scanner.storage.ScanResultStore
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
                    val exportText = remember { mutableStateOf<String?>(null) }
                    val context = LocalContext.current
                    val resultStore = remember { ScanResultStore(context) }

                    BackHandler(enabled = screen.value != Screen.Dashboard) {
                        screen.value = Screen.Dashboard
                    }

                    LaunchedEffect(Unit) {
                        if (state.value is ScanUiState.Idle) {
                            val stored = resultStore.load()
                            if (stored != null) {
                                state.value = ScanUiState.Success(stored)
                            }
                        }
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
                                modifier = contentModifier
                            )
                        }
                        Screen.Permission -> {
                            PermissionScreen(
                                onBack = { screen.value = Screen.Dashboard },
                                modifier = contentModifier
                            )
                        }
                        Screen.Targets -> {
                            TargetsScreen(
                                onBack = { screen.value = Screen.Dashboard },
                                modifier = contentModifier
                            )
                        }
                        Screen.ScanCommand -> {
                            ScanCommandScreen(
                                state = state,
                                onScanComplete = {
                                    exportText.value = null
                                    resultStore.save(it.result)
                                    screen.value = Screen.Results
                                },
                                onBack = { screen.value = Screen.Dashboard },
                                modifier = contentModifier
                            )
                        }
                        Screen.Results -> {
                            ResultsScreen(
                                state = state,
                                exportText = exportText,
                                onBackToDashboard = { screen.value = Screen.Dashboard },
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
    Results
}