package opensource.cached_dupe_scanner.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
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
import opensource.cached_dupe_scanner.ui.components.RadioOptionRow
import opensource.cached_dupe_scanner.ui.components.ScrollbarDefaults
import opensource.cached_dupe_scanner.ui.components.Spacing
import opensource.cached_dupe_scanner.ui.components.VerticalLazyScrollbar
import opensource.cached_dupe_scanner.ui.results.ScanUiState
import java.util.Locale
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

@Composable
fun ResultsScreen(
    state: MutableState<ScanUiState>,
    onBackToDashboard: () -> Unit,
    settingsStore: AppSettingsStore,
    displayResult: ScanResult? = null,
    deletedPaths: Set<String> = emptySet(),
    onDeleteFile: (suspend (FileMetadata) -> Boolean)? = null,
    onRefresh: (suspend () -> Unit)? = null,
    onOpenGroup: ((Int) -> Unit)? = null,
    onSortChanged: (() -> Unit)? = null,
    selectedGroupIndex: Int? = null,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val menuExpanded = remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val imageLoader = remember {
        ImageLoader.Builder(context)
            .components { add(VideoFrameDecoder.Factory()) }
            .build()
    }
    val rememberedPreviewCache = remember { mutableStateMapOf<String, ImageBitmap>() }
    val settingsSnapshot = remember { settingsStore.load() }
    val showFullPaths = remember { mutableStateOf(settingsSnapshot.showFullPaths) }
    val keepLoadedThumbnailsInMemory = settingsSnapshot.keepLoadedThumbnailsInMemory
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
    val groupMemberSortKey = remember {
        val parsed = runCatching { ResultGroupMemberSortKey.valueOf(settingsSnapshot.resultGroupSortKey) }
            .getOrDefault(ResultGroupMemberSortKey.Path)
        mutableStateOf(parsed)
    }
    val groupMemberSortDirection = remember {
        val parsed = runCatching { SortDirection.valueOf(settingsSnapshot.resultGroupSortDirection) }
            .getOrDefault(SortDirection.Asc)
        mutableStateOf(parsed)
    }
    val sortDialogOpen = remember { mutableStateOf(false) }
    val pendingSortKey = remember { mutableStateOf(ResultSortKey.Count) }
    val pendingSortDirection = remember { mutableStateOf(SortDirection.Desc) }
    val pageSize = 50
    val buffer = 20
    val result = remember(state.value, sortKey.value, sortDirection.value, displayResult) {
        if (state.value is ScanUiState.Success) {
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
    }
    val totalGroups = result?.duplicateGroups?.size ?: 0
    val visibleCount = rememberSaveable { mutableStateOf(0) }
    val topVisibleGroupIndex = remember { mutableStateOf(0) }
    LaunchedEffect(totalGroups) {
        if (totalGroups > 0) {
            val initial = pageSize.coerceAtMost(totalGroups)
            if (visibleCount.value == 0) {
                visibleCount.value = initial
            } else {
                visibleCount.value = visibleCount.value.coerceAtMost(totalGroups)
            }
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
        LazyColumn(
            state = listState,
            modifier = Modifier.padding(Spacing.screenPadding),
            contentPadding = PaddingValues(end = ScrollbarDefaults.ThumbWidth + 8.dp)
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
                                    text = { Text("Refresh") },
                                    leadingIcon = {
                                        Icon(Icons.Filled.Refresh, contentDescription = null)
                                    },
                                    onClick = {
                                        menuExpanded.value = false
                                        val handler = onRefresh ?: return@DropdownMenuItem
                                        scope.launch { handler() }
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
                            val hasPreviewMedia = group.files.any { isMediaFile(it.normalizedPath) }
                            val previewCandidates = mediaPreviewCandidates(
                                files = group.files,
                                deletedPaths = deletedPaths
                            )
                            val previewMemoryKey = remember(group.hashHex, group.files.firstOrNull()?.sizeBytes) {
                                "${group.files.firstOrNull()?.sizeBytes ?: 0L}:${group.hashHex}"
                            }
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
                                    if (hasPreviewMedia) {
                                        GroupPreviewThumbnail(
                                            candidatePaths = previewCandidates,
                                            previewMemoryKey = previewMemoryKey,
                                            rememberedPreviewCache = rememberedPreviewCache,
                                            imageLoader = imageLoader,
                                            keepLoadedInMemory = keepLoadedThumbnailsInMemory,
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

        if (selectedGroupIndex != null && result != null) {
            val group = result.duplicateGroups.getOrNull(selectedGroupIndex)
            val detailListState = rememberLazyListState()
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                Box {
                    LazyColumn(
                        state = detailListState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(Spacing.screenPadding),
                        contentPadding = PaddingValues(end = ScrollbarDefaults.ThumbWidth + 8.dp)
                    ) {
                        item {
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
                                    keepLoadedThumbnailsInMemory = keepLoadedThumbnailsInMemory,
                                    rememberedPreviewCache = rememberedPreviewCache,
                                    sortKey = groupMemberSortKey.value,
                                    sortDirection = groupMemberSortDirection.value,
                                    onApplySort = { key, direction ->
                                        groupMemberSortKey.value = key
                                        groupMemberSortDirection.value = direction
                                        settingsStore.setResultGroupSortKey(key.name)
                                        settingsStore.setResultGroupSortDirection(direction.name)
                                    },
                                    onDeleteFile = { file ->
                                        val handler = onDeleteFile ?: return@GroupDetailContent false
                                        handler(file)
                                    }
                                )
                            } else {
                                Text("Group not found.")
                            }
                        }
                    }

                    VerticalLazyScrollbar(
                        listState = detailListState,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight()
                            .padding(end = 4.dp)
                    )
                }
            }
            BackHandler {
                onBackToDashboard()
            }
        }

        if (selectedGroupIndex == null) {
            loadIndicatorText?.let { indicator ->
                Text(
                    text = indicator,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(end = ScrollbarDefaults.ThumbWidth + 12.dp, top = 12.dp)
                )
            }
        }

        if (selectedGroupIndex == null) {
            VerticalLazyScrollbar(
                listState = listState,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .padding(end = 4.dp)
            )
        }
    }

    if (sortDialogOpen.value) {
        AlertDialog(
            onDismissRequest = { sortDialogOpen.value = false },
            title = { Text("Sort options") },
            text = {
                Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                    Text("Sort by")
                    RadioOptionRow(
                        option = ResultSortKey.Count,
                        selected = pendingSortKey.value,
                        label = ResultSortKey.Count.label,
                        onSelect = { pendingSortKey.value = it }
                    )
                    RadioOptionRow(
                        option = ResultSortKey.TotalSize,
                        selected = pendingSortKey.value,
                        label = ResultSortKey.TotalSize.label,
                        onSelect = { pendingSortKey.value = it }
                    )
                    RadioOptionRow(
                        option = ResultSortKey.PerFileSize,
                        selected = pendingSortKey.value,
                        label = ResultSortKey.PerFileSize.label,
                        onSelect = { pendingSortKey.value = it }
                    )
                    RadioOptionRow(
                        option = ResultSortKey.Name,
                        selected = pendingSortKey.value,
                        label = ResultSortKey.Name.label,
                        onSelect = { pendingSortKey.value = it }
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Order")
                    RadioOptionRow(
                        option = SortDirection.Asc,
                        selected = pendingSortDirection.value,
                        label = "Ascending",
                        onSelect = { pendingSortDirection.value = it }
                    )
                    RadioOptionRow(
                        option = SortDirection.Desc,
                        selected = pendingSortDirection.value,
                        label = "Descending",
                        onSelect = { pendingSortDirection.value = it }
                    )
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
    keepLoadedThumbnailsInMemory: Boolean,
    rememberedPreviewCache: MutableMap<String, ImageBitmap>,
    sortKey: ResultGroupMemberSortKey,
    sortDirection: SortDirection,
    onApplySort: (ResultGroupMemberSortKey, SortDirection) -> Unit,
    onDeleteFile: suspend (FileMetadata) -> Boolean
) {
    val groupCount = group.files.size
    val groupSize = group.files.sumOf { it.sizeBytes }
    val fileSize = formatBytesWithExact(group.files.firstOrNull()?.sizeBytes ?: 0)
    val previewMemoryKey = remember(group.hashHex, group.files.firstOrNull()?.sizeBytes) {
        "${group.files.firstOrNull()?.sizeBytes ?: 0L}:${group.hashHex}"
    }

    EagerDuplicateGroupDetailContent(
        title = "Group detail",
        memberCount = groupCount,
        totalBytes = groupSize,
        summaryLines = listOf("Per-file $fileSize"),
        members = group.files,
        deletedPaths = deletedPaths,
        imageLoader = imageLoader,
        keepLoadedThumbnailsInMemory = keepLoadedThumbnailsInMemory,
        rememberedPreviewCache = rememberedPreviewCache,
        previewMemoryKey = previewMemoryKey,
        previewHeight = 180.dp,
        sortKey = sortKey,
        sortDirection = sortDirection,
        onApplySort = onApplySort,
        onDeleteFile = onDeleteFile
    )
}
