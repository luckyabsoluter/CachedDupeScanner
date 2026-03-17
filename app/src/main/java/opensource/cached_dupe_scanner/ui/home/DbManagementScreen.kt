package opensource.cached_dupe_scanner.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import opensource.cached_dupe_scanner.notifications.DbTaskNotificationKind
import opensource.cached_dupe_scanner.notifications.ScanNotificationController
import opensource.cached_dupe_scanner.notifications.buildDbMaintenanceNotificationContent
import opensource.cached_dupe_scanner.notifications.buildDbTaskStartedNotificationContent
import opensource.cached_dupe_scanner.storage.ResultsDbRepository
import opensource.cached_dupe_scanner.storage.ScanHistoryRepository
import opensource.cached_dupe_scanner.ui.components.AppTopBar
import opensource.cached_dupe_scanner.ui.components.ScrollbarDefaults
import opensource.cached_dupe_scanner.ui.components.Spacing
import opensource.cached_dupe_scanner.ui.components.VerticalScrollbar

@Composable
fun DbManagementScreen(
    historyRepo: ScanHistoryRepository,
    resultsRepo: ResultsDbRepository,
    uiState: DbManagementUiState,
    appScope: CoroutineScope,
    onMaintenanceApplied: () -> Unit,
    onClearAll: suspend () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    val notificationController = remember { ScanNotificationController(context) }
    val deleteMissing = remember { mutableStateOf(true) }
    val rehashStale = remember { mutableStateOf(false) }
    val rehashMissing = remember { mutableStateOf(false) }
    val clearDialogOpen = remember { mutableStateOf(false) }

    val refreshOverview: () -> Unit = {
        appScope.launch {
            val counts = withContext(Dispatchers.IO) {
                Pair(historyRepo.countAll(), resultsRepo.countGroups())
            }
            uiState.updateOverview(dbCount = counts.first, groupCount = counts.second)
        }
    }

    LaunchedEffect(Unit) {
        refreshOverview()
    }

    val isBusy = uiState.isRunning || uiState.isRebuilding || uiState.isClearing
    val canRun = (deleteMissing.value || rehashStale.value || rehashMissing.value) && !isBusy

    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(Spacing.screenPadding)
                .padding(end = ScrollbarDefaults.ThumbWidth + 8.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AppTopBar(title = "DB management", onBack = onBack)

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Overview",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = "Choose policies, then run. The app scans storage based on DB entries.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "DB entries: ${uiState.dbCount ?: "-"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Duplicate groups: ${uiState.groupCount ?: "-"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Duplicate group snapshot",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = "This action rebuilds the derived group snapshot only. It does not scan files on storage.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = {
                            appScope.launch {
                                uiState.startRebuild()
                                val content =
                                    buildDbTaskStartedNotificationContent(DbTaskNotificationKind.RebuildGroups)
                                notificationController.showTaskStarted(
                                    title = content.title,
                                    text = content.text,
                                    subText = content.subText
                                )
                                runCatching {
                                    withContext(Dispatchers.IO) {
                                        resultsRepo.rebuildGroups()
                                    }
                                }.onSuccess {
                                    uiState.completeRebuild()
                                    notificationController.showTaskCompleted(
                                        title = "Duplicate groups rebuilt",
                                        text = "The duplicate group snapshot is ready."
                                    )
                                    onMaintenanceApplied()
                                    refreshOverview()
                                }.onFailure {
                                    uiState.failRebuild()
                                    notificationController.showTaskFailed(
                                        title = "Duplicate group rebuild failed",
                                        text = "The duplicate group snapshot could not be refreshed."
                                    )
                                }
                            }
                        },
                        enabled = !isBusy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (uiState.isRebuilding) "Rebuilding groups..." else "Rebuild duplicate groups")
                    }
                    if (uiState.isRebuilding) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    uiState.groupStatusMessage?.let { message ->
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "File maintenance policies",
                        style = MaterialTheme.typography.titleSmall
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

                    Text(
                        text = "Run",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Button(
                        onClick = {
                            appScope.launch {
                                uiState.startMaintenance()
                                val content =
                                    buildDbTaskStartedNotificationContent(DbTaskNotificationKind.Maintenance)
                                notificationController.showTaskStarted(
                                    title = content.title,
                                    text = content.text,
                                    subText = content.subText
                                )
                                runCatching {
                                    withContext(Dispatchers.IO) {
                                        historyRepo.runMaintenance(
                                            deleteMissing = deleteMissing.value,
                                            rehashStale = rehashStale.value,
                                            rehashMissing = rehashMissing.value
                                        ) { progress ->
                                            uiState.applyMaintenanceProgress(progress)
                                            val progressContent =
                                                buildDbMaintenanceNotificationContent(progress)
                                            notificationController.showTaskProgress(
                                                title = progressContent.title,
                                                text = progressContent.text,
                                                subText = progressContent.subText,
                                                progress = progress.processed,
                                                total = progress.total
                                            )
                                        }
                                    }
                                }.onSuccess { summary ->
                                    uiState.completeMaintenance(summary)
                                    notificationController.showTaskCompleted(
                                        title = "DB maintenance complete",
                                        text = "Deleted ${summary.deleted} • Rehashed ${summary.rehashed} • Missing hash ${summary.missingHashed}"
                                    )
                                    onMaintenanceApplied()
                                    refreshOverview()
                                }.onFailure {
                                    uiState.failMaintenance()
                                    notificationController.showTaskFailed(
                                        title = "DB maintenance failed",
                                        text = "The maintenance run did not finish."
                                    )
                                }
                            }
                        },
                        enabled = canRun,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (uiState.isRunning) "Running..." else "Run maintenance")
                    }

                    Text(
                        text = "Maintenance progress",
                        style = MaterialTheme.typography.titleSmall
                    )
                    uiState.maintenanceStatusMessage?.let { message ->
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (uiState.isRunning) {
                        val total = uiState.progressTotal
                        val processed = uiState.progressProcessed
                        val progress =
                            if (total > 0) processed.toFloat() / total.toFloat() else 0f
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Progress: $processed/$total",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Deleted: ${uiState.progressDeleted} · Rehashed: ${uiState.progressRehashed} · Missing hash: ${uiState.progressMissingHashed}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        uiState.progressCurrentPath?.let { path ->
                            Text(
                                text = "Current: $path",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    } else {
                        Text(
                            text = "Idle",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            OutlinedButton(
                onClick = { clearDialogOpen.value = true },
                enabled = !isBusy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Clear all cached results")
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

    if (clearDialogOpen.value) {
        AlertDialog(
            onDismissRequest = { clearDialogOpen.value = false },
            title = { Text("Clear all cached results?") },
            text = { Text("This removes all cached files and results from the database.") },
            confirmButton = {
                Button(
                    onClick = {
                        clearDialogOpen.value = false
                        appScope.launch {
                            uiState.startClearing()
                            val content =
                                buildDbTaskStartedNotificationContent(DbTaskNotificationKind.ClearAll)
                            notificationController.showTaskStarted(
                                title = content.title,
                                text = content.text,
                                subText = content.subText
                            )
                            runCatching {
                                onClearAll()
                            }.onSuccess {
                                uiState.completeClearing()
                                notificationController.showTaskCompleted(
                                    title = "Cached results cleared",
                                    text = "Cached files and duplicate groups were removed."
                                )
                                refreshOverview()
                            }.onFailure {
                                uiState.failClearing()
                                notificationController.showTaskFailed(
                                    title = "Clear cached results failed",
                                    text = "Cached files and duplicate groups could not be removed."
                                )
                            }
                        }
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
