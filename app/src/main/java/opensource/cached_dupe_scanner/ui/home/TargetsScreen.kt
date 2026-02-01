package opensource.cached_dupe_scanner.ui.home

import android.os.Environment
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import opensource.cached_dupe_scanner.sample.SampleData
import opensource.cached_dupe_scanner.storage.ScanTarget
import opensource.cached_dupe_scanner.storage.ScanTargetStore
import opensource.cached_dupe_scanner.ui.components.AppTopBar
import opensource.cached_dupe_scanner.ui.components.Spacing
import java.io.File

@Composable
fun TargetsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { ScanTargetStore(context) }
    val targets = remember { mutableStateOf(store.loadTargets()) }
    val newPath = remember { mutableStateOf("") }
    val editingId = remember { mutableStateOf<String?>(null) }
    val editingPath = remember { mutableStateOf("") }
    val rootDir = remember { File(context.filesDir, "scans") }

    LaunchedEffect(Unit) {
        if (!rootDir.exists()) {
            rootDir.mkdirs()
        }
    }

    Column(
        modifier = modifier
            .padding(Spacing.screenPadding)
            .verticalScroll(rememberScrollState())
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
            }
        }, modifier = Modifier.fillMaxWidth()) {
            Text("Add target")
        }

        Button(onClick = {
            val path = Environment.getExternalStorageDirectory().absolutePath
            store.addTarget(path)
            targets.value = store.loadTargets()
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
            }
        }, modifier = Modifier.fillMaxWidth()) {
            Text("Create samples + add target")
        }

        Spacer(modifier = Modifier.height(12.dp))

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            targets.value.forEach { target ->
                TargetRow(
                    target = target,
                    isEditing = editingId.value == target.id,
                    editingPath = editingPath,
                    onEdit = {
                        editingId.value = target.id
                        editingPath.value = target.path
                    },
                    onSave = {
                        val path = editingPath.value.trim()
                        if (path.isNotBlank()) {
                            store.updateTarget(target.id, path)
                            targets.value = store.loadTargets()
                            editingId.value = null
                            editingPath.value = ""
                        }
                    },
                    onCancel = {
                        editingId.value = null
                        editingPath.value = ""
                    },
                    onRemove = {
                        store.removeTarget(target.id)
                        targets.value = store.loadTargets()
                    }
                )
            }
        }
    }
}

@Composable
private fun TargetRow(
    target: ScanTarget,
    isEditing: Boolean,
    editingPath: MutableState<String>,
    onEdit: () -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onRemove: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            if (isEditing) {
                TextField(
                    value = editingPath.value,
                    onValueChange = { editingPath.value = it },
                    label = { Text("Edit path") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onSave, modifier = Modifier.weight(1f)) {
                        Text("Save")
                    }
                    Button(onClick = onCancel, modifier = Modifier.weight(1f)) {
                        Text("Cancel")
                    }
                }
            } else {
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
}
