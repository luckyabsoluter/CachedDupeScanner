package opensource.cached_dupe_scanner.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.room.Room
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import opensource.cached_dupe_scanner.cache.CacheDatabase
import opensource.cached_dupe_scanner.cache.CacheStore
import opensource.cached_dupe_scanner.cache.CacheMigrations
import opensource.cached_dupe_scanner.core.ScanResult
import opensource.cached_dupe_scanner.core.ScanResultMerger
import opensource.cached_dupe_scanner.engine.IncrementalScanner
import opensource.cached_dupe_scanner.storage.ScanTarget
import opensource.cached_dupe_scanner.storage.ScanTargetStore
import opensource.cached_dupe_scanner.storage.AppSettingsStore
import opensource.cached_dupe_scanner.ui.components.AppTopBar
import opensource.cached_dupe_scanner.ui.components.Spacing
import opensource.cached_dupe_scanner.ui.results.ScanUiState
import java.io.File

@Composable
fun ScanCommandScreen(
    state: MutableState<ScanUiState>,
    onScanComplete: (ScanResult) -> Unit,
    settingsStore: AppSettingsStore,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { ScanTargetStore(context) }
    val targets = remember { mutableStateOf(store.loadTargets()) }

    val database = remember {
        Room.databaseBuilder(context, CacheDatabase::class.java, "scan-cache.db")
            .addMigrations(CacheMigrations.MIGRATION_1_3, CacheMigrations.MIGRATION_2_3)
            .build()
    }
    val cacheStore = remember { CacheStore(database.fileCacheDao()) }
    val scanner = remember { IncrementalScanner(cacheStore) }

    LaunchedEffect(Unit) {
        targets.value = store.loadTargets()
    }

    Column(
        modifier = modifier
            .padding(Spacing.screenPadding)
            .verticalScroll(rememberScrollState())
    ) {
        AppTopBar(title = "Scan command", onBack = onBack)
        Spacer(modifier = Modifier.height(8.dp))

        if (targets.value.isEmpty()) {
            Text("No scan targets yet. Add one first.")
            return@Column
        }

        Text("Select a target:")
        Spacer(modifier = Modifier.height(6.dp))

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            targets.value.forEach { target ->
                TargetScanRow(
                    target = target,
                    onScan = {
                        runScanForTarget(
                            scope,
                            scanner,
                            state,
                            target,
                            onScanComplete
                        )
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Button(onClick = {
            runScanForAllTargets(
                scope,
                scanner,
                state,
                targets.value,
                onScanComplete
            )
        }, modifier = Modifier.fillMaxWidth()) {
            Text("Scan all targets")
        }
    }
}

@Composable
private fun TargetScanRow(target: ScanTarget, onScan: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = target.path, style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(6.dp))
            Button(onClick = onScan, modifier = Modifier.fillMaxWidth()) {
                Text("Scan target")
            }
        }
    }
}

private fun runScanForTarget(
    scope: kotlinx.coroutines.CoroutineScope,
    scanner: IncrementalScanner,
    state: MutableState<ScanUiState>,
    target: ScanTarget,
    onScanComplete: (ScanResult) -> Unit
) {
    scope.launch {
        state.value = ScanUiState.Scanning(scanned = 0, total = null)
        val targetFile = File(target.path)
        if (!targetFile.exists()) {
            state.value = ScanUiState.Error("Target path not found")
            return@launch
        }
        val result = withContext(Dispatchers.IO) {
            scanner.scan(targetFile)
        }
        onScanComplete(result)
    }
}

private fun runScanForAllTargets(
    scope: kotlinx.coroutines.CoroutineScope,
    scanner: IncrementalScanner,
    state: MutableState<ScanUiState>,
    targets: List<ScanTarget>,
    onScanComplete: (ScanResult) -> Unit
) {
    if (targets.isEmpty()) {
        state.value = ScanUiState.Error("No scan targets")
        return
    }

    scope.launch {
        state.value = ScanUiState.Scanning(scanned = 0, total = null)
        val results = mutableListOf<ScanResult>()
        for (target in targets) {
            val targetFile = File(target.path)
            if (!targetFile.exists()) {
                continue
            }
            val result = withContext(Dispatchers.IO) {
                scanner.scan(targetFile)
            }
            results.add(result)
        }

        if (results.isEmpty()) {
            state.value = ScanUiState.Error("No valid targets to scan")
            return@launch
        }

        val merged = ScanResultMerger.merge(
            System.currentTimeMillis(),
            results
        )
        onScanComplete(merged)
    }
}
