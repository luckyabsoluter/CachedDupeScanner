package opensource.cached_dupe_scanner.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.ImageLoader
import coil.decode.VideoFrameDecoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.collect
import opensource.cached_dupe_scanner.core.FileMetadata
import opensource.cached_dupe_scanner.storage.ScanHistoryRepository
import opensource.cached_dupe_scanner.storage.AppSettingsStore
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

private enum class FileSortDirection {
    Asc,
    Desc
}

@Composable
fun FilesScreen(
    historyRepo: ScanHistoryRepository,
    clearVersion: Int,
    refreshVersion: Int,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val settingsStore = remember { AppSettingsStore(context) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val menuExpanded = remember { mutableStateOf(false) }
    val sortDialogOpen = remember { mutableStateOf(false) }
    val settingsSnapshot = remember { settingsStore.load() }
    val sortKey = remember {
        val key = runCatching { FileSortKey.valueOf(settingsSnapshot.filesSortKey) }
            .getOrDefault(FileSortKey.Name)
        mutableStateOf(key)
    }
    val sortDirection = remember {
        val dir = runCatching { FileSortDirection.valueOf(settingsSnapshot.filesSortDirection) }
            .getOrDefault(FileSortDirection.Asc)
        mutableStateOf(dir)
    }
    val pendingSortKey = remember { mutableStateOf(FileSortKey.Name) }
    val pendingSortDirection = remember { mutableStateOf(FileSortDirection.Asc) }
    val filesState = remember { mutableStateOf<List<FileMetadata>?>(null) }
    val isLoading = remember { mutableStateOf(true) }
    val selectedFile = remember { mutableStateOf<FileMetadata?>(null) }
    val topVisibleIndex = remember { mutableStateOf(0) }
    val imageLoader = remember {
        ImageLoader.Builder(context)
            .components { add(VideoFrameDecoder.Factory()) }
            .build()
    }

    LaunchedEffect(Unit) {
        isLoading.value = true
        val result = withContext(Dispatchers.IO) {
            historyRepo.loadMergedHistory()
        }
        filesState.value = result?.files ?: emptyList()
        isLoading.value = false
    }

    LaunchedEffect(refreshVersion) {
        isLoading.value = true
        val result = withContext(Dispatchers.IO) {
            historyRepo.loadMergedHistory()
        }
        filesState.value = result?.files ?: emptyList()
        isLoading.value = false
    }

    LaunchedEffect(clearVersion) {
        filesState.value = emptyList()
        selectedFile.value = null
        isLoading.value = false
    }

    val sortedFiles = remember(filesState.value, sortKey.value, sortDirection.value) {
        val base = filesState.value ?: emptyList()
        val sorted = when (sortKey.value) {
            FileSortKey.Name -> base.sortedBy { it.normalizedPath.lowercase() }
            FileSortKey.Size -> base.sortedBy { it.sizeBytes }
            FileSortKey.Modified -> base.sortedBy { it.lastModifiedMillis }
        }
        if (sortDirection.value == FileSortDirection.Asc) sorted else sorted.reversed()
    }

    val fileIndexByPath = remember(sortedFiles) {
        sortedFiles.mapIndexed { index, file -> file.normalizedPath to index }.toMap()
    }

    LaunchedEffect(sortedFiles.size) {
        if (sortedFiles.isNotEmpty()) {
            topVisibleIndex.value = 0
        }
    }

    LaunchedEffect(sortedFiles.size) {
        if (sortedFiles.isEmpty()) return@LaunchedEffect
        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo
                .firstOrNull { it.key is String }
                ?.key as? String
        }
            .distinctUntilChanged()
            .filter { it != null }
            .collect { key ->
                val index = fileIndexByPath[key] ?: 0
                topVisibleIndex.value = index
            }
    }

    val overlayText = run {
        val total = sortedFiles.size
        if (total == 0) {
            null
        } else {
            val current = (topVisibleIndex.value + 1).coerceAtLeast(1)
            val percent = ((current.toDouble() / total.toDouble()) * 100).toInt()
            "$current/$total (${percent}%)"
        }
    }

    Box(modifier = modifier) {
        LazyColumn(
            state = listState,
            modifier = Modifier.padding(Spacing.screenPadding)
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
                                    pendingSortDirection.value = sortDirection.value
                                    sortDialogOpen.value = true
                                }
                            )
                        }
                    }
                )
            }
            item { Spacer(modifier = Modifier.height(8.dp)) }
            if (isLoading.value) {
                item { Text("Loading files...") }
            } else if (sortedFiles.isEmpty()) {
                item { Text("No files in history.") }
            } else {
                items(sortedFiles, key = { it.normalizedPath }) { file ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedFile.value = file }
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(12.dp)
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isMediaFile(file.normalizedPath)) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(java.io.File(file.normalizedPath))
                                        .build(),
                                    imageLoader = imageLoader,
                                    contentDescription = "Thumbnail",
                                    modifier = Modifier.size(56.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Column(modifier = Modifier.fillMaxWidth()) {
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
                                    text = "${formatBytesWithExact(file.sizeBytes)} · ${formatDate(file.lastModifiedMillis)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
        overlayText?.let { indicator ->
            Text(
                text = indicator,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 12.dp, top = 12.dp)
            )
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
                    Text("Size: ${formatBytesWithExact(file.sizeBytes)}")
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
                            filesState.value = (filesState.value ?: emptyList())
                                .filterNot { it.normalizedPath == toDelete }
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
                            selected = pendingSortDirection.value == FileSortDirection.Asc,
                            onClick = { pendingSortDirection.value = FileSortDirection.Asc }
                        )
                        Text("Ascending")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = pendingSortDirection.value == FileSortDirection.Desc,
                            onClick = { pendingSortDirection.value = FileSortDirection.Desc }
                        )
                        Text("Descending")
                    }
                }
            },
            confirmButton = {
                OutlinedButton(onClick = {
                    sortKey.value = pendingSortKey.value
                    sortDirection.value = pendingSortDirection.value
                    settingsStore.setFilesSortKey(sortKey.value.name)
                    settingsStore.setFilesSortDirection(sortDirection.value.name)
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
