package opensource.cached_dupe_scanner.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.ImageLoader
import coil.decode.VideoFrameDecoder
import opensource.cached_dupe_scanner.core.DuplicateGroup
import opensource.cached_dupe_scanner.core.FileMetadata
import opensource.cached_dupe_scanner.core.ResultSortKey
import opensource.cached_dupe_scanner.core.SortDirection
import opensource.cached_dupe_scanner.core.ScanResultViewFilter
import opensource.cached_dupe_scanner.core.ScanResult
import opensource.cached_dupe_scanner.storage.AppSettingsStore
import opensource.cached_dupe_scanner.ui.components.AppTopBar
import opensource.cached_dupe_scanner.ui.components.Spacing
import opensource.cached_dupe_scanner.ui.results.ScanUiState
import java.io.File
import java.util.Locale
import androidx.compose.material3.RadioButton
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.collect

@Composable
fun ResultsScreen(
    state: MutableState<ScanUiState>,
    onBackToDashboard: () -> Unit,
    onClearResults: () -> Unit,
    settingsStore: AppSettingsStore,
    displayResult: ScanResult? = null,
    deletedPaths: Set<String> = emptySet(),
    onDeleteFile: ((FileMetadata) -> Unit)? = null,
    onOpenGroup: ((Int) -> Unit)? = null,
    onSortChanged: (() -> Unit)? = null,
    selectedGroupIndex: Int? = null,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val menuExpanded = remember { mutableStateOf(false) }
    val context = LocalContext.current
    val imageLoader = remember {
        ImageLoader.Builder(context)
            .components { add(VideoFrameDecoder.Factory()) }
            .build()
    }
    val settingsSnapshot = remember { settingsStore.load() }
        val showFullPaths = remember { mutableStateOf(settingsSnapshot.showFullPaths) }
    val sortKey = remember {
        val key = runCatching { ResultSortKey.valueOf(settingsSnapshot.resultSortKey) }
            .getOrDefault(ResultSortKey.Count)
        mutableStateOf(key)
    }
    val sortDirection = remember {
        val dir = runCatching { SortDirection.valueOf(settingsSnapshot.resultSortDirection) }
            .getOrDefault(SortDirection.Desc)
        mutableStateOf(dir)
    }
    val sortDialogOpen = remember { mutableStateOf(false) }
    val pendingSortKey = remember { mutableStateOf(ResultSortKey.Count) }
    val pendingSortDirection = remember { mutableStateOf(SortDirection.Desc) }
    val pageSize = 50
    val buffer = 20
    val result = if (state.value is ScanUiState.Success) {
        val settings = settingsStore.load()
        displayResult ?: ScanResultViewFilter.filterForDisplay(
            result = (state.value as ScanUiState.Success).result,
            hideZeroSizeInResults = settings.hideZeroSizeInResults,
            sortKey = sortKey.value,
            sortDirection = sortDirection.value
        )
    } else {
        null
    }
    val totalGroups = result?.duplicateGroups?.size ?: 0
    val visibleCount = remember { mutableStateOf(pageSize) }
    val topVisibleGroupIndex = remember { mutableStateOf(0) }
    LaunchedEffect(totalGroups) {
        if (totalGroups > 0) {
            visibleCount.value = pageSize
        }
    }
    val groupIndexByHash = remember(result?.duplicateGroups) {
        result?.duplicateGroups
            ?.mapIndexed { index, group -> group.hashHex to index }
            ?.toMap()
            ?: emptyMap()
    }
    LaunchedEffect(result?.duplicateGroups?.size, selectedGroupIndex) {
        if (selectedGroupIndex != null) return@LaunchedEffect
        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo
                .firstOrNull { it.key is String }
                ?.key as? String
        }
            .distinctUntilChanged()
            .filter { it != null }
            .collect { key ->
                val index = groupIndexByHash[key] ?: 0
                topVisibleGroupIndex.value = index
            }
    }
    val loadIndicatorText = if (selectedGroupIndex != null || totalGroups == 0) {
        null
    } else {
        val loaded = visibleCount.value.coerceAtMost(totalGroups)
        val current = (topVisibleGroupIndex.value + 1).coerceAtLeast(1)
        val safeLoaded = loaded.coerceAtLeast(1)
        val safeTotal = totalGroups.coerceAtLeast(1)
        val currentPercent = ((current.toDouble() / safeLoaded.toDouble()) * 100).toInt()
        val loadedPercent = ((loaded.toDouble() / safeTotal.toDouble()) * 100).toInt()
        "$current/$loaded/$totalGroups (${currentPercent}%/${loadedPercent}%)"
    }
    LaunchedEffect(totalGroups) {
        if (totalGroups <= 0) return@LaunchedEffect
        snapshotFlow {
            val layoutInfo = listState.layoutInfo
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val totalItems = layoutInfo.totalItemsCount
            val remaining = totalGroups - visibleCount.value
            lastVisible >= (totalItems - buffer) && remaining > 0
        }
            .distinctUntilChanged()
            .filter { it }
            .collect {
                visibleCount.value = (visibleCount.value + pageSize)
                    .coerceAtMost(totalGroups)
            }
    }
    Box(modifier = modifier) {
        if (selectedGroupIndex != null && result != null) {
            val group = result.duplicateGroups.getOrNull(selectedGroupIndex)
            val detailScrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .padding(Spacing.screenPadding)
                    .verticalScroll(detailScrollState)
            ) {
                AppTopBar(
                    title = "Group detail",
                    onBack = {
                        onBackToDashboard()
                    }
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (group != null) {
                    GroupDetailContent(
                        group = group,
                        deletedPaths = deletedPaths,
                        imageLoader = imageLoader,
                        onFileDeleted = { file ->
                            onDeleteFile?.invoke(file)
                        }
                    )
                } else {
                    Text("Group not found.")
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.padding(Spacing.screenPadding)
            ) {
                item {
                    AppTopBar(
                        title = "Results",
                        onBack = {
                            onBackToDashboard()
                        },
                        actions = {
                            if (selectedGroupIndex == null) {
                                IconButton(onClick = { menuExpanded.value = true }) {
                                    Icon(Icons.Filled.MoreVert, contentDescription = "Menu")
                                }
                                androidx.compose.material3.DropdownMenu(
                                    expanded = menuExpanded.value,
                                    onDismissRequest = { menuExpanded.value = false }
                                ) {
                                    androidx.compose.material3.DropdownMenuItem(
                                        text = { Text("Clear all results") },
                                        onClick = {
                                            menuExpanded.value = false
                                            onClearResults()
                                        }
                                    )
                                    androidx.compose.material3.DropdownMenuItem(
                                        text = { Text("Show full paths") },
                                        leadingIcon = {
                                            Checkbox(
                                                checked = showFullPaths.value,
                                                onCheckedChange = null
                                            )
                                        },
                                        onClick = {
                                            showFullPaths.value = !showFullPaths.value
                                            settingsStore.setShowFullPaths(showFullPaths.value)
                                            menuExpanded.value = false
                                        }
                                    )
                                }
                            }
                        }
                    )
                }
                item { Spacer(modifier = Modifier.height(8.dp)) }
                when (val current = state.value) {
                    ScanUiState.Idle -> item { Text("No results yet.") }
                    is ScanUiState.Scanning -> item { Text("Scanning…") }
                    is ScanUiState.Error -> item { Text("Error: ${current.message}") }
                    is ScanUiState.Success -> {
                        val resultValue = result ?: return@LazyColumn
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text("Files scanned: ${result.files.size}")
                                    Text("Duplicate groups: ${result.duplicateGroups.size}")
                                }
                                OutlinedButton(onClick = {
                                    pendingSortKey.value = sortKey.value
                                    pendingSortDirection.value = sortDirection.value
                                    sortDialogOpen.value = true
                                }) {
                                    Text("Sort")
                                }
                            }
                        }
                        item { Spacer(modifier = Modifier.height(8.dp)) }
                        if (resultValue.duplicateGroups.isEmpty()) {
                            item { Text("No duplicates found.") }
                        } else {
                            val groupsToShow = resultValue.duplicateGroups.take(visibleCount.value)
                            itemsIndexed(
                                items = groupsToShow,
                                key = { _, group -> group.hashHex }
                            ) { _, group ->
                                val groupCount = group.files.size
                                val groupSize = group.files.sumOf { it.sizeBytes }
                                val fileSize = formatBytes(group.files.firstOrNull()?.sizeBytes ?: 0)
                                val preview = group.files.firstOrNull { isMediaFile(it.normalizedPath) }
                                val groupDeleted = group.files.any { deletedPaths.contains(it.normalizedPath) }
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            val index = resultValue.duplicateGroups.indexOf(group)
                                            if (onOpenGroup != null && index >= 0) {
                                                onOpenGroup(index)
                                            }
                                        }
                                    ,
                                    colors = if (groupDeleted) {
                                        CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                                        )
                                    } else {
                                        CardDefaults.cardColors()
                                    }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .padding(12.dp)
                                            .fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (preview != null) {
                                            AsyncImage(
                                                model = ImageRequest.Builder(context)
                                                    .data(File(preview.normalizedPath))
                                                    .build(),
                                                imageLoader = imageLoader,
                                                contentDescription = "Thumbnail",
                                                modifier = Modifier.size(72.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                        }
                                        Column(modifier = Modifier.fillMaxWidth()) {
                                            Text(
                                                text = "${groupCount} files · Total ${formatBytes(groupSize)}",
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                            Text(
                                                text = "Per-file ${fileSize}",
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
                            if (resultValue.duplicateGroups.size > visibleCount.value) {
                                item {
                                    OutlinedButton(
                                        onClick = {
                                            visibleCount.value = (visibleCount.value + pageSize)
                                                .coerceAtMost(resultValue.duplicateGroups.size)
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Load more")
                                    }
                                }
                            }
                        }
                    }
                }
            }
            loadIndicatorText?.let { indicator ->
                Text(
                    text = indicator,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(end = 12.dp, top = 12.dp)
                )
            }
        }
    }

    if (sortDialogOpen.value) {
        AlertDialog(
            onDismissRequest = { sortDialogOpen.value = false },
            title = { Text("Sort options") },
            text = {
                Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                    Text("Sort by")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = pendingSortKey.value == ResultSortKey.Count,
                            onClick = { pendingSortKey.value = ResultSortKey.Count }
                        )
                        Text("Count")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = pendingSortKey.value == ResultSortKey.TotalSize,
                            onClick = { pendingSortKey.value = ResultSortKey.TotalSize }
                        )
                        Text("Total size")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = pendingSortKey.value == ResultSortKey.PerFileSize,
                            onClick = { pendingSortKey.value = ResultSortKey.PerFileSize }
                        )
                        Text("Per-file size")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = pendingSortKey.value == ResultSortKey.Name,
                            onClick = { pendingSortKey.value = ResultSortKey.Name }
                        )
                        Text("Name")
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Order")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = pendingSortDirection.value == SortDirection.Asc,
                            onClick = { pendingSortDirection.value = SortDirection.Asc }
                        )
                        Text("Ascending")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = pendingSortDirection.value == SortDirection.Desc,
                            onClick = { pendingSortDirection.value = SortDirection.Desc }
                        )
                        Text("Descending")
                    }
                }
            },
            confirmButton = {
                OutlinedButton(onClick = {
                    sortKey.value = pendingSortKey.value
                    sortDirection.value = pendingSortDirection.value
                    settingsStore.setResultSortKey(sortKey.value.name)
                    settingsStore.setResultSortDirection(sortDirection.value.name)
                    onSortChanged?.invoke()
                    sortDialogOpen.value = false
                }) {
                    Text("Apply")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { sortDialogOpen.value = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun GroupDetailContent(
    group: DuplicateGroup,
    deletedPaths: Set<String>,
    imageLoader: ImageLoader,
    onFileDeleted: (FileMetadata) -> Unit
) {
    val context = LocalContext.current
    val selectedFile = remember { mutableStateOf<FileMetadata?>(null) }
    val groupCount = group.files.size
    val groupSize = group.files.sumOf { it.sizeBytes }
    val fileSize = formatBytesWithExact(group.files.firstOrNull()?.sizeBytes ?: 0)
    val preview = group.files.firstOrNull { isMediaFile(it.normalizedPath) }

    Text("Group detail")
    Spacer(modifier = Modifier.height(8.dp))
    if (preview != null) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(File(preview.normalizedPath))
                .build(),
            imageLoader = imageLoader,
            contentDescription = "Thumbnail",
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
    }
    Text("${groupCount} files · Total ${formatBytes(groupSize)}")
    Text("Per-file ${fileSize}")
    Spacer(modifier = Modifier.height(8.dp))

    group.files.sortedBy { it.normalizedPath }.forEach { file ->
        val date = formatDate(file.lastModifiedMillis)
        val isDeleted = deletedPaths.contains(file.normalizedPath)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { selectedFile.value = file }
            ,
            colors = if (isDeleted) {
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            } else {
                CardDefaults.cardColors()
            }
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
                        overflow = TextOverflow.Ellipsis,
                        color = if (isDeleted) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${formatBytesWithExact(file.sizeBytes)} · ${date}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isDeleted) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }

    selectedFile.value?.let { file ->
        AlertDialog(
            onDismissRequest = { selectedFile.value = null },
            title = { Text("File details") },
            text = {
                Column {
                    Text("Path: ${file.normalizedPath}")
                    Text("Size: ${formatBytesWithExact(file.sizeBytes)}")
                    Text("Modified: ${formatDate(file.lastModifiedMillis)}")
                }
            },
            confirmButton = {
                OutlinedButton(onClick = {
                    openFile(context, file.normalizedPath)
                    selectedFile.value = null
                }) {
                    Text("Open")
                }
            },
            dismissButton = {
                Row {
                    OutlinedButton(onClick = {
                        File(file.normalizedPath).delete()
                        onFileDeleted(file)
                        selectedFile.value = null
                    }) {
                        Text("Delete")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedButton(onClick = { selectedFile.value = null }) {
                        Text("Cancel")
                    }
                }
            }
        )
    }
}
