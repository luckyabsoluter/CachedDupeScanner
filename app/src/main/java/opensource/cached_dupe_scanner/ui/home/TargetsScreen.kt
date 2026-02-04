package opensource.cached_dupe_scanner.ui.home

import android.os.Environment
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import opensource.cached_dupe_scanner.sample.SampleData
import opensource.cached_dupe_scanner.storage.ScanTarget
import opensource.cached_dupe_scanner.storage.ScanTargetStore
import opensource.cached_dupe_scanner.ui.components.AppTopBar
import opensource.cached_dupe_scanner.ui.components.ScrollbarDefaults
import opensource.cached_dupe_scanner.ui.components.Spacing
import opensource.cached_dupe_scanner.ui.components.VerticalScrollbar
import java.io.File

@Composable
fun TargetsScreen(
    onBack: () -> Unit,
    onTargetsChanged: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { ScanTargetStore(context) }
    val targets = remember { mutableStateOf(store.loadTargets()) }
    val newPath = remember { mutableStateOf("") }
    val editingId = remember { mutableStateOf<String?>(null) }
    val editingPath = remember { mutableStateOf("") }
    val deletingId = remember { mutableStateOf<String?>(null) }
    val rootDir = remember { File(context.filesDir, "scans") }

    LaunchedEffect(Unit) {
        if (!rootDir.exists()) {
            rootDir.mkdirs()
        }
    }

    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .padding(Spacing.screenPadding)
                .padding(end = ScrollbarDefaults.ThumbWidth + 8.dp)
                .verticalScroll(scrollState)
        ) {
            AppTopBar(title = "Scan targets", onBack = onBack)
            Spacer(modifier = Modifier.height(8.dp))

            TextField(
                value = newPath.value,
                onValueChange = { newPath.value = it },
                label = { Text("Add target path") },
                modifier = Modifier.fillMaxWidth()
            )
            Button(onClick = {
                val path = newPath.value.trim()
                if (path.isNotBlank()) {
                    store.addTarget(path)
                    targets.value = store.loadTargets()
                    newPath.value = ""
                    onTargetsChanged()
                }
            }, modifier = Modifier.fillMaxWidth()) {
                Text("Add target")
            }

            Button(onClick = {
                val path = Environment.getExternalStorageDirectory().absolutePath
                store.addTarget(path)
                targets.value = store.loadTargets()
                onTargetsChanged()
            }, modifier = Modifier.fillMaxWidth()) {
                Text("Add device storage root")
            }

            Button(onClick = {
                scope.launch {
                    withContext(Dispatchers.IO) {
                        SampleData.createSampleFiles(rootDir)
                    }
                    store.addTarget(rootDir.absolutePath)
                    targets.value = store.loadTargets()
                    onTargetsChanged()
                }
            }, modifier = Modifier.fillMaxWidth()) {
                Text("Create samples + add target")
            }

            Spacer(modifier = Modifier.height(12.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                targets.value.forEach { target ->
                    TargetRow(
                        target = target,
                        onEdit = {
                            editingId.value = target.id
                            editingPath.value = target.path
                        },
                        onRemove = {
                            deletingId.value = target.id
                        }
                    )
                }
            }
        }

        VerticalScrollbar(
            scrollState = scrollState,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .padding(end = 4.dp)
        )
    }

    if (editingId.value != null) {
        AlertDialog(
            onDismissRequest = {
                editingId.value = null
                editingPath.value = ""
            },
            title = { Text("Edit target") },
            text = {
                TextField(
                    value = editingPath.value,
                    onValueChange = { editingPath.value = it },
                    label = { Text("Target path") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    val id = editingId.value
                    val path = editingPath.value.trim()
                    if (id != null && path.isNotBlank()) {
                        store.updateTarget(id, path)
                        targets.value = store.loadTargets()
                        onTargetsChanged()
                    }
                    editingId.value = null
                    editingPath.value = ""
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                Button(onClick = {
                    editingId.value = null
                    editingPath.value = ""
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (deletingId.value != null) {
        AlertDialog(
            onDismissRequest = { deletingId.value = null },
            title = { Text("Remove target") },
            text = { Text("Are you sure you want to remove this target?") },
            confirmButton = {
                Button(onClick = {
                    val id = deletingId.value
                    if (id != null) {
                        store.removeTarget(id)
                        targets.value = store.loadTargets()
                        onTargetsChanged()
                    }
                    deletingId.value = null
                }) {
                    Text("Remove")
                }
            },
            dismissButton = {
                Button(onClick = { deletingId.value = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun TargetRow(
    target: ScanTarget,
    onEdit: () -> Unit,
    onRemove: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = target.path, style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onEdit, modifier = Modifier.weight(1f)) {
                    Text("Edit")
                }
                Button(onClick = onRemove, modifier = Modifier.weight(1f)) {
                    Text("Remove")
                }
            }
        }
    }
}
