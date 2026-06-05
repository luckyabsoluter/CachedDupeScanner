package opensource.cached_dupe_scanner.ui.home

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.decode.VideoFrameDecoder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import opensource.cached_dupe_scanner.cache.SimilarityClusterEntity
import opensource.cached_dupe_scanner.cache.SimilaritySettingEntity
import opensource.cached_dupe_scanner.core.FileMetadata
import opensource.cached_dupe_scanner.core.SIMILARITY_METHOD_DURATION_NEIGHBOR_LIST
import opensource.cached_dupe_scanner.core.SIMILARITY_METHOD_DURATION_TOLERANCE
import opensource.cached_dupe_scanner.core.SIMILARITY_METHOD_EXACT_THUMBNAIL
import opensource.cached_dupe_scanner.core.SimilarityMediaScope
import opensource.cached_dupe_scanner.core.SortDirection
import opensource.cached_dupe_scanner.core.durationNeighborListStepFromParams
import opensource.cached_dupe_scanner.core.durationToleranceStepFromParams
import opensource.cached_dupe_scanner.core.exactThumbnailStepFromParams
import opensource.cached_dupe_scanner.core.similarityMethodLabel
import opensource.cached_dupe_scanner.notifications.TaskNotificationController
import opensource.cached_dupe_scanner.storage.SimilarityClusterMember
import opensource.cached_dupe_scanner.storage.SimilaritySettingsRepository
import opensource.cached_dupe_scanner.tasks.TaskArea
import opensource.cached_dupe_scanner.tasks.TaskCoordinator
import opensource.cached_dupe_scanner.ui.components.AppTopBar
import opensource.cached_dupe_scanner.ui.components.ConfirmationDialog
import opensource.cached_dupe_scanner.ui.components.ConfirmationDialogButtonStyle
import opensource.cached_dupe_scanner.ui.components.ScreenScrollColumn
import opensource.cached_dupe_scanner.ui.home.similarity.SimilaritySizeUnit
import opensource.cached_dupe_scanner.ui.home.similarity.SimilarityTimeUnit
import opensource.cached_dupe_scanner.ui.home.similarity.parsedDurationNeighborListStep
import opensource.cached_dupe_scanner.ui.home.similarity.parsedDurationToleranceStep
import opensource.cached_dupe_scanner.ui.home.similarity.parsedExactThumbnailStep
import opensource.cached_dupe_scanner.ui.home.similarity.parsedFrameSeconds
import opensource.cached_dupe_scanner.ui.home.similarity.parsedMinSizeBytes
import opensource.cached_dupe_scanner.ui.home.similarity.sanitizeFrameSecondsInput
import opensource.cached_dupe_scanner.ui.home.similarity.sanitizeNumberDraftInput
import opensource.cached_dupe_scanner.ui.home.similarity.startSimilarityMaintenanceTask

private const val SIMILARITY_CLUSTER_PREVIEW_MEMBER_LIMIT = 4
private const val SIMILARITY_CLUSTER_DETAIL_MEMBER_PAGE_SIZE = 100
private const val SIMILARITY_CLUSTER_DETAIL_AUTO_LOAD_THRESHOLD_ITEMS = 3

@Composable
fun SimilaritySettingsScreen(
    repository: SimilaritySettingsRepository,
    refreshVersion: Int,
    onBack: () -> Unit,
    onCreateSetting: () -> Unit,
    onOpenMaintenance: () -> Unit,
    onOpenSetting: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val settings = remember { mutableStateListOf<SimilaritySettingEntity>() }
    val clustersBySetting = remember { mutableStateMapOf<Long, List<SimilarityClusterEntity>>() }

    fun refresh() {
        scope.launch {
            val loaded = withContext(Dispatchers.IO) { repository.listSettings() }
            val clusterRows = withContext(Dispatchers.IO) {
                loaded.associate { setting ->
                    setting.settingId to repository.listClusters(setting.settingId)
                }
            }
            settings.clear()
            settings.addAll(loaded)
            clustersBySetting.clear()
            clustersBySetting.putAll(clusterRows)
        }
    }

    LaunchedEffect(refreshVersion) {
        refresh()
    }

    ScreenScrollColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "top_bar") {
            AppTopBar(
                title = "Similarity settings",
                onBack = onBack
            )
        }
        item(key = "create_setting") {
            Button(
                onClick = onCreateSetting,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("New setting")
            }
        }
        item(key = "maintenance") {
            OutlinedButton(
                onClick = onOpenMaintenance,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Maintenance")
            }
        }
        item(key = "settings_header") {
            SimilaritySettingsHeader(hasSettings = settings.isNotEmpty())
        }
        settings.forEach { setting ->
            item(key = "setting:${setting.settingId}") {
                SimilaritySettingListCard(
                    setting = setting,
                    clusterCount = clustersBySetting[setting.settingId].orEmpty().size,
                    fileCount = clustersBySetting[setting.settingId].orEmpty().sumOf { cluster -> cluster.fileCount },
                    onOpen = { onOpenSetting(setting.settingId) }
                )
            }
        }
    }
}

