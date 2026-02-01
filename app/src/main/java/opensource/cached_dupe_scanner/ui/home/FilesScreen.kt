package opensource.cached_dupe_scanner.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.RadioButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import opensource.cached_dupe_scanner.core.FileMetadata
import opensource.cached_dupe_scanner.storage.ScanHistoryRepository
import opensource.cached_dupe_scanner.ui.components.AppTopBar
import opensource.cached_dupe_scanner.ui.components.Spacing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.runtime.rememberCoroutineScope

private enum class FileSortKey {
    Name,
    Size,
    Modified
}

@Composable
fun FilesScreen(
    historyRepo: ScanHistoryRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val menuExpanded = remember { mutableStateOf(false) }
    val sortDialogOpen = remember { mutableStateOf(false) }
    val sortKey = remember { mutableStateOf(FileSortKey.Name) }
    val sortDirectionAsc = remember { mutableStateOf(true) }
    val pendingSortKey = remember { mutableStateOf(FileSortKey.Name) }
    val pendingSortDirectionAsc = remember { mutableStateOf(true) }
    val filesState = remember { mutableStateOf<List<FileMetadata>>(emptyList()) }
    val selectedFile = remember { mutableStateOf<FileMetadata?>(null) }

    LaunchedEffect(Unit) {
        val result = withContext(Dispatchers.IO) {
            historyRepo.loadMergedHistory()
        }
        filesState.value = result?.files ?: emptyList()
    }

    val sortedFiles = remember(filesState.value, sortKey.value, sortDirectionAsc.value) {
        val sorted = when (sortKey.value) {
            FileSortKey.Name -> filesState.value.sortedBy { it.normalizedPath.lowercase() }
            FileSortKey.Size -> filesState.value.sortedBy { it.sizeBytes }
            FileSortKey.Modified -> filesState.value.sortedBy { it.lastModifiedMillis }
        }
        if (sortDirectionAsc.value) sorted else sorted.reversed()
    }

    LazyColumn(
        state = listState,
        modifier = modifier.padding(Spacing.screenPadding)
    ) {
        item {
            AppTopBar(
                title = "Files",
                onBack = onBack,
                actions = {
                    IconButton(onClick = { menuExpanded.value = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Menu")
                    }
                    androidx.compose.material3.DropdownMenu(
                        expanded = menuExpanded.value,
                        onDismissRequest = { menuExpanded.value = false }
                    ) {
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text("Sort") },
                            onClick = {
                                menuExpanded.value = false
                                pendingSortKey.value = sortKey.value
                                pendingSortDirectionAsc.value = sortDirectionAsc.value
                                sortDialogOpen.value = true
                            }
                        )
                    }
                }
            )
        }
        item { Spacer(modifier = Modifier.height(8.dp)) }
        if (sortedFiles.isEmpty()) {
            item { Text("No files in history.") }
        } else {
            items(sortedFiles, key = { it.normalizedPath }) { file ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedFile.value = file }
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = formatPath(file.normalizedPath, showFullPath = false),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = file.normalizedPath,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${formatBytes(file.sizeBytes)} · ${formatDate(file.lastModifiedMillis)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }

    selectedFile.value?.let { file ->
        AlertDialog(
            onDismissRequest = { selectedFile.value = null },
            title = { Text("File details") },
            text = {
                Column {
                    Text("Name: ${formatPath(file.normalizedPath, showFullPath = false)}")
                    Text("Path: ${file.normalizedPath}")
                    Text("Size: ${formatBytes(file.sizeBytes)}")
                    Text("Modified: ${formatDate(file.lastModifiedMillis)}")
                }
            },
            confirmButton = {
                OutlinedButton(onClick = {
                    openFile(context, file.normalizedPath)
                    selectedFile.value = null
                }) {
                    Text("Open")
                }
            },
            dismissButton = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = {
                        val toDelete = file.normalizedPath
                        val deleted = java.io.File(toDelete).delete()
                        if (deleted) {
                            selectedFile.value = null
                            filesState.value = filesState.value.filterNot { it.normalizedPath == toDelete }
                            scope.launch(Dispatchers.IO) {
                                historyRepo.deleteByNormalizedPath(toDelete)
                            }
                        }
                    }) {
                        Text("Delete")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedButton(onClick = { selectedFile.value = null }) {
                        Text("Cancel")
                    }
                }
            }
        )
    }

    if (sortDialogOpen.value) {
        AlertDialog(
            onDismissRequest = { sortDialogOpen.value = false },
            title = { Text("Sort options") },
            text = {
                Column {
                    Text("Sort by")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = pendingSortKey.value == FileSortKey.Name,
                            onClick = { pendingSortKey.value = FileSortKey.Name }
                        )
                        Text("Name")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = pendingSortKey.value == FileSortKey.Size,
                            onClick = { pendingSortKey.value = FileSortKey.Size }
                        )
                        Text("Size")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = pendingSortKey.value == FileSortKey.Modified,
                            onClick = { pendingSortKey.value = FileSortKey.Modified }
                        )
                        Text("Modified")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Order")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = pendingSortDirectionAsc.value,
                            onClick = { pendingSortDirectionAsc.value = true }
                        )
                        Text("Ascending")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = !pendingSortDirectionAsc.value,
                            onClick = { pendingSortDirectionAsc.value = false }
                        )
                        Text("Descending")
                    }
                }
            },
            confirmButton = {
                OutlinedButton(onClick = {
                    sortKey.value = pendingSortKey.value
                    sortDirectionAsc.value = pendingSortDirectionAsc.value
                    sortDialogOpen.value = false
                }) {
                    Text("Apply")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { sortDialogOpen.value = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
