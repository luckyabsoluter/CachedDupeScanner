package opensource.cached_dupe_scanner.ui.home

import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
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
import opensource.cached_dupe_scanner.core.durationNeighborListSettingDraft
import opensource.cached_dupe_scanner.core.durationNeighborListStepFromParams
import opensource.cached_dupe_scanner.core.durationToleranceSettingDraft
import opensource.cached_dupe_scanner.core.durationToleranceStepFromParams
import opensource.cached_dupe_scanner.core.exactThumbnailSettingDraft
import opensource.cached_dupe_scanner.core.exactThumbnailStepFromParams
import opensource.cached_dupe_scanner.core.similarityMethodLabel
import opensource.cached_dupe_scanner.notifications.TaskNotificationController
import opensource.cached_dupe_scanner.storage.AppSettingsStore
import opensource.cached_dupe_scanner.storage.SimilarityClusterMember
import opensource.cached_dupe_scanner.storage.SimilaritySettingsRepository
import opensource.cached_dupe_scanner.tasks.TaskArea
import opensource.cached_dupe_scanner.tasks.TaskCoordinator
import opensource.cached_dupe_scanner.ui.components.AppTopBar
import opensource.cached_dupe_scanner.ui.components.ConfirmationDialog
import opensource.cached_dupe_scanner.ui.components.ConfirmationDialogButtonStyle
import opensource.cached_dupe_scanner.ui.components.RadioOptionRow
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
import opensource.cached_dupe_scanner.ui.home.similarity.startSimilaritySettingGenerationTask

private const val SIMILARITY_CLUSTER_PREVIEW_MEMBER_LOAD_LIMIT = 10
private const val SIMILARITY_CLUSTER_PREVIEW_TEXT_MEMBER_LIMIT = 4
private const val SIMILARITY_CLUSTER_PREVIEW_ITEMS_PER_LINE = 2
private const val SIMILARITY_CLUSTER_DETAIL_MEMBER_PAGE_SIZE = 100
private const val SIMILARITY_CLUSTER_DETAIL_AUTO_LOAD_THRESHOLD_ITEMS = 3
private const val SIMILARITY_SIGNATURE_SAMPLE_DISPLAY_LIMIT = 32

internal enum class SimilarityClusterSortKey(val label: String) {
    FileCount("File count"),
    TotalSize("Total size")
}

