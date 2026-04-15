package opensource.cached_dupe_scanner.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.RadioButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.decode.VideoFrameDecoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import opensource.cached_dupe_scanner.core.FileMetadata
import opensource.cached_dupe_scanner.storage.AppSettingsStore
import opensource.cached_dupe_scanner.storage.PagedFileRepository
import opensource.cached_dupe_scanner.storage.TrashController
import opensource.cached_dupe_scanner.ui.components.AppTopBar
import opensource.cached_dupe_scanner.ui.components.TopRightLoadIndicator
import opensource.cached_dupe_scanner.ui.components.ScrollbarDefaults
import opensource.cached_dupe_scanner.ui.components.Spacing
import opensource.cached_dupe_scanner.ui.components.VerticalLazyScrollbar

internal fun markDeletedPath(
    currentDeletedPaths: Set<String>,
    deletedPath: String
): Set<String> = currentDeletedPaths + deletedPath

private enum class FilesPreviewMode {
    Compact,
    VideoTimeline
}

@Composable
fun FilesScreenDb(
    fileRepo: PagedFileRepository,
    trashController: TrashController,
    settingsStore: AppSettingsStore,
    keepLoadedThumbnailsInMemory: Boolean,
    keepLoadedVideoPreviewsInMemory: Boolean,
    rememberedThumbnailCache: MutableMap<String, ImageBitmap>,
    rememberedVideoPreviewCache: MutableMap<String, ImageBitmap>,
    clearVersion: Int,
    refreshVersion: Int,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val menuExpanded = remember { mutableStateOf(false) }
    val sortDialogOpen = remember { mutableStateOf(false) }
    val filterScreenOpen = remember { mutableStateOf(false) }
    val previewMode = rememberSaveable { mutableStateOf(FilesPreviewMode.Compact.name) }

    val imageLoader = remember {
        ImageLoader.Builder(context)
            .components { add(VideoFrameDecoder.Factory()) }
            .build()
    }

    val settingsSnapshot = remember { settingsStore.load() }
    val sortKey = remember {
        val key = runCatching { PagedFileRepository.SortKey.valueOf(settingsSnapshot.filesSortKey) }
            .getOrDefault(PagedFileRepository.SortKey.Name)
        mutableStateOf(key)
    }
    val sortDirection = remember {
        val dir = runCatching { PagedFileRepository.SortDirection.valueOf(settingsSnapshot.filesSortDirection) }
            .getOrDefault(PagedFileRepository.SortDirection.Asc)
        mutableStateOf(dir)
    }
    val pendingSortKey = remember { mutableStateOf(sortKey.value) }
    val pendingSortDirection = remember { mutableStateOf(sortDirection.value) }
    val initialFilterDefinition = remember(settingsSnapshot.filesFilterDefinitionJson) {
        resultsFilterDefinitionFromJson(settingsSnapshot.filesFilterDefinitionJson)
    }
    val appliedFilter = remember { mutableStateOf(initialFilterDefinition) }
    val draftFilter = remember {
        mutableStateOf(
            if (initialFilterDefinition.clusters.isEmpty()) {
                ResultsFilterDefinition(clusters = listOf(createResultsFilterCluster()))
            } else {
                initialFilterDefinition
            }
        )
    }

    val items = remember { mutableStateOf<List<FileMetadata>>(emptyList()) }
    val cursor = remember { mutableStateOf<PagedFileRepository.Cursor>(PagedFileRepository.Cursor.Start) }
    val filterSourceExhausted = remember { mutableStateOf(false) }
    val total = remember { mutableStateOf(0) }
    val isLoading = remember { mutableStateOf(false) }
    val selectedFile = remember { mutableStateOf<FileMetadata?>(null) }
    val deletedPaths = rememberSaveable { mutableStateOf(setOf<String>()) }
    val topVisibleIndex = remember { mutableStateOf(0) }

    val pageSize = 200
    val buffer = 50
    val visibleCount = rememberSaveable { mutableStateOf(0) }

    fun isVideoTimelinePreviewEnabled(): Boolean {
        return previewMode.value == FilesPreviewMode.VideoTimeline.name
    }

    fun filtersActive(): Boolean = appliedFilter.value.hasActiveRules(FILE_FILTER_TARGETS)

    fun resetAndLoad() {
        items.value = emptyList()
        cursor.value = PagedFileRepository.Cursor.Start
        filterSourceExhausted.value = false
        visibleCount.value = 0
        scope.launch {
            isLoading.value = true
            total.value = withContext(Dispatchers.IO) { fileRepo.countAll() }
            val page = withContext(Dispatchers.IO) {
                if (filtersActive()) {
                    loadFilteredFilesPage(
                        fileRepo = fileRepo,
                        sortKey = sortKey.value,
                        direction = sortDirection.value,
                        cursor = PagedFileRepository.Cursor.Start,
                        definition = appliedFilter.value,
                        minMatches = pageSize,
                        sourcePageSize = pageSize
                    )
                } else {
                    FilteredFilesPage.fromUnfiltered(
                        fileRepo.loadPage(sortKey.value, sortDirection.value, cursor.value, pageSize)
                    )
                }
            }
            items.value = page.items
            cursor.value = page.nextCursor ?: cursor.value
            filterSourceExhausted.value = page.exhausted
            visibleCount.value = page.items.size
            isLoading.value = false
        }
    }

    fun loadMore() {
        if (isLoading.value) return
        if (filtersActive() && filterSourceExhausted.value) return
        scope.launch {
            isLoading.value = true
            val page = withContext(Dispatchers.IO) {
                if (filtersActive()) {
                    loadFilteredFilesPage(
                        fileRepo = fileRepo,
                        sortKey = sortKey.value,
                        direction = sortDirection.value,
                        cursor = cursor.value,
                        definition = appliedFilter.value,
                        minMatches = pageSize,
                        sourcePageSize = pageSize
                    )
                } else {
                    FilteredFilesPage.fromUnfiltered(
                        fileRepo.loadPage(sortKey.value, sortDirection.value, cursor.value, pageSize)
                    )
                }
            }
            if (page.items.isNotEmpty()) {
                items.value = items.value + page.items
                cursor.value = page.nextCursor ?: cursor.value
                visibleCount.value = items.value.size
            }
            filterSourceExhausted.value = page.exhausted
            isLoading.value = false
        }
    }

    LaunchedEffect(Unit) { resetAndLoad() }
    LaunchedEffect(refreshVersion) { resetAndLoad() }
    LaunchedEffect(clearVersion) {
        items.value = emptyList()
        total.value = 0
        deletedPaths.value = emptySet()
        cursor.value = PagedFileRepository.Cursor.Start
        visibleCount.value = 0
    }

    LaunchedEffect(Unit) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { topVisibleIndex.value = it }
    }

    val overlayText = run {
        val totalCount = total.value
        if (totalCount <= 0) {
            null
        } else if (filtersActive()) {
            val matchedCount = items.value.size
            val current = if (matchedCount <= 0) 0 else (topVisibleIndex.value + 1).coerceAtMost(matchedCount)
            "$current/$matchedCount - ${matchedCount.coerceAtMost(totalCount)}/$totalCount"
        } else {
            val loaded = items.value.size.coerceAtMost(totalCount).coerceAtLeast(1)
            val current = (topVisibleIndex.value + 1).coerceAtLeast(1)
            val currentPercent = ((current.toDouble() / loaded.toDouble()) * 100).toInt()
            val loadedPercent = ((loaded.toDouble() / totalCount.toDouble()) * 100).toInt()
            "$current/$loaded/$totalCount (${currentPercent}%/${loadedPercent}%)"
        }
    }

    // Auto-load next page on scroll (legacy behavior).
    LaunchedEffect(total.value, items.value.size) {
        if (total.value <= 0) return@LaunchedEffect
        snapshotFlow {
            val layoutInfo = listState.layoutInfo
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val totalItems = layoutInfo.totalItemsCount
            val hasMore = items.value.size < total.value
            val closeToEnd = lastVisible >= (totalItems - buffer)
            closeToEnd && hasMore && !isLoading.value
        }
            .distinctUntilChanged()
            .filter { it }
            .collect {
                loadMore()
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
                    title = "Files",
                    onBack = onBack,
                    actions = {
                        IconButton(onClick = { menuExpanded.value = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "Menu")
                        }
                        androidx.compose.material3.DropdownMenu(
                            expanded = menuExpanded.value,
                            onDismissRequest = { menuExpanded.value = false }
                        ) {
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text("Refresh") },
                                leadingIcon = { Icon(Icons.Filled.Refresh, contentDescription = null) },
                                onClick = {
                                    menuExpanded.value = false
                                    resetAndLoad()
                                }
                            )
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text("Filter") },
                                onClick = {
                                    menuExpanded.value = false
                                    draftFilter.value = if (appliedFilter.value.clusters.isEmpty()) {
                                        ResultsFilterDefinition(clusters = listOf(createResultsFilterCluster()))
                                    } else {
                                        appliedFilter.value
                                    }
                                    filterScreenOpen.value = true
                                }
                            )
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text("Sort") },
                                onClick = {
                                    menuExpanded.value = false
                                    pendingSortKey.value = sortKey.value
                                    pendingSortDirection.value = sortDirection.value
                                    sortDialogOpen.value = true
                                }
                            )
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text("Video preview") },
                                leadingIcon = {
                                    Checkbox(
                                        checked = isVideoTimelinePreviewEnabled(),
                                        onCheckedChange = null
                                    )
                                },
                                onClick = {
                                    previewMode.value = if (isVideoTimelinePreviewEnabled()) {
                                        FilesPreviewMode.Compact.name
                                    } else {
                                        FilesPreviewMode.VideoTimeline.name
                                    }
                                    menuExpanded.value = false
                                }
                            )
                        }
                    }
                )
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }
            item {
                Text(
                    text = if (filtersActive()) {
                        "${summarizeResultsFilter(appliedFilter.value, FILE_FILTER_TARGETS)} · matched ${items.value.size}/${total.value}"
                    } else {
                        "Loaded ${items.value.size}/${total.value}"
                    } + if (isVideoTimelinePreviewEnabled()) {
                        " · Video preview"
                    } else {
                        ""
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            item { Spacer(modifier = Modifier.height(8.dp)) }

            if (items.value.isEmpty() && !isLoading.value) {
                item { Text("No files in cache.") }
            } else {
                items(items.value, key = { it.normalizedPath }) { file ->
                    val isDeleted = deletedPaths.value.contains(file.normalizedPath)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedFile.value = file },
                        colors = if (isDeleted) {
                            CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                            )
                        } else {
                            CardDefaults.cardColors()
                        }
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(12.dp)
                                .fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (isMediaFile(file.normalizedPath)) {
                                    RememberingAsyncThumbnail(
                                        filePath = file.normalizedPath,
                                        previewMemoryKey = file.normalizedPath,
                                        rememberedPreviewCache = rememberedThumbnailCache,
                                        imageLoader = imageLoader,
                                        keepLoadedInMemory = keepLoadedThumbnailsInMemory,
                                        contentDescription = "Thumbnail",
                                        modifier = Modifier
                                            .width(56.dp)
                                            .height(56.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        text = formatPath(file.normalizedPath, showFullPath = false),
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = if (isDeleted) {
                                            MaterialTheme.colorScheme.onSecondaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        }
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = file.normalizedPath,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = if (isDeleted) {
                                            MaterialTheme.colorScheme.onSecondaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "${formatBytesWithExact(file.sizeBytes)} · ${formatDate(file.lastModifiedMillis)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isDeleted) {
                                            MaterialTheme.colorScheme.onSecondaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                }
                            }

                            if (isVideoTimelinePreviewEnabled() && isVideoFile(file.normalizedPath)) {
                                Spacer(modifier = Modifier.height(8.dp))
                                VideoTimelinePreviewStrip(
                                    filePath = file.normalizedPath,
                                    rememberedPreviewCache = rememberedVideoPreviewCache,
                                    imageLoader = imageLoader,
                                    keepLoadedInMemory = keepLoadedVideoPreviewsInMemory,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (isLoading.value && items.value.isNotEmpty()) {
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

        TopRightLoadIndicator(text = overlayText)

        VerticalLazyScrollbar(
            listState = listState,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .padding(end = 4.dp)
        )
    }

    selectedFile.value?.let { file ->
        FileDetailsDialogWithDeleteConfirm(
            file = file,
            showName = true,
            onOpen = {
                openFile(context, file.normalizedPath)
                selectedFile.value = null
            },
            onDelete = {
                withContext(Dispatchers.IO) {
                    trashController.moveToTrash(file.normalizedPath).success
                }
            },
            onDeleteResult = { deleted ->
                if (deleted) {
                    selectedFile.value = null
                    deletedPaths.value = markDeletedPath(
                        currentDeletedPaths = deletedPaths.value,
                        deletedPath = file.normalizedPath
                    )
                }
            },
            onDismiss = { selectedFile.value = null }
        )
    }

    if (sortDialogOpen.value) {
        AlertDialog(
            onDismissRequest = { sortDialogOpen.value = false },
            title = { Text("Sort options") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Sort by")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = pendingSortKey.value == PagedFileRepository.SortKey.Name,
                            onClick = { pendingSortKey.value = PagedFileRepository.SortKey.Name }
                        )
                        Text("Name")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = pendingSortKey.value == PagedFileRepository.SortKey.Size,
                            onClick = { pendingSortKey.value = PagedFileRepository.SortKey.Size }
                        )
                        Text("Size")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = pendingSortKey.value == PagedFileRepository.SortKey.Modified,
                            onClick = { pendingSortKey.value = PagedFileRepository.SortKey.Modified }
                        )
                        Text("Modified")
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Direction")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = pendingSortDirection.value == PagedFileRepository.SortDirection.Asc,
                            onClick = { pendingSortDirection.value = PagedFileRepository.SortDirection.Asc }
                        )
                        Text("Ascending")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = pendingSortDirection.value == PagedFileRepository.SortDirection.Desc,
                            onClick = { pendingSortDirection.value = PagedFileRepository.SortDirection.Desc }
                        )
                        Text("Descending")
                    }
                }
            },
            confirmButton = {
                OutlinedButton(
                    onClick = {
                        sortKey.value = pendingSortKey.value
                        sortDirection.value = pendingSortDirection.value
                        settingsStore.setFilesSortKey(sortKey.value.name)
                        settingsStore.setFilesSortDirection(sortDirection.value.name)
                        sortDialogOpen.value = false
                        resetAndLoad()
                    }
                ) {
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

    if (filterScreenOpen.value) {
        FileFilterScreen(
            definition = draftFilter.value,
            onDefinitionChange = { draftFilter.value = it },
            onBack = { filterScreenOpen.value = false },
            onApply = {
                appliedFilter.value = draftFilter.value
                settingsStore.setFilesFilterDefinitionJson(
                    resultsFilterDefinitionToJson(appliedFilter.value)
                )
                filterScreenOpen.value = false
                resetAndLoad()
            }
        )
    }
}

internal data class FilteredFilesPage(
    val items: List<FileMetadata>,
    val nextCursor: PagedFileRepository.Cursor?,
    val exhausted: Boolean
) {
    companion object {
        fun fromUnfiltered(page: PagedFileRepository.Page): FilteredFilesPage {
            return FilteredFilesPage(
                items = page.items,
                nextCursor = page.nextCursor,
                exhausted = page.items.isEmpty()
            )
        }
    }
}

internal fun loadFilteredFilesPage(
    fileRepo: PagedFileRepository,
    sortKey: PagedFileRepository.SortKey,
    direction: PagedFileRepository.SortDirection,
    cursor: PagedFileRepository.Cursor,
    definition: ResultsFilterDefinition,
    minMatches: Int,
    sourcePageSize: Int
): FilteredFilesPage {
    if (sourcePageSize <= 0 || minMatches <= 0) {
        return FilteredFilesPage(
            items = emptyList(),
            nextCursor = cursor,
            exhausted = true
        )
    }

    val matchedItems = mutableListOf<FileMetadata>()
    var nextCursor: PagedFileRepository.Cursor? = cursor
    var exhausted = false

    while (matchedItems.size < minMatches && !exhausted) {
        val page = fileRepo.loadPage(
            sortKey = sortKey,
            direction = direction,
            cursor = nextCursor ?: cursor,
            limit = sourcePageSize
        )
        if (page.items.isEmpty()) {
            exhausted = true
            nextCursor = null
            break
        }
        matchedItems += page.items.filter { file ->
            matchesFileFilter(definition, file)
        }
        nextCursor = page.nextCursor
    }

    return FilteredFilesPage(
        items = matchedItems.take(minMatches),
        nextCursor = nextCursor,
        exhausted = exhausted
    )
}
