package opensource.cached_dupe_scanner.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import opensource.cached_dupe_scanner.cache.TrashEntryEntity
import opensource.cached_dupe_scanner.storage.TrashController
import opensource.cached_dupe_scanner.storage.TrashRepository
import opensource.cached_dupe_scanner.ui.components.AppTopBar
import opensource.cached_dupe_scanner.ui.components.ScrollbarDefaults
import opensource.cached_dupe_scanner.ui.components.Spacing
import opensource.cached_dupe_scanner.ui.components.VerticalLazyScrollbar

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
    val menuOpen = remember { mutableStateOf(false) }
    val confirmEmpty = remember { mutableStateOf(false) }
    val confirmDeleteEntry = remember { mutableStateOf<TrashEntryEntity?>(null) }

    fun refresh() {
        scope.launch {
            val loaded = withContext(Dispatchers.IO) { trashRepo.listAll() }
            entries.value = loaded
        }
    }

    LaunchedEffect(Unit) {
        refresh()
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
                        onRestore = {
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    trashController.restoreFromTrash(entry)
                                }
                                refresh()
                            }
                        },
                        onDeleteForever = {
                            confirmDeleteEntry.value = entry
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
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
                        refresh()
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
                        refresh()
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
    onRestore: () -> Unit,
    onDeleteForever: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = formatPath(entry.originalPath, showFullPath = true))
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = "Deleted: ${formatDate(entry.deletedAtMillis)}")
            Spacer(modifier = Modifier.height(8.dp))
            androidx.compose.foundation.layout.Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onRestore) { Text("Restore") }
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedButton(onClick = onDeleteForever) { Text("Delete") }
            }
        }
    }
}
