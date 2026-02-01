package opensource.cached_dupe_scanner.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ScrollState
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import opensource.cached_dupe_scanner.core.DuplicateGroup
import opensource.cached_dupe_scanner.core.ResultSortOption
import opensource.cached_dupe_scanner.core.ScanResultViewFilter
import opensource.cached_dupe_scanner.storage.AppSettingsStore
import opensource.cached_dupe_scanner.ui.components.AppTopBar
import opensource.cached_dupe_scanner.ui.components.Spacing
import opensource.cached_dupe_scanner.ui.results.ScanUiState
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ResultsScreen(
    state: MutableState<ScanUiState>,
    onBackToDashboard: () -> Unit,
    onClearResults: () -> Unit,
    settingsStore: AppSettingsStore,
    scrollState: ScrollState,
    modifier: Modifier = Modifier
) {
    val menuExpanded = remember { mutableStateOf(false) }
    val selectedGroup = remember { mutableStateOf<DuplicateGroup?>(null) }
    val showFullPaths = remember { mutableStateOf(false) }
    val sortOption = remember { mutableStateOf(ResultSortOption.CountDesc) }
    BackHandler(enabled = selectedGroup.value != null) {
        selectedGroup.value = null
    }
    Column(
        modifier = modifier
            .padding(Spacing.screenPadding)
            .verticalScroll(scrollState)
    ) {
        AppTopBar(
            title = "Results",
            onBack = {
                if (selectedGroup.value != null) {
                    selectedGroup.value = null
                } else {
                    onBackToDashboard()
                }
            },
            actions = {
                if (selectedGroup.value == null) {
                    IconButton(onClick = { menuExpanded.value = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Menu")
                    }
                    DropdownMenu(
                        expanded = menuExpanded.value,
                        onDismissRequest = { menuExpanded.value = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Clear all results") },
                            onClick = {
                                menuExpanded.value = false
                                onClearResults()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Show full paths") },
                            leadingIcon = {
                                Checkbox(
                                    checked = showFullPaths.value,
                                    onCheckedChange = null
                                )
                            },
                            onClick = {
                                showFullPaths.value = !showFullPaths.value
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
                val result = ScanResultViewFilter.filterForDisplay(
                    result = current.result,
                    hideZeroSizeInResults = settings.hideZeroSizeInResults,
                    sortOption = sortOption.value
                )
                selectedGroup.value?.let { group ->
                    GroupDetailContent(group = group)
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FilterChip(
                            selected = sortOption.value == ResultSortOption.CountDesc,
                            onClick = { sortOption.value = ResultSortOption.CountDesc },
                            label = { Text("Count") }
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        FilterChip(
                            selected = sortOption.value == ResultSortOption.TotalSizeDesc,
                            onClick = { sortOption.value = ResultSortOption.TotalSizeDesc },
                            label = { Text("Size") }
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        FilterChip(
                            selected = sortOption.value == ResultSortOption.NameAsc,
                            onClick = { sortOption.value = ResultSortOption.NameAsc },
                            label = { Text("Name") }
                        )
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
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedGroup.value = group }
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(12.dp)
                                    .fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (preview != null) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(LocalContext.current)
                                            .data(File(preview.normalizedPath))
                                            .build(),
                                        contentDescription = "Thumbnail",
                                        modifier = Modifier.size(72.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        text = "${groupCount} files · ${formatBytes(groupSize)}",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = "File size: ${fileSize}",
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
}

@Composable
private fun GroupDetailContent(group: DuplicateGroup) {
    val context = LocalContext.current
    val groupCount = group.files.size
    val groupSize = group.files.sumOf { it.sizeBytes }
    val fileSize = formatBytes(group.files.firstOrNull()?.sizeBytes ?: 0)
    val preview = group.files.firstOrNull { isMediaFile(it.normalizedPath) }

    Text("Group detail")
    Spacer(modifier = Modifier.height(8.dp))
    if (preview != null) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(File(preview.normalizedPath))
                .build(),
            contentDescription = "Thumbnail",
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
    }
    Text("${groupCount} files · ${formatBytes(groupSize)}")
    Text("File size: ${fileSize}")
    Spacer(modifier = Modifier.height(8.dp))

    group.files.sortedBy { it.normalizedPath }.forEach { file ->
        val date = formatDate(file.lastModifiedMillis)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { openFile(context, file.normalizedPath) }
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
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${formatBytes(file.sizeBytes)} · ${date}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
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
