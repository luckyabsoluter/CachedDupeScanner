package opensource.cached_dupe_scanner.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.RadioButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import opensource.cached_dupe_scanner.cache.DuplicateGroupEntity
import opensource.cached_dupe_scanner.core.FileMetadata
import opensource.cached_dupe_scanner.core.ResultSortKey
import opensource.cached_dupe_scanner.core.SortDirection
import opensource.cached_dupe_scanner.storage.AppSettingsStore
import opensource.cached_dupe_scanner.storage.DuplicateGroupSortKey
import opensource.cached_dupe_scanner.storage.ResultsDbRepository
import opensource.cached_dupe_scanner.ui.components.AppTopBar
import opensource.cached_dupe_scanner.ui.components.ScrollbarDefaults
import opensource.cached_dupe_scanner.ui.components.Spacing
import opensource.cached_dupe_scanner.ui.components.VerticalLazyScrollbar
import opensource.cached_dupe_scanner.ui.components.VerticalScrollbar
import java.io.File

private class MembersCacheEntry {
    val members = mutableStateListOf<FileMetadata>()
    val cursor = mutableStateOf<String?>(null)
    val isLoading = mutableStateOf(false)
    val isComplete = mutableStateOf(false)
}

private data class BulkDeleteOutcome(
    val successCount: Int,
    val failedPaths: Set<String>
)

