package opensource.cached_dupe_scanner.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import opensource.cached_dupe_scanner.storage.ScanHistoryRepository
import opensource.cached_dupe_scanner.ui.components.AppTopBar
import opensource.cached_dupe_scanner.ui.components.Spacing

@Composable
fun DbManagementScreen(
    historyRepo: ScanHistoryRepository,
    onClearAll: () -> Unit,
    clearVersion: Int,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val deleteMissing = remember { mutableStateOf(true) }
    val rehashStale = remember { mutableStateOf(false) }
    val rehashMissing = remember { mutableStateOf(false) }
    val isRunning = remember { mutableStateOf(false) }
    val isClearing = remember { mutableStateOf(false) }
    val clearDialogOpen = remember { mutableStateOf(false) }
    val statusMessage = remember { mutableStateOf<String?>(null) }
    val dbCount = remember { mutableStateOf<Int?>(null) }
    val progressTotal = remember { mutableStateOf(0) }
    val progressProcessed = remember { mutableStateOf(0) }
    val progressDeleted = remember { mutableStateOf(0) }
    val progressRehashed = remember { mutableStateOf(0) }
    val progressMissingHashed = remember { mutableStateOf(0) }
    val progressCurrentPath = remember { mutableStateOf<String?>(null) }

    val refreshCount: () -> Unit = {
        scope.launch {
            dbCount.value = withContext(Dispatchers.IO) { historyRepo.countAll() }
        }
    }

    LaunchedEffect(Unit) {
        refreshCount()
    }

    LaunchedEffect(clearVersion) {
        refreshCount()
        if (isClearing.value) {
            statusMessage.value = "Cleared all cached results."
            isClearing.value = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(Spacing.screenPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AppTopBar(title = "DB management", onBack = onBack)

        Text(
            text = "Choose policies, then run. The app scans storage based on DB entries.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "DB entries: ${dbCount.value ?: "-"}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = deleteMissing.value,
                    onCheckedChange = { deleteMissing.value = it }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Delete DB entries missing on storage")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = rehashStale.value,
                    onCheckedChange = { rehashStale.value = it }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Rehash entries with stale size/date")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = rehashMissing.value,
                    onCheckedChange = { rehashMissing.value = it }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Compute hash for missing entries")
            }
        }

        OutlinedButton(
            onClick = { clearDialogOpen.value = true },
            enabled = !isRunning.value && !isClearing.value,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Clear all cached results")
        }

        statusMessage.value?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }

        if (isRunning.value) {
            val total = progressTotal.value
            val processed = progressProcessed.value
            val progress = if (total > 0) processed.toFloat() / total.toFloat() else 0f
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Progress: ${processed}/${total}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Deleted: ${progressDeleted.value} · Rehashed: ${progressRehashed.value} · Missing hash: ${progressMissingHashed.value}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            progressCurrentPath.value?.let { path ->
                Text(
                    text = "Current: $path",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }

        val canRun = (deleteMissing.value || rehashStale.value || rehashMissing.value) && !isRunning.value
        Button(
            onClick = {
                scope.launch {
                    isRunning.value = true
                    progressTotal.value = 0
                    progressProcessed.value = 0
                    progressDeleted.value = 0
                    progressRehashed.value = 0
                    progressMissingHashed.value = 0
                    progressCurrentPath.value = null
                    val summary = withContext(Dispatchers.IO) {
                        historyRepo.runMaintenance(
                            deleteMissing = deleteMissing.value,
                            rehashStale = rehashStale.value,
                            rehashMissing = rehashMissing.value
                        ) { progress ->
                            progressTotal.value = progress.total
                            progressProcessed.value = progress.processed
                            progressDeleted.value = progress.deleted
                            progressRehashed.value = progress.rehashed
                            progressMissingHashed.value = progress.missingHashed
                            progressCurrentPath.value = progress.currentPath
                        }
                    }
                    statusMessage.value = "Maintenance complete. Deleted ${summary.deleted}, rehashed ${summary.rehashed}, missing hashes ${summary.missingHashed}."
                    isRunning.value = false
                    refreshCount()
                }
            },
            enabled = canRun,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isRunning.value) "Running..." else "Run maintenance")
        }
    }

    if (clearDialogOpen.value) {
        AlertDialog(
            onDismissRequest = { clearDialogOpen.value = false },
            title = { Text("Clear all cached results?") },
            text = { Text("This removes all cached files and results from the database.") },
            confirmButton = {
                Button(
                    onClick = {
                        clearDialogOpen.value = false
                        isClearing.value = true
                        statusMessage.value = "Clearing all cached results..."
                        onClearAll()
                    }
                ) {
                    Text("Clear")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { clearDialogOpen.value = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
