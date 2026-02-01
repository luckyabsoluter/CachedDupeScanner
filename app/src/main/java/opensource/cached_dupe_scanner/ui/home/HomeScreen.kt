package opensource.cached_dupe_scanner.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.material3.TextField
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.room.Room
import android.content.Intent
import android.net.Uri
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import opensource.cached_dupe_scanner.cache.CacheDatabase
import opensource.cached_dupe_scanner.cache.CacheStore
import opensource.cached_dupe_scanner.engine.IncrementalScanner
import opensource.cached_dupe_scanner.export.ExportFormat
import opensource.cached_dupe_scanner.export.ScanExporter
import opensource.cached_dupe_scanner.sample.SampleData
import opensource.cached_dupe_scanner.storage.PathStore
import opensource.cached_dupe_scanner.ui.results.ScanUiState
import java.io.File

@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state = remember { mutableStateOf<ScanUiState>(ScanUiState.Idle) }
    val exportText = remember { mutableStateOf<String?>(null) }

    val database = remember {
        Room.databaseBuilder(context, CacheDatabase::class.java, "scan-cache.db")
            .build()
    }
    val cacheStore = remember { CacheStore(database.fileCacheDao()) }
    val scanner = remember { IncrementalScanner(cacheStore) }
    val pathStore = remember { PathStore(context) }
    val targetPath = remember { mutableStateOf(pathStore.load() ?: defaultRootPath()) }
    val rootDir = remember { File(context.filesDir, "scans") }

    LaunchedEffect(Unit) {
        if (!rootDir.exists()) {
            rootDir.mkdirs()
        }
    }

    Column(modifier = modifier.padding(16.dp)) {
        Text(
            text = "CachedDupeScanner",
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            text = "Grant all-files access to scan any folder path you choose.",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = if (Environment.isExternalStorageManager()) {
                "All-files access: granted"
            } else {
                "All-files access: not granted"
            },
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(modifier = Modifier.height(8.dp))

        TextField(
            value = targetPath.value,
            onValueChange = { value ->
                targetPath.value = value
                pathStore.save(value)
            },
            label = { Text("Target folder path") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))

        ActionButtons(
            onRequestAllFiles = {
                val uri = Uri.parse("package:${context.packageName}")
                val intent = Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, uri)
                context.startActivity(intent)
            },
            onUseDefaultPath = {
                val path = defaultRootPath()
                targetPath.value = path
                pathStore.save(path)
            },
            onCreateSamples = {
                scope.launch {
                    withContext(Dispatchers.IO) {
                        SampleData.createSampleFiles(rootDir)
                    }
                    exportText.value = null
                }
            },
            onScan = {
                scope.launch {
                    state.value = ScanUiState.Scanning(scanned = 0, total = null)
                    exportText.value = null
                    val result = withContext(Dispatchers.IO) {
                        val target = File(targetPath.value)
                        if (target.exists()) {
                            scanner.scan(target)
                        } else {
                            scanner.scan(rootDir)
                        }
                    }
                    state.value = ScanUiState.Success(result)
                }
            },
            onExportJson = {
                val result = (state.value as? ScanUiState.Success)?.result
                exportText.value = result?.let { ScanExporter.export(it, ExportFormat.JSON) }
            },
            onExportCsv = {
                val result = (state.value as? ScanUiState.Success)?.result
                exportText.value = result?.let { ScanExporter.export(it, ExportFormat.CSV) }
            }
        )

        Spacer(modifier = Modifier.height(16.dp))
        StateSection(state = state)

        exportText.value?.let { text ->
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = "Export", style = MaterialTheme.typography.titleMedium)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Text(
                    text = text,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun ActionButtons(
    onRequestAllFiles: () -> Unit,
    onUseDefaultPath: () -> Unit,
    onCreateSamples: () -> Unit,
    onScan: () -> Unit,
    onExportJson: () -> Unit,
    onExportCsv: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onRequestAllFiles, modifier = Modifier.fillMaxWidth()) {
            Text("Grant all-files access")
        }
        Button(onClick = onUseDefaultPath, modifier = Modifier.fillMaxWidth()) {
            Text("Use device storage root")
        }
        Button(onClick = onCreateSamples, modifier = Modifier.fillMaxWidth()) {
            Text("Create sample files")
        }
        Button(onClick = onScan, modifier = Modifier.fillMaxWidth()) {
            Text("Scan app storage")
        }
        Button(onClick = onExportJson, modifier = Modifier.fillMaxWidth()) {
            Text("Export JSON")
        }
        Button(onClick = onExportCsv, modifier = Modifier.fillMaxWidth()) {
            Text("Export CSV")
        }
    }
}

private fun defaultRootPath(): String {
    return Environment.getExternalStorageDirectory().absolutePath
}

@Composable
private fun StateSection(state: MutableState<ScanUiState>) {
    when (val current = state.value) {
        ScanUiState.Idle -> Text("Ready.")
        is ScanUiState.Scanning -> Text("Scanning…")
        is ScanUiState.Error -> Text("Error: ${current.message}")
        is ScanUiState.Success -> {
            val result = current.result
            Text("Files scanned: ${result.files.size}")
            Text("Duplicate groups: ${result.duplicateGroups.size}")
            Spacer(modifier = Modifier.height(8.dp))

            if (result.duplicateGroups.isEmpty()) {
                Text("No duplicates found.")
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(result.duplicateGroups) { group ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "Hash: ${group.hashHex}",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                group.files.sortedBy { it.normalizedPath }.forEach { file ->
                                    Text(
                                        text = file.normalizedPath,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
