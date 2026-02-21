package opensource.cached_dupe_scanner.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
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
import opensource.cached_dupe_scanner.ui.components.TopRightLoadIndicator
import opensource.cached_dupe_scanner.ui.components.VerticalLazyScrollbar
import opensource.cached_dupe_scanner.ui.components.VerticalScrollbar
import opensource.cached_dupe_scanner.ui.components.formatLoadProgressText
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

private data class GroupListScrollAnchor(
    val index: Int,
    val offset: Int,
    val key: String?
)

internal enum class ResultGroupMemberSortKey(val label: String) {
    Path("Path"),
    Modified("Modified")
}

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

    fun captureCurrentGroupListAnchor(): GroupListScrollAnchor {
        val index = listState.firstVisibleItemIndex
        val offset = listState.firstVisibleItemScrollOffset
        val key = groups.value.getOrNull(index)?.let(::groupStableKey)
        return GroupListScrollAnchor(
            index = index,
            offset = offset,
            key = key
        )
    }

    fun refresh(
        reset: Boolean,
        rebuild: Boolean,
        preserveScroll: Boolean = false,
        preservedAnchor: GroupListScrollAnchor? = null
    ) {
        if (isRefreshing.value) return
        val anchorIndex = if (preserveScroll) preservedAnchor?.index ?: listState.firstVisibleItemIndex else 0
        val anchorOffset = if (preserveScroll) preservedAnchor?.offset ?: listState.firstVisibleItemScrollOffset else 0
        val anchorKey = if (preserveScroll) {
            preservedAnchor?.key ?: groups.value.getOrNull(anchorIndex)?.let(::groupStableKey)
        } else {
            null
        }
        if (reset && !preserveScroll) {
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
                val firstPageLimit = if (reset && preserveScroll) {
                    visibleCount.value.coerceAtLeast(pageSize)
                } else {
                    pageSize
                }
                val snapshot = withContext(Dispatchers.IO) {
                    resultsRepo.loadInitialSnapshot(
                        sortKey = sortForQuery,
                        limit = firstPageLimit,
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
                    if (preserveScroll) {
                        val targetIndex = findIndexByGroupKeyOrFallback(
                            groups = snapshot.firstPage,
                            key = anchorKey,
                            fallbackIndex = anchorIndex
                        )
                        topVisibleGroupIndex.value = targetIndex
                        if (snapshot.firstPage.isNotEmpty()) {
                            val targetOffset = if (targetIndex == anchorIndex) anchorOffset.coerceAtLeast(0) else 0
                            listState.scrollToItem(targetIndex, targetOffset)
                        }
                    } else {
                        topVisibleGroupIndex.value = 0
                    }
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
        val current = if (loaded <= 0) 1 else (topVisibleGroupIndex.value + 1).coerceAtLeast(1)
        formatLoadProgressText(
            current = current,
            loaded = loaded,
            total = totalGroups
        )
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
            TopRightLoadIndicator(text = loadIndicatorText)
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
        val detailLoadIndicatorText = run {
            if (group == null || entry == null) {
                null
            } else {
                val total = group.fileCount
                val loaded = entry.members.size.coerceAtMost(total)
                val current = estimateCurrentFromScroll(
                    scrollValue = detailScrollState.value,
                    maxScrollValue = detailScrollState.maxValue,
                    loadedCount = loaded
                )
                formatLoadProgressText(
                    current = current,
                    loaded = loaded,
                    total = total
                )
            }
        }
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
                            onGroupEdited = { _ -> },
                            imageLoader = imageLoader,
                            cacheEntry = entry,
                            detailScrollState = detailScrollState,
                            sortKey = groupMemberSortKey.value,
                            sortDirection = groupMemberSortDirection.value,
                            onApplySort = { key, direction ->
                                groupMemberSortKey.value = key
                                groupMemberSortDirection.value = direction
                                settingsStore.setResultGroupSortKey(key.name)
                                settingsStore.setResultGroupSortDirection(direction.name)
                            }
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
                TopRightLoadIndicator(text = detailLoadIndicatorText)
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
    onGroupEdited: (rebuildSucceeded: Boolean) -> Unit,
    imageLoader: ImageLoader,
    cacheEntry: MembersCacheEntry?,
    detailScrollState: ScrollState,
    sortKey: ResultGroupMemberSortKey,
    sortDirection: SortDirection,
    onApplySort: (ResultGroupMemberSortKey, SortDirection) -> Unit
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
    val groupSortDialogOpen = remember { mutableStateOf(false) }
    val pendingGroupSortKey = remember { mutableStateOf(sortKey) }
    val pendingGroupSortDirection = remember { mutableStateOf(sortDirection) }
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

    suspend fun refreshCurrentGroupNow(): Boolean {
        return runCatching {
            withContext(Dispatchers.IO) {
                resultsRepo.refreshSingleGroup(
                    sizeBytes = group.sizeBytes,
                    hashHex = group.hashHex
                )
            }
        }.isSuccess
    }

    LaunchedEffect(group.sizeBytes, group.hashHex) {
        // Continue loading when entering detail, even if preview members were already cached.
        if (entry.isLoading.value || entry.isComplete.value) return@LaunchedEffect
        loadMore(reset = entry.members.isEmpty())
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
    LaunchedEffect(group.sizeBytes, group.hashHex, detailScrollState) {
        val thresholdPx = 240
        snapshotFlow {
            shouldTriggerDetailAutoLoad(
                scrollValue = detailScrollState.value,
                maxScrollValue = detailScrollState.maxValue,
                thresholdPx = thresholdPx,
                isLoading = entry.isLoading.value,
                isComplete = entry.isComplete.value
            )
        }
            .distinctUntilChanged()
            .filter { it }
            .collect {
                loadMore(reset = false)
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
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text("${group.fileCount} files · Total ${formatBytes(group.totalBytes)}")
            Text("Per-file ${formatBytesWithExact(group.sizeBytes)}")
        }
        OutlinedButton(
            onClick = {
                pendingGroupSortKey.value = sortKey
                pendingGroupSortDirection.value = sortDirection
                groupSortDialogOpen.value = true
            }
        ) {
            Text("Sort")
        }
    }
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

    val sortedMembers = sortGroupMembers(
        members = entry.members,
        sortKey = sortKey,
        direction = sortDirection
    )

    sortedMembers.forEach { file ->
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
                    val deleted = handler(file)
                    if (deleted) {
                        val refreshed = refreshCurrentGroupNow()
                        onGroupEdited(refreshed)
                    }
                    deleted
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
                            if (outcome.successCount > 0) {
                                val refreshed = refreshCurrentGroupNow()
                                onGroupEdited(refreshed)
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

    if (groupSortDialogOpen.value) {
        AlertDialog(
            onDismissRequest = { groupSortDialogOpen.value = false },
            title = { Text("Group sort options") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Sort by")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = pendingGroupSortKey.value == ResultGroupMemberSortKey.Path,
                            onClick = { pendingGroupSortKey.value = ResultGroupMemberSortKey.Path }
                        )
                        Text(ResultGroupMemberSortKey.Path.label)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = pendingGroupSortKey.value == ResultGroupMemberSortKey.Modified,
                            onClick = { pendingGroupSortKey.value = ResultGroupMemberSortKey.Modified }
                        )
                        Text(ResultGroupMemberSortKey.Modified.label)
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Direction")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = pendingGroupSortDirection.value == SortDirection.Asc,
                            onClick = { pendingGroupSortDirection.value = SortDirection.Asc }
                        )
                        Text(SortDirection.Asc.label)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = pendingGroupSortDirection.value == SortDirection.Desc,
                            onClick = { pendingGroupSortDirection.value = SortDirection.Desc }
                        )
                        Text(SortDirection.Desc.label)
                    }
                }
            },
            confirmButton = {
                OutlinedButton(
                    onClick = {
                        onApplySort(
                            pendingGroupSortKey.value,
                            pendingGroupSortDirection.value
                        )
                        groupSortDialogOpen.value = false
                    }
                ) {
                    Text("Apply")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { groupSortDialogOpen.value = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

internal fun sortGroupMembers(
    members: List<FileMetadata>,
    sortKey: ResultGroupMemberSortKey,
    direction: SortDirection
): List<FileMetadata> {
    val comparator = when (sortKey) {
        ResultGroupMemberSortKey.Path -> {
            compareBy<FileMetadata> { it.normalizedPath }
        }
        ResultGroupMemberSortKey.Modified -> {
            compareBy<FileMetadata> { it.lastModifiedMillis }
                .thenBy { it.normalizedPath }
        }
    }
    return if (direction == SortDirection.Asc) {
        members.sortedWith(comparator)
    } else {
        members.sortedWith(comparator.reversed())
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

internal fun estimateCurrentFromScroll(
    scrollValue: Int,
    maxScrollValue: Int,
    loadedCount: Int
): Int {
    if (loadedCount <= 0) return 1
    val safeLoaded = loadedCount.coerceAtLeast(1)
    if (maxScrollValue <= 0) return 1
    val ratio = scrollValue.toDouble() / maxScrollValue.toDouble()
    return (1 + (ratio * (safeLoaded - 1)).toInt()).coerceIn(1, safeLoaded)
}

internal fun shouldTriggerDetailAutoLoad(
    scrollValue: Int,
    maxScrollValue: Int,
    thresholdPx: Int,
    isLoading: Boolean,
    isComplete: Boolean
): Boolean {
    if (isLoading || isComplete) return false
    val remaining = (maxScrollValue - scrollValue).coerceAtLeast(0)
    return remaining <= thresholdPx.coerceAtLeast(0)
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

internal fun groupStableKey(group: DuplicateGroupEntity): String {
    return "${group.sizeBytes}:${group.hashHex}"
}

internal fun findIndexByGroupKeyOrFallback(
    groups: List<DuplicateGroupEntity>,
    key: String?,
    fallbackIndex: Int
): Int {
    if (groups.isEmpty()) return 0
    if (key != null) {
        val indexByKey = groups.indexOfFirst { groupStableKey(it) == key }
        if (indexByKey >= 0) return indexByKey
    }
    return fallbackIndex.coerceIn(0, groups.lastIndex)
}
