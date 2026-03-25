package opensource.cached_dupe_scanner.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import opensource.cached_dupe_scanner.cache.TrashEntryEntity
import opensource.cached_dupe_scanner.notifications.TaskNotificationController
import opensource.cached_dupe_scanner.storage.TrashController
import opensource.cached_dupe_scanner.storage.TrashProgress
import opensource.cached_dupe_scanner.storage.TrashRepository
import opensource.cached_dupe_scanner.storage.TrashRunSummary
import opensource.cached_dupe_scanner.tasks.TaskArea
import opensource.cached_dupe_scanner.tasks.TaskCoordinator
import opensource.cached_dupe_scanner.tasks.TaskKind
import opensource.cached_dupe_scanner.tasks.trashTaskCompletedDetail
import opensource.cached_dupe_scanner.tasks.trashTaskDetail
import opensource.cached_dupe_scanner.tasks.trashTaskTitle
import opensource.cached_dupe_scanner.tasks.withLinearProgress
import opensource.cached_dupe_scanner.ui.components.AppTopBar
import opensource.cached_dupe_scanner.ui.components.ScrollbarDefaults
import opensource.cached_dupe_scanner.ui.components.Spacing
import opensource.cached_dupe_scanner.ui.components.VerticalLazyScrollbar

@Composable
fun TrashScreen(
    trashRepo: TrashRepository,
    trashController: TrashController,
    taskCoordinator: TaskCoordinator,
    notificationController: TaskNotificationController,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val entries = remember { mutableStateOf<List<TrashEntryEntity>>(emptyList()) }
    val totalCount = remember { mutableStateOf(0) }
    val isLoading = remember { mutableStateOf(false) }
    val cursor = remember { mutableStateOf<Pair<Long, String>?>(null) }
    val topVisibleIndex = remember { mutableStateOf(0) }
    val menuOpen = remember { mutableStateOf(false) }
    val confirmEmpty = remember { mutableStateOf(false) }
    val selectedEntry = remember { mutableStateOf<TrashEntryEntity?>(null) }
    val confirmDeleteEntry = remember { mutableStateOf<TrashEntryEntity?>(null) }
    val restoreError = remember { mutableStateOf<String?>(null) }
    val currentJob = remember { mutableStateOf<Job?>(null) }
    val activeTask = taskCoordinator.activeTask(TaskArea.Trash)
    val isBusy = activeTask != null || currentJob.value != null
    val context = LocalContext.current
    val imageLoader = remember {
        ImageLoader.Builder(context)
            .components { add(VideoFrameDecoder.Factory()) }
            .build()
    }

    val pageSize = 200
    val buffer = 50

    fun resetAndLoad() {
        scope.launch {
            isLoading.value = true
            val count = withContext(Dispatchers.IO) { trashRepo.countAll() }
            val first = withContext(Dispatchers.IO) { trashRepo.getFirstPage(pageSize) }
            totalCount.value = count
            entries.value = first
            cursor.value = first.lastOrNull()?.let { it.deletedAtMillis to it.id }
            isLoading.value = false
        }
    }

    fun loadMore() {
        if (isLoading.value) return
        if (entries.value.size >= totalCount.value) return
        val before = cursor.value ?: return
        scope.launch {
            isLoading.value = true
            val next = withContext(Dispatchers.IO) {
                trashRepo.getPageBefore(beforeMillis = before.first, beforeId = before.second, limit = pageSize)
            }
            if (next.isNotEmpty()) {
                entries.value = entries.value + next
                cursor.value = next.last().deletedAtMillis to next.last().id
            }
            isLoading.value = false
        }
    }

    LaunchedEffect(Unit) {
        resetAndLoad()
    }

    LaunchedEffect(Unit) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { topVisibleIndex.value = it }
    }

    val overlayText = run {
        val total = totalCount.value
        if (total <= 0) {
            null
        } else {
            val loaded = entries.value.size.coerceAtMost(total).coerceAtLeast(1)
            val current = (topVisibleIndex.value + 1).coerceAtLeast(1)
            val currentPercent = ((current.toDouble() / loaded.toDouble()) * 100).toInt()
            val loadedPercent = ((loaded.toDouble() / total.toDouble()) * 100).toInt()
            "$current/$loaded/$total (${currentPercent}%/${loadedPercent}%)"
        }
    }

    LaunchedEffect(totalCount.value, entries.value.size) {
        if (totalCount.value <= 0) return@LaunchedEffect
        snapshotFlow {
            val layoutInfo = listState.layoutInfo
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val totalItems = layoutInfo.totalItemsCount
            val hasMore = entries.value.size < totalCount.value
            val closeToEnd = lastVisible >= (totalItems - buffer)
            closeToEnd && hasMore && !isLoading.value
        }
            .distinctUntilChanged()
            .filter { it }
            .collect { loadMore() }
    }

    Box(modifier = modifier) {
        LazyColumn(
            state = listState,
            modifier = Modifier.padding(Spacing.screenPadding),
            contentPadding = PaddingValues(end = ScrollbarDefaults.ThumbWidth + Spacing.itemGap)
        ) {
            item {
                AppTopBar(
                    title = "Trash",
                    onBack = onBack,
                    actions = {
                        IconButton(onClick = { menuOpen.value = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "Menu")
                        }
                        DropdownMenu(
                            expanded = menuOpen.value,
                            onDismissRequest = { menuOpen.value = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Empty trash") },
                                enabled = !isBusy,
                                onClick = {
                                    menuOpen.value = false
                                    confirmEmpty.value = true
                                }
                            )
                        }
                    }
                )
            }

            item { Spacer(modifier = Modifier.height(Spacing.itemGap)) }

            activeTask?.let { task ->
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(Spacing.cardPadding),
                            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Spacing.itemGap)
                        ) {
                            Text(task.title, style = MaterialTheme.typography.titleSmall)
                            Text(task.detail)
                            task.currentPath?.let { current ->
                                Text(
                                    text = current,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            OutlinedButton(
                                onClick = { taskCoordinator.requestCancel(TaskArea.Trash) },
                                enabled = task.isCancellable,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Cancel")
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(Spacing.itemGap))
                }
            }

            if (entries.value.isEmpty()) {
                item { Text("Trash is empty.") }
            } else {
                items(entries.value, key = { it.id }) { entry ->
                    TrashEntryCard(
                        entry = entry,
                        imageLoader = imageLoader,
                        onClick = { selectedEntry.value = entry }
                    )
                    Spacer(modifier = Modifier.height(Spacing.itemGap))
                }

                if (isLoading.value && entries.value.isNotEmpty()) {
                    item {
                        Text(
                            text = "Loading…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        VerticalLazyScrollbar(
            listState = listState,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .padding(end = Spacing.xs)
        )

        overlayText?.let { indicator ->
            Text(
                text = indicator,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(
                        end = ScrollbarDefaults.ThumbWidth + Spacing.lg,
                        top = Spacing.lg
                    )
            )
        }
    }

    if (confirmEmpty.value) {
        AlertDialog(
            onDismissRequest = { confirmEmpty.value = false },
            title = { Text("Empty trash?") },
            text = { Text("This will permanently delete all items in trash.") },
            confirmButton = {
                OutlinedButton(
                    enabled = !isBusy,
                    onClick = {
                        confirmEmpty.value = false
                        startEmptyTrashTask(
                            trashController = trashController,
                            scope = scope,
                            taskCoordinator = taskCoordinator,
                            notificationController = notificationController,
                            onJobChanged = { job -> currentJob.value = job },
                            resetAndLoad = ::resetAndLoad
                        )
                    }
                ) {
                    Text("Delete all")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { confirmEmpty.value = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    selectedEntry.value?.let { entry ->
        TrashEntryDetailsDialog(
            entry = entry,
            actionsEnabled = !isBusy,
            onOpen = {
                openFile(context, entry.trashedPath)
                selectedEntry.value = null
            },
            onRestore = {
                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        trashController.restoreFromTrash(entry)
                    }
                    when (result) {
                        TrashController.RestoreResult.Success -> {
                            selectedEntry.value = null
                            resetAndLoad()
                        }

                        TrashController.RestoreResult.ConflictTargetExists -> {
                            restoreError.value = "Restore skipped: a file already exists at the original path."
                        }

                        TrashController.RestoreResult.TrashedFileMissing -> {
                            selectedEntry.value = null
                            resetAndLoad()
                        }

                        TrashController.RestoreResult.MoveFailed -> {
                            restoreError.value = "Restore failed: unable to move file out of trash."
                        }

                        TrashController.RestoreResult.DbUpdateFailed -> {
                            restoreError.value = "Restore failed: database update failed. File was kept in trash."
                        }
                    }
                }
            },
            onDeleteForeverRequest = {
                selectedEntry.value = null
                confirmDeleteEntry.value = entry
            },
            onDismiss = { selectedEntry.value = null }
        )
    }

    restoreError.value?.let { message ->
        AlertDialog(
            onDismissRequest = { restoreError.value = null },
            title = { Text("Restore") },
            text = { Text(message) },
            confirmButton = {
                OutlinedButton(onClick = { restoreError.value = null }) {
                    Text("OK")
                }
            }
        )
    }

    confirmDeleteEntry.value?.let { entry ->
        AlertDialog(
            onDismissRequest = { confirmDeleteEntry.value = null },
            title = { Text("Delete permanently?") },
            text = { Text(entry.originalPath) },
            confirmButton = {
                OutlinedButton(
                    enabled = !isBusy,
                    onClick = {
                        confirmDeleteEntry.value = null
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                trashController.deletePermanently(entry)
                            }
                            resetAndLoad()
                        }
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { confirmDeleteEntry.value = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

internal fun startEmptyTrashTask(
    trashController: TrashController,
    scope: kotlinx.coroutines.CoroutineScope,
    taskCoordinator: TaskCoordinator,
    notificationController: TaskNotificationController,
    onJobChanged: (Job?) -> Unit,
    resetAndLoad: () -> Unit
) {
    startEmptyTrashTask(
        scope = scope,
        taskCoordinator = taskCoordinator,
        notificationController = notificationController,
        onJobChanged = onJobChanged,
        resetAndLoad = resetAndLoad
    ) { shouldContinue, onProgress ->
        trashController.emptyTrash(shouldContinue = shouldContinue, onProgress = onProgress)
    }
}

internal fun startEmptyTrashTask(
    scope: kotlinx.coroutines.CoroutineScope,
    taskCoordinator: TaskCoordinator,
    notificationController: TaskNotificationController,
    onJobChanged: (Job?) -> Unit,
    resetAndLoad: () -> Unit,
    runEmptyTrash: ((() -> Boolean), (TrashProgress) -> Unit) -> TrashRunSummary
) {
    val cancelRequested = AtomicBoolean(false)
    val started = taskCoordinator.tryStart(
        area = TaskArea.Trash,
        kind = TaskKind.EmptyTrash,
        title = trashTaskTitle(),
        detail = "Preparing trash cleanup.",
        processed = 0,
        total = null,
        indeterminate = true,
        isCancellable = true,
        onCancel = {
            cancelRequested.set(true)
            requestImmediateTrashCancel(
                taskCoordinator = taskCoordinator,
                notificationController = notificationController
            )
        }
    ) ?: return
    notificationController.showActive(started)
    val job = scope.launch {
        try {
            val summary = withContext(Dispatchers.IO) {
                runEmptyTrash(
                    { !cancelRequested.get() },
                    { progress ->
                        taskCoordinator.update(TaskArea.Trash) { task ->
                            task.withLinearProgress(
                                title = trashTaskTitle(),
                                detail = trashTaskDetail(progress),
                                currentPath = progress.currentPath,
                                processed = progress.processed,
                                total = progress.total
                            )
                        }?.let(notificationController::showActive)
                    }
                )
            }
            if (summary.cancelled) {
                taskCoordinator.cancel(
                    area = TaskArea.Trash,
                    title = "Trash empty cancelled",
                    detail = "Cancelled after ${summary.processed}/${summary.total} items.",
                    currentPath = summary.currentPath,
                    processed = summary.processed,
                    total = summary.total,
                    indeterminate = summary.total <= 0
                )?.let(notificationController::showTerminal)
            } else {
                taskCoordinator.complete(
                    area = TaskArea.Trash,
                    title = "Trash empty complete",
                    detail = trashTaskCompletedDetail(summary),
                    currentPath = summary.currentPath,
                    processed = summary.processed,
                    total = summary.total,
                    indeterminate = summary.total <= 0
                )?.let(notificationController::showTerminal)
            }
            resetAndLoad()
        } finally {
            onJobChanged(null)
        }
    }
    onJobChanged(job)
}

private fun requestImmediateTrashCancel(
    taskCoordinator: TaskCoordinator,
    notificationController: TaskNotificationController
) {
    val snapshot = taskCoordinator.activeTask(TaskArea.Trash)
    taskCoordinator.cancel(
        area = TaskArea.Trash,
        title = "Trash empty cancelled",
        detail = "Cancelling trash cleanup.",
        currentPath = snapshot?.currentPath,
        processed = snapshot?.processed,
        total = snapshot?.total,
        indeterminate = snapshot?.indeterminate ?: true
    )?.let(notificationController::showTerminal)
}

@Composable
private fun TrashEntryCard(
    entry: TrashEntryEntity,
    imageLoader: ImageLoader,
    onClick: () -> Unit
) {
    val showThumbnail = isMediaFile(entry.originalPath)
    val fileName = File(entry.originalPath).name.ifBlank { "(unknown)" }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors()
    ) {
        Row(
            modifier = Modifier.padding(Spacing.cardPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showThumbnail) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(File(entry.trashedPath))
                        .build(),
                    imageLoader = imageLoader,
                    contentDescription = "Thumbnail",
                    modifier = Modifier
                        .width(56.dp)
                        .height(56.dp)
                )
                Spacer(modifier = Modifier.width(Spacing.lg))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = fileName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(Spacing.xs))
                Text(
                    text = formatPath(entry.originalPath, showFullPath = true),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(Spacing.xs))
                Text(
                    text = "${formatBytesWithExact(entry.sizeBytes)} · Deleted ${formatDate(entry.deletedAtMillis)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun TrashEntryDetailsDialog(
    entry: TrashEntryEntity,
    actionsEnabled: Boolean,
    onOpen: () -> Unit,
    onRestore: () -> Unit,
    onDeleteForeverRequest: () -> Unit,
    onDismiss: () -> Unit
) {
    val fileName = File(entry.originalPath).name.ifBlank { "(unknown)" }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Trash item") },
        text = {
            Column {
                Text(
                    text = fileName,
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(modifier = Modifier.height(Spacing.compactGap))
                Text("Path: ${entry.originalPath}")
                Spacer(modifier = Modifier.height(Spacing.compactGap))
                Text("Trashed: ${entry.trashedPath}")
                Spacer(modifier = Modifier.height(Spacing.compactGap))
                Text("Deleted: ${formatDate(entry.deletedAtMillis)}")
                Text("Size: ${formatBytesWithExact(entry.sizeBytes)}")
                Text("Modified: ${formatDate(entry.lastModifiedMillis)}")
            }
        },
        confirmButton = {
            Row {
                OutlinedButton(onClick = onOpen) { Text("Open") }
                Spacer(modifier = Modifier.width(Spacing.inlineGap))
                OutlinedButton(
                    enabled = actionsEnabled,
                    onClick = onRestore
                ) {
                    Text("Restore")
                }
            }
        },
        dismissButton = {
            Row {
                OutlinedButton(
                    enabled = actionsEnabled,
                    onClick = onDeleteForeverRequest
                ) {
                    Text("Delete")
                }
                Spacer(modifier = Modifier.width(Spacing.inlineGap))
                OutlinedButton(onClick = onDismiss) { Text("Close") }
            }
        }
    )
}
