package opensource.cached_dupe_scanner.ui.home

import android.os.Environment
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
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
import opensource.cached_dupe_scanner.engine.IncrementalScanner
import opensource.cached_dupe_scanner.sample.SampleData
import opensource.cached_dupe_scanner.storage.PathStore
import opensource.cached_dupe_scanner.ui.results.ScanUiState
import java.io.File

@Composable
fun ScanScreen(
    state: MutableState<ScanUiState>,
    onScanComplete: (ScanUiState.Success) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pathStore = remember { PathStore(context) }
    val targetPath = remember { mutableStateOf(pathStore.load() ?: defaultRootPath()) }
    val rootDir = remember { File(context.filesDir, "scans") }

    val database = remember {
        Room.databaseBuilder(context, CacheDatabase::class.java, "scan-cache.db")
            .build()
    }
    val cacheStore = remember { CacheStore(database.fileCacheDao()) }
    val scanner = remember { IncrementalScanner(cacheStore) }

    LaunchedEffect(Unit) {
        if (!rootDir.exists()) {
            rootDir.mkdirs()
        }
    }

    Column(modifier = modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = "2) Scan", style = MaterialTheme.typography.headlineSmall)
        TextField(
            value = targetPath.value,
            onValueChange = { value ->
                targetPath.value = value
                pathStore.save(value)
            },
            label = { Text("Target folder path") },
            modifier = Modifier.fillMaxWidth()
        )
        Button(onClick = {
            val path = defaultRootPath()
            targetPath.value = path
            pathStore.save(path)
        }, modifier = Modifier.fillMaxWidth()) {
            Text("Use device storage root")
        }
        Button(onClick = {
            scope.launch {
                withContext(Dispatchers.IO) {
                    SampleData.createSampleFiles(rootDir)
                }
            }
        }, modifier = Modifier.fillMaxWidth()) {
            Text("Create sample files")
        }
        Button(onClick = {
            scope.launch {
                state.value = ScanUiState.Scanning(scanned = 0, total = null)
                val result = withContext(Dispatchers.IO) {
                    val target = File(targetPath.value)
                    if (target.exists()) {
                        scanner.scan(target)
                    } else {
                        scanner.scan(rootDir)
                    }
                }
                val success = ScanUiState.Success(result)
                state.value = success
                onScanComplete(success)
            }
        }, modifier = Modifier.fillMaxWidth()) {
            Text("Scan now")
        }
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("Back")
        }
    }
}

private fun defaultRootPath(): String {
    return Environment.getExternalStorageDirectory().absolutePath
}