@Composable
fun SimilarityMaintenanceScreen(
    repository: SimilaritySettingsRepository,
    appScope: CoroutineScope,
    taskCoordinator: TaskCoordinator,
    notificationController: TaskNotificationController,
    onChanged: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var statusText by remember { mutableStateOf("No similarity maintenance running.") }
    var confirmClearAll by remember { mutableStateOf(false) }
    val activeTask = taskCoordinator.activeTask(TaskArea.Similarity)
    val displayedStatus = activeTask?.detail ?: statusText
    fun runMaintenance(rebuild: Boolean) {
        val started = startSimilarityMaintenanceTask(
            repository = repository,
            settingId = null,
            rebuild = rebuild,
            scope = appScope,
            taskCoordinator = taskCoordinator,
            notificationController = notificationController,
            onStatusText = { status -> statusText = status },
            onFinished = { onChanged() }
        )
        if (!started) {
            statusText = "Similarity maintenance is already running."
        }
    }
    fun clearAllResults() {
        confirmClearAll = false
        appScope.launch {
            withContext(Dispatchers.IO) { repository.clearAllResults() }
            statusText = "All generated similarity data was cleared."
            onChanged()
        }
    }

    ScreenScrollColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "top_bar") {
            AppTopBar(
                title = "Similarity maintenance",
                onBack = onBack
            )
        }
        item(key = "maintenance") {
            SimilarityMaintenanceCard(
                statusText = displayedStatus,
                running = activeTask != null,
                onRunEnabled = { runMaintenance(rebuild = false) },
                onRebuildEnabled = { runMaintenance(rebuild = true) },
                onClearAll = { confirmClearAll = true }
            )
        }
    }
    if (confirmClearAll) {
        ConfirmationDialog(
            title = "Clear all similarity data?",
            text = "Generated similarity groups, member links, and method features will be removed. Configured settings stay available.",
            confirmText = "Clear",
            onConfirm = ::clearAllResults,
            onDismissRequest = { confirmClearAll = false },
            confirmEnabled = activeTask == null,
            confirmStyle = ConfirmationDialogButtonStyle.Outlined
        )
    }
}

@Composable
fun SimilaritySettingCreateScreen(
    onBack: () -> Unit,
    onOpenExactThumbnail: () -> Unit,
    onOpenDurationTolerance: () -> Unit,
    onOpenDurationNeighbor: () -> Unit,
    modifier: Modifier = Modifier
) {
    ScreenScrollColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "top_bar") {
            AppTopBar(
                title = "New similarity setting",
                onBack = onBack
            )
        }
        item(key = "create_header") {
            Text(
                text = "Choose setting type",
                style = MaterialTheme.typography.titleMedium
            )
        }
        item(key = "method_exact") {
            SimilarityMethodCard(
                title = "Exact thumbnail",
                description = "Groups video or image candidates by the same configured reduced thumbnail signature.",
                onOpen = onOpenExactThumbnail
            )
        }
        item(key = "method_duration") {
            SimilarityMethodCard(
                title = "Duration tolerance",
                description = "Groups video candidates whose durations fit within one tolerance window.",
                onOpen = onOpenDurationTolerance
            )
        }
        item(key = "method_neighbor") {
            SimilarityMethodCard(
                title = "Duration neighbor list",
                description = "Builds a duration-sorted video list and keeps neighbors inside the configured tolerance.",
                onOpen = onOpenDurationNeighbor
            )
        }
    }
}

@Composable
fun SimilarityExactThumbnailSettingScreen(
    repository: SimilaritySettingsRepository,
    onChanged: () -> Unit,
    onCreated: (Long) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var statusText by remember { mutableStateOf("Ready to create setting.") }
    val draft = rememberSimilaritySettingDraftState()
    val exactStep = parsedExactThumbnailStep(
        frameSecondsInput = draft.frameSecondsInput,
        resizeWidthInput = draft.resizeWidthInput,
        resizeHeightInput = draft.resizeHeightInput,
        quantizationEnabled = draft.quantizationEnabled,
        quantizationInput = draft.quantizationInput,
        grayscale = draft.grayscale
    )
    fun createExact() {
        scope.launch {
            val created = withContext(Dispatchers.IO) {
                repository.createExactThumbnailSetting(
                    mediaScope = draft.mediaScope,
                    minSizeBytes = parsedMinSizeBytes(draft.minSizeInput, draft.minSizeUnit),
                    step = exactStep
                )
            }
            onChanged()
            onCreated(created.settingId)
        }
    }

    ScreenScrollColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "top_bar") {
            AppTopBar(
                title = "Exact thumbnail setting",
                onBack = onBack
            )
        }
        item(key = "exact_form") {
            ExactThumbnailSettingForm(
                minSizeInput = draft.minSizeInput,
                onMinSizeInputChange = { draft.minSizeInput = sanitizeNumberDraftInput(it) },
                minSizeUnit = draft.minSizeUnit,
                onMinSizeUnitChange = { draft.minSizeUnit = it },
                mediaScope = draft.mediaScope,
                onMediaScopeChange = { draft.mediaScope = it },
                frameSecondsInput = draft.frameSecondsInput,
                onFrameSecondsInputChange = { draft.frameSecondsInput = sanitizeFrameSecondsInput(it) },
                resizeWidthInput = draft.resizeWidthInput,
                onResizeWidthInputChange = { draft.resizeWidthInput = sanitizeNumberDraftInput(it) },
                resizeHeightInput = draft.resizeHeightInput,
                onResizeHeightInputChange = { draft.resizeHeightInput = sanitizeNumberDraftInput(it) },
                quantizationEnabled = draft.quantizationEnabled,
                onQuantizationEnabledChange = { draft.quantizationEnabled = it },
                quantizationInput = draft.quantizationInput,
                onQuantizationInputChange = { draft.quantizationInput = sanitizeNumberDraftInput(it) },
                grayscale = draft.grayscale,
                onGrayscaleChange = { draft.grayscale = it },
                statusText = statusText,
                onCreate = ::createExact
            )
        }
    }
}

