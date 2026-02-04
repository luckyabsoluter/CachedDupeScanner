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
import androidx.compose.material3.CardDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.snapshotFlow
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import opensource.cached_dupe_scanner.cache.TrashEntryEntity
import opensource.cached_dupe_scanner.storage.TrashController
import opensource.cached_dupe_scanner.storage.TrashRepository
import opensource.cached_dupe_scanner.ui.components.AppTopBar
import opensource.cached_dupe_scanner.ui.components.ScrollbarDefaults
import opensource.cached_dupe_scanner.ui.components.Spacing
import opensource.cached_dupe_scanner.ui.components.VerticalLazyScrollbar
import java.io.File

@Composable
fun TrashScreen(
    trashRepo: TrashRepository,
    trashController: TrashController,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val entries = remember { mutableStateOf<List<TrashEntryEntity>>(emptyList()) }
    val totalCount = remember { mutableStateOf(0) }
    val isLoading = remember { mutableStateOf(false) }
    val cursor = remember { mutableStateOf<Pair<Long, String>?>(null) }
    val menuOpen = remember { mutableStateOf(false) }
    val confirmEmpty = remember { mutableStateOf(false) }
    val selectedEntry = remember { mutableStateOf<TrashEntryEntity?>(null) }
    val confirmDeleteEntry = remember { mutableStateOf<TrashEntryEntity?>(null) }
    val restoreError = remember { mutableStateOf<String?>(null) }
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

    // Infinite scroll auto paging (legacy behavior).
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
            contentPadding = PaddingValues(end = ScrollbarDefaults.ThumbWidth + 8.dp)
        ) {
            item {
                AppTopBar(
                    title = "Trash",
                    onBack = onBack,
                    actions = {
                        IconButton(onClick = { menuOpen.value = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "Menu")
                        }
                        androidx.compose.material3.DropdownMenu(
                            expanded = menuOpen.value,
                            onDismissRequest = { menuOpen.value = false }
                        ) {
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text("Empty trash") },
                                onClick = {
                                    menuOpen.value = false
                                    confirmEmpty.value = true
                                }
                            )
                        }
                    }
                )
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }

            if (entries.value.isEmpty()) {
                item { Text("Trash is empty.") }
            } else {
                items(entries.value, key = { it.id }) { entry ->
                    TrashEntryCard(
                        entry = entry,
                        imageLoader = imageLoader,
                        onClick = { selectedEntry.value = entry }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
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
                .padding(end = 4.dp)
        )
    }

    if (confirmEmpty.value) {
        AlertDialog(
            onDismissRequest = { confirmEmpty.value = false },
            title = { Text("Empty trash?") },
            text = { Text("This will permanently delete all items in trash.") },
            confirmButton = {
                OutlinedButton(onClick = {
                    confirmEmpty.value = false
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            trashController.emptyTrash()
                        }
                        resetAndLoad()
                    }
                }) {
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
                OutlinedButton(onClick = {
                    confirmDeleteEntry.value = null
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            trashController.deletePermanently(entry)
                        }
                        resetAndLoad()
                    }
                }) {
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
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
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
                Spacer(modifier = Modifier.width(12.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = fileName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = formatPath(entry.originalPath, showFullPath = true),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
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
                Spacer(modifier = Modifier.height(6.dp))
                Text("Path: ${entry.originalPath}")
                Spacer(modifier = Modifier.height(6.dp))
                Text("Trashed: ${entry.trashedPath}")
                Spacer(modifier = Modifier.height(6.dp))
                Text("Deleted: ${formatDate(entry.deletedAtMillis)}")
                Text("Size: ${formatBytesWithExact(entry.sizeBytes)}")
                Text("Modified: ${formatDate(entry.lastModifiedMillis)}")
            }
        },
        confirmButton = {
            Row {
                OutlinedButton(onClick = onOpen) { Text("Open") }
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedButton(onClick = onRestore) { Text("Restore") }
            }
        },
        dismissButton = {
            Row {
                OutlinedButton(onClick = onDeleteForeverRequest) { Text("Delete") }
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedButton(onClick = onDismiss) { Text("Close") }
            }
        }
    )
}
