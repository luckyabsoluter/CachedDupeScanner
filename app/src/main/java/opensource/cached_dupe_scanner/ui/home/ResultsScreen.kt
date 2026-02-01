package opensource.cached_dupe_scanner.ui.home

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.ImageLoader
import coil.decode.VideoFrameDecoder
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import opensource.cached_dupe_scanner.core.DuplicateGroup
import opensource.cached_dupe_scanner.core.FileMetadata
import opensource.cached_dupe_scanner.core.ResultSortKey
import opensource.cached_dupe_scanner.core.SortDirection
import opensource.cached_dupe_scanner.core.ScanResultViewFilter
import opensource.cached_dupe_scanner.core.ScanResult
import opensource.cached_dupe_scanner.storage.AppSettingsStore
import opensource.cached_dupe_scanner.ui.components.AppTopBar
import opensource.cached_dupe_scanner.ui.components.Spacing
import opensource.cached_dupe_scanner.ui.results.ScanUiState
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.material3.RadioButton

@Composable
fun ResultsScreen(
    state: MutableState<ScanUiState>,
    onBackToDashboard: () -> Unit,
    onClearResults: () -> Unit,
    settingsStore: AppSettingsStore,
    displayResult: ScanResult? = null,
    deletedPaths: Set<String> = emptySet(),
    onDeleteFile: ((FileMetadata) -> Unit)? = null,
    onOpenGroup: ((Int) -> Unit)? = null,
    onSortChanged: (() -> Unit)? = null,
    selectedGroupIndex: Int? = null,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val menuExpanded = remember { mutableStateOf(false) }
    val context = LocalContext.current
    val imageLoader = remember {
        ImageLoader.Builder(context)
            .components { add(VideoFrameDecoder.Factory()) }
            .build()
    }
    val settingsSnapshot = remember { settingsStore.load() }
        val showFullPaths = remember { mutableStateOf(settingsSnapshot.showFullPaths) }
    val sortKey = remember {
        val key = runCatching { ResultSortKey.valueOf(settingsSnapshot.resultSortKey) }
            .getOrDefault(ResultSortKey.Count)
        mutableStateOf(key)
    }
    val sortDirection = remember {
        val dir = runCatching { SortDirection.valueOf(settingsSnapshot.resultSortDirection) }
            .getOrDefault(SortDirection.Desc)
        mutableStateOf(dir)
    }
    val sortDialogOpen = remember { mutableStateOf(false) }
    val pendingSortKey = remember { mutableStateOf(ResultSortKey.Count) }
    val pendingSortDirection = remember { mutableStateOf(SortDirection.Desc) }
    Column(
        modifier = modifier
            .padding(Spacing.screenPadding)
            .verticalScroll(scrollState)
    ) {
        AppTopBar(
            title = "Results",
            onBack = {
                onBackToDashboard()
            },
            actions = {
                if (selectedGroupIndex == null) {
                    IconButton(onClick = { menuExpanded.value = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Menu")
                    }
                    androidx.compose.material3.DropdownMenu(
                        expanded = menuExpanded.value,
                        onDismissRequest = { menuExpanded.value = false }
                    ) {
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text("Clear all results") },
                            onClick = {
                                menuExpanded.value = false
                                onClearResults()
                            }
                        )
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text("Show full paths") },
                            leadingIcon = {
                                Checkbox(
                                    checked = showFullPaths.value,
                                    onCheckedChange = null
                                )
                            },
                            onClick = {
                                showFullPaths.value = !showFullPaths.value
                                    settingsStore.setShowFullPaths(showFullPaths.value)
                                menuExpanded.value = false
                            }
                        )
                    }
                }
            }
        )
        Spacer(modifier = Modifier.height(8.dp))
        when (val current = state.value) {
            ScanUiState.Idle -> Text("No results yet.")
            is ScanUiState.Scanning -> Text("Scanning…")
            is ScanUiState.Error -> Text("Error: ${current.message}")
            is ScanUiState.Success -> {
                val settings = settingsStore.load()
                val result = displayResult ?: ScanResultViewFilter.filterForDisplay(
                    result = current.result,
                    hideZeroSizeInResults = settings.hideZeroSizeInResults,
                    sortKey = sortKey.value,
                    sortDirection = sortDirection.value
                )
                if (selectedGroupIndex != null) {
                    val group = result.duplicateGroups.getOrNull(selectedGroupIndex)
                    if (group != null) {
                        GroupDetailContent(
                            group = group,
                            deletedPaths = deletedPaths,
                            imageLoader = imageLoader,
                            onFileDeleted = { file ->
                                onDeleteFile?.invoke(file)
                            }
                        )
                    } else {
                        Text("Group not found.")
                    }
                    return@Column
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Files scanned: ${result.files.size}")
                        Text("Duplicate groups: ${result.duplicateGroups.size}")
                    }
                    OutlinedButton(onClick = {
                        pendingSortKey.value = sortKey.value
                        pendingSortDirection.value = sortDirection.value
                        sortDialogOpen.value = true
                    }) {
                        Text("Sort")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                if (result.duplicateGroups.isEmpty()) {
                    Text("No duplicates found.")
                } else {
                    result.duplicateGroups.forEach { group ->
                        val groupCount = group.files.size
                        val groupSize = group.files.sumOf { it.sizeBytes }
                        val fileSize = formatBytes(group.files.firstOrNull()?.sizeBytes ?: 0)
                        val preview = group.files.firstOrNull { isMediaFile(it.normalizedPath) }
                        val groupDeleted = group.files.any { deletedPaths.contains(it.normalizedPath) }
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val index = result.duplicateGroups.indexOf(group)
                                    if (onOpenGroup != null && index >= 0) {
                                        onOpenGroup(index)
                                    }
                                }
                            ,
                            colors = if (groupDeleted) {
                                CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                                )
                            } else {
                                CardDefaults.cardColors()
                            }
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(12.dp)
                                    .fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (preview != null) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(File(preview.normalizedPath))
                                            .build(),
                                        imageLoader = imageLoader,
                                        contentDescription = "Thumbnail",
                                        modifier = Modifier.size(72.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        text = "${groupCount} files · Total ${formatBytes(groupSize)}",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = "Per-file ${fileSize}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    group.files.sortedBy { it.normalizedPath }.forEach { file ->
                                        val date = formatDate(file.lastModifiedMillis)
                                        Text(
                                            text = "${formatPath(file.normalizedPath, showFullPaths.value)} · ${date}",
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }

    }

    if (sortDialogOpen.value) {
        AlertDialog(
            onDismissRequest = { sortDialogOpen.value = false },
            title = { Text("Sort options") },
            text = {
                Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                    Text("Sort by")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = pendingSortKey.value == ResultSortKey.Count,
                            onClick = { pendingSortKey.value = ResultSortKey.Count }
                        )
                        Text("Count")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = pendingSortKey.value == ResultSortKey.TotalSize,
                            onClick = { pendingSortKey.value = ResultSortKey.TotalSize }
                        )
                        Text("Total size")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = pendingSortKey.value == ResultSortKey.PerFileSize,
                            onClick = { pendingSortKey.value = ResultSortKey.PerFileSize }
                        )
                        Text("Per-file size")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = pendingSortKey.value == ResultSortKey.Name,
                            onClick = { pendingSortKey.value = ResultSortKey.Name }
                        )
                        Text("Name")
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Order")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = pendingSortDirection.value == SortDirection.Asc,
                            onClick = { pendingSortDirection.value = SortDirection.Asc }
                        )
                        Text("Ascending")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = pendingSortDirection.value == SortDirection.Desc,
                            onClick = { pendingSortDirection.value = SortDirection.Desc }
                        )
                        Text("Descending")
                    }
                }
            },
            confirmButton = {
                OutlinedButton(onClick = {
                    sortKey.value = pendingSortKey.value
                    sortDirection.value = pendingSortDirection.value
                    settingsStore.setResultSortKey(sortKey.value.name)
                    settingsStore.setResultSortDirection(sortDirection.value.name)
                    onSortChanged?.invoke()
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

@Composable
private fun GroupDetailContent(
    group: DuplicateGroup,
    deletedPaths: Set<String>,
    imageLoader: ImageLoader,
    onFileDeleted: (FileMetadata) -> Unit
) {
    val context = LocalContext.current
    val selectedFile = remember { mutableStateOf<FileMetadata?>(null) }
    val groupCount = group.files.size
    val groupSize = group.files.sumOf { it.sizeBytes }
    val fileSize = formatBytes(group.files.firstOrNull()?.sizeBytes ?: 0)
    val preview = group.files.firstOrNull { isMediaFile(it.normalizedPath) }

    Text("Group detail")
    Spacer(modifier = Modifier.height(8.dp))
    if (preview != null) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(File(preview.normalizedPath))
                .build(),
            imageLoader = imageLoader,
            contentDescription = "Thumbnail",
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
    }
    Text("${groupCount} files · Total ${formatBytes(groupSize)}")
    Text("Per-file ${fileSize}")
    Spacer(modifier = Modifier.height(8.dp))

    group.files.sortedBy { it.normalizedPath }.forEach { file ->
        val date = formatDate(file.lastModifiedMillis)
        val isDeleted = deletedPaths.contains(file.normalizedPath)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { selectedFile.value = file }
            ,
            colors = if (isDeleted) {
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            } else {
                CardDefaults.cardColors()
            }
        ) {
            Row(
                modifier = Modifier
                    .padding(10.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = file.normalizedPath,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = if (isDeleted) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${formatBytes(file.sizeBytes)} · ${date}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isDeleted) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }

    selectedFile.value?.let { file ->
        AlertDialog(
            onDismissRequest = { selectedFile.value = null },
            title = { Text("File details") },
            text = {
                Column {
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
                Row {
                    OutlinedButton(onClick = {
                        File(file.normalizedPath).delete()
                        onFileDeleted(file)
                        selectedFile.value = null
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
}
private fun formatBytes(bytes: Long): String {
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unitIndex = 0
    while (value >= 1024 && unitIndex < units.lastIndex) {
        value /= 1024
        unitIndex++
    }
    return if (unitIndex == 0) {
        String.format(Locale.getDefault(), "%.0f %s", value, units[unitIndex])
    } else {
        String.format(Locale.getDefault(), "%.1f %s", value, units[unitIndex])
    }
}

private fun formatDate(millis: Long): String {
    val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    return formatter.format(Date(millis))
}

private fun formatPath(path: String, showFullPath: Boolean): String {
    return if (showFullPath) {
        path
    } else {
        File(path).name.ifBlank { path }
    }
}

private fun openFile(context: android.content.Context, path: String) {
    val file = File(path)
    if (!file.exists()) return
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )
    val mime = getMimeType(uri, path)
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, mime)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    intent.resolveActivity(context.packageManager)?.let {
        context.startActivity(intent)
    }
}

private fun getMimeType(uri: Uri, path: String): String {
    val ext = MimeTypeMap.getFileExtensionFromUrl(path)
    val mime = if (!ext.isNullOrBlank()) {
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.lowercase(Locale.getDefault()))
    } else {
        null
    }
    return mime ?: "*/*"
}

private fun isMediaFile(path: String): Boolean {
    val extension = File(path).extension.lowercase(Locale.getDefault())
    return extension in setOf(
        "jpg", "jpeg", "png", "webp", "gif", "bmp",
        "mp4", "mkv", "mov", "webm"
    )
}