@Composable
fun SimilarityDurationSettingScreen(
    repository: SimilaritySettingsRepository,
    neighborList: Boolean,
    onChanged: () -> Unit,
    onCreated: (Long) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var statusText by remember { mutableStateOf("Ready to create setting.") }
    val draft = rememberSimilaritySettingDraftState()
    fun createDuration() {
        scope.launch {
            val created = withContext(Dispatchers.IO) {
                if (neighborList) {
                    repository.createDurationNeighborListSetting(
                        minSizeBytes = parsedMinSizeBytes(draft.minSizeInput, draft.minSizeUnit),
                        step = parsedDurationNeighborListStep(draft.durationToleranceInput, draft.durationToleranceUnit)
                    )
                } else {
                    repository.createDurationToleranceSetting(
                        minSizeBytes = parsedMinSizeBytes(draft.minSizeInput, draft.minSizeUnit),
                        step = parsedDurationToleranceStep(draft.durationToleranceInput, draft.durationToleranceUnit)
                    )
                }
            }
            onChanged()
            onCreated(created.settingId)
        }
    }
    val title = if (neighborList) "Duration neighbor setting" else "Duration tolerance setting"
    val description = if (neighborList) {
        "Tolerance is the maximum duration gap between adjacent sorted videos. Isolated videos are omitted."
    } else {
        "Tolerance is the maximum duration gap inside one group. Use 0 for exact millisecond duration matches."
    }
    val buttonText = if (neighborList) "Create duration neighbor setting" else "Create duration tolerance setting"

    ScreenScrollColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "top_bar") {
            AppTopBar(
                title = title,
                onBack = onBack
            )
        }
        item(key = "duration_form") {
            DurationSettingForm(
                description = description,
                buttonText = buttonText,
                minSizeInput = draft.minSizeInput,
                onMinSizeInputChange = { draft.minSizeInput = sanitizeNumberDraftInput(it) },
                minSizeUnit = draft.minSizeUnit,
                onMinSizeUnitChange = { draft.minSizeUnit = it },
                toleranceInput = draft.durationToleranceInput,
                onToleranceInputChange = { draft.durationToleranceInput = sanitizeNumberDraftInput(it) },
                toleranceUnit = draft.durationToleranceUnit,
                onToleranceUnitChange = { draft.durationToleranceUnit = it },
                statusText = statusText,
                onCreate = ::createDuration
            )
        }
    }
}

@Composable
fun SimilaritySettingDetailScreen(
    repository: SimilaritySettingsRepository,
    appScope: CoroutineScope,
    taskCoordinator: TaskCoordinator,
    notificationController: TaskNotificationController,
    keepLoadedThumbnailsInMemory: Boolean,
    thumbnailSizeScale: Float,
    rememberedPreviewCache: MutableMap<String, ImageBitmap>,
    showFullPaths: Boolean,
    settingId: Long,
    refreshVersion: Int,
    onChanged: () -> Unit,
    onBack: () -> Unit,
    onOpenCluster: (Long, Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val imageLoader = rememberSimilarityImageLoader(context)
    val previewThumbnailSize = 72.dp * thumbnailSizeScale.coerceAtLeast(0f)
    var setting by remember { mutableStateOf<SimilaritySettingEntity?>(null) }
    val clusters = remember { mutableStateListOf<SimilarityClusterEntity>() }
    var statusText by remember { mutableStateOf("No similarity maintenance running.") }
    var confirmClearSetting by remember { mutableStateOf(false) }
    val activeTask = taskCoordinator.activeTask(TaskArea.Similarity)
    val displayedStatus = activeTask?.detail ?: statusText
    fun refresh() {
        scope.launch {
            val loadedSetting = withContext(Dispatchers.IO) {
                repository.listSettings().firstOrNull { candidate -> candidate.settingId == settingId }
            }
            val loadedClusters = withContext(Dispatchers.IO) { repository.listClusters(settingId) }
            setting = loadedSetting
            clusters.clear()
            clusters.addAll(loadedClusters)
        }
    }
    fun runMaintenance(rebuild: Boolean) {
        val started = startSimilarityMaintenanceTask(
            repository = repository,
            settingId = settingId,
            rebuild = rebuild,
            scope = appScope,
            taskCoordinator = taskCoordinator,
            notificationController = notificationController,
            onStatusText = { status -> statusText = status },
            onFinished = {
                onChanged()
                refresh()
            }
        )
        if (!started) {
            statusText = "Similarity maintenance is already running."
        }
    }
    fun clearSettingResults() {
        confirmClearSetting = false
        scope.launch {
            withContext(Dispatchers.IO) {
                repository.clearSettingResults(settingId)
            }
            statusText = "Generated similarity data was cleared for this setting."
            onChanged()
            refresh()
        }
    }

    LaunchedEffect(settingId, refreshVersion) {
        refresh()
    }

    ScreenScrollColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "top_bar") {
            AppTopBar(
                title = setting?.displayName ?: "Similarity setting",
                onBack = onBack
            )
        }
        val selectedSetting = setting
        if (selectedSetting == null) {
            item(key = "missing_setting") {
                MissingSelectionCard(
                    message = "This similarity setting is no longer available.",
                    onBack = onBack
                )
            }
        } else {
            item(key = "setting_detail") {
                SimilaritySettingDetailCard(
                    setting = selectedSetting,
                    clusterCount = clusters.size,
                    fileCount = clusters.sumOf { cluster -> cluster.fileCount },
                    statusText = displayedStatus,
                    running = activeTask != null,
                    onToggle = { enabled ->
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                repository.setEnabled(settingId, enabled)
                            }
                            onChanged()
                            refresh()
                        }
                    },
                    onRun = { runMaintenance(rebuild = false) },
                    onRebuild = { runMaintenance(rebuild = true) },
                    onClear = { confirmClearSetting = true }
                )
            }
            item(key = "cluster_header") {
                SimilarityGroupsHeader(
                    clusterCount = clusters.size,
                    fileCount = clusters.sumOf { cluster -> cluster.fileCount }
                )
            }
            if (clusters.isEmpty()) {
                item(key = "clusters_empty") {
                    Text(
                        text = "No similarity groups found for this setting.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            clusters.forEach { cluster ->
                item(key = "cluster:${cluster.clusterId}") {
                    SimilarityClusterListCard(
                        repository = repository,
                        cluster = cluster,
                        imageLoader = imageLoader,
                        rememberedPreviewCache = rememberedPreviewCache,
                        keepLoadedThumbnailsInMemory = keepLoadedThumbnailsInMemory,
                        previewThumbnailSize = previewThumbnailSize,
                        showFullPaths = showFullPaths,
                        onOpenCluster = { onOpenCluster(settingId, cluster.clusterId) }
                    )
                }
            }
        }
    }
    if (confirmClearSetting) {
        ConfirmationDialog(
            title = "Clear this setting's results?",
            text = "Generated groups and member links for this setting will be removed. The setting itself remains.",
            confirmText = "Clear",
            onConfirm = ::clearSettingResults,
            onDismissRequest = { confirmClearSetting = false },
            confirmEnabled = activeTask == null,
            confirmStyle = ConfirmationDialogButtonStyle.Outlined
        )
    }
}

