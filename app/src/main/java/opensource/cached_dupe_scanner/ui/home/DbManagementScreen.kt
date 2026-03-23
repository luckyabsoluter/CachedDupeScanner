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
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import opensource.cached_dupe_scanner.notifications.TaskNotificationController
import opensource.cached_dupe_scanner.storage.ClearCacheProgress
import opensource.cached_dupe_scanner.storage.ClearCacheSummary
import opensource.cached_dupe_scanner.storage.RebuildGroupsProgress
import opensource.cached_dupe_scanner.storage.RebuildGroupsSummary
import opensource.cached_dupe_scanner.storage.ResultsDbRepository
import opensource.cached_dupe_scanner.storage.ScanHistoryRepository
import opensource.cached_dupe_scanner.tasks.TaskArea
import opensource.cached_dupe_scanner.tasks.TaskCoordinator
import opensource.cached_dupe_scanner.tasks.TaskKind
import opensource.cached_dupe_scanner.tasks.clearCacheCompletedDetail
import opensource.cached_dupe_scanner.tasks.clearCacheTaskDetail
import opensource.cached_dupe_scanner.tasks.clearCacheTaskTitle
import opensource.cached_dupe_scanner.tasks.dbMaintenanceCompletedDetail
import opensource.cached_dupe_scanner.tasks.dbMaintenanceTaskDetail
import opensource.cached_dupe_scanner.tasks.dbMaintenanceTaskTitle
import opensource.cached_dupe_scanner.tasks.rebuildGroupsCompletedDetail
import opensource.cached_dupe_scanner.tasks.rebuildGroupsTaskDetail
import opensource.cached_dupe_scanner.tasks.rebuildGroupsTaskTitle
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
    taskCoordinator: TaskCoordinator,
    notificationController: TaskNotificationController,
    onMaintenanceApplied: () -> Unit,
    onCacheCleared: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val deleteMissing = remember { mutableStateOf(true) }
    val rehashStale = remember { mutableStateOf(false) }
    val rehashMissing = remember { mutableStateOf(false) }
    val clearDialogOpen = remember { mutableStateOf(false) }
    val activeTask = taskCoordinator.activeTask(TaskArea.Db)

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

    val isBusy = activeTask != null || uiState.isRunning || uiState.isRebuilding || uiState.isClearing
    val canRun = (deleteMissing.value || rehashStale.value || rehashMissing.value) && !isBusy

    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(Spacing.screenPadding)
                .padding(end = ScrollbarDefaults.ThumbWidth + Spacing.itemGap)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(Spacing.sectionGap)
        ) {
            AppTopBar(title = "DB management", onBack = onBack)

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(Spacing.cardPadding),
                    verticalArrangement = Arrangement.spacedBy(Spacing.compactGap)
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
                    modifier = Modifier.padding(Spacing.cardPadding),
                    verticalArrangement = Arrangement.spacedBy(Spacing.itemGap)
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
                            startRebuildGroupsTask(
                                resultsRepo = resultsRepo,
                                uiState = uiState,
                                appScope = appScope,
                                taskCoordinator = taskCoordinator,
                                notificationController = notificationController,
                                onMaintenanceApplied = onMaintenanceApplied,
                                refreshOverview = refreshOverview
                            )
                        },
                        enabled = !isBusy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (uiState.isRebuilding) "Rebuilding groups..." else "Rebuild duplicate groups")
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
                    modifier = Modifier.padding(Spacing.cardPadding),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sectionGap)
                ) {
                    Text(
                        text = "File maintenance policies",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.itemGap)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = deleteMissing.value,
                                onCheckedChange = { deleteMissing.value = it }
                            )
                            Spacer(modifier = Modifier.width(Spacing.inlineGap))
                            Text("Delete DB entries missing on storage")
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = rehashStale.value,
                                onCheckedChange = { rehashStale.value = it }
                            )
                            Spacer(modifier = Modifier.width(Spacing.inlineGap))
                            Text("Rehash entries with stale size/date")
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = rehashMissing.value,
                                onCheckedChange = { rehashMissing.value = it }
                            )
                            Spacer(modifier = Modifier.width(Spacing.inlineGap))
                            Text("Compute hash for missing entries")
                        }
                    }

                    Button(
                        onClick = {
                            startDbMaintenanceTask(
                                historyRepo = historyRepo,
                                uiState = uiState,
                                appScope = appScope,
                                taskCoordinator = taskCoordinator,
                                notificationController = notificationController,
                                deleteMissing = deleteMissing.value,
                                rehashStale = rehashStale.value,
                                rehashMissing = rehashMissing.value,
                                onMaintenanceApplied = onMaintenanceApplied,
                                refreshOverview = refreshOverview
                            )
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
                    activeTask?.let { task ->
                        if (task.total != null && task.total > 0 && !task.indeterminate) {
                            LinearProgressIndicator(
                                progress = {
                                    ((task.processed ?: 0).toFloat() / task.total.toFloat())
                                        .coerceIn(0f, 1f)
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                        Spacer(modifier = Modifier.height(Spacing.compactGap))
                        Text(
                            text = task.detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        task.currentPath?.let { path ->
                            Text(
                                text = "Current: $path",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        OutlinedButton(
                            onClick = { taskCoordinator.requestCancel(TaskArea.Db) },
                            enabled = task.isCancellable,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Cancel running task")
                        }
                    } ?: Text(
                        text = "Idle",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
                .padding(end = Spacing.xs)
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
                        startClearCacheTask(
                            historyRepo = historyRepo,
                            uiState = uiState,
                            appScope = appScope,
                            taskCoordinator = taskCoordinator,
                            notificationController = notificationController,
                            onCacheCleared = onCacheCleared,
                            refreshOverview = refreshOverview
                        )
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

internal fun startRebuildGroupsTask(
    resultsRepo: ResultsDbRepository,
    uiState: DbManagementUiState,
    appScope: CoroutineScope,
    taskCoordinator: TaskCoordinator,
    notificationController: TaskNotificationController,
    onMaintenanceApplied: () -> Unit,
    refreshOverview: () -> Unit
) {
    startRebuildGroupsTask(
        uiState = uiState,
        appScope = appScope,
        taskCoordinator = taskCoordinator,
        notificationController = notificationController,
        onMaintenanceApplied = onMaintenanceApplied,
        refreshOverview = refreshOverview
    ) { shouldContinue, onProgress ->
        resultsRepo.rebuildGroups(shouldContinue = shouldContinue, onProgress = onProgress)
    }
}

internal fun startRebuildGroupsTask(
    uiState: DbManagementUiState,
    appScope: CoroutineScope,
    taskCoordinator: TaskCoordinator,
    notificationController: TaskNotificationController,
    onMaintenanceApplied: () -> Unit,
    refreshOverview: () -> Unit,
    runRebuildGroups: ((() -> Boolean), (RebuildGroupsProgress) -> Unit) -> RebuildGroupsSummary
) {
    val cancelRequested = AtomicBoolean(false)
    val started = taskCoordinator.tryStart(
        area = TaskArea.Db,
        kind = TaskKind.RebuildGroups,
        title = rebuildGroupsTaskTitle(),
        detail = "Preparing duplicate group rebuild.",
        processed = 0,
        total = null,
        indeterminate = true,
        isCancellable = true,
        onCancel = {
            cancelRequested.set(true)
            requestImmediateDbCancel(
                taskCoordinator = taskCoordinator,
                notificationController = notificationController,
                title = "Duplicate groups rebuild cancelled",
                detail = "Cancelling duplicate group rebuild."
            )
        }
    ) ?: return
    notificationController.showActive(started)
    uiState.startRebuild()
    appScope.launch {
        runCatching {
            withContext(Dispatchers.IO) {
                runRebuildGroups(
                    { !cancelRequested.get() },
                    { progress ->
                        taskCoordinator.update(TaskArea.Db) { task ->
                            task.copy(
                                title = rebuildGroupsTaskTitle(),
                                detail = rebuildGroupsTaskDetail(progress),
                                processed = progress.processed,
                                total = progress.total,
                                indeterminate = progress.total <= 0
                            )
                        }?.let(notificationController::showActive)
                    }
                )
            }
        }.onSuccess { summary ->
            if (summary.cancelled) {
                uiState.cancelRebuild(summary)
                taskCoordinator.cancel(
                    area = TaskArea.Db,
                    title = "Duplicate groups rebuild cancelled",
                    detail = "Cancelled after ${summary.processed}/${summary.total} duplicate groups.",
                    processed = summary.processed,
                    total = summary.total,
                    indeterminate = summary.total <= 0
                )?.let(notificationController::showTerminal)
            } else {
                uiState.completeRebuild()
                taskCoordinator.complete(
                    area = TaskArea.Db,
                    title = "Duplicate groups rebuilt",
                    detail = rebuildGroupsCompletedDetail(summary),
                    processed = summary.processed,
                    total = summary.total,
                    indeterminate = summary.total <= 0
                )?.let(notificationController::showTerminal)
            }
            onMaintenanceApplied()
            refreshOverview()
        }.onFailure {
            uiState.failRebuild()
            taskCoordinator.fail(
                area = TaskArea.Db,
                title = "Duplicate group rebuild failed",
                detail = "The duplicate group snapshot could not be refreshed."
            )?.let(notificationController::showTerminal)
        }
    }
}

internal fun startClearCacheTask(
    historyRepo: ScanHistoryRepository,
    uiState: DbManagementUiState,
    appScope: CoroutineScope,
    taskCoordinator: TaskCoordinator,
    notificationController: TaskNotificationController,
    onCacheCleared: () -> Unit,
    refreshOverview: () -> Unit
) {
    startClearCacheTask(
        uiState = uiState,
        appScope = appScope,
        taskCoordinator = taskCoordinator,
        notificationController = notificationController,
        onCacheCleared = onCacheCleared,
        refreshOverview = refreshOverview
    ) { shouldContinue, onProgress ->
        historyRepo.clearAll(shouldContinue = shouldContinue, onProgress = onProgress)
    }
}

internal fun startClearCacheTask(
    uiState: DbManagementUiState,
    appScope: CoroutineScope,
    taskCoordinator: TaskCoordinator,
    notificationController: TaskNotificationController,
    onCacheCleared: () -> Unit,
    refreshOverview: () -> Unit,
    runClearAll: ((() -> Boolean), (ClearCacheProgress) -> Unit) -> ClearCacheSummary
) {
    val cancelRequested = AtomicBoolean(false)
    val started = taskCoordinator.tryStart(
        area = TaskArea.Db,
        kind = TaskKind.ClearCache,
        title = clearCacheTaskTitle(),
        detail = "Preparing cache clear.",
        processed = 0,
        total = null,
        indeterminate = true,
        isCancellable = true,
        onCancel = {
            cancelRequested.set(true)
            requestImmediateDbCancel(
                taskCoordinator = taskCoordinator,
                notificationController = notificationController,
                title = "Clear cached results cancelled",
                detail = "Cancelling cache clear."
            )
        }
    ) ?: return
    notificationController.showActive(started)
    uiState.startClearing()
    appScope.launch {
        runCatching {
            withContext(Dispatchers.IO) {
                runClearAll(
                    { !cancelRequested.get() },
                    { progress ->
                        taskCoordinator.update(TaskArea.Db) { task ->
                            task.copy(
                                title = clearCacheTaskTitle(),
                                detail = clearCacheTaskDetail(progress),
                                processed = progress.processed,
                                total = progress.total,
                                indeterminate = progress.total <= 0
                            )
                        }?.let(notificationController::showActive)
                    }
                )
            }
        }.onSuccess { summary ->
            if (summary.cancelled) {
                uiState.cancelClearing(summary)
                taskCoordinator.cancel(
                    area = TaskArea.Db,
                    title = "Clear cached results cancelled",
                    detail = "Cancelled after ${summary.processed}/${summary.total} items.",
                    processed = summary.processed,
                    total = summary.total,
                    indeterminate = summary.total <= 0
                )?.let(notificationController::showTerminal)
            } else {
                uiState.completeClearing()
                taskCoordinator.complete(
                    area = TaskArea.Db,
                    title = "Cached results cleared",
                    detail = clearCacheCompletedDetail(summary),
                    processed = summary.processed,
                    total = summary.total,
                    indeterminate = summary.total <= 0
                )?.let(notificationController::showTerminal)
            }
            onCacheCleared()
            refreshOverview()
        }.onFailure {
            uiState.failClearing()
            taskCoordinator.fail(
                area = TaskArea.Db,
                title = "Clear cached results failed",
                detail = "Cached files and duplicate groups could not be removed."
            )?.let(notificationController::showTerminal)
        }
    }
}

internal fun startDbMaintenanceTask(
    historyRepo: ScanHistoryRepository,
    uiState: DbManagementUiState,
    appScope: CoroutineScope,
    taskCoordinator: TaskCoordinator,
    notificationController: TaskNotificationController,
    deleteMissing: Boolean,
    rehashStale: Boolean,
    rehashMissing: Boolean,
    onMaintenanceApplied: () -> Unit,
    refreshOverview: () -> Unit
) {
    val cancelRequested = AtomicBoolean(false)
    val started = taskCoordinator.tryStart(
        area = TaskArea.Db,
        kind = TaskKind.DbMaintenance,
        title = dbMaintenanceTaskTitle(),
        detail = "Preparing maintenance run.",
        processed = 0,
        total = null,
        indeterminate = true,
        isCancellable = true,
        onCancel = {
            cancelRequested.set(true)
            requestImmediateDbCancel(
                taskCoordinator = taskCoordinator,
                notificationController = notificationController,
                title = "DB maintenance cancelled",
                detail = "Cancelling maintenance run."
            )
        }
    ) ?: return
    notificationController.showActive(started)
    appScope.launch {
        uiState.startMaintenance()
        runCatching {
            withContext(Dispatchers.IO) {
                historyRepo.runMaintenance(
                    deleteMissing = deleteMissing,
                    rehashStale = rehashStale,
                    rehashMissing = rehashMissing,
                    shouldContinue = { !cancelRequested.get() }
                ) { progress ->
                    uiState.applyMaintenanceProgress(progress)
                    taskCoordinator.update(TaskArea.Db) { task ->
                        task.copy(
                            title = dbMaintenanceTaskTitle(),
                            detail = dbMaintenanceTaskDetail(progress),
                            currentPath = progress.currentPath,
                            processed = progress.processed,
                            total = progress.total,
                            indeterminate = progress.total <= 0
                        )
                    }?.let(notificationController::showActive)
                }
            }
        }.onSuccess { summary ->
            if (summary.cancelled) {
                uiState.cancelMaintenance(summary)
                taskCoordinator.cancel(
                    area = TaskArea.Db,
                    title = "DB maintenance cancelled",
                    detail = "Cancelled after ${summary.processed}/${summary.total} items.",
                    currentPath = summary.currentPath,
                    processed = summary.processed,
                    total = summary.total,
                    indeterminate = summary.total <= 0
                )?.let(notificationController::showTerminal)
            } else {
                uiState.completeMaintenance(summary)
                taskCoordinator.complete(
                    area = TaskArea.Db,
                    title = "DB maintenance complete",
                    detail = dbMaintenanceCompletedDetail(summary),
                    processed = summary.processed,
                    total = summary.total,
                    indeterminate = summary.total <= 0
                )?.let(notificationController::showTerminal)
            }
            onMaintenanceApplied()
            refreshOverview()
        }.onFailure {
            uiState.failMaintenance()
            taskCoordinator.fail(
                area = TaskArea.Db,
                title = "DB maintenance failed",
                detail = "The maintenance run did not finish."
            )?.let(notificationController::showTerminal)
        }
    }
}

private fun requestImmediateDbCancel(
    taskCoordinator: TaskCoordinator,
    notificationController: TaskNotificationController,
    title: String,
    detail: String
) {
    val snapshot = taskCoordinator.activeTask(TaskArea.Db)
    taskCoordinator.cancel(
        area = TaskArea.Db,
        title = title,
        detail = detail,
        currentPath = snapshot?.currentPath,
        processed = snapshot?.processed,
        total = snapshot?.total,
        indeterminate = snapshot?.indeterminate ?: true
    )?.let(notificationController::showTerminal)
}
