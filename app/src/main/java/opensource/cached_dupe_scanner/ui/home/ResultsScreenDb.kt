package opensource.cached_dupe_scanner.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

@Composable
fun ResultsScreenDb(
    resultsRepo: ResultsDbRepository,
    settingsStore: AppSettingsStore,
    deletedPaths: Set<String>,
    onDeleteFile: (suspend (FileMetadata) -> Boolean)?,
    onBack: () -> Unit,
    onOpenGroup: ((Int) -> Unit)?,
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
    val pendingSortKey = remember { mutableStateOf(ResultSortKey.Count) }
    val pendingSortDirection = remember { mutableStateOf(SortDirection.Desc) }

    val groups = remember { mutableStateOf<List<DuplicateGroupEntity>>(emptyList()) }
    val fileCount = remember { mutableStateOf(0) }
    val groupCount = remember { mutableStateOf(0) }
    val isLoading = remember { mutableStateOf(false) }

    val pageSize = 50
    val visibleCount = rememberSaveable { mutableStateOf(0) }

    val imageLoader = remember {
        ImageLoader.Builder(context)
            .components { add(VideoFrameDecoder.Factory()) }
            .build()
    }

    fun mapSort(key: ResultSortKey): DuplicateGroupSortKey {
        return when (key) {
            ResultSortKey.Count -> DuplicateGroupSortKey.CountDesc
            ResultSortKey.TotalSize -> DuplicateGroupSortKey.TotalBytesDesc
            ResultSortKey.PerFileSize -> DuplicateGroupSortKey.PerFileSizeDesc
            ResultSortKey.Name -> DuplicateGroupSortKey.CountDesc
        }
    }

    fun refresh(reset: Boolean, rebuild: Boolean) {
        scope.launch {
            isLoading.value = true
            withContext(Dispatchers.IO) {
                if (rebuild) {
                    resultsRepo.rebuildGroups()
                }
                fileCount.value = resultsRepo.countFiles()
                groupCount.value = resultsRepo.countGroups()
            }
            if (reset) {
                groups.value = emptyList()
                visibleCount.value = 0
            }
            isLoading.value = false
        }
    }

    fun loadMoreIfNeeded() {
        if (isLoading.value) return
        val needed = visibleCount.value.coerceAtMost(groupCount.value)
        if (groups.value.size >= needed) return
        val offset = groups.value.size
        val limit = needed - offset
        if (limit <= 0) return
        scope.launch {
            isLoading.value = true
            val next = withContext(Dispatchers.IO) {
                // SortDirection is currently enforced as Desc in SQL; Asc is not supported yet.
                resultsRepo.listGroups(sortKey = mapSort(sortKey.value), offset = offset, limit = limit)
            }
            if (next.isNotEmpty()) {
                groups.value = groups.value + next
            }
            isLoading.value = false
        }
    }

    LaunchedEffect(Unit) {
        refresh(reset = true, rebuild = true)
    }

    LaunchedEffect(sortKey.value) {
        refresh(reset = true, rebuild = false)
    }

    LaunchedEffect(groupCount.value) {
        if (groupCount.value > 0 && visibleCount.value == 0) {
            visibleCount.value = pageSize.coerceAtMost(groupCount.value)
        }
    }

    LaunchedEffect(visibleCount.value) {
        loadMoreIfNeeded()
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
                    OutlinedButton(onClick = { sortDialogOpen.value = true }) {
                        Text("Sort")
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }

            if (groupCount.value == 0 && !isLoading.value) {
                item { Text("No duplicates found.") }
            } else {
                val toShow = groups.value.take(visibleCount.value)
                itemsIndexed(toShow, key = { _, g -> "${g.sizeBytes}:${g.hashHex}" }) { index, g ->
                    DuplicateGroupCardDb(
                        resultsRepo = resultsRepo,
                        group = g,
                        deletedPaths = deletedPaths,
                        showFullPaths = showFullPaths.value,
                        imageLoader = imageLoader,
                        onOpen = {
                            val handler = onOpenGroup ?: return@DuplicateGroupCardDb
                            handler(index)
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (groupCount.value > visibleCount.value) {
                    item {
                        OutlinedButton(
                            onClick = {
                                visibleCount.value = (visibleCount.value + pageSize)
                                    .coerceAtMost(groupCount.value)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Load more")
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
    }

    if (selectedGroupIndex != null) {
        val group = groups.value.getOrNull(selectedGroupIndex)
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
                            imageLoader = imageLoader
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
                    Text(
                        text = "Direction is currently applied as Desc.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                OutlinedButton(
                    onClick = {
                        sortKey.value = pendingSortKey.value
                        sortDirection.value = pendingSortDirection.value
                        settingsStore.setResultSortKey(sortKey.value.name)
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
    onOpen: () -> Unit
) {
    val context = LocalContext.current
    val previewMembers = remember(group.sizeBytes, group.hashHex) { mutableStateOf<List<FileMetadata>>(emptyList()) }

    LaunchedEffect(group.sizeBytes, group.hashHex) {
        val members = withContext(Dispatchers.IO) {
            resultsRepo.listGroupMembers(
                sizeBytes = group.sizeBytes,
                hashHex = group.hashHex,
                afterPath = null,
                limit = 10
            )
        }
        previewMembers.value = members
    }

    val preview = previewMembers.value.firstOrNull { isMediaFile(it.normalizedPath) }
    val groupDeleted = previewMembers.value.any { deletedPaths.contains(it.normalizedPath) }

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
                previewMembers.value
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

                val remaining = (group.fileCount - previewMembers.value.size).coerceAtLeast(0)
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
private fun GroupDetailDb(
    resultsRepo: ResultsDbRepository,
    group: DuplicateGroupEntity,
    deletedPaths: Set<String>,
    onDeleteFile: (suspend (FileMetadata) -> Boolean)?,
    imageLoader: ImageLoader
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val selectedFile = remember { mutableStateOf<FileMetadata?>(null) }
    val members = remember { mutableStateOf<List<FileMetadata>>(emptyList()) }
    val isLoading = remember { mutableStateOf(false) }
    val pageSize = 200
    val cursor = remember { mutableStateOf<String?>(null) }

    fun loadMore(reset: Boolean) {
        if (isLoading.value) return
        scope.launch {
            isLoading.value = true
            if (reset) {
                cursor.value = null
                members.value = emptyList()
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
                members.value = members.value + next
                cursor.value = next.last().normalizedPath
            }
            isLoading.value = false
        }
    }

    LaunchedEffect(group.sizeBytes, group.hashHex) {
        loadMore(reset = true)
    }

    val preview = members.value.firstOrNull { isMediaFile(it.normalizedPath) }
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

    members.value
        .sortedBy { it.normalizedPath }
        .forEach { file ->
            val date = formatDate(file.lastModifiedMillis)
            val isDeleted = deletedPaths.contains(file.normalizedPath)
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

    OutlinedButton(
        onClick = { loadMore(reset = false) },
        enabled = !isLoading.value,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(if (isLoading.value) "Loading…" else "Load more")
    }

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