@Composable
fun SimilarityClusterDetailScreen(
    repository: SimilaritySettingsRepository,
    keepLoadedThumbnailsInMemory: Boolean,
    thumbnailSizeScale: Float,
    rememberedPreviewCache: MutableMap<String, ImageBitmap>,
    showFullPaths: Boolean,
    deletedPaths: Set<String>,
    onDeleteFile: (suspend (FileMetadata) -> Boolean)?,
    settingId: Long,
    clusterId: Long,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val imageLoader = rememberSimilarityImageLoader(context)
    val memberThumbnailSize = 64.dp * thumbnailSizeScale.coerceAtLeast(0f)
    val memberListState = rememberLazyListState()
    var setting by remember { mutableStateOf<SimilaritySettingEntity?>(null) }
    var cluster by remember { mutableStateOf<SimilarityClusterEntity?>(null) }
    val members = remember { mutableStateListOf<SimilarityClusterMember>() }
    var memberLoading by remember { mutableStateOf(false) }
    var memberOffset by remember { mutableStateOf(0) }
    var membersExhausted by remember { mutableStateOf(false) }
    var selectedFile by remember { mutableStateOf<FileMetadata?>(null) }
    var memberSortKey by remember { mutableStateOf(ResultGroupMemberSortKey.Path) }
    var memberSortDirection by remember { mutableStateOf(SortDirection.Asc) }

    fun loadMoreMembers() {
        if (memberLoading || membersExhausted) return
        memberLoading = true
        scope.launch {
            val nextMembers = withContext(Dispatchers.IO) {
                repository.listClusterMembersPage(
                    clusterId = clusterId,
                    offset = memberOffset,
                    limit = SIMILARITY_CLUSTER_DETAIL_MEMBER_PAGE_SIZE
                )
            }
            members.addAll(nextMembers)
            memberOffset += nextMembers.size
            membersExhausted = nextMembers.size < SIMILARITY_CLUSTER_DETAIL_MEMBER_PAGE_SIZE ||
                memberOffset >= (cluster?.fileCount ?: Int.MAX_VALUE)
            memberLoading = false
        }
    }

    LaunchedEffect(settingId, clusterId) {
        memberLoading = true
        setting = null
        cluster = null
        members.clear()
        memberOffset = 0
        membersExhausted = false
        val loadedSetting = withContext(Dispatchers.IO) {
            repository.listSettings().firstOrNull { candidate -> candidate.settingId == settingId }
        }
        val loadedCluster = withContext(Dispatchers.IO) {
            repository.listClusters(settingId).firstOrNull { candidate -> candidate.clusterId == clusterId }
        }
        val firstMembers = withContext(Dispatchers.IO) {
            repository.listClusterMembersPage(
                clusterId = clusterId,
                offset = 0,
                limit = SIMILARITY_CLUSTER_DETAIL_MEMBER_PAGE_SIZE
            )
        }
        setting = loadedSetting
        cluster = loadedCluster
        members.clear()
        members.addAll(firstMembers)
        memberOffset = firstMembers.size
        membersExhausted = firstMembers.size < SIMILARITY_CLUSTER_DETAIL_MEMBER_PAGE_SIZE ||
            firstMembers.size >= (loadedCluster?.fileCount ?: Int.MAX_VALUE)
        memberLoading = false
    }

    LaunchedEffect(clusterId, memberListState) {
        snapshotFlow {
            val layoutInfo = memberListState.layoutInfo
            if (cluster == null) {
                false
            } else {
                shouldTriggerSimilarityMemberAutoLoad(
                    lastVisibleItemIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1,
                    totalItemsCount = layoutInfo.totalItemsCount,
                    thresholdItems = SIMILARITY_CLUSTER_DETAIL_AUTO_LOAD_THRESHOLD_ITEMS,
                    isLoading = memberLoading,
                    isComplete = membersExhausted
                )
            }
        }
            .distinctUntilChanged()
            .filter { it }
            .collect {
                loadMoreMembers()
            }
    }

    val displayedMembers = sortGroupMembers(
        members = members.map { member -> member.metadata },
        sortKey = memberSortKey,
        direction = memberSortDirection
    )

    ScreenScrollColumn(
        modifier = modifier,
        listState = memberListState,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "top_bar") {
            AppTopBar(
                title = "Similarity group",
                onBack = onBack
            )
        }
        val selectedSetting = setting
        val selectedCluster = cluster
        if (selectedSetting == null || selectedCluster == null) {
            item(key = "missing_cluster") {
                MissingSelectionCard(
                    message = "This similarity group is no longer available.",
                    onBack = onBack
                )
            }
        } else {
            item(key = "cluster_summary") {
                SimilarityClusterSummaryCard(
                    setting = selectedSetting,
                    cluster = selectedCluster
                )
            }
            item(key = "members_header") {
                SimilarityMembersHeader(
                    loadedCount = members.size,
                    totalCount = selectedCluster.fileCount,
                    loading = memberLoading,
                    sortKey = memberSortKey,
                    sortDirection = memberSortDirection,
                    onApplySort = { key, direction ->
                        memberSortKey = key
                        memberSortDirection = direction
                    }
                )
            }
            displayedMembers.forEachIndexed { index, metadata ->
                item(key = "member:${metadata.normalizedPath}") {
                    SimilarityMemberCard(
                        index = index + 1,
                        metadata = metadata,
                        deleted = deletedPaths.contains(metadata.normalizedPath),
                        imageLoader = imageLoader,
                        rememberedPreviewCache = rememberedPreviewCache,
                        keepLoadedThumbnailsInMemory = keepLoadedThumbnailsInMemory,
                        thumbnailSize = memberThumbnailSize,
                        showFullPath = showFullPaths,
                        onOpen = { selectedFile = metadata }
                    )
                }
            }
            if (memberLoading) {
                item(key = "members_loading") {
                    Text(text = "Loading members...", style = MaterialTheme.typography.bodySmall)
                }
            } else if (!membersExhausted) {
                item(key = "members_more") {
                    OutlinedButton(
                        onClick = ::loadMoreMembers,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Load more members")
                    }
                }
            } else if (members.isEmpty()) {
                item(key = "members_empty") {
                    Text(text = "No active members.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
    selectedFile?.let { file ->
        FileDetailsDialogWithDeleteConfirm(
            file = file,
            showName = true,
            onOpen = { openFile(context, file.normalizedPath) },
            onDelete = {
                val handler = onDeleteFile ?: return@FileDetailsDialogWithDeleteConfirm false
                handler(file)
            },
            onDeleteResult = { deleted ->
                if (deleted) {
                    selectedFile = null
                }
            },
            onDismiss = { selectedFile = null }
        )
    }
}

private class SimilaritySettingDraftState {
    var minSizeInput by mutableStateOf("100")
    var minSizeUnit by mutableStateOf(SimilaritySizeUnit.MB)
    var mediaScope by mutableStateOf(SimilarityMediaScope.Video)
    var frameSecondsInput by mutableStateOf("0,1,10")
    var resizeWidthInput by mutableStateOf("1")
    var resizeHeightInput by mutableStateOf("1")
    var quantizationEnabled by mutableStateOf(true)
    var quantizationInput by mutableStateOf("16")
    var grayscale by mutableStateOf(false)
    var durationToleranceInput by mutableStateOf("1")
    var durationToleranceUnit by mutableStateOf(SimilarityTimeUnit.S)
}

@Composable
private fun rememberSimilaritySettingDraftState(): SimilaritySettingDraftState {
    return remember { SimilaritySettingDraftState() }
}

@Composable
private fun rememberSimilarityImageLoader(context: Context): ImageLoader {
    return remember(context) {
        ImageLoader.Builder(context)
            .components { add(VideoFrameDecoder.Factory()) }
            .build()
    }
}

@Composable
private fun SimilaritySettingsHeader(hasSettings: Boolean) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(text = "Configured settings", style = MaterialTheme.typography.titleMedium)
        if (!hasSettings) {
            Text(text = "No similarity settings yet.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun SimilarityMaintenanceCard(
    statusText: String,
    running: Boolean,
    onRunEnabled: () -> Unit,
    onRebuildEnabled: () -> Unit,
    onClearAll: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(text = "Maintenance", style = MaterialTheme.typography.titleMedium)
            Text(text = statusText, style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onRunEnabled, enabled = !running) {
                    Text("Run enabled")
                }
                OutlinedButton(onClick = onRebuildEnabled, enabled = !running) {
                    Text("Rebuild enabled")
                }
            }
            OutlinedButton(
                onClick = onClearAll,
                modifier = Modifier.fillMaxWidth(),
                enabled = !running
            ) {
                Text("Clear all similarity data")
            }
        }
    }
}

@Composable
private fun SimilaritySettingListCard(
    setting: SimilaritySettingEntity,
    clusterCount: Int,
    fileCount: Int,
    onOpen: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = setting.displayName,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = settingSummary(setting),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (setting.enabled) "Enabled" else "Disabled",
                    style = MaterialTheme.typography.labelMedium
                )
            }
            Text(
                text = "$clusterCount groups, $fileCount files",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun SimilarityMethodCard(
    title: String,
    description: String,
    onOpen: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(text = description, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExactThumbnailSettingForm(
    minSizeInput: String,
    onMinSizeInputChange: (String) -> Unit,
    minSizeUnit: SimilaritySizeUnit,
    onMinSizeUnitChange: (SimilaritySizeUnit) -> Unit,
    mediaScope: SimilarityMediaScope,
    onMediaScopeChange: (SimilarityMediaScope) -> Unit,
    frameSecondsInput: String,
    onFrameSecondsInputChange: (String) -> Unit,
    resizeWidthInput: String,
    onResizeWidthInputChange: (String) -> Unit,
    resizeHeightInput: String,
    onResizeHeightInputChange: (String) -> Unit,
    quantizationEnabled: Boolean,
    onQuantizationEnabledChange: (Boolean) -> Unit,
    quantizationInput: String,
    onQuantizationInputChange: (String) -> Unit,
    grayscale: Boolean,
    onGrayscaleChange: (Boolean) -> Unit,
    statusText: String,
    onCreate: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(text = "Exact thumbnail parameters", style = MaterialTheme.typography.titleMedium)
            SizeFloorControls(
                minSizeInput = minSizeInput,
                onMinSizeInputChange = onMinSizeInputChange,
                minSizeUnit = minSizeUnit,
                onMinSizeUnitChange = onMinSizeUnitChange
            )
            Text(text = "Media scope", style = MaterialTheme.typography.labelMedium)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ChoiceButton(
                    label = "Video",
                    selected = mediaScope == SimilarityMediaScope.Video,
                    onClick = { onMediaScopeChange(SimilarityMediaScope.Video) }
                )
                ChoiceButton(
                    label = "Image",
                    selected = mediaScope == SimilarityMediaScope.Image,
                    onClick = { onMediaScopeChange(SimilarityMediaScope.Image) }
                )
            }
            OutlinedTextField(
                value = frameSecondsInput,
                onValueChange = onFrameSecondsInputChange,
                label = { Text("Frame seconds") },
                supportingText = { Text("Effective: ${effectiveFrameSecondsText(frameSecondsInput)}") },
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = resizeWidthInput,
                    onValueChange = onResizeWidthInputChange,
                    label = { Text("Width") },
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = resizeHeightInput,
                    onValueChange = onResizeHeightInputChange,
                    label = { Text("Height") },
                    modifier = Modifier.weight(1f)
                )
            }
            ToggleRow(
                label = "Quantization",
                checked = quantizationEnabled,
                onCheckedChange = onQuantizationEnabledChange
            )
            if (quantizationEnabled) {
                OutlinedTextField(
                    value = quantizationInput,
                    onValueChange = onQuantizationInputChange,
                    label = { Text("Quantization levels") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            ToggleRow(
                label = "Grayscale",
                checked = grayscale,
                onCheckedChange = onGrayscaleChange
            )
            Text(text = statusText, style = MaterialTheme.typography.bodySmall)
            Button(onClick = onCreate, modifier = Modifier.fillMaxWidth()) {
                Text("Create exact thumbnail setting")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DurationSettingForm(
    description: String,
    buttonText: String,
    minSizeInput: String,
    onMinSizeInputChange: (String) -> Unit,
    minSizeUnit: SimilaritySizeUnit,
    onMinSizeUnitChange: (SimilaritySizeUnit) -> Unit,
    toleranceInput: String,
    onToleranceInputChange: (String) -> Unit,
    toleranceUnit: SimilarityTimeUnit,
    onToleranceUnitChange: (SimilarityTimeUnit) -> Unit,
    statusText: String,
    onCreate: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(text = "Duration parameters", style = MaterialTheme.typography.titleMedium)
            Text(text = description, style = MaterialTheme.typography.bodySmall)
            SizeFloorControls(
                minSizeInput = minSizeInput,
                onMinSizeInputChange = onMinSizeInputChange,
                minSizeUnit = minSizeUnit,
                onMinSizeUnitChange = onMinSizeUnitChange
            )
            Text(text = "Tolerance", style = MaterialTheme.typography.labelMedium)
            OutlinedTextField(
                value = toleranceInput,
                onValueChange = onToleranceInputChange,
                label = { Text("Tolerance") },
                modifier = Modifier.fillMaxWidth()
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SimilarityTimeUnit.entries.forEach { unit ->
                    ChoiceButton(
                        label = unit.label,
                        selected = toleranceUnit == unit,
                        onClick = { onToleranceUnitChange(unit) }
                    )
                }
            }
            Text(
                text = "Effective tolerance: ${formatMillis(parsedDurationToleranceStep(toleranceInput, toleranceUnit).toleranceMillis)}",
                style = MaterialTheme.typography.bodySmall
            )
            Text(text = statusText, style = MaterialTheme.typography.bodySmall)
            Button(onClick = onCreate, modifier = Modifier.fillMaxWidth()) {
                Text(buttonText)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SizeFloorControls(
    minSizeInput: String,
    onMinSizeInputChange: (String) -> Unit,
    minSizeUnit: SimilaritySizeUnit,
    onMinSizeUnitChange: (SimilaritySizeUnit) -> Unit
) {
    Text(text = "Size floor", style = MaterialTheme.typography.labelMedium)
    OutlinedTextField(
        value = minSizeInput,
        onValueChange = onMinSizeInputChange,
        label = { Text("Min size") },
        modifier = Modifier.fillMaxWidth()
    )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SimilaritySizeUnit.entries.forEach { unit ->
            ChoiceButton(
                label = unit.label,
                selected = minSizeUnit == unit,
                onClick = { onMinSizeUnitChange(unit) }
            )
        }
    }
    Text(
        text = "Effective minimum: ${formatBytes(parsedMinSizeBytes(minSizeInput, minSizeUnit))}",
        style = MaterialTheme.typography.bodySmall
    )
}

@Composable
private fun SimilaritySettingDetailCard(
    setting: SimilaritySettingEntity,
    clusterCount: Int,
    fileCount: Int,
    statusText: String,
    running: Boolean,
    onToggle: (Boolean) -> Unit,
    onRun: () -> Unit,
    onRebuild: () -> Unit,
    onClear: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = setting.displayName, style = MaterialTheme.typography.titleMedium)
                    Text(text = settingSummary(setting), style = MaterialTheme.typography.bodySmall)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Switch(checked = setting.enabled, onCheckedChange = onToggle)
            }
            Text(text = settingParametersSummary(setting), style = MaterialTheme.typography.bodySmall)
            Text(text = resultSummary(clusterCount = clusterCount, fileCount = fileCount), style = MaterialTheme.typography.bodySmall)
            Text(text = statusText, style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onRun, enabled = !running) {
                    Text("Run")
                }
                OutlinedButton(onClick = onRebuild, enabled = !running) {
                    Text("Rebuild")
                }
                OutlinedButton(onClick = onClear, enabled = !running) {
                    Text("Clear")
                }
            }
        }
    }
}

@Composable
private fun SimilarityGroupsHeader(clusterCount: Int, fileCount: Int) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(text = "Similarity groups", style = MaterialTheme.typography.titleMedium)
        Text(text = "$clusterCount groups, $fileCount files", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun SimilarityClusterMemberPreviewLines(
    members: List<FileMetadata>,
    showFullPaths: Boolean
) {
    members
        .take(SIMILARITY_CLUSTER_PREVIEW_MEMBER_LIMIT)
        .chunked(2)
        .forEach { row ->
            Text(
                text = row.joinToString("  •  ") { file ->
                    formatPath(file.normalizedPath, showFullPaths)
                },
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
}

@Composable
private fun SimilarityClusterListCard(
    repository: SimilaritySettingsRepository,
    cluster: SimilarityClusterEntity,
    imageLoader: ImageLoader,
    rememberedPreviewCache: MutableMap<String, ImageBitmap>,
    keepLoadedThumbnailsInMemory: Boolean,
    previewThumbnailSize: Dp,
    showFullPaths: Boolean,
    onOpenCluster: () -> Unit
) {
    val previewMembers = remember(cluster.clusterId) { mutableStateListOf<SimilarityClusterMember>() }
    var previewLoading by remember(cluster.clusterId) { mutableStateOf(false) }

    LaunchedEffect(cluster.clusterId) {
        previewLoading = true
        val loadedMembers = withContext(Dispatchers.IO) {
            repository.listClusterMembersPage(
                clusterId = cluster.clusterId,
                offset = 0,
                limit = SIMILARITY_CLUSTER_PREVIEW_MEMBER_LIMIT
            )
        }
        previewMembers.clear()
        previewMembers.addAll(loadedMembers)
        previewLoading = false
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenCluster)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            GroupPreviewThumbnail(
                candidatePaths = previewMembers.map { member -> member.metadata.normalizedPath },
                previewMemoryKey = "similarity-cluster-preview:${cluster.clusterId}",
                rememberedPreviewCache = rememberedPreviewCache,
                imageLoader = imageLoader,
                keepLoadedInMemory = keepLoadedThumbnailsInMemory,
                modifier = Modifier.size(previewThumbnailSize),
                contentDescription = "Similarity group preview"
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Similarity group with ${pluralize(cluster.fileCount, "file")}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Total ${formatBytes(cluster.totalBytes)}",
                    style = MaterialTheme.typography.bodySmall
                )
                if (previewLoading && previewMembers.isEmpty()) {
                    Text(text = "Loading preview members...", style = MaterialTheme.typography.bodySmall)
                } else {
                    SimilarityClusterMemberPreviewLines(
                        members = previewMembers.map { member -> member.metadata },
                        showFullPaths = showFullPaths
                    )
                    val remaining = (cluster.fileCount - previewMembers.size).coerceAtLeast(0)
                    if (remaining > 0) {
                        Text(text = "+${remaining} more", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun SimilarityClusterSummaryCard(
    setting: SimilaritySettingEntity,
    cluster: SimilarityClusterEntity
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text = "Similarity group", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "${cluster.fileCount} files, total ${formatBytes(cluster.totalBytes)}",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = settingParametersSummary(setting),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun SimilarityMembersHeader(
    loadedCount: Int,
    totalCount: Int,
    loading: Boolean,
    sortKey: ResultGroupMemberSortKey,
    sortDirection: SortDirection,
    onApplySort: (ResultGroupMemberSortKey, SortDirection) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(text = "Members", style = MaterialTheme.typography.titleMedium)
            Text(
                text = if (loading && loadedCount == 0) {
                    "Loading 0/$totalCount files"
                } else {
                    "Loaded ${loadedCount.coerceAtMost(totalCount)}/$totalCount files"
                },
                style = MaterialTheme.typography.bodySmall
            )
        }
        GroupMemberSortButton(
            sortKey = sortKey,
            sortDirection = sortDirection,
            onApplySort = onApplySort
        )
    }
}

@Composable
private fun SimilarityMemberCard(
    index: Int,
    metadata: FileMetadata,
    deleted: Boolean,
    imageLoader: ImageLoader,
    rememberedPreviewCache: MutableMap<String, ImageBitmap>,
    keepLoadedThumbnailsInMemory: Boolean,
    thumbnailSize: Dp,
    showFullPath: Boolean,
    onOpen: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        colors = if (deleted) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        } else {
            CardDefaults.cardColors()
        }
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GroupPreviewThumbnail(
                candidatePaths = listOf(metadata.normalizedPath),
                previewMemoryKey = "similarity-member:${metadata.normalizedPath}",
                rememberedPreviewCache = rememberedPreviewCache,
                imageLoader = imageLoader,
                keepLoadedInMemory = keepLoadedThumbnailsInMemory,
                modifier = Modifier.size(thumbnailSize),
                contentDescription = "Member thumbnail"
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "$index. ${formatPath(metadata.normalizedPath, showFullPath)}",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${formatBytes(metadata.sizeBytes)} | ${formatDate(metadata.lastModifiedMillis)}",
                    style = MaterialTheme.typography.bodySmall
                )
                if (deleted) {
                    Text(text = "Deleted in this session", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun MissingSelectionCard(message: String, onBack: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text = message, style = MaterialTheme.typography.bodySmall)
            Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text("Back")
            }
        }
    }
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ChoiceButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    if (selected) {
        Button(onClick = onClick) {
            Text(label)
        }
    } else {
        OutlinedButton(onClick = onClick) {
            Text(label)
        }
    }
}

private fun settingSummary(setting: SimilaritySettingEntity): String {
    return "${similarityMethodLabel(setting.methodId)} | ${setting.mediaScope} | Min ${formatBytes(setting.minSizeBytes)}"
}

private fun settingParametersSummary(setting: SimilaritySettingEntity): String {
    return when (setting.methodId) {
        SIMILARITY_METHOD_EXACT_THUMBNAIL -> {
            val step = exactThumbnailStepFromParams(setting.paramsJson)
            val frames = step.frameSeconds.joinToString(", ") { second -> "${second}s" }
            val quantization = step.quantizationLevels?.let { levels -> "$levels levels" } ?: "off"
            "Frames $frames | Size ${step.resizeWidthPx}x${step.resizeHeightPx} | Quantization $quantization | ${if (step.grayscale) "Grayscale" else "Color"}"
        }
        SIMILARITY_METHOD_DURATION_TOLERANCE -> {
            val step = durationToleranceStepFromParams(setting.paramsJson)
            "Duration window ${formatMillis(step.toleranceMillis)}"
        }
        SIMILARITY_METHOD_DURATION_NEIGHBOR_LIST -> {
            val step = durationNeighborListStepFromParams(setting.paramsJson)
            "Neighbor gap ${formatMillis(step.toleranceMillis)}"
        }
        else -> "Custom parameters"
    }
}

internal fun shouldTriggerSimilarityMemberAutoLoad(
    lastVisibleItemIndex: Int,
    totalItemsCount: Int,
    thresholdItems: Int,
    isLoading: Boolean,
    isComplete: Boolean
): Boolean {
    if (isLoading || isComplete) return false
    if (lastVisibleItemIndex < 0 || totalItemsCount <= 0) return false
    val remainingItems = (totalItemsCount - 1 - lastVisibleItemIndex).coerceAtLeast(0)
    return remainingItems <= thresholdItems.coerceAtLeast(0)
}

private fun resultSummary(clusterCount: Int, fileCount: Int): String {
    return "${pluralize(clusterCount, "group")}, ${pluralize(fileCount, "file")}"
}

private fun effectiveFrameSecondsText(input: String): String {
    return parsedFrameSeconds(input).ifEmpty { listOf(0) }.joinToString(", ")
}

private fun pluralize(count: Int, singular: String): String {
    return "$count $singular${if (count == 1) "" else "s"}"
}

private fun formatMillis(millis: Long): String {
    return when {
        millis % 60_000L == 0L -> "${millis / 60_000L} min"
        millis % 1_000L == 0L -> "${millis / 1_000L} s"
        else -> "$millis ms"
    }
}