@Composable
fun SimilaritySettingsScreen(
    repository: SimilaritySettingsRepository,
    refreshVersion: Int,
    onBack: () -> Unit,
    onCreateSetting: () -> Unit,
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
                text = "Similarity templates",
                style = MaterialTheme.typography.titleMedium
            )
        }
        item(key = "method_exact") {
            SimilarityMethodCard(
                title = "Exact thumbnail hash",
                description = "Configures cached-media matching with editable media type, size floor, frame timestamps, resize target, optional quantization, and color mode.",
                onOpen = onOpenExactThumbnail
            )
        }
        item(key = "method_duration") {
            SimilarityMethodCard(
                title = "Video duration tolerance",
                description = "Configures cached-video matching that extracts each video's duration and clusters candidates inside the configured tolerance.",
                onOpen = onOpenDurationTolerance
            )
        }
        item(key = "method_neighbor") {
            SimilarityMethodCard(
                title = "Video duration neighbor list",
                description = "Configures cached-video matching that builds one duration-sorted list and keeps only adjacent neighbors inside the tolerance.",
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
    val defaultDisplayName = exactThumbnailSettingDraft(
        mediaScope = draft.mediaScope,
        minSizeBytes = parsedMinSizeBytes(draft.minSizeInput, draft.minSizeUnit),
        step = exactStep
    ).displayName
    fun createExact() {
        scope.launch {
            val created = withContext(Dispatchers.IO) {
                repository.createExactThumbnailSetting(
                    mediaScope = draft.mediaScope,
                    minSizeBytes = parsedMinSizeBytes(draft.minSizeInput, draft.minSizeUnit),
                    step = exactStep,
                    enabled = true,
                    displayName = draft.displayNameInput
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
                displayNameInput = draft.displayNameInput,
                onDisplayNameInputChange = { draft.displayNameInput = it },
                defaultDisplayName = defaultDisplayName,
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
    val durationToleranceStep = parsedDurationToleranceStep(draft.durationToleranceInput, draft.durationToleranceUnit)
    val durationNeighborStep = parsedDurationNeighborListStep(draft.durationToleranceInput, draft.durationToleranceUnit)
    val defaultDisplayName = if (neighborList) {
        durationNeighborListSettingDraft(
            minSizeBytes = parsedMinSizeBytes(draft.minSizeInput, draft.minSizeUnit),
            step = durationNeighborStep
        ).displayName
    } else {
        durationToleranceSettingDraft(
            minSizeBytes = parsedMinSizeBytes(draft.minSizeInput, draft.minSizeUnit),
            step = durationToleranceStep
        ).displayName
    }
    fun createDuration() {
        scope.launch {
            val created = withContext(Dispatchers.IO) {
                if (neighborList) {
                    repository.createDurationNeighborListSetting(
                        minSizeBytes = parsedMinSizeBytes(draft.minSizeInput, draft.minSizeUnit),
                        step = durationNeighborStep,
                        enabled = true,
                        displayName = draft.displayNameInput
                    )
                } else {
                    repository.createDurationToleranceSetting(
                        minSizeBytes = parsedMinSizeBytes(draft.minSizeInput, draft.minSizeUnit),
                        step = durationToleranceStep,
                        enabled = true,
                        displayName = draft.displayNameInput
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
                displayNameInput = draft.displayNameInput,
                onDisplayNameInputChange = { draft.displayNameInput = it },
                defaultDisplayName = defaultDisplayName,
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
    settingId: Long,
    refreshVersion: Int,
    onChanged: () -> Unit,
    onBack: () -> Unit,
    onOpenGroups: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var setting by remember { mutableStateOf<SimilaritySettingEntity?>(null) }
    val clusters = remember { mutableStateListOf<SimilarityClusterEntity>() }
    var statusText by remember { mutableStateOf("Scans generate enabled settings; use Update or Rebuild to run this setting now.") }
    val activeSimilarityTask = taskCoordinator.activeTask(TaskArea.Similarity)
    val generationRunning = activeSimilarityTask != null
    val displayedStatusText = activeSimilarityTask?.detail ?: statusText
    var displayNameInput by remember(settingId) { mutableStateOf("") }
    var lastLoadedDisplayName by remember(settingId) { mutableStateOf<String?>(null) }
    var confirmClearSetting by remember { mutableStateOf(false) }
    var confirmDeleteSetting by remember { mutableStateOf(false) }
    fun refresh() {
        scope.launch {
            val loadedSetting = withContext(Dispatchers.IO) {
                repository.listSettings().firstOrNull { candidate -> candidate.settingId == settingId }
            }
            val loadedClusters = withContext(Dispatchers.IO) { repository.listClusters(settingId) }
            setting = loadedSetting
            if (loadedSetting != null) {
                val previousLoadedDisplayName = lastLoadedDisplayName
                if (displayNameInput.isBlank() || displayNameInput == previousLoadedDisplayName) {
                    displayNameInput = loadedSetting.displayName
                }
                lastLoadedDisplayName = loadedSetting.displayName
            }
            clusters.clear()
            clusters.addAll(loadedClusters)
        }
    }
    fun saveSettingName() {
        scope.launch {
            withContext(Dispatchers.IO) {
                repository.renameSetting(settingId, displayNameInput)
            }
            statusText = "Setting name saved."
            onChanged()
            refresh()
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
    fun runSettingGeneration(rebuild: Boolean) {
        val started = startSimilaritySettingGenerationTask(
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
            statusText = "Another similarity update or rebuild is already running."
        }
    }
    fun deleteSetting() {
        confirmDeleteSetting = false
        scope.launch {
            withContext(Dispatchers.IO) {
                repository.deleteSetting(settingId)
            }
            onChanged()
            onBack()
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
                    statusText = displayedStatusText,
                    displayNameInput = displayNameInput,
                    onDisplayNameInputChange = { displayNameInput = it },
                    onSaveName = ::saveSettingName,
                    nameSaveEnabled = displayNameInput.trim().isNotEmpty() &&
                        displayNameInput.trim() != selectedSetting.displayName,
                    generationRunning = generationRunning,
                    onToggle = { enabled ->
                        scope.launch {
                            statusText = if (enabled) {
                                "Enabled. Scans generate this setting; use Update to catch up now."
                            } else {
                                "Similarity setting paused. Scans skip it until enabled."
                            }
                            withContext(Dispatchers.IO) {
                                repository.setEnabled(settingId, enabled)
                            }
                            statusText = if (enabled) {
                                "Enabled. Scans generate this setting; use Update to catch up now."
                            } else {
                                "Similarity setting paused. Scans skip it until enabled."
                            }
                            onChanged()
                            refresh()
                        }
                    },
                    onUpdate = { runSettingGeneration(rebuild = false) },
                    onRebuild = { runSettingGeneration(rebuild = true) },
                    onClear = { confirmClearSetting = true },
                    onDelete = { confirmDeleteSetting = true }
                )
            }
            item(key = "groups_entry") {
                SimilarityGroupsEntryCard(
                    clusterCount = clusters.size,
                    fileCount = clusters.sumOf { cluster -> cluster.fileCount },
                    onOpenGroups = { onOpenGroups(settingId) }
                )
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
            confirmStyle = ConfirmationDialogButtonStyle.Outlined
        )
    }
    if (confirmDeleteSetting) {
        ConfirmationDialog(
            title = "Delete this similarity setting?",
            text = "The setting, generated groups, member links, method features, and maintenance history will be removed.",
            confirmText = "Delete",
            onConfirm = ::deleteSetting,
            onDismissRequest = { confirmDeleteSetting = false },
            confirmStyle = ConfirmationDialogButtonStyle.Outlined
        )
    }
}

@Composable
fun SimilaritySettingGroupsScreen(
    repository: SimilaritySettingsRepository,
    settingsStore: AppSettingsStore,
    keepLoadedThumbnailsInMemory: Boolean,
    thumbnailSizeScale: Float,
    rememberedPreviewCache: MutableMap<String, ImageBitmap>,
    showFullPaths: Boolean,
    settingId: Long,
    refreshVersion: Int,
    onBack: () -> Unit,
    onOpenCluster: (Long, Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val imageLoader = rememberSimilarityImageLoader(context)
    val previewThumbnailSize = 72.dp * thumbnailSizeScale.coerceAtLeast(0f)
    val settingsSnapshot = remember { settingsStore.load() }
    var setting by remember { mutableStateOf<SimilaritySettingEntity?>(null) }
    val clusters = remember { mutableStateListOf<SimilarityClusterEntity>() }
    var clusterSortKey by remember {
        mutableStateOf(
            runCatching { SimilarityClusterSortKey.valueOf(settingsSnapshot.similarityClusterSortKey) }
                .getOrDefault(SimilarityClusterSortKey.FileCount)
        )
    }
    var clusterSortDirection by remember {
        mutableStateOf(
            runCatching { SortDirection.valueOf(settingsSnapshot.similarityClusterSortDirection) }
                .getOrDefault(SortDirection.Desc)
        )
    }
    val displayedClusters = sortSimilarityClusters(
        clusters = clusters,
        sortKey = clusterSortKey,
        direction = clusterSortDirection
    )

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

    LaunchedEffect(settingId, refreshVersion) {
        refresh()
    }

    ScreenScrollColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "top_bar") {
            AppTopBar(
                title = setting?.displayName ?: "Similarity results",
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
            item(key = "cluster_header") {
                SimilarityGroupsHeader(
                    clusterCount = clusters.size,
                    fileCount = clusters.sumOf { cluster -> cluster.fileCount },
                    sortKey = clusterSortKey,
                    sortDirection = clusterSortDirection,
                    sortEnabled = clusters.isNotEmpty(),
                    onApplySort = { key, direction ->
                        clusterSortKey = key
                        clusterSortDirection = direction
                        settingsStore.setSimilarityClusterSortKey(key.name)
                        settingsStore.setSimilarityClusterSortDirection(direction.name)
                    }
                )
            }
            if (clusters.isEmpty()) {
                item(key = "clusters_empty") {
                    Text(
                        text = "No similarity clusters found for this setting.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            displayedClusters.forEach { cluster ->
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
}

@Composable
fun SimilarityClusterDetailScreen(
    repository: SimilaritySettingsRepository,
    settingsStore: AppSettingsStore,
    keepLoadedThumbnailsInMemory: Boolean,
    keepLoadedVideoPreviewsInMemory: Boolean,
    snapVideoPreviewFramesToWidth: Boolean,
    videoPreviewLineCount: Int,
    thumbnailSizeScale: Float,
    videoPreviewSizeScale: Float,
    rememberedPreviewCache: MutableMap<String, ImageBitmap>,
    rememberedVideoPreviewCache: MutableMap<String, ImageBitmap>,
    showFullPaths: Boolean,
    showVideoPreviews: Boolean,
    showVideoPreviewDurations: Boolean,
    showVideoPreviewResolutions: Boolean,
    onShowVideoPreviewsChange: (Boolean) -> Unit,
    onShowVideoPreviewDurationsChange: (Boolean) -> Unit,
    onShowVideoPreviewResolutionsChange: (Boolean) -> Unit,
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
    val detailPreviewHeight = 180.dp * thumbnailSizeScale.coerceAtLeast(0f)
    val videoPreviewFrameHeight = 44.dp * videoPreviewSizeScale.coerceAtLeast(0f)
    val memberListState = rememberLazyListState()
    val settingsSnapshot = remember { settingsStore.load() }
    var setting by remember { mutableStateOf<SimilaritySettingEntity?>(null) }
    var cluster by remember { mutableStateOf<SimilarityClusterEntity?>(null) }
    val members = remember { mutableStateListOf<SimilarityClusterMember>() }
    var memberLoading by remember { mutableStateOf(false) }
    var memberOffset by remember { mutableStateOf(0) }
    var membersExhausted by remember { mutableStateOf(false) }
    var selectedFile by remember { mutableStateOf<FileMetadata?>(null) }
    var memberSortKey by remember {
        mutableStateOf(
            runCatching { ResultGroupMemberSortKey.valueOf(settingsSnapshot.similarityMemberSortKey) }
                .getOrDefault(ResultGroupMemberSortKey.Path)
        )
    }
    var memberSortDirection by remember {
        mutableStateOf(
            runCatching { SortDirection.valueOf(settingsSnapshot.similarityMemberSortDirection) }
                .getOrDefault(SortDirection.Asc)
        )
    }
    var durationMemberSortDirection by remember {
        mutableStateOf(
            runCatching { SortDirection.valueOf(settingsSnapshot.similarityDurationMemberSortDirection) }
                .getOrDefault(SortDirection.Asc)
        )
    }
    var previewMenuExpanded by remember { mutableStateOf(false) }
    val selectionState = rememberLazyDetailSelectionState("similarity-cluster:$clusterId")
    var confirmDeleteSelected by remember(clusterId) { mutableStateOf(false) }
    var isDeletingSelected by remember(clusterId) { mutableStateOf(false) }
    var deleteSelectedMessage by remember(clusterId) { mutableStateOf<String?>(null) }

    fun isDurationNeighborSetting(): Boolean {
        return setting?.methodId == SIMILARITY_METHOD_DURATION_NEIGHBOR_LIST
    }

    fun loadMoreMembers(directionOverride: SortDirection? = null) {
        if (memberLoading || membersExhausted) return
        memberLoading = true
        val pageDirection = if (isDurationNeighborSetting()) {
            directionOverride ?: durationMemberSortDirection
        } else {
            SortDirection.Asc
        }
        scope.launch {
            val nextMembers = withContext(Dispatchers.IO) {
                repository.listClusterMembersPage(
                    clusterId = clusterId,
                    offset = memberOffset,
                    limit = SIMILARITY_CLUSTER_DETAIL_MEMBER_PAGE_SIZE,
                    direction = pageDirection
                )
            }
            members.addAll(nextMembers)
            memberOffset += nextMembers.size
            membersExhausted = nextMembers.size < SIMILARITY_CLUSTER_DETAIL_MEMBER_PAGE_SIZE ||
                memberOffset >= (cluster?.fileCount ?: Int.MAX_VALUE)
            memberLoading = false
        }
    }

    fun applyDurationMemberSortDirection(direction: SortDirection) {
        if (durationMemberSortDirection == direction || memberLoading) return
        durationMemberSortDirection = direction
        settingsStore.setSimilarityDurationMemberSortDirection(direction.name)
        members.clear()
        memberOffset = 0
        membersExhausted = false
        selectedFile = null
        selectionState.clear()
        loadMoreMembers(directionOverride = direction)
    }

    LaunchedEffect(settingId, clusterId) {
        memberLoading = true
        setting = null
        cluster = null
        members.clear()
        memberOffset = 0
        membersExhausted = false
        selectionState.clear()
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
                limit = SIMILARITY_CLUSTER_DETAIL_MEMBER_PAGE_SIZE,
                direction = if (loadedSetting?.methodId == SIMILARITY_METHOD_DURATION_NEIGHBOR_LIST) {
                    durationMemberSortDirection
                } else {
                    SortDirection.Asc
                }
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

    val durationNeighborMode = setting?.methodId == SIMILARITY_METHOD_DURATION_NEIGHBOR_LIST
    val displayedMembers = sortSimilarityClusterMembers(
        members = members,
        durationNeighborMode = durationNeighborMode,
        durationDirection = durationMemberSortDirection,
        sortKey = memberSortKey,
        sortDirection = memberSortDirection
    )
    val displayedFiles = displayedMembers.map { member -> member.metadata }
    val hasVideoMembers = members.any { member -> isVideoFile(member.metadata.normalizedPath) }
    val selectionMode = selectionState.isSelectionMode

    LaunchedEffect(displayedMembers, deletedPaths) {
        selectionState.filterPartialSelectionToLoadedMembers(
            members = displayedFiles,
            deletedPaths = deletedPaths
        )
    }
    LaunchedEffect(selectionMode) {
        if (selectionMode) {
            selectedFile = null
        }
    }

    ScreenScrollColumn(
        modifier = modifier,
        listState = memberListState,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "top_bar") {
            AppTopBar(
                title = if (setting?.methodId == SIMILARITY_METHOD_DURATION_NEIGHBOR_LIST) {
                    "Similarity list detail"
                } else {
                    "Similarity cluster detail"
                },
                onBack = onBack,
                actions = {
                    IconButton(onClick = { previewMenuExpanded = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Menu")
                    }
                    DropdownMenu(
                        expanded = previewMenuExpanded,
                        onDismissRequest = { previewMenuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Video preview") },
                            leadingIcon = {
                                Checkbox(
                                    checked = showVideoPreviews,
                                    onCheckedChange = null
                                )
                            },
                            enabled = hasVideoMembers,
                            onClick = { onShowVideoPreviewsChange(!showVideoPreviews) }
                        )
                        DropdownMenuItem(
                            text = { Text("Video duration") },
                            leadingIcon = {
                                Checkbox(
                                    checked = showVideoPreviewDurations,
                                    onCheckedChange = null
                                )
                            },
                            enabled = hasVideoMembers,
                            onClick = { onShowVideoPreviewDurationsChange(!showVideoPreviewDurations) }
                        )
                        DropdownMenuItem(
                            text = { Text("Video resolution") },
                            leadingIcon = {
                                Checkbox(
                                    checked = showVideoPreviewResolutions,
                                    onCheckedChange = null
                                )
                            },
                            enabled = hasVideoMembers,
                            onClick = { onShowVideoPreviewResolutionsChange(!showVideoPreviewResolutions) }
                        )
                    }
                }
            )
        }
        val selectedSetting = setting
        val selectedCluster = cluster
        if (selectedSetting == null || selectedCluster == null) {
            item(key = "missing_cluster") {
                MissingSelectionCard(
                    message = "This similarity result is no longer available.",
                    onBack = onBack
                )
            }
        } else {
            val exactHashExplanation = exactThumbnailClusterExplanation(selectedCluster.clusterKey)
            val durationNeighborExplanation = durationNeighborClusterExplanation(selectedCluster.clusterKey)
            item(key = "exact_reduction") {
                ExactHashReductionPreviewCard(exactHashExplanation = exactHashExplanation)
            }
            item(key = "detail_overview") {
                SimilarityClusterDetailOverviewCard(
                    setting = selectedSetting,
                    cluster = selectedCluster,
                    members = members,
                    imageLoader = imageLoader,
                    rememberedPreviewCache = rememberedPreviewCache,
                    keepLoadedThumbnailsInMemory = keepLoadedThumbnailsInMemory,
                    previewHeight = detailPreviewHeight,
                    previewMemoryKey = "similarity-cluster-detail:${selectedCluster.clusterId}",
                    loadedCount = members.size,
                    memberLoading = memberLoading,
                    showFullPaths = showFullPaths,
                    exactHashExplanation = exactHashExplanation,
                    durationNeighborExplanation = durationNeighborExplanation,
                    sortingEnabled = !durationNeighborMode,
                    sortKey = memberSortKey,
                    sortDirection = memberSortDirection,
                    onApplySort = { key, direction ->
                        memberSortKey = key
                        memberSortDirection = direction
                        settingsStore.setSimilarityMemberSortKey(key.name)
                        settingsStore.setSimilarityMemberSortDirection(direction.name)
                    }
                )
            }
            if (durationNeighborMode) {
                item(key = "duration_member_sort") {
                    DurationNeighborSortDirectionCard(
                        direction = durationMemberSortDirection,
                        enabled = !memberLoading,
                        onDirectionChange = ::applyDurationMemberSortDirection
                    )
                }
            }
            if (selectionMode) {
                item(key = "selection_controls") {
                    SimilaritySelectionControls(
                        statusText = selectionState.statusText(totalCount = selectedCluster.fileCount),
                        allSelectedAcrossGroup = selectionState.allSelectedAcrossGroup,
                        selectAllIncludesNotLoaded = selectionState.isSelectAllMode && !membersExhausted,
                        deleting = isDeletingSelected,
                        deleteEnabled = onDeleteFile != null,
                        onToggleSelectAll = {
                            selectionState.toggleSelectAll()
                            deleteSelectedMessage = null
                        },
                        onDeleteSelected = { confirmDeleteSelected = true }
                    )
                }
            }
            deleteSelectedMessage?.let { message ->
                item(key = "selection_message") {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            displayedMembers.forEach { member ->
                val metadata = member.metadata
                item(key = "member:${metadata.normalizedPath}") {
                    val isDeleted = deletedPaths.contains(metadata.normalizedPath)
                    SimilarityMemberCard(
                        metadata = metadata,
                        deleted = isDeleted,
                        selected = selectionState.isPathSelected(metadata.normalizedPath),
                        selectionMode = selectionMode,
                        imageLoader = imageLoader,
                        rememberedPreviewCache = rememberedPreviewCache,
                        rememberedVideoPreviewCache = rememberedVideoPreviewCache,
                        keepLoadedThumbnailsInMemory = keepLoadedThumbnailsInMemory,
                        keepLoadedVideoPreviewsInMemory = keepLoadedVideoPreviewsInMemory,
                        snapVideoPreviewFramesToWidth = snapVideoPreviewFramesToWidth,
                        videoPreviewLineCount = videoPreviewLineCount,
                        videoPreviewFrameHeight = videoPreviewFrameHeight,
                        thumbnailSize = memberThumbnailSize,
                        showVideoPreviews = showVideoPreviews,
                        showVideoPreviewDurations = showVideoPreviewDurations,
                        showVideoPreviewResolutions = showVideoPreviewResolutions,
                        durationMillis = member.durationMillis,
                        showFullPath = showFullPaths,
                        onOpen = { selectedFile = metadata },
                        onToggleSelection = {
                            selectionState.togglePath(
                                path = metadata.normalizedPath,
                                isDeleted = isDeleted
                            )
                            deleteSelectedMessage = null
                        }
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
    if (confirmDeleteSelected) {
        val loadedTargets = selectionState.selectedLoadedFilesForDelete(
            members = displayedFiles,
            deletedPaths = deletedPaths
        )
        val selectedCountLabel = if (selectionState.isSelectAllMode) {
            selectionState.selectedCount(totalCount = cluster?.fileCount ?: members.size)
        } else {
            loadedTargets.size
        }
        AlertDialog(
            onDismissRequest = {
                if (!isDeletingSelected) {
                    confirmDeleteSelected = false
                }
            },
            title = { Text("Delete selected files?") },
            text = {
                if (selectedCountLabel <= 0) {
                    Text("No deletable files are selected.")
                } else if (selectionState.isSelectAllMode) {
                    if (selectionState.deselectedPathsInSelectAll.isEmpty()) {
                        Text("Select all is active. $selectedCountLabel files will be deleted, including not-loaded files.")
                    } else {
                        Text("Select all is active with ${selectionState.deselectedPathsInSelectAll.size} exclusions. $selectedCountLabel files will be deleted.")
                    }
                } else {
                    Text("$selectedCountLabel selected files will be deleted (moved to app trash).")
                }
            },
            confirmButton = {
                OutlinedButton(
                    onClick = {
                        val handler = onDeleteFile ?: return@OutlinedButton
                        val selectAllSnapshot = selectionState.isSelectAllMode
                        val selectedPathsSnapshot = selectionState.selectedPaths
                        val excludedFromAllSnapshot = selectionState.deselectedPathsInSelectAll
                        val deletedPathsSnapshot = deletedPaths

                        isDeletingSelected = true
                        deleteSelectedMessage = null
                        scope.launch {
                            val targets = if (selectAllSnapshot) {
                                val pageSizeForBulk = 500
                                val collected = mutableListOf<FileMetadata>()
                                var offset = 0
                                while (true) {
                                    val page = withContext(Dispatchers.IO) {
                                        repository.listClusterMembersPage(
                                            clusterId = clusterId,
                                            offset = offset,
                                            limit = pageSizeForBulk
                                        )
                                    }
                                    if (page.isEmpty()) break
                                    page.forEach { member ->
                                        val file = member.metadata
                                        val path = file.normalizedPath
                                        if (
                                            !excludedFromAllSnapshot.contains(path) &&
                                            !deletedPathsSnapshot.contains(path)
                                        ) {
                                            collected.add(file)
                                        }
                                    }
                                    offset += page.size
                                    if (page.size < pageSizeForBulk) break
                                }
                                collected
                            } else {
                                selectedFilesForDelete(
                                    members = displayedFiles,
                                    selectedPaths = selectedPathsSnapshot,
                                    deletedPaths = deletedPathsSnapshot
                                )
                            }

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

                            selectionState.markFailedPaths(failedPaths)
                            deleteSelectedMessage = when {
                                successCount == 0 && failedPaths.isEmpty() -> "No files deleted."
                                failedPaths.isEmpty() -> "$successCount files deleted."
                                successCount == 0 -> "Delete failed for ${failedPaths.size} files."
                                else -> "$successCount deleted, ${failedPaths.size} failed."
                            }
                            isDeletingSelected = false
                            confirmDeleteSelected = false
                        }
                    },
                    enabled = onDeleteFile != null && selectedCountLabel > 0 && !isDeletingSelected
                ) {
                    Text(if (isDeletingSelected) "Deleting..." else "Delete")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { confirmDeleteSelected = false },
                    enabled = !isDeletingSelected
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

private class SimilaritySettingDraftState {
    var displayNameInput by mutableStateOf("")
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
        Text(text = "Similarity settings", style = MaterialTheme.typography.titleMedium)
        Text(
            text = "Scans generate enabled settings from the scan cache. Use Update to catch up from current cached files, or Rebuild to clear and recalculate a setting.",
            style = MaterialTheme.typography.bodySmall
        )
        if (!hasSettings) {
            Text(text = "No similarity settings yet.", style = MaterialTheme.typography.bodySmall)
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
                    text = if (setting.enabled) "Enabled" else "Paused",
                    style = MaterialTheme.typography.labelMedium
                )
            }
            Text(
                text = "$clusterCount clusters, $fileCount files",
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
            Text(
                text = "Similarity method template",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(text = description, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExactThumbnailSettingForm(
    displayNameInput: String,
    onDisplayNameInputChange: (String) -> Unit,
    defaultDisplayName: String,
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
            Text(
                text = "Configures cached-media matching with editable inputs.",
                style = MaterialTheme.typography.bodySmall
            )
            SettingNameField(
                displayNameInput = displayNameInput,
                onDisplayNameInputChange = onDisplayNameInputChange,
                defaultDisplayName = defaultDisplayName
            )
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
    displayNameInput: String,
    onDisplayNameInputChange: (String) -> Unit,
    defaultDisplayName: String,
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
            SettingNameField(
                displayNameInput = displayNameInput,
                onDisplayNameInputChange = onDisplayNameInputChange,
                defaultDisplayName = defaultDisplayName
            )
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

@Composable
private fun SettingNameField(
    displayNameInput: String,
    onDisplayNameInputChange: (String) -> Unit,
    defaultDisplayName: String
) {
    OutlinedTextField(
        value = displayNameInput,
        onValueChange = onDisplayNameInputChange,
        label = { Text("Setting name") },
        supportingText = { Text("Default: $defaultDisplayName") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SimilaritySettingDetailCard(
    setting: SimilaritySettingEntity,
    clusterCount: Int,
    fileCount: Int,
    statusText: String,
    displayNameInput: String,
    onDisplayNameInputChange: (String) -> Unit,
    onSaveName: () -> Unit,
    nameSaveEnabled: Boolean,
    generationRunning: Boolean,
    onToggle: (Boolean) -> Unit,
    onUpdate: () -> Unit,
    onRebuild: () -> Unit,
    onClear: () -> Unit,
    onDelete: () -> Unit
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
                Switch(
                    checked = setting.enabled,
                    onCheckedChange = onToggle,
                    enabled = !generationRunning
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = displayNameInput,
                    onValueChange = onDisplayNameInputChange,
                    label = { Text("Setting name") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedButton(
                    onClick = onSaveName,
                    enabled = nameSaveEnabled && !generationRunning
                ) {
                    Text("Save name")
                }
            }
            Text(text = settingParametersSummary(setting), style = MaterialTheme.typography.bodySmall)
            Text(text = resultSummary(clusterCount = clusterCount, fileCount = fileCount), style = MaterialTheme.typography.bodySmall)
            Text(text = settingGenerationSummary(setting), style = MaterialTheme.typography.bodySmall)
            Text(text = statusText, style = MaterialTheme.typography.bodySmall)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onUpdate,
                    enabled = !generationRunning
                ) {
                    Text("Update")
                }
                OutlinedButton(
                    onClick = onRebuild,
                    enabled = !generationRunning
                ) {
                    Text("Rebuild")
                }
                OutlinedButton(
                    onClick = onClear,
                    enabled = !generationRunning
                ) {
                    Text("Clear")
                }
                OutlinedButton(
                    onClick = onDelete,
                    enabled = !generationRunning
                ) {
                    Text("Delete setting")
                }
            }
        }
    }
}

@Composable
private fun SimilarityGroupsEntryCard(
    clusterCount: Int,
    fileCount: Int,
    onOpenGroups: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text = "Stored similarity results", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "$clusterCount clusters, $fileCount files",
                style = MaterialTheme.typography.bodySmall
            )
            Button(
                onClick = onOpenGroups,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Open results")
            }
        }
    }
}

@Composable
private fun SimilarityGroupsHeader(
    clusterCount: Int,
    fileCount: Int,
    sortKey: SimilarityClusterSortKey,
    sortDirection: SortDirection,
    sortEnabled: Boolean,
    onApplySort: (SimilarityClusterSortKey, SortDirection) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(text = "Selected similarity clusters", style = MaterialTheme.typography.titleMedium)
            Text(text = "$clusterCount clusters, $fileCount files", style = MaterialTheme.typography.bodySmall)
        }
        SimilarityClusterSortButton(
            sortKey = sortKey,
            sortDirection = sortDirection,
            enabled = sortEnabled,
            onApplySort = onApplySort
        )
    }
}

@Composable
private fun SimilarityClusterSortButton(
    sortKey: SimilarityClusterSortKey,
    sortDirection: SortDirection,
    enabled: Boolean,
    onApplySort: (SimilarityClusterSortKey, SortDirection) -> Unit
) {
    var dialogOpen by remember { mutableStateOf(false) }
    var pendingSortKey by remember { mutableStateOf(sortKey) }
    var pendingSortDirection by remember { mutableStateOf(sortDirection) }

    OutlinedButton(
        enabled = enabled,
        onClick = {
            pendingSortKey = sortKey
            pendingSortDirection = sortDirection
            dialogOpen = true
        }
    ) {
        Text("Sort")
    }

    if (dialogOpen) {
        AlertDialog(
            onDismissRequest = { dialogOpen = false },
            title = { Text("Cluster sort options") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Sort by")
                    RadioOptionRow(
                        option = SimilarityClusterSortKey.FileCount,
                        selected = pendingSortKey,
                        label = SimilarityClusterSortKey.FileCount.label,
                        onSelect = { pendingSortKey = it }
                    )
                    RadioOptionRow(
                        option = SimilarityClusterSortKey.TotalSize,
                        selected = pendingSortKey,
                        label = SimilarityClusterSortKey.TotalSize.label,
                        onSelect = { pendingSortKey = it }
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Direction")
                    RadioOptionRow(
                        option = SortDirection.Desc,
                        selected = pendingSortDirection,
                        label = SortDirection.Desc.label,
                        onSelect = { pendingSortDirection = it }
                    )
                    RadioOptionRow(
                        option = SortDirection.Asc,
                        selected = pendingSortDirection,
                        label = SortDirection.Asc.label,
                        onSelect = { pendingSortDirection = it }
                    )
                }
            },
            confirmButton = {
                OutlinedButton(
                    onClick = {
                        onApplySort(pendingSortKey, pendingSortDirection)
                        dialogOpen = false
                    }
                ) {
                    Text("Apply")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { dialogOpen = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun SimilarityClusterMemberPreviewLines(
    members: List<FileMetadata>,
    showFullPaths: Boolean,
    durationMillisByNormalizedPath: Map<String, Long> = emptyMap(),
    preserveOrder: Boolean = false
) {
    similarityClusterPreviewLineTexts(
        members = members,
        showFullPaths = showFullPaths,
        durationMillisByNormalizedPath = durationMillisByNormalizedPath,
        preserveOrder = preserveOrder
    ).forEach { line ->
        Text(
            text = line,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurfaceVariant
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
    val exactHashExplanation = exactThumbnailClusterExplanation(cluster.clusterKey)
    val durationExplanation = durationClusterExplanation(cluster.clusterKey)
    val durationNeighborExplanation = durationNeighborClusterExplanation(cluster.clusterKey)
    val durationMillisByNormalizedPath = similarityMemberDurationMap(previewMembers)

    LaunchedEffect(cluster.clusterId) {
        previewLoading = true
        val loadedMembers = withContext(Dispatchers.IO) {
            repository.listClusterMembersPage(
                clusterId = cluster.clusterId,
                offset = 0,
                limit = SIMILARITY_CLUSTER_PREVIEW_MEMBER_LOAD_LIMIT
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
                    text = if (durationNeighborExplanation != null) {
                        "${cluster.fileCount} listed videos | Total ${formatBytes(cluster.totalBytes)}"
                    } else {
                        "${cluster.fileCount} files | Total ${formatBytes(cluster.totalBytes)}"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = exactHashExplanation?.let(::exactHashClusterSummary)
                        ?: durationExplanation?.let(::durationClusterSummary)
                        ?: durationNeighborExplanation?.let(::durationNeighborClusterSummary)
                        ?: "Signature ${cluster.clusterKey.take(16)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (exactHashExplanation != null) {
                    Text(
                        text = "Members matched the same exact thumbnail signature.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (durationNeighborExplanation != null) {
                    Text(
                        text = "One duration-sorted list; isolated videos are omitted.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (previewLoading && previewMembers.isEmpty()) {
                    Text(text = "Loading preview members...", style = MaterialTheme.typography.bodySmall)
                } else {
                    SimilarityClusterMemberPreviewLines(
                        members = previewMembers.map { member -> member.metadata },
                        showFullPaths = showFullPaths,
                        durationMillisByNormalizedPath = durationMillisByNormalizedPath,
                        preserveOrder = durationNeighborExplanation != null
                    )
                    val remaining = (cluster.fileCount - similarityClusterPreviewDisplayCount(previewMembers.map { it.metadata }))
                        .coerceAtLeast(0)
                    if (remaining > 0) {
                        Text(
                            text = "+${remaining} more...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SimilarityClusterDetailOverviewCard(
    setting: SimilaritySettingEntity,
    cluster: SimilarityClusterEntity,
    members: List<SimilarityClusterMember>,
    imageLoader: ImageLoader,
    rememberedPreviewCache: MutableMap<String, ImageBitmap>,
    keepLoadedThumbnailsInMemory: Boolean,
    previewHeight: Dp,
    previewMemoryKey: String,
    loadedCount: Int,
    memberLoading: Boolean,
    showFullPaths: Boolean,
    exactHashExplanation: ExactThumbnailClusterExplanation?,
    durationNeighborExplanation: DurationNeighborClusterExplanation?,
    sortingEnabled: Boolean,
    sortKey: ResultGroupMemberSortKey,
    sortDirection: SortDirection,
    onApplySort: (ResultGroupMemberSortKey, SortDirection) -> Unit
) {
    val metadataMembers = members.map { member -> member.metadata }
    val previewCandidates = metadataMembers
        .map { file -> file.normalizedPath }
        .filter(::isMediaFile)
    val durationMillisByNormalizedPath = similarityMemberDurationMap(members)
    val title = if (durationNeighborExplanation != null) "List detail" else "Group detail"
    val summaryLines = similarityClusterDetailSummaryLines(
        setting = setting,
        cluster = cluster,
        exactHashExplanation = exactHashExplanation,
        durationNeighborExplanation = durationNeighborExplanation
    )

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            if (previewCandidates.isNotEmpty()) {
                GroupPreviewThumbnail(
                    candidatePaths = previewCandidates,
                    previewMemoryKey = previewMemoryKey,
                    rememberedPreviewCache = rememberedPreviewCache,
                    imageLoader = imageLoader,
                    keepLoadedInMemory = keepLoadedThumbnailsInMemory,
                    contentDescription = "Similarity group preview",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(previewHeight)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "${cluster.fileCount} files | Total ${formatBytes(cluster.totalBytes)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (memberLoading && loadedCount == 0) {
                            "Loading 0/${cluster.fileCount} files"
                        } else {
                            "Loaded ${loadedCount.coerceAtMost(cluster.fileCount)}/${cluster.fileCount} files"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    summaryLines.forEach { line ->
                        Text(
                            text = line,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (metadataMembers.isNotEmpty()) {
                        SimilarityClusterMemberPreviewLines(
                            members = metadataMembers,
                            showFullPaths = showFullPaths,
                            durationMillisByNormalizedPath = durationMillisByNormalizedPath,
                            preserveOrder = durationNeighborExplanation != null
                        )
                    }
                }
                if (sortingEnabled) {
                    Spacer(modifier = Modifier.width(8.dp))
                    GroupMemberSortButton(
                        sortKey = sortKey,
                        sortDirection = sortDirection,
                        onApplySort = onApplySort
                    )
                }
            }
        }
    }
}

@Composable
private fun SimilarityMembersHeader(
    loadedCount: Int,
    totalCount: Int,
    loading: Boolean,
    sortEnabled: Boolean,
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
        if (sortEnabled) {
            GroupMemberSortButton(
                sortKey = sortKey,
                sortDirection = sortDirection,
                onApplySort = onApplySort
            )
        }
    }
}

@Composable
private fun DurationNeighborSortDirectionCard(
    direction: SortDirection,
    enabled: Boolean,
    onDirectionChange: (SortDirection) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(text = "Duration order", style = MaterialTheme.typography.titleSmall)
            Text(
                text = "Sort members by extracted video duration.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RadioOptionRow(
                    option = SortDirection.Asc,
                    selected = direction,
                    label = "Ascending",
                    onSelect = { selectedDirection ->
                        if (enabled) onDirectionChange(selectedDirection)
                    }
                )
                RadioOptionRow(
                    option = SortDirection.Desc,
                    selected = direction,
                    label = "Descending",
                    onSelect = { selectedDirection ->
                        if (enabled) onDirectionChange(selectedDirection)
                    }
                )
            }
        }
    }
}

@Composable
private fun SimilaritySelectionControls(
    statusText: String,
    allSelectedAcrossGroup: Boolean,
    selectAllIncludesNotLoaded: Boolean,
    deleting: Boolean,
    deleteEnabled: Boolean,
    onToggleSelectAll: () -> Unit,
    onDeleteSelected: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(text = statusText, style = MaterialTheme.typography.bodyMedium)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onToggleSelectAll,
                enabled = !deleting
            ) {
                Text(if (allSelectedAcrossGroup) "Deselect all" else "Select all")
            }
            OutlinedButton(
                onClick = onDeleteSelected,
                enabled = deleteEnabled && !deleting
            ) {
                Text(if (deleting) "Deleting..." else "Delete selected")
            }
        }
        if (selectAllIncludesNotLoaded) {
            Text(
                text = "Select all includes not-loaded files in delete queries.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SimilarityMemberCard(
    metadata: FileMetadata,
    deleted: Boolean,
    selected: Boolean,
    selectionMode: Boolean,
    imageLoader: ImageLoader,
    rememberedPreviewCache: MutableMap<String, ImageBitmap>,
    rememberedVideoPreviewCache: MutableMap<String, ImageBitmap>,
    keepLoadedThumbnailsInMemory: Boolean,
    keepLoadedVideoPreviewsInMemory: Boolean,
    snapVideoPreviewFramesToWidth: Boolean,
    videoPreviewLineCount: Int,
    videoPreviewFrameHeight: Dp,
    thumbnailSize: Dp,
    showVideoPreviews: Boolean,
    showVideoPreviewDurations: Boolean,
    showVideoPreviewResolutions: Boolean,
    durationMillis: Long?,
    showFullPath: Boolean,
    onOpen: () -> Unit,
    onToggleSelection: () -> Unit
) {
    val isVideo = isVideoFile(metadata.normalizedPath)
    val showMemberThumbnail = isMediaFile(metadata.normalizedPath)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {
                    if (selectionMode) {
                        onToggleSelection()
                    } else {
                        onOpen()
                    }
                },
                onLongClick = onToggleSelection
            ),
        colors = if (deleted) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        } else {
            CardDefaults.cardColors()
        }
    ) {
        Column(
            modifier = Modifier
                .padding(10.dp)
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (selectionMode) {
                    Checkbox(
                        checked = selected,
                        enabled = !deleted || selected,
                        onCheckedChange = { onToggleSelection() }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                if (showMemberThumbnail) {
                    GroupPreviewThumbnail(
                        candidatePaths = if (deleted) emptyList() else listOf(metadata.normalizedPath),
                        previewMemoryKey = "similarity-member:${metadata.normalizedPath}",
                        rememberedPreviewCache = rememberedPreviewCache,
                        imageLoader = imageLoader,
                        keepLoadedInMemory = keepLoadedThumbnailsInMemory,
                        modifier = Modifier.size(thumbnailSize),
                        contentDescription = "Member thumbnail"
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = formatPath(metadata.normalizedPath, showFullPath),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = if (deleted) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${formatBytesWithExact(metadata.sizeBytes)} | ${formatDate(metadata.lastModifiedMillis)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (deleted) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    durationMillis?.let { value ->
                        Text(
                            text = "Duration ${durationMillisLabel(value)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (deleted) {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }
            SimilarityClusterMemberVideoMetadata(
                visible = !showVideoPreviews &&
                    (showVideoPreviewDurations || showVideoPreviewResolutions) &&
                    isVideo &&
                    !deleted,
                filePath = metadata.normalizedPath,
                showDuration = showVideoPreviewDurations,
                showResolution = showVideoPreviewResolutions
            )
            SimilarityClusterMemberVideoPreview(
                visible = showVideoPreviews && showMemberThumbnail && isVideo && !deleted,
                filePath = metadata.normalizedPath,
                rememberedVideoPreviewCache = rememberedVideoPreviewCache,
                imageLoader = imageLoader,
                keepLoadedVideoPreviewsInMemory = keepLoadedVideoPreviewsInMemory,
                snapVideoPreviewFramesToWidth = snapVideoPreviewFramesToWidth,
                videoPreviewLineCount = videoPreviewLineCount,
                videoPreviewFrameHeight = videoPreviewFrameHeight,
                showDuration = showVideoPreviewDurations,
                showResolution = showVideoPreviewResolutions
            )
            if (deleted) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Deleted in this session",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}

@Composable
private fun SimilarityClusterMemberVideoMetadata(
    visible: Boolean,
    filePath: String,
    showDuration: Boolean,
    showResolution: Boolean
) {
    if (!visible) return
    Spacer(modifier = Modifier.height(8.dp))
    VideoMetadataLabelText(
        filePath = filePath,
        showDuration = showDuration,
        showResolution = showResolution,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun SimilarityClusterMemberVideoPreview(
    visible: Boolean,
    filePath: String,
    rememberedVideoPreviewCache: MutableMap<String, ImageBitmap>,
    imageLoader: ImageLoader,
    keepLoadedVideoPreviewsInMemory: Boolean,
    snapVideoPreviewFramesToWidth: Boolean,
    videoPreviewLineCount: Int,
    videoPreviewFrameHeight: Dp,
    showDuration: Boolean,
    showResolution: Boolean
) {
    if (!visible) return
    Spacer(modifier = Modifier.height(8.dp))
    VideoTimelinePreviewStrip(
        filePath = filePath,
        rememberedPreviewCache = rememberedVideoPreviewCache,
        imageLoader = imageLoader,
        keepLoadedInMemory = keepLoadedVideoPreviewsInMemory,
        snapToFillWidth = snapVideoPreviewFramesToWidth,
        lineCount = videoPreviewLineCount,
        frameHeight = videoPreviewFrameHeight,
        showDuration = showDuration,
        showResolution = showResolution,
        modifier = Modifier.fillMaxWidth()
    )
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

private fun settingGenerationSummary(setting: SimilaritySettingEntity): String {
    return if (setting.enabled) {
        "Enabled: scans generate this setting automatically. Update catches up from the current scan cache; Rebuild clears and recalculates it."
    } else {
        "Paused: scans skip this setting. Stored results remain available, and Update/Rebuild can still run manually."
    }
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

@Composable
private fun ExactHashReductionPreviewCard(exactHashExplanation: ExactThumbnailClusterExplanation?) {
    val samples = exactHashExplanation?.let(::exactHashReductionSamples).orEmpty()
    if (samples.isEmpty()) return

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text = "Reduction preview", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "These enlarged tiles show the exact reduced image values used for this similarity equality check.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            samples.forEach { sample ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ExactHashReductionSampleGrid(sample = sample)
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = sample.label,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = sample.signature,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExactHashReductionSampleGrid(sample: ExactHashReductionSample) {
    val tileSize = when (maxOf(sample.width, sample.height)) {
        1 -> 56.dp
        2 -> 28.dp
        else -> 18.dp
    }
    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
        (0 until sample.height).forEach { y ->
            Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                (0 until sample.width).forEach { x ->
                    val color = sample.colors.getOrNull(y * sample.width + x)
                        ?: ExactHashReductionColor(red = 0, green = 0, blue = 0)
                    Box(
                        modifier = Modifier
                            .size(tileSize)
                            .background(
                                Color(
                                    red = color.red,
                                    green = color.green,
                                    blue = color.blue
                                )
                            )
                    )
                }
            }
        }
    }
}

internal data class ExactThumbnailClusterExplanation(
    val mediaScope: String,
    val colorMode: String,
    val resize: String,
    val quantization: String,
    val frameSeconds: List<String>,
    val sampleSignatures: List<String>
)

internal fun exactThumbnailClusterExplanation(signature: String): ExactThumbnailClusterExplanation? {
    val parts = signature.split(":", limit = 7)
    if (parts.size != 7 || parts[0] != "thumb-v1") return null
    val mediaScope = parts[1].takeIf { it.isNotBlank() } ?: return null
    val colorMode = parts[2].takeIf { it == "gray" || it == "color" } ?: return null
    val resize = parts[3].takeIf { it.contains("x") } ?: return null
    val quantization = parts[4].takeIf { it.isNotBlank() } ?: return null
    val frameSeconds = parts[5]
        .split(',')
        .map { frame -> frame.trim() }
        .filter { frame -> frame.isNotEmpty() }
    val sampleSignatures = parts[6]
        .split('|')
        .map { sample -> sample.trim() }
        .filter { sample -> sample.isNotEmpty() }

    return ExactThumbnailClusterExplanation(
        mediaScope = mediaScope,
        colorMode = colorMode,
        resize = resize,
        quantization = quantization,
        frameSeconds = frameSeconds,
        sampleSignatures = sampleSignatures
    )
}

internal data class DurationClusterExplanation(
    val toleranceMillis: Long,
    val minDurationMillis: Long,
    val maxDurationMillis: Long
)

private fun durationClusterExplanation(signature: String): DurationClusterExplanation? {
    val parts = signature.split(":", limit = 3)
    if (parts.size != 3 || parts[0] != "duration-v1") return null
    val toleranceMillis = parts[1].toLongOrNull()?.coerceAtLeast(0L) ?: return null
    val minDurationMillis = parts[2].substringBefore('-').toLongOrNull()?.coerceAtLeast(0L) ?: return null
    val maxDurationMillis = parts[2].substringAfter('-', missingDelimiterValue = "")
        .toLongOrNull()
        ?.coerceAtLeast(minDurationMillis)
        ?: return null
    return DurationClusterExplanation(
        toleranceMillis = toleranceMillis,
        minDurationMillis = minDurationMillis,
        maxDurationMillis = maxDurationMillis
    )
}

internal data class DurationNeighborClusterExplanation(
    val toleranceMillis: Long,
    val minDurationMillis: Long,
    val maxDurationMillis: Long
)

internal fun durationNeighborClusterExplanation(signature: String): DurationNeighborClusterExplanation? {
    val parts = signature.split(":", limit = 3)
    if (parts.size != 3 || !isDurationNeighborListSignature(signature)) return null
    val toleranceMillis = parts[1].toLongOrNull()?.coerceAtLeast(0L) ?: return null
    val minDurationMillis = parts[2].substringBefore('-').toLongOrNull()?.coerceAtLeast(0L) ?: return null
    val maxDurationMillis = parts[2].substringAfter('-', missingDelimiterValue = "")
        .toLongOrNull()
        ?.coerceAtLeast(minDurationMillis)
        ?: return null
    return DurationNeighborClusterExplanation(
        toleranceMillis = toleranceMillis,
        minDurationMillis = minDurationMillis,
        maxDurationMillis = maxDurationMillis
    )
}

private fun similarityClusterDetailSummaryLines(
    setting: SimilaritySettingEntity,
    cluster: SimilarityClusterEntity,
    exactHashExplanation: ExactThumbnailClusterExplanation?,
    durationNeighborExplanation: DurationNeighborClusterExplanation?
): List<String> {
    val durationExplanation = durationClusterExplanation(cluster.clusterKey)
    return when {
        durationNeighborExplanation != null -> listOf(
            "List rule: duration-sorted neighbor filter",
            "Why included: the full candidate set is sorted by extracted duration, then only videos with a previous or next item inside the tolerance are shown.",
            "Visible duration span: ${durationMillisLabel(durationNeighborExplanation.minDurationMillis)} - ${durationMillisLabel(durationNeighborExplanation.maxDurationMillis)}",
            "Tolerance: ${durationMillisLabel(durationNeighborExplanation.toleranceMillis)}",
            "Order: sorted by extracted video duration",
            "Snapshot ${formatDate(cluster.updatedAtMillis)}"
        )
        exactHashExplanation != null -> listOf(
            "Group rule: exact thumbnail hash equality",
            "Why included: every member produced the same exact thumbnail signature.",
            "Media: ${mediaScopeLabel(exactHashExplanation.mediaScope)}",
            "Samples: ${framesLabel(exactHashExplanation)}",
            "Resize: ${exactHashExplanation.resize}",
            "Color mode: ${colorModeLabel(exactHashExplanation.colorMode)}",
            "Quantization: ${quantizationLabel(exactHashExplanation.quantization)}",
            "Sample signature values: ${sampleSignaturesLabel(exactHashExplanation.sampleSignatures)}",
            "Snapshot ${formatDate(cluster.updatedAtMillis)}"
        )
        durationExplanation != null -> listOf(
            "Group rule: duration tolerance window",
            "Why included: every member's extracted duration fits inside the configured tolerance window.",
            "Visible duration span: ${durationMillisLabel(durationExplanation.minDurationMillis)} - ${durationMillisLabel(durationExplanation.maxDurationMillis)}",
            "Tolerance: ${durationMillisLabel(durationExplanation.toleranceMillis)}",
            "Snapshot ${formatDate(cluster.updatedAtMillis)}"
        )
        else -> listOf(
            settingParametersSummary(setting),
            "Similarity signature ${cluster.clusterKey}",
            "Snapshot ${formatDate(cluster.updatedAtMillis)}"
        )
    }
}

internal fun exactHashClusterSummary(explanation: ExactThumbnailClusterExplanation): String {
    return "Exact hash: ${mediaScopeLabel(explanation.mediaScope)}, ${framesLabel(explanation)}, ${explanation.resize}, " +
        "${colorModeLabel(explanation.colorMode)}, ${quantizationLabel(explanation.quantization)}"
}

private fun durationClusterSummary(explanation: DurationClusterExplanation): String {
    return "Duration window: ${durationMillisLabel(explanation.minDurationMillis)} - " +
        "${durationMillisLabel(explanation.maxDurationMillis)}, tolerance ${durationMillisLabel(explanation.toleranceMillis)}"
}

internal fun durationNeighborClusterSummary(explanation: DurationNeighborClusterExplanation): String {
    return "Duration neighbor list: ${durationMillisLabel(explanation.minDurationMillis)} - " +
        "${durationMillisLabel(explanation.maxDurationMillis)}, tolerance ${durationMillisLabel(explanation.toleranceMillis)}"
}

internal fun similarityClusterPreviewLineTexts(
    members: List<FileMetadata>,
    showFullPaths: Boolean,
    durationMillisByNormalizedPath: Map<String, Long> = emptyMap(),
    itemsPerLine: Int = SIMILARITY_CLUSTER_PREVIEW_ITEMS_PER_LINE,
    maxItems: Int = SIMILARITY_CLUSTER_PREVIEW_TEXT_MEMBER_LIMIT,
    preserveOrder: Boolean = false
): List<String> {
    val orderedMembers = if (preserveOrder) {
        members
    } else {
        members.sortedBy { file -> file.normalizedPath }
    }
    return orderedMembers
        .take(maxItems.coerceAtLeast(0))
        .chunked(itemsPerLine.coerceAtLeast(1))
        .map { row ->
            row.joinToString("  |  ") { file ->
                similarityClusterPreviewMemberText(
                    file = file,
                    showFullPaths = showFullPaths,
                    durationMillisByNormalizedPath = durationMillisByNormalizedPath
                )
            }
        }
}

private fun similarityMemberDurationMap(
    members: List<SimilarityClusterMember>
): Map<String, Long> {
    return members.mapNotNull { member ->
        member.durationMillis?.let { durationMillis ->
            member.metadata.normalizedPath to durationMillis
        }
    }.toMap()
}

private fun similarityClusterPreviewMemberText(
    file: FileMetadata,
    showFullPaths: Boolean,
    durationMillisByNormalizedPath: Map<String, Long>
): String {
    val path = formatPath(file.normalizedPath, showFullPaths)
    val durationMillis = durationMillisByNormalizedPath[file.normalizedPath] ?: return path
    return "${durationMillisLabel(durationMillis)} | $path"
}

internal fun similarityClusterPreviewDisplayCount(
    members: List<FileMetadata>,
    maxItems: Int = SIMILARITY_CLUSTER_PREVIEW_TEXT_MEMBER_LIMIT
): Int {
    return members.size.coerceAtMost(maxItems.coerceAtLeast(0))
}

internal data class ExactHashReductionSample(
    val label: String,
    val signature: String,
    val width: Int,
    val height: Int,
    val colors: List<ExactHashReductionColor>
)

internal data class ExactHashReductionColor(
    val red: Int,
    val green: Int,
    val blue: Int
)

internal fun exactHashReductionSamples(
    explanation: ExactThumbnailClusterExplanation
): List<ExactHashReductionSample> {
    val configuredGrid = exactHashReductionGridSize(explanation.resize)
    return explanation.sampleSignatures.mapIndexedNotNull { index, signature ->
        val colors = exactHashReductionColors(
            colorMode = explanation.colorMode,
            quantization = explanation.quantization,
            signature = signature
        )
        if (colors.isEmpty()) return@mapIndexedNotNull null
        val configuredPixelCount = configuredGrid.width * configuredGrid.height
        val sampleWidth = if (configuredPixelCount == colors.size) configuredGrid.width else colors.size
        val sampleHeight = if (configuredPixelCount == colors.size) configuredGrid.height else 1
        ExactHashReductionSample(
            label = exactHashReductionSampleLabel(
                explanation = explanation,
                index = index
            ),
            signature = signature,
            width = sampleWidth.coerceAtLeast(1),
            height = sampleHeight.coerceAtLeast(1),
            colors = colors
        )
    }
}

internal data class ExactHashReductionGridSize(
    val width: Int,
    val height: Int
)

internal fun exactHashReductionGridSize(resize: String): ExactHashReductionGridSize {
    val parts = resize.split('x', limit = 2)
    val width = parts.getOrNull(0)?.toIntOrNull()?.coerceAtLeast(1) ?: 1
    val height = parts.getOrNull(1)?.toIntOrNull()?.coerceAtLeast(1) ?: 1
    return ExactHashReductionGridSize(width = width, height = height)
}

internal fun exactHashReductionColor(
    colorMode: String,
    quantization: String,
    signature: String
): ExactHashReductionColor? {
    return exactHashReductionColors(
        colorMode = colorMode,
        quantization = quantization,
        signature = signature
    ).firstOrNull()
}

internal fun exactHashReductionColors(
    colorMode: String,
    quantization: String,
    signature: String
): List<ExactHashReductionColor> {
    return splitPixelSignatures(signature).mapNotNull { pixelSignature ->
        exactHashReductionPixelColor(
            colorMode = colorMode,
            quantization = quantization,
            signature = pixelSignature
        )
    }
}

private fun exactHashReductionPixelColor(
    colorMode: String,
    quantization: String,
    signature: String
): ExactHashReductionColor? {
    val trimmedSignature = signature.trim()
    val quantizationLevels = quantization
        .takeIf { value -> value.startsWith("q") }
        ?.drop(1)
        ?.toIntOrNull()
        ?.coerceAtLeast(2)
    return if (colorMode == "gray") {
        val gray = if (quantizationLevels == null) {
            parseHexChannel(trimmedSignature)
        } else {
            parseHexBucket(trimmedSignature)?.let { bucket -> bucketToChannel(bucket, quantizationLevels) }
        } ?: return null
        ExactHashReductionColor(red = gray, green = gray, blue = gray)
    } else if (colorMode == "color") {
        if (quantizationLevels == null) {
            if (trimmedSignature.length < 6) return null
            val red = parseHexChannel(trimmedSignature.substring(0, 2)) ?: return null
            val green = parseHexChannel(trimmedSignature.substring(2, 4)) ?: return null
            val blue = parseHexChannel(trimmedSignature.substring(4, 6)) ?: return null
            ExactHashReductionColor(red = red, green = green, blue = blue)
        } else {
            val channelWidth = quantizedChannelHexWidth(quantizationLevels)
            if (trimmedSignature.length < channelWidth * 3) return null
            val red = parseHexBucket(trimmedSignature.substring(0, channelWidth))
                ?.let { bucket -> bucketToChannel(bucket, quantizationLevels) }
                ?: return null
            val green = parseHexBucket(trimmedSignature.substring(channelWidth, channelWidth * 2))
                ?.let { bucket -> bucketToChannel(bucket, quantizationLevels) }
                ?: return null
            val blue = parseHexBucket(trimmedSignature.substring(channelWidth * 2, channelWidth * 3))
                ?.let { bucket -> bucketToChannel(bucket, quantizationLevels) }
                ?: return null
            ExactHashReductionColor(red = red, green = green, blue = blue)
        }
    } else {
        null
    }
}

private fun splitPixelSignatures(signature: String): List<String> {
    return if (signature.contains(',')) {
        signature.split(',')
            .map { part -> part.trim() }
            .filter { part -> part.isNotEmpty() }
    } else {
        listOf(signature.trim()).filter { part -> part.isNotEmpty() }
    }
}

private fun exactHashReductionSampleLabel(
    explanation: ExactThumbnailClusterExplanation,
    index: Int
): String {
    if (explanation.mediaScope == "image") return "Reduced image"
    val frameSecond = explanation.frameSeconds.getOrNull(index)
    return frameSecond?.let { second -> "Reduced frame ${second}s" } ?: "Reduced sample ${index + 1}"
}

private fun parseHexChannel(value: String): Int? {
    return value.toIntOrNull(radix = 16)?.coerceIn(0, 255)
}

private fun parseHexBucket(value: String): Int? {
    return value.toIntOrNull(radix = 16)?.coerceAtLeast(0)
}

private fun bucketToChannel(bucket: Int, levels: Int): Int {
    val normalizedLevels = levels.coerceAtLeast(2)
    return ((bucket.coerceIn(0, normalizedLevels - 1) * 255) / (normalizedLevels - 1))
        .coerceIn(0, 255)
}

private fun quantizedChannelHexWidth(levels: Int): Int {
    return (levels.coerceAtLeast(2) - 1).toString(16).length
}

private fun mediaScopeLabel(value: String): String {
    return when (value) {
        "video" -> "Video"
        "image" -> "Image"
        else -> value
    }
}

private fun framesLabel(explanation: ExactThumbnailClusterExplanation): String {
    if (explanation.mediaScope == "image") return "image pixels"
    if (explanation.frameSeconds.isEmpty()) return "no configured frame seconds"
    return explanation.frameSeconds.joinToString(", ") { second -> "${second}s" }
}

private fun colorModeLabel(value: String): String {
    return when (value) {
        "gray" -> "grayscale"
        "color" -> "color"
        else -> value
    }
}

private fun durationMillisLabel(value: Long): String {
    val safeValue = value.coerceAtLeast(0L)
    val seconds = safeValue / 1_000L
    val millis = safeValue % 1_000L
    return if (millis == 0L) {
        "${seconds}s"
    } else {
        "$seconds.${millis.toString().padStart(3, '0')}s"
    }
}

private fun quantizationLabel(value: String): String {
    return if (value == "raw") {
        "raw pixels"
    } else if (value.startsWith("q")) {
        "${value.drop(1)} levels"
    } else {
        value
    }
}

private fun sampleSignaturesLabel(values: List<String>): String {
    if (values.isEmpty()) return "none"
    return values.joinToString(" | ") { value ->
        value.take(SIMILARITY_SIGNATURE_SAMPLE_DISPLAY_LIMIT)
    }
}

private fun isDurationNeighborListSignature(signature: String): Boolean {
    return signature.startsWith("duration-neighbor-list-v1:") ||
        signature.startsWith("duration-neighbor-v1:")
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

internal fun sortSimilarityClusters(
    clusters: List<SimilarityClusterEntity>,
    sortKey: SimilarityClusterSortKey,
    direction: SortDirection
): List<SimilarityClusterEntity> {
    return clusters.sortedWith { left, right ->
        val primary = when (sortKey) {
            SimilarityClusterSortKey.FileCount -> compareValues(left.fileCount, right.fileCount)
            SimilarityClusterSortKey.TotalSize -> compareValues(left.totalBytes, right.totalBytes)
        }
        val secondary = when (sortKey) {
            SimilarityClusterSortKey.FileCount -> compareValues(left.totalBytes, right.totalBytes)
            SimilarityClusterSortKey.TotalSize -> compareValues(left.fileCount, right.fileCount)
        }
        val sortedComparison = if (primary != 0) primary else secondary
        val directedComparison = if (direction == SortDirection.Asc) {
            sortedComparison
        } else {
            -sortedComparison
        }

        if (directedComparison != 0) {
            directedComparison
        } else {
            left.clusterKey.compareTo(right.clusterKey)
        }
    }
}

internal fun sortSimilarityClusterMembers(
    members: List<SimilarityClusterMember>,
    durationNeighborMode: Boolean,
    durationDirection: SortDirection,
    sortKey: ResultGroupMemberSortKey,
    sortDirection: SortDirection
): List<SimilarityClusterMember> {
    if (durationNeighborMode) {
        val comparator = compareBy<SimilarityClusterMember>(
            { member -> member.durationMillis ?: Long.MAX_VALUE },
            { member -> member.metadata.normalizedPath }
        )
        return if (durationDirection == SortDirection.Asc) {
            members.sortedWith(comparator)
        } else {
            members.sortedWith(comparator.reversed())
        }
    }

    val memberByPath = members.associateBy { member -> member.metadata.normalizedPath }
    return sortGroupMembers(
        members = members.map { member -> member.metadata },
        sortKey = sortKey,
        direction = sortDirection
    ).mapNotNull { metadata -> memberByPath[metadata.normalizedPath] }
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
