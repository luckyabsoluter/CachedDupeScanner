package opensource.cached_dupe_scanner.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import opensource.cached_dupe_scanner.core.FileMetadata
import opensource.cached_dupe_scanner.storage.ScanHistoryRepository
import opensource.cached_dupe_scanner.ui.components.AppTopBar
import opensource.cached_dupe_scanner.ui.components.Spacing

@Composable
fun DbManagementScreen(
    historyRepo: ScanHistoryRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val filesState = remember { mutableStateOf<List<FileMetadata>?>(null) }
    val isLoading = remember { mutableStateOf(false) }
    val selected = remember { mutableStateOf(setOf<String>()) }
    val isDeleting = remember { mutableStateOf(false) }
    val isRehashing = remember { mutableStateOf(false) }
    val statusMessage = remember { mutableStateOf<String?>(null) }

    val refreshFiles: () -> Unit = {
        scope.launch {
            isLoading.value = true
            val files = withContext(Dispatchers.IO) { historyRepo.loadAllFiles() }
            filesState.value = files
            selected.value = selected.value.filter { selectedPath ->
                files.any { it.normalizedPath == selectedPath }
            }.toSet()
            isLoading.value = false
        }
    }

    fun toggleSelection(path: String) {
        val current = selected.value
        selected.value = if (current.contains(path)) {
            current - path
        } else {
            current + path
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(Spacing.screenPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AppTopBar(title = "DB management", onBack = onBack)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Selected ${selected.value.size}/${filesState.value?.size ?: 0}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = refreshFiles,
                    enabled = !isLoading.value && !isDeleting.value && !isRehashing.value
                ) {
                    Text(if (isLoading.value) "Scanning..." else "Scan DB")
                }
                OutlinedButton(
                    onClick = {
                        val files = filesState.value ?: emptyList()
                        selected.value = files.map { it.normalizedPath }.toSet()
                    },
                    enabled = (filesState.value?.isNotEmpty() == true) && !isLoading.value
                ) {
                    Text("Select all")
                }
                OutlinedButton(
                    onClick = { selected.value = emptySet() },
                    enabled = selected.value.isNotEmpty() && !isLoading.value
                ) {
                    Text("Clear")
                }
            }
        }

        statusMessage.value?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    val targets = selected.value.toList()
                    scope.launch {
                        isDeleting.value = true
                        val deleted = withContext(Dispatchers.IO) {
                            historyRepo.deleteMissingByNormalizedPaths(targets)
                        }
                        statusMessage.value = "Deleted ${deleted} missing entries."
                        isDeleting.value = false
                        refreshFiles()
                    }
                },
                enabled = selected.value.isNotEmpty() && !isDeleting.value && !isRehashing.value && !isLoading.value
            ) {
                Text(if (isDeleting.value) "Deleting..." else "Delete missing")
            }
            Button(
                onClick = {
                    val targets = selected.value.toList()
                    scope.launch {
                        isRehashing.value = true
                        val updated = withContext(Dispatchers.IO) {
                            historyRepo.rehashIfChanged(targets)
                        }
                        statusMessage.value = "Rehashed ${updated} entries."
                        isRehashing.value = false
                        refreshFiles()
                    }
                },
                enabled = selected.value.isNotEmpty() && !isDeleting.value && !isRehashing.value && !isLoading.value
            ) {
                Text(if (isRehashing.value) "Rehashing..." else "Rehash changed")
            }
        }

        when {
            filesState.value == null -> {
                Text("Scan the DB to load cached entries.")
            }
            isLoading.value -> {
                Text("Loading cached entries...")
            }
            filesState.value?.isEmpty() == true -> {
                Text("No cached files found.")
            }
            else -> {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val files = filesState.value ?: emptyList()
                items(files, key = { it.normalizedPath }) { file ->
                    val isSelected = selected.value.contains(file.normalizedPath)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { toggleSelection(file.normalizedPath) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = { toggleSelection(file.normalizedPath) }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
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
                }
            }
            }
        }
    }
}
