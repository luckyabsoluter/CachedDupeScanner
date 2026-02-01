package opensource.cached_dupe_scanner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import opensource.cached_dupe_scanner.ui.home.DashboardScreen
import opensource.cached_dupe_scanner.ui.home.PermissionScreen
import opensource.cached_dupe_scanner.ui.home.ResultsScreen
import opensource.cached_dupe_scanner.ui.home.ScanScreen
import opensource.cached_dupe_scanner.ui.results.ScanUiState
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

                    val contentModifier = Modifier
                        .fillMaxSize()
                        .systemBarsPadding()
                        .padding(innerPadding)

                    when (screen.value) {
                        Screen.Dashboard -> {
                            DashboardScreen(
                                onOpenPermission = { screen.value = Screen.Permission },
                                onOpenScan = { screen.value = Screen.Scan },
                                onOpenResults = { screen.value = Screen.Results },
                                modifier = contentModifier
                            )
                        }
                        Screen.Permission -> {
                            PermissionScreen(
                                onNext = { screen.value = Screen.Scan },
                                onBack = { screen.value = Screen.Dashboard },
                                modifier = contentModifier
                            )
                        }
                        Screen.Scan -> {
                            ScanScreen(
                                state = state,
                                onScanComplete = {
                                    exportText.value = null
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
                                onBackToScan = { screen.value = Screen.Scan },
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
    Scan,
    Results
}