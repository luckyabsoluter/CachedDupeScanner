package opensource.cached_dupe_scanner.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import opensource.cached_dupe_scanner.core.DuplicateGroup
import opensource.cached_dupe_scanner.core.ScanResultViewFilter
import opensource.cached_dupe_scanner.storage.AppSettingsStore
import opensource.cached_dupe_scanner.ui.components.AppTopBar
import opensource.cached_dupe_scanner.ui.components.Spacing
import opensource.cached_dupe_scanner.ui.results.ScanUiState
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
                    hideZeroSizeInResults = settings.hideZeroSizeInResults
                )
                selectedGroup.value?.let { group ->
                    GroupDetailContent(group = group)
                    return@Column
                }
                Text("Files scanned: ${result.files.size}")
                Text("Duplicate groups: ${result.duplicateGroups.size}")
                Spacer(modifier = Modifier.height(8.dp))

                if (result.duplicateGroups.isEmpty()) {
                    Text("No duplicates found.")
                } else {
                    result.duplicateGroups.forEach { group ->
                        val groupCount = group.files.size
                        val groupSize = group.files.sumOf { it.sizeBytes }
                        val fileSize = formatBytes(group.files.firstOrNull()?.sizeBytes ?: 0)
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedGroup.value = group }
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
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
                                        text = "${file.normalizedPath} · ${date}",
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
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
    val groupCount = group.files.size
    val groupSize = group.files.sumOf { it.sizeBytes }
    val fileSize = formatBytes(group.files.firstOrNull()?.sizeBytes ?: 0)

    Text("Group detail")
    Spacer(modifier = Modifier.height(8.dp))
    Text("${groupCount} files · ${formatBytes(groupSize)}")
    Text("File size: ${fileSize}")
    Spacer(modifier = Modifier.height(8.dp))

    group.files.sortedBy { it.normalizedPath }.forEach { file ->
        val date = formatDate(file.lastModifiedMillis)
        Text(
            text = "${file.normalizedPath}\n${formatBytes(file.sizeBytes)} · ${date}",
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(modifier = Modifier.height(6.dp))
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