@Composable
fun ResultsScreenDb(
    resultsRepo: ResultsDbRepository,
    settingsStore: AppSettingsStore,
    deletedPaths: Set<String>,
    onDeleteFile: (suspend (FileMetadata) -> Boolean)?,
    onBack: () -> Unit,
    onOpenGroup: ((Int) -> Unit)?,
    refreshVersion: Int = 0,
    selectedGroupIndex: Int? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val menuExpanded = remember { mutableStateOf(false) }
    val sortDialogOpen = remember { mutableStateOf(false) }
    val settingsSnapshot = remember { settingsStore.load() }
    val showFullPaths = remember { mutableStateOf(settingsSnapshot.showFullPaths) }

    fun normalizeDbSortKey(key: ResultSortKey): ResultSortKey {
        return if (key == ResultSortKey.Name) ResultSortKey.Count else key
    }

    val initialSortKey = remember(settingsSnapshot.resultSortKey) {
        val parsed = runCatching { ResultSortKey.valueOf(settingsSnapshot.resultSortKey) }
            .getOrDefault(ResultSortKey.Count)
        normalizeDbSortKey(parsed)
    }
    val shouldNormalizeSortKeySetting = remember(settingsSnapshot.resultSortKey, initialSortKey) {
        settingsSnapshot.resultSortKey != initialSortKey.name
    }
    val sortKey = remember {
        mutableStateOf(initialSortKey)
    }
    val sortDirection = remember {
        val dir = runCatching { SortDirection.valueOf(settingsSnapshot.resultSortDirection) }
            .getOrDefault(SortDirection.Desc)
        mutableStateOf(dir)
    }
    val pendingSortKey = remember { mutableStateOf(ResultSortKey.Count) }
    val pendingSortDirection = remember { mutableStateOf(SortDirection.Desc) }

    val groups = remember { mutableStateOf<List<DuplicateGroupEntity>>(emptyList()) }
    val fileCount = remember { mutableStateOf(0) }
    val groupCount = remember { mutableStateOf(0) }
    val isRefreshing = remember { mutableStateOf(false) }
    val isPaging = remember { mutableStateOf(false) }
    val loadError = remember { mutableStateOf<String?>(null) }
    val snapshotUpdatedAtMillis = remember { mutableStateOf<Long?>(null) }
    val queryCoordinator = remember { ResultsScreenDbQueryCoordinator() }

    // Keep already loaded group members in RAM so opening a detail screen is instant after first load.
    // Key: "<sizeBytes>:<hashHex>"
    val membersCache = remember { mutableStateMapOf<String, MembersCacheEntry>() }

    val pageSize = 50
    val buffer = 20
    val visibleCount = rememberSaveable { mutableStateOf(0) }
    val topVisibleGroupIndex = remember { mutableStateOf(0) }

    val imageLoader = remember {
        ImageLoader.Builder(context)
            .components { add(VideoFrameDecoder.Factory()) }
            .build()
    }

    fun mapSort(key: ResultSortKey, direction: SortDirection): DuplicateGroupSortKey {
        val normalizedKey = normalizeDbSortKey(key)
        return when (normalizedKey) {
            ResultSortKey.Count -> {
                if (direction == SortDirection.Asc) DuplicateGroupSortKey.CountAsc
                else DuplicateGroupSortKey.CountDesc
            }
            ResultSortKey.TotalSize -> {
                if (direction == SortDirection.Asc) DuplicateGroupSortKey.TotalBytesAsc
                else DuplicateGroupSortKey.TotalBytesDesc
            }
            ResultSortKey.PerFileSize -> {
                if (direction == SortDirection.Asc) DuplicateGroupSortKey.PerFileSizeAsc
                else DuplicateGroupSortKey.PerFileSizeDesc
            }
            ResultSortKey.Name -> {
                if (direction == SortDirection.Asc) DuplicateGroupSortKey.CountAsc
                else DuplicateGroupSortKey.CountDesc
            }
        }
    }

    LaunchedEffect(shouldNormalizeSortKeySetting) {
        if (shouldNormalizeSortKeySetting) {
            settingsStore.setResultSortKey(initialSortKey.name)
        }
    }

    fun refresh(reset: Boolean, rebuild: Boolean) {
        if (isRefreshing.value) return
        if (reset) {
            groups.value = emptyList()
            visibleCount.value = 0
            topVisibleGroupIndex.value = 0
            snapshotUpdatedAtMillis.value = null
        }
        scope.launch {
            isRefreshing.value = true
            loadError.value = null
            val requestToken = queryCoordinator.beginRefresh(reset = reset)
            try {
                val normalizedSortKey = normalizeDbSortKey(sortKey.value)
                if (normalizedSortKey != sortKey.value) {
                    sortKey.value = normalizedSortKey
                    settingsStore.setResultSortKey(normalizedSortKey.name)
                }
                val sortForQuery = mapSort(normalizedSortKey, sortDirection.value)
                val snapshot = withContext(Dispatchers.IO) {
                    resultsRepo.loadInitialSnapshot(
                        sortKey = sortForQuery,
                        limit = pageSize,
                        rebuild = rebuild
                    )
                }
                if (!queryCoordinator.isRefreshTokenValid(requestToken)) return@launch
                fileCount.value = snapshot.fileCount
                groupCount.value = snapshot.groupCount
                snapshotUpdatedAtMillis.value = snapshot.updatedAtMillis
                if (reset) {
                    groups.value = snapshot.firstPage
                    visibleCount.value = snapshot.firstPage.size
                    topVisibleGroupIndex.value = 0
                }
            } catch (_: Exception) {
                loadError.value = "결과를 불러오지 못했습니다. 다시 시도해 주세요."
            } finally {
                isRefreshing.value = false
            }
        }
    }

    fun loadMoreIfNeeded() {
        if (isRefreshing.value || isPaging.value) return
        val snapshotUpdatedAt = snapshotUpdatedAtMillis.value ?: return
        val needed = visibleCount.value.coerceAtMost(groupCount.value)
        if (groups.value.size >= needed) return
        val offset = groups.value.size
        val limit = needed - offset
        if (limit <= 0) return
        scope.launch {
            isPaging.value = true
            loadError.value = null
            val requestToken = queryCoordinator.beginPaging()
            var needsRefresh = false
            try {
                val snapshotChangedBeforePaging = withContext(Dispatchers.IO) {
                    resultsRepo.hasSnapshotChanged(snapshotUpdatedAt)
                }
                if (snapshotChangedBeforePaging) {
                    needsRefresh = true
                    return@launch
                }
                val next = withContext(Dispatchers.IO) {
                    resultsRepo.loadPageAtSnapshot(
                        sortKey = mapSort(sortKey.value, sortDirection.value),
                        snapshotUpdatedAtMillis = snapshotUpdatedAt,
                        offset = offset,
                        limit = limit
                    )
                }
                if (!queryCoordinator.isPagingTokenValid(requestToken)) return@launch
                if (offset != groups.value.size) return@launch
                if (next.isEmpty()) {
                    val snapshotChangedAfterPaging = withContext(Dispatchers.IO) {
                        resultsRepo.hasSnapshotChanged(snapshotUpdatedAt)
                    }
                    if (snapshotChangedAfterPaging) {
                        needsRefresh = true
                        return@launch
                    }
                }
                if (next.isNotEmpty()) {
                    groups.value = groups.value + next
                }
            } catch (_: Exception) {
                loadError.value = "목록을 이어서 불러오지 못했습니다. 새로고침해 주세요."
            } finally {
                isPaging.value = false
                if (needsRefresh && !isRefreshing.value) {
                    refresh(reset = true, rebuild = false)
                }
            }
        }
    }

    LaunchedEffect(sortKey.value, sortDirection.value) {
        refresh(reset = true, rebuild = false)
    }

    LaunchedEffect(refreshVersion) {
        if (refreshVersion <= 0) return@LaunchedEffect
        while (isRefreshing.value) {
            delay(100)
        }
        refresh(reset = true, rebuild = false)
    }

    LaunchedEffect(visibleCount.value, groupCount.value, groups.value.size) {
        loadMoreIfNeeded()
    }

    LaunchedEffect(Unit) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { topVisibleGroupIndex.value = it }
    }

    val loadIndicatorText = if (selectedGroupIndex != null || groupCount.value == 0) {
        null
    } else {
        val totalGroups = groupCount.value
        val loaded = visibleCount.value.coerceAtMost(totalGroups)
        val current = if (loaded <= 0) 0 else (topVisibleGroupIndex.value + 1).coerceAtLeast(1)
        val safeLoaded = loaded.coerceAtLeast(1)
        val safeTotal = totalGroups.coerceAtLeast(1)
        val currentPercent = ((current.toDouble() / safeLoaded.toDouble()) * 100).toInt()
        val loadedPercent = ((loaded.toDouble() / safeTotal.toDouble()) * 100).toInt()
        "$current/$loaded/$totalGroups (${currentPercent}%/${loadedPercent}%)"
    }

    // Auto-load next page as user scrolls (legacy behavior).
    LaunchedEffect(groupCount.value, groups.value.size) {
        if (groupCount.value <= 0) return@LaunchedEffect
        snapshotFlow {
            val layoutInfo = listState.layoutInfo
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val totalItems = layoutInfo.totalItemsCount
            val remainingToTarget = groupCount.value - visibleCount.value
            val closeToEnd = lastVisible >= (totalItems - buffer)
            closeToEnd && remainingToTarget > 0
        }
            .distinctUntilChanged()
            .filter { it }
            .collect {
                visibleCount.value = (visibleCount.value + pageSize)
                    .coerceAtMost(groupCount.value)
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
                    onBack = onBack,
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
                                    leadingIcon = { Icon(Icons.Filled.Refresh, contentDescription = null) },
                                    onClick = {
                                        menuExpanded.value = false
                                        refresh(reset = true, rebuild = true)
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

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Files scanned: ${fileCount.value}")
                        Text("Duplicate groups: ${groupCount.value}")
                    }
                    OutlinedButton(onClick = {
                        pendingSortKey.value = normalizeDbSortKey(sortKey.value)
                        pendingSortDirection.value = sortDirection.value
                        sortDialogOpen.value = true
                    }) {
                        Text("Sort")
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }

            if (groupCount.value == 0) {
                item {
                    Text(
                        if (isRefreshing.value) "Loading results..."
                        else "No duplicates found."
                    )
                }
            } else {
                val toShow = groups.value.take(visibleCount.value)
                itemsIndexed(toShow, key = { _, g -> "${g.sizeBytes}:${g.hashHex}" }) { index, g ->
                    DuplicateGroupCardDb(
                        resultsRepo = resultsRepo,
                        group = g,
                        deletedPaths = deletedPaths,
                        showFullPaths = showFullPaths.value,
                        imageLoader = imageLoader,
                        membersCache = membersCache,
                        onOpen = {
                            val handler = onOpenGroup ?: return@DuplicateGroupCardDb
                            handler(index)
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if ((isRefreshing.value || isPaging.value) && groups.value.isNotEmpty()) {
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

            loadError.value?.let { message ->
                item { Spacer(modifier = Modifier.height(8.dp)) }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        OutlinedButton(
                            onClick = { refresh(reset = true, rebuild = true) },
                            enabled = !isRefreshing.value
                        ) {
                            Text("Retry")
                        }
                    }
                }
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
    }

    if (selectedGroupIndex != null) {
        val group = groups.value.getOrNull(selectedGroupIndex)
        val cacheKey = remember(group?.sizeBytes, group?.hashHex) {
            if (group == null) null else "${group.sizeBytes}:${group.hashHex}"
        }
        val entry = remember(cacheKey) {
            if (cacheKey == null) null else membersCache.getOrPut(cacheKey) { MembersCacheEntry() }
        }
        val detailScrollState = rememberScrollState()
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Box {
                Column(
                    modifier = Modifier
                        .padding(Spacing.screenPadding)
                        .padding(end = ScrollbarDefaults.ThumbWidth + 8.dp)
                        .verticalScroll(detailScrollState)
                ) {
                    AppTopBar(title = "Group detail", onBack = onBack)
                    Spacer(modifier = Modifier.height(8.dp))
                    if (group == null) {
                        Text("Group not found.")
                    } else {
                        GroupDetailDb(
                            resultsRepo = resultsRepo,
                            group = group,
                            deletedPaths = deletedPaths,
                            onDeleteFile = onDeleteFile,
                            imageLoader = imageLoader,
                            cacheEntry = entry
                        )
                    }
                }
                VerticalScrollbar(
                    scrollState = detailScrollState,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .padding(end = 4.dp)
                )
            }
        }
        BackHandler { onBack() }
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
                            selected = pendingSortKey.value == ResultSortKey.Count,
                            onClick = { pendingSortKey.value = ResultSortKey.Count }
                        )
                        Text(ResultSortKey.Count.label)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = pendingSortKey.value == ResultSortKey.TotalSize,
                            onClick = { pendingSortKey.value = ResultSortKey.TotalSize }
                        )
                        Text(ResultSortKey.TotalSize.label)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = pendingSortKey.value == ResultSortKey.PerFileSize,
                            onClick = { pendingSortKey.value = ResultSortKey.PerFileSize }
                        )
                        Text(ResultSortKey.PerFileSize.label)
                    }
                    // Name sort is not supported in DB mode yet.
                    Text(
                        text = "Name sort is not supported in DB mode.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Direction")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = pendingSortDirection.value == SortDirection.Desc,
                            onClick = { pendingSortDirection.value = SortDirection.Desc }
                        )
                        Text(SortDirection.Desc.label)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = pendingSortDirection.value == SortDirection.Asc,
                            onClick = { pendingSortDirection.value = SortDirection.Asc }
                        )
                        Text(SortDirection.Asc.label)
                    }
                }
            },
            confirmButton = {
                OutlinedButton(
                    onClick = {
                        val normalizedSortKey = normalizeDbSortKey(pendingSortKey.value)
                        sortKey.value = normalizedSortKey
                        sortDirection.value = pendingSortDirection.value
                        settingsStore.setResultSortKey(normalizedSortKey.name)
                        settingsStore.setResultSortDirection(sortDirection.value.name)
                        sortDialogOpen.value = false
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
}

@Composable
private fun DuplicateGroupCardDb(
    resultsRepo: ResultsDbRepository,
    group: DuplicateGroupEntity,
    deletedPaths: Set<String>,
    showFullPaths: Boolean,
    imageLoader: ImageLoader,
    membersCache: MutableMap<String, MembersCacheEntry>,
    onOpen: () -> Unit
) {
    val context = LocalContext.current
    val cacheKey = remember(group.sizeBytes, group.hashHex) { "${group.sizeBytes}:${group.hashHex}" }
    val entry = remember(cacheKey) { membersCache.getOrPut(cacheKey) { MembersCacheEntry() } }

    LaunchedEffect(group.sizeBytes, group.hashHex) {
        // Only do the initial DB hit once per group key.
        if (entry.members.isNotEmpty() || entry.isLoading.value) return@LaunchedEffect
        entry.isLoading.value = true
        try {
            val members = withContext(Dispatchers.IO) {
                resultsRepo.listGroupMembers(
                    sizeBytes = group.sizeBytes,
                    hashHex = group.hashHex,
                    afterPath = null,
                    limit = 10
                )
            }
            entry.members.clear()
            entry.members.addAll(members)
            entry.cursor.value = members.lastOrNull()?.normalizedPath
            // If we already got all files (small group), mark complete.
            entry.isComplete.value = members.size >= group.fileCount
        } finally {
            entry.isLoading.value = false
        }
    }

    val preview = entry.members.firstOrNull { isMediaFile(it.normalizedPath) }
    val groupDeleted = entry.members.any { deletedPaths.contains(it.normalizedPath) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
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
                    modifier = Modifier
                        .height(72.dp)
                        .width(72.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }

            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "${group.fileCount} files · Total ${formatBytes(group.totalBytes)}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Per-file ${formatBytes(group.sizeBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(6.dp))
                entry.members
                    .sortedBy { it.normalizedPath }
                    .forEach { file ->
                        val date = formatDate(file.lastModifiedMillis)
                        Text(
                            text = "${formatPath(file.normalizedPath, showFullPaths)} · ${date}",
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                val remaining = (group.fileCount - entry.members.size).coerceAtLeast(0)
                if (remaining > 0) {
                    Text(
                        text = "+${remaining} more…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun GroupDetailDb(
    resultsRepo: ResultsDbRepository,
    group: DuplicateGroupEntity,
    deletedPaths: Set<String>,
    onDeleteFile: (suspend (FileMetadata) -> Boolean)?,
    imageLoader: ImageLoader,
    cacheEntry: MembersCacheEntry?
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val selectedFile = remember { mutableStateOf<FileMetadata?>(null) }
    val isSelectAllMode = remember(group.sizeBytes, group.hashHex) { mutableStateOf(false) }
    val selectedPaths = remember(group.sizeBytes, group.hashHex) { mutableStateOf<Set<String>>(emptySet()) }
    val deselectedPathsInSelectAll = remember(group.sizeBytes, group.hashHex) { mutableStateOf<Set<String>>(emptySet()) }
    val confirmBulkDelete = remember(group.sizeBytes, group.hashHex) { mutableStateOf(false) }
    val isBulkDeleting = remember(group.sizeBytes, group.hashHex) { mutableStateOf(false) }
    val bulkDeleteMessage = remember(group.sizeBytes, group.hashHex) { mutableStateOf<String?>(null) }
    val entry = cacheEntry ?: remember(group.sizeBytes, group.hashHex) { MembersCacheEntry() }
    val pageSize = 200
    val cursor = entry.cursor
    val selectionMode = isSelectAllMode.value || selectedPaths.value.isNotEmpty()
    val allSelectedAcrossGroup = isSelectAllMode.value && deselectedPathsInSelectAll.value.isEmpty()

    fun loadMore(reset: Boolean) {
        if (entry.isLoading.value) return
        if (!reset && entry.isComplete.value) return
        scope.launch {
            entry.isLoading.value = true
            try {
                if (reset) {
                    cursor.value = null
                    entry.members.clear()
                    entry.isComplete.value = false
                    isSelectAllMode.value = false
                    selectedPaths.value = emptySet()
                    deselectedPathsInSelectAll.value = emptySet()
                }
                val next = withContext(Dispatchers.IO) {
                    resultsRepo.listGroupMembers(
                        sizeBytes = group.sizeBytes,
                        hashHex = group.hashHex,
                        afterPath = cursor.value,
                        limit = pageSize
                    )
                }
                if (next.isNotEmpty()) {
                    entry.members.addAll(next)
                    cursor.value = next.last().normalizedPath
                }
                // Mark complete if we reached the known count or got a short/empty page.
                if (entry.members.size >= group.fileCount || next.size < pageSize) {
                    entry.isComplete.value = true
                }
            } finally {
                entry.isLoading.value = false
            }
        }
    }

    LaunchedEffect(group.sizeBytes, group.hashHex) {
        // If we already have members cached, don't re-query on every open.
        if (entry.members.isEmpty() && !entry.isLoading.value) {
            loadMore(reset = true)
        }
    }
    LaunchedEffect(selectionMode) {
        if (selectionMode) {
            selectedFile.value = null
        }
    }
    LaunchedEffect(entry.members.size) {
        if (isSelectAllMode.value) return@LaunchedEffect
        val filtered = filterSelectionToLoadedMembers(
            selectedPaths = selectedPaths.value,
            members = entry.members
        )
        if (filtered != selectedPaths.value) {
            selectedPaths.value = filtered
        }
    }

    val preview = entry.members.firstOrNull { isMediaFile(it.normalizedPath) }
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
    Text("${group.fileCount} files · Total ${formatBytes(group.totalBytes)}")
    Text("Per-file ${formatBytesWithExact(group.sizeBytes)}")
    Spacer(modifier = Modifier.height(8.dp))

    if (selectionMode) {
        Text(
            text = selectionStatusText(
                totalCount = group.fileCount,
                selectAllMode = isSelectAllMode.value,
                selectedPaths = selectedPaths.value,
                deselectedPaths = deselectedPathsInSelectAll.value
            ),
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
                    if (allSelectedAcrossGroup) {
                        isSelectAllMode.value = false
                        selectedPaths.value = emptySet()
                        deselectedPathsInSelectAll.value = emptySet()
                    } else {
                        isSelectAllMode.value = true
                        selectedPaths.value = emptySet()
                        deselectedPathsInSelectAll.value = emptySet()
                    }
                    bulkDeleteMessage.value = null
                },
                enabled = !isBulkDeleting.value
            ) {
                Text(if (allSelectedAcrossGroup) "Deselect all" else "Select all")
            }
            OutlinedButton(
                onClick = { confirmBulkDelete.value = true },
                enabled = onDeleteFile != null && !isBulkDeleting.value
            ) {
                Text(if (isBulkDeleting.value) "Deleting..." else "Delete selected")
            }
        }
        if (isSelectAllMode.value && !entry.isComplete.value) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Select all includes not-loaded files in delete queries.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
    }

    bulkDeleteMessage.value?.let { message ->
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
    }

    entry.members
        .sortedBy { it.normalizedPath }
        .forEach { file ->
            val date = formatDate(file.lastModifiedMillis)
            val isDeleted = deletedPaths.contains(file.normalizedPath)
            val isSelected = isPathSelectedForMode(
                path = file.normalizedPath,
                selectAllMode = isSelectAllMode.value,
                selectedPaths = selectedPaths.value,
                deselectedPaths = deselectedPathsInSelectAll.value
            )
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = {
                            if (selectionMode) {
                                if (isSelectAllMode.value) {
                                    deselectedPathsInSelectAll.value = togglePathSelection(
                                        selectedPaths = deselectedPathsInSelectAll.value,
                                        path = file.normalizedPath
                                    )
                                } else {
                                    selectedPaths.value = togglePathSelection(
                                        selectedPaths = selectedPaths.value,
                                        path = file.normalizedPath
                                    )
                                }
                                bulkDeleteMessage.value = null
                            } else {
                                selectedFile.value = file
                            }
                        },
                        onLongClick = {
                            if (isSelectAllMode.value) {
                                deselectedPathsInSelectAll.value = togglePathSelection(
                                    selectedPaths = deselectedPathsInSelectAll.value,
                                    path = file.normalizedPath
                                )
                            } else {
                                selectedPaths.value = togglePathSelection(
                                    selectedPaths = selectedPaths.value,
                                    path = file.normalizedPath
                                )
                            }
                            bulkDeleteMessage.value = null
                        }
                    ),
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
                    if (selectionMode) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = {
                                if (isSelectAllMode.value) {
                                    deselectedPathsInSelectAll.value = togglePathSelection(
                                        selectedPaths = deselectedPathsInSelectAll.value,
                                        path = file.normalizedPath
                                    )
                                } else {
                                    selectedPaths.value = togglePathSelection(
                                        selectedPaths = selectedPaths.value,
                                        path = file.normalizedPath
                                    )
                                }
                                bulkDeleteMessage.value = null
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
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

    OutlinedButton(
        onClick = { loadMore(reset = false) },
        enabled = !entry.isLoading.value && !entry.isComplete.value,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            when {
                entry.isComplete.value -> "All loaded"
                entry.isLoading.value -> "Loading…"
                else -> "Load more"
            }
        )
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

    if (confirmBulkDelete.value) {
        val immediateTargets = if (isSelectAllMode.value) {
            emptyList()
        } else {
            selectedFilesForDelete(
                members = entry.members,
                selectedPaths = selectedPaths.value,
                deletedPaths = deletedPaths
            )
        }
        val selectedCountLabel = if (isSelectAllMode.value) {
            countSelectedForDisplay(
                totalCount = group.fileCount,
                selectAllMode = true,
                selectedPaths = emptySet(),
                deselectedPaths = deselectedPathsInSelectAll.value
            )
        } else {
            immediateTargets.size
        }
        AlertDialog(
            onDismissRequest = {
                if (!isBulkDeleting.value) {
                    confirmBulkDelete.value = false
                }
            },
            title = { Text("Delete selected files?") },
            text = {
                if (selectedCountLabel <= 0) {
                    Text("No deletable files are selected.")
                } else if (isSelectAllMode.value) {
                    if (deselectedPathsInSelectAll.value.isEmpty()) {
                        Text("Select all is active. $selectedCountLabel files will be deleted, including not-loaded files.")
                    } else {
                        Text("Select all is active with ${deselectedPathsInSelectAll.value.size} exclusions. $selectedCountLabel files will be deleted.")
                    }
                } else {
                    Text("$selectedCountLabel selected files will be deleted (moved to app trash).")
                }
            },
            confirmButton = {
                OutlinedButton(
                    onClick = {
                        val handler = onDeleteFile ?: return@OutlinedButton
                        val selectAllSnapshot = isSelectAllMode.value
                        val selectedPathsSnapshot = selectedPaths.value
                        val excludedFromAllSnapshot = deselectedPathsInSelectAll.value
                        val deletedPathsSnapshot = deletedPaths

                        isBulkDeleting.value = true
                        bulkDeleteMessage.value = null
                        scope.launch {
                            val outcome = if (selectAllSnapshot) {
                                val pageSizeForBulk = 500
                                var afterPath: String? = null
                                var successCount = 0
                                val failedPaths = linkedSetOf<String>()

                                while (true) {
                                    val page = withContext(Dispatchers.IO) {
                                        resultsRepo.listGroupMembers(
                                            sizeBytes = group.sizeBytes,
                                            hashHex = group.hashHex,
                                            afterPath = afterPath,
                                            limit = pageSizeForBulk
                                        )
                                    }
                                    if (page.isEmpty()) break
                                    page.forEach { file ->
                                        val path = file.normalizedPath
                                        val isSelectedInAll = !excludedFromAllSnapshot.contains(path)
                                        if (!isSelectedInAll || deletedPathsSnapshot.contains(path)) return@forEach

                                        val deleted = runCatching { handler(file) }.getOrDefault(false)
                                        if (deleted) {
                                            successCount += 1
                                        } else {
                                            failedPaths.add(path)
                                        }
                                    }
                                    if (page.size < pageSizeForBulk) break
                                    afterPath = page.last().normalizedPath
                                }
                                BulkDeleteOutcome(
                                    successCount = successCount,
                                    failedPaths = failedPaths
                                )
                            } else {
                                val targets = selectedFilesForDelete(
                                    members = entry.members,
                                    selectedPaths = selectedPathsSnapshot,
                                    deletedPaths = deletedPathsSnapshot
                                )
                                var successCount = 0
                                val failedPaths = linkedSetOf<String>()
                                targets.forEach { file ->
                                    val deleted = runCatching { handler(file) }.getOrDefault(false)
                                    if (deleted) {
                                        successCount += 1
                                    } else {
                                        failedPaths.add(file.normalizedPath)
                                    }
                                }
                                BulkDeleteOutcome(
                                    successCount = successCount,
                                    failedPaths = failedPaths
                                )
                            }

                            isSelectAllMode.value = false
                            deselectedPathsInSelectAll.value = emptySet()
                            selectedPaths.value = outcome.failedPaths

                            bulkDeleteMessage.value = when {
                                outcome.successCount == 0 && outcome.failedPaths.isEmpty() -> "No files deleted."
                                outcome.failedPaths.isEmpty() -> "${outcome.successCount} files deleted."
                                outcome.successCount == 0 -> "Delete failed for ${outcome.failedPaths.size} files."
                                else -> "${outcome.successCount} deleted, ${outcome.failedPaths.size} failed."
                            }
                            isBulkDeleting.value = false
                            confirmBulkDelete.value = false
                        }
                    },
                    enabled = onDeleteFile != null && !isBulkDeleting.value
                ) {
                    Text(if (isBulkDeleting.value) "Deleting..." else "Delete")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { confirmBulkDelete.value = false },
                    enabled = !isBulkDeleting.value
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

internal fun togglePathSelection(selectedPaths: Set<String>, path: String): Set<String> {
    if (selectedPaths.contains(path)) {
        return selectedPaths - path
    }
    return selectedPaths + path
}

internal fun countSelectedForDisplay(
    totalCount: Int,
    selectAllMode: Boolean,
    selectedPaths: Set<String>,
    deselectedPaths: Set<String>
): Int {
    return if (selectAllMode) {
        (totalCount - deselectedPaths.size).coerceAtLeast(0)
    } else {
        selectedPaths.size
    }
}

internal fun isPathSelectedForMode(
    path: String,
    selectAllMode: Boolean,
    selectedPaths: Set<String>,
    deselectedPaths: Set<String>
): Boolean {
    return if (selectAllMode) {
        !deselectedPaths.contains(path)
    } else {
        selectedPaths.contains(path)
    }
}

internal fun selectionStatusText(
    totalCount: Int,
    selectAllMode: Boolean,
    selectedPaths: Set<String>,
    deselectedPaths: Set<String>
): String {
    val selectedCount = countSelectedForDisplay(
        totalCount = totalCount,
        selectAllMode = selectAllMode,
        selectedPaths = selectedPaths,
        deselectedPaths = deselectedPaths
    )
    return if (!selectAllMode) {
        "$selectedCount selected"
    } else if (deselectedPaths.isEmpty()) {
        "Select all active · $selectedCount selected"
    } else {
        "Select all active · ${deselectedPaths.size} excluded · $selectedCount selected"
    }
}

internal fun filterSelectionToLoadedMembers(
    selectedPaths: Set<String>,
    members: List<FileMetadata>
): Set<String> {
    val loadedPaths = members.mapTo(hashSetOf()) { it.normalizedPath }
    return selectedPaths.filterTo(linkedSetOf()) { loadedPaths.contains(it) }
}

internal fun selectedFilesForDelete(
    members: List<FileMetadata>,
    selectedPaths: Set<String>,
    deletedPaths: Set<String>
): List<FileMetadata> {
    return members.filter { file ->
        selectedPaths.contains(file.normalizedPath) && !deletedPaths.contains(file.normalizedPath)
    }
}
