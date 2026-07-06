package opensource.cached_dupe_scanner.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import opensource.cached_dupe_scanner.core.FileMetadata
import opensource.cached_dupe_scanner.core.SortDirection

/**
 * Use only when the complete member list is already loaded.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun EagerDuplicateGroupDetailContent(
    title: String,
    memberCount: Int,
    totalBytes: Long,
    summaryLines: List<String>,
    members: List<FileMetadata>,
    deletedPaths: Set<String>,
    imageLoader: ImageLoader,
    keepLoadedThumbnailsInMemory: Boolean,
    rememberedPreviewCache: MutableMap<String, ImageBitmap>,
    previewMemoryKey: String,
    previewHeight: Dp,
    showMemberThumbnails: Boolean = false,
    sortKey: ResultGroupMemberSortKey = ResultGroupMemberSortKey.Path,
    sortDirection: SortDirection = SortDirection.Asc,
    sortingEnabled: Boolean = true,
    onApplySort: ((ResultGroupMemberSortKey, SortDirection) -> Unit)? = null,
    onDeleteFile: (suspend (FileMetadata) -> Boolean)?
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val selectedFile = remember { mutableStateOf<FileMetadata?>(null) }
    val selectedPaths = remember(previewMemoryKey) { mutableStateOf<Set<String>>(emptySet()) }
    val confirmDeleteSelected = remember(previewMemoryKey) { mutableStateOf(false) }
    val isDeletingSelected = remember(previewMemoryKey) { mutableStateOf(false) }
    val deleteSelectedMessage = remember(previewMemoryKey) { mutableStateOf<String?>(null) }
    val missingPaths = remember(previewMemoryKey) { mutableStateMapOf<String, Boolean>() }
    val selectionMode = selectedPaths.value.isNotEmpty()
    val hasPreviewMedia = members.any { isMediaFile(it.normalizedPath) }
    val previewCandidates = mediaPreviewCandidates(
        files = members,
        deletedPaths = deletedPaths
    )

    LaunchedEffect(members) {
        val checkedMissingPaths = withContext(Dispatchers.IO) {
            missingFilePaths(members)
        }
        missingPaths.clear()
        checkedMissingPaths.forEach { path -> missingPaths[path] = true }
    }

    Text(title)
    Spacer(modifier = Modifier.height(8.dp))
    if (hasPreviewMedia) {
        GroupPreviewThumbnail(
            candidatePaths = previewCandidates,
            previewMemoryKey = previewMemoryKey,
            rememberedPreviewCache = rememberedPreviewCache,
            imageLoader = imageLoader,
            keepLoadedInMemory = keepLoadedThumbnailsInMemory,
            contentDescription = "Thumbnail",
            modifier = Modifier
                .fillMaxWidth()
                .height(previewHeight)
        )
        Spacer(modifier = Modifier.height(8.dp))
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("$memberCount files · Total ${formatBytes(totalBytes)}")
            summaryLines.forEach { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (sortingEnabled && onApplySort != null) {
            Spacer(modifier = Modifier.width(8.dp))
            GroupMemberSortButton(
                sortKey = sortKey,
                sortDirection = sortDirection,
                onApplySort = onApplySort
            )
        }
    }
    Spacer(modifier = Modifier.height(8.dp))

    val displayedMembers = if (sortingEnabled) {
        sortGroupMembers(
            members = members,
            sortKey = sortKey,
            direction = sortDirection
        )
    } else {
        members
    }
    LaunchedEffect(displayedMembers, deletedPaths) {
        selectedPaths.value = selectedPaths.value.filterTo(linkedSetOf()) { path ->
            displayedMembers.any { file -> file.normalizedPath == path } &&
                !deletedPaths.contains(path)
        }
    }
    LaunchedEffect(selectionMode) {
        if (selectionMode) {
            selectedFile.value = null
        }
    }
    if (selectionMode) {
        val selectablePaths = displayedMembers
            .map { file -> file.normalizedPath }
            .filterNot { path -> deletedPaths.contains(path) }
        val allDisplayedSelected = selectablePaths.isNotEmpty() &&
            selectedPaths.value.containsAll(selectablePaths)
        Text(
            text = "${selectedPaths.value.size} selected",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = {
                    selectedPaths.value = if (allDisplayedSelected) {
                        emptySet()
                    } else {
                        selectablePaths.toCollection(linkedSetOf())
                    }
                    deleteSelectedMessage.value = null
                },
                enabled = !isDeletingSelected.value
            ) {
                Text(if (allDisplayedSelected) "Deselect all" else "Select all")
            }
            OutlinedButton(
                onClick = { confirmDeleteSelected.value = true },
                enabled = onDeleteFile != null && !isDeletingSelected.value
            ) {
                Text(if (isDeletingSelected.value) "Deleting..." else "Delete selected")
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
    deleteSelectedMessage.value?.let { message ->
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
    }
    displayedMembers.forEach { file ->
        val date = formatDate(file.lastModifiedMillis)
        val isDeleted = deletedPaths.contains(file.normalizedPath)
        val isMissing = isMissingDetailFile(
            path = file.normalizedPath,
            deletedPaths = deletedPaths,
            missingPaths = missingPaths.keys
        )
        val isSelected = selectedPaths.value.contains(file.normalizedPath)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = {
                        if (selectionMode) {
                            if (!isDeleted) {
                                selectedPaths.value = togglePathSelection(
                                    selectedPaths = selectedPaths.value,
                                    path = file.normalizedPath
                                )
                                deleteSelectedMessage.value = null
                            }
                        } else {
                            selectedFile.value = file
                        }
                    },
                    onLongClick = {
                        if (!isDeleted) {
                            selectedPaths.value = togglePathSelection(
                                selectedPaths = selectedPaths.value,
                                path = file.normalizedPath
                            )
                            deleteSelectedMessage.value = null
                        }
                    }
                ),
            colors = when {
                isDeleted -> CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
                isMissing -> CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
                else -> CardDefaults.cardColors()
            }
        ) {
            Row(
                modifier = Modifier
                    .padding(10.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (selectionMode) {
                    Checkbox(
                        checked = isSelected,
                        enabled = !isDeleted,
                        onCheckedChange = {
                            if (!isDeleted) {
                                selectedPaths.value = togglePathSelection(
                                    selectedPaths = selectedPaths.value,
                                    path = file.normalizedPath
                                )
                                deleteSelectedMessage.value = null
                            }
                        }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                if (showMemberThumbnails && !isMissing && isMediaFile(file.normalizedPath)) {
                    GroupPreviewThumbnail(
                        candidatePaths = if (isDeleted) emptyList() else listOf(file.normalizedPath),
                        previewMemoryKey = memberPreviewMemoryKey(
                            previewMemoryKey = previewMemoryKey,
                            file = file
                        ),
                        rememberedPreviewCache = rememberedPreviewCache,
                        imageLoader = imageLoader,
                        keepLoadedInMemory = keepLoadedThumbnailsInMemory,
                        contentDescription = "Member thumbnail",
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = file.normalizedPath,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = when {
                            isDeleted -> MaterialTheme.colorScheme.onSecondaryContainer
                            isMissing -> MaterialTheme.colorScheme.onErrorContainer
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = buildString {
                            append("${formatBytesWithExact(file.sizeBytes)} · $date")
                            if (isMissing) append(" · Missing")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = when {
                            isDeleted -> MaterialTheme.colorScheme.onSecondaryContainer
                            isMissing -> MaterialTheme.colorScheme.onErrorContainer
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }

    if (!selectionMode) {
        selectedFile.value?.let { file ->
            FileDetailsDialogWithDeleteConfirm(
                file = file,
                showName = false,
                onOpen = {
                    openFile(context, file.normalizedPath)
                    selectedFile.value = null
                },
                onDelete = {
                    val handler = onDeleteFile ?: return@FileDetailsDialogWithDeleteConfirm false
                    handler(file)
                },
                onDeleteResult = { deleted ->
                    if (deleted) {
                        selectedFile.value = null
                    }
                },
                onDismiss = { selectedFile.value = null }
            )
        }
    }

    if (confirmDeleteSelected.value) {
        val targets = selectedFilesForDelete(
            members = displayedMembers,
            selectedPaths = selectedPaths.value,
            deletedPaths = deletedPaths
        )
        AlertDialog(
            onDismissRequest = {
                if (!isDeletingSelected.value) {
                    confirmDeleteSelected.value = false
                }
            },
            title = { Text("Delete selected files?") },
            text = {
                if (targets.isEmpty()) {
                    Text("No deletable files are selected.")
                } else {
                    Text("${targets.size} selected files will be deleted (moved to app trash).")
                }
            },
            confirmButton = {
                OutlinedButton(
                    onClick = {
                        val handler = onDeleteFile ?: return@OutlinedButton
                        val selectedSnapshot = targets
                        isDeletingSelected.value = true
                        deleteSelectedMessage.value = null
                        scope.launch {
                            var successCount = 0
                            val failedPaths = linkedSetOf<String>()
                            selectedSnapshot.forEach { file ->
                                val deleted = runCatching { handler(file) }.getOrDefault(false)
                                if (deleted) {
                                    successCount += 1
                                } else {
                                    failedPaths.add(file.normalizedPath)
                                }
                            }
                            selectedPaths.value = failedPaths
                            deleteSelectedMessage.value = when {
                                successCount == 0 && failedPaths.isEmpty() -> "No files deleted."
                                failedPaths.isEmpty() -> "$successCount files deleted."
                                successCount == 0 -> "Delete failed for ${failedPaths.size} files."
                                else -> "$successCount deleted, ${failedPaths.size} failed."
                            }
                            isDeletingSelected.value = false
                            confirmDeleteSelected.value = false
                        }
                    },
                    enabled = onDeleteFile != null && targets.isNotEmpty() && !isDeletingSelected.value
                ) {
                    Text(if (isDeletingSelected.value) "Deleting..." else "Delete")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { confirmDeleteSelected.value = false },
                    enabled = !isDeletingSelected.value
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

private fun memberPreviewMemoryKey(
    previewMemoryKey: String,
    file: FileMetadata
): String {
    return "$previewMemoryKey:member:${file.normalizedPath}:${file.sizeBytes}:${file.lastModifiedMillis}"
}
