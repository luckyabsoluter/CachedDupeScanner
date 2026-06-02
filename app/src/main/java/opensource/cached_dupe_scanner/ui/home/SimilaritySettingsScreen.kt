package opensource.cached_dupe_scanner.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import opensource.cached_dupe_scanner.cache.SimilarityClusterEntity
import opensource.cached_dupe_scanner.cache.SimilaritySettingEntity
import opensource.cached_dupe_scanner.core.FileMetadata
import opensource.cached_dupe_scanner.core.SimilarityMediaScope
import opensource.cached_dupe_scanner.core.similarityMethodLabel
import opensource.cached_dupe_scanner.notifications.TaskNotificationController
import opensource.cached_dupe_scanner.storage.SimilarityClusterMember
import opensource.cached_dupe_scanner.storage.SimilaritySettingsRepository
import opensource.cached_dupe_scanner.tasks.TaskArea
import opensource.cached_dupe_scanner.tasks.TaskCoordinator
import opensource.cached_dupe_scanner.ui.components.AppTopBar
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

private enum class SimilaritySettingsPane {
    List,
    Create,
    ExactThumbnail,
    DurationTolerance,
    DurationNeighbor,
    SettingDetail,
    ClusterDetail
}

@Composable
fun SimilaritySettingsScreen(
    repository: SimilaritySettingsRepository,
    appScope: CoroutineScope,
    taskCoordinator: TaskCoordinator,
    notificationController: TaskNotificationController,
    refreshVersion: Int,
    onChanged: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val settings = remember { mutableStateListOf<SimilaritySettingEntity>() }
    val clustersBySetting = remember { mutableStateMapOf<Long, List<SimilarityClusterEntity>>() }
    val selectedClusterMembers = remember { mutableStateListOf<SimilarityClusterMember>() }
    var pane by remember { mutableStateOf(SimilaritySettingsPane.List) }
    var selectedSettingId by remember { mutableStateOf<Long?>(null) }
    var selectedCluster by remember { mutableStateOf<SimilarityClusterEntity?>(null) }
    var memberLoading by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("No similarity maintenance running.") }
    var minSizeInput by remember { mutableStateOf("100") }
    var minSizeUnit by remember { mutableStateOf(SimilaritySizeUnit.MB) }
    var mediaScope by remember { mutableStateOf(SimilarityMediaScope.Video) }
    var frameSecondsInput by remember { mutableStateOf("0,1,10") }
    var resizeWidthInput by remember { mutableStateOf("1") }
    var resizeHeightInput by remember { mutableStateOf("1") }
    var quantizationEnabled by remember { mutableStateOf(true) }
    var quantizationInput by remember { mutableStateOf("16") }
    var grayscale by remember { mutableStateOf(false) }
    var durationToleranceInput by remember { mutableStateOf("1") }
    var durationToleranceUnit by remember { mutableStateOf(SimilarityTimeUnit.S) }
    val activeTask = taskCoordinator.activeTask(TaskArea.Similarity)
    val displayedStatus = activeTask?.detail ?: statusText
    val selectedSetting = selectedSettingId?.let { id ->
        settings.firstOrNull { setting -> setting.settingId == id }
    }
    val selectedSettingClusters = selectedSettingId
        ?.let { id -> clustersBySetting[id].orEmpty() }
        .orEmpty()
    val minSizeBytes = parsedMinSizeBytes(minSizeInput, minSizeUnit)
    val exactStep = parsedExactThumbnailStep(
        frameSecondsInput = frameSecondsInput,
        resizeWidthInput = resizeWidthInput,
        resizeHeightInput = resizeHeightInput,
        quantizationEnabled = quantizationEnabled,
        quantizationInput = quantizationInput,
        grayscale = grayscale
    )
    val durationToleranceStep = parsedDurationToleranceStep(
        input = durationToleranceInput,
        unit = durationToleranceUnit
    )
    val durationNeighborStep = parsedDurationNeighborListStep(
        input = durationToleranceInput,
        unit = durationToleranceUnit
    )

    fun refresh(preferredSettingId: Long? = selectedSettingId) {
        scope.launch {
            val loaded = withContext(Dispatchers.IO) {
                repository.listSettings()
            }
            val clusterRows = withContext(Dispatchers.IO) {
                loaded.associate { setting ->
                    setting.settingId to repository.listClusters(setting.settingId)
                }
            }
            settings.clear()
            settings.addAll(loaded)
            clustersBySetting.clear()
            clustersBySetting.putAll(clusterRows)
            if (preferredSettingId != null && loaded.none { setting -> setting.settingId == preferredSettingId }) {
                selectedSettingId = null
                selectedCluster = null
                selectedClusterMembers.clear()
                pane = SimilaritySettingsPane.List
            }
        }
    }

    fun openListPane() {
        pane = SimilaritySettingsPane.List
        selectedSettingId = null
        selectedCluster = null
        selectedClusterMembers.clear()
    }

    fun openCreatePane() {
        pane = SimilaritySettingsPane.Create
        selectedSettingId = null
        selectedCluster = null
        selectedClusterMembers.clear()
    }

    fun openSetting(setting: SimilaritySettingEntity) {
        selectedSettingId = setting.settingId
        selectedCluster = null
        selectedClusterMembers.clear()
        pane = SimilaritySettingsPane.SettingDetail
    }

    fun openCluster(setting: SimilaritySettingEntity, cluster: SimilarityClusterEntity) {
        selectedSettingId = setting.settingId
        selectedCluster = cluster
        selectedClusterMembers.clear()
        memberLoading = true
        pane = SimilaritySettingsPane.ClusterDetail
        scope.launch {
            val members = withContext(Dispatchers.IO) {
                repository.listClusterMembers(cluster.clusterId)
            }
            selectedClusterMembers.clear()
            selectedClusterMembers.addAll(members)
            memberLoading = false
        }
    }

    fun runMaintenance(settingId: Long?, rebuild: Boolean) {
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
                refresh(settingId)
            }
        )
        if (!started) {
            statusText = "Similarity maintenance is already running."
        }
    }

    fun createExact() {
        if (exactStep.frameSeconds.isEmpty()) {
            statusText = "Add at least one frame timestamp."
            return
        }
        scope.launch {
            val created = withContext(Dispatchers.IO) {
                repository.createExactThumbnailSetting(
                    mediaScope = mediaScope,
                    minSizeBytes = minSizeBytes,
                    step = exactStep
                )
            }
            onChanged()
            selectedSettingId = created.settingId
            pane = SimilaritySettingsPane.SettingDetail
            refresh(created.settingId)
        }
    }

    fun createDurationTolerance() {
        scope.launch {
            val created = withContext(Dispatchers.IO) {
                repository.createDurationToleranceSetting(
                    minSizeBytes = minSizeBytes,
                    step = durationToleranceStep
                )
            }
            onChanged()
            selectedSettingId = created.settingId
            pane = SimilaritySettingsPane.SettingDetail
            refresh(created.settingId)
        }
    }

    fun createDurationNeighbor() {
        scope.launch {
            val created = withContext(Dispatchers.IO) {
                repository.createDurationNeighborListSetting(
                    minSizeBytes = minSizeBytes,
                    step = durationNeighborStep
                )
            }
            onChanged()
            selectedSettingId = created.settingId
            pane = SimilaritySettingsPane.SettingDetail
            refresh(created.settingId)
        }
    }

    fun detailBack() {
        when (pane) {
            SimilaritySettingsPane.List -> onBack()
            SimilaritySettingsPane.Create -> openListPane()
            SimilaritySettingsPane.ExactThumbnail,
            SimilaritySettingsPane.DurationTolerance,
            SimilaritySettingsPane.DurationNeighbor -> pane = SimilaritySettingsPane.Create
            SimilaritySettingsPane.SettingDetail -> openListPane()
            SimilaritySettingsPane.ClusterDetail -> {
                selectedCluster = null
                selectedClusterMembers.clear()
                pane = SimilaritySettingsPane.SettingDetail
            }
        }
    }

    BackHandler(enabled = pane != SimilaritySettingsPane.List) {
        detailBack()
    }

    LaunchedEffect(refreshVersion) {
        refresh()
    }

    ScreenScrollColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        when (pane) {
            SimilaritySettingsPane.List -> {
                item(key = "top_bar") {
                    AppTopBar(
                        title = "Similarity settings",
                        onBack = onBack
                    )
                }
                item(key = "new_setting") {
                    Button(
                        onClick = ::openCreatePane,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("New setting")
                    }
                }
                item(key = "maintenance") {
                    SimilarityMaintenanceCard(
                        statusText = displayedStatus,
                        running = activeTask != null,
                        onRunEnabled = { runMaintenance(settingId = null, rebuild = false) },
                        onRebuildEnabled = { runMaintenance(settingId = null, rebuild = true) },
                        onClearAll = {
                            scope.launch {
                                withContext(Dispatchers.IO) { repository.clearAllResults() }
                                onChanged()
                                refresh()
                            }
                        }
                    )
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
                            onOpen = { openSetting(setting) }
                        )
                    }
                }
            }
            SimilaritySettingsPane.Create -> {
                item(key = "top_bar") {
                    AppTopBar(
                        title = "New similarity setting",
                        onBack = ::openListPane
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
                        onOpen = { pane = SimilaritySettingsPane.ExactThumbnail }
                    )
                }
                item(key = "method_duration") {
                    SimilarityMethodCard(
                        title = "Duration tolerance",
                        description = "Groups video candidates whose durations fit within one tolerance window.",
                        onOpen = { pane = SimilaritySettingsPane.DurationTolerance }
                    )
                }
                item(key = "method_neighbor") {
                    SimilarityMethodCard(
                        title = "Duration neighbor list",
                        description = "Builds a duration-sorted video list and keeps neighbors inside the configured tolerance.",
                        onOpen = { pane = SimilaritySettingsPane.DurationNeighbor }
                    )
                }
            }
            SimilaritySettingsPane.ExactThumbnail -> {
                item(key = "top_bar") {
                    AppTopBar(
                        title = "Exact thumbnail setting",
                        onBack = { pane = SimilaritySettingsPane.Create }
                    )
                }
                item(key = "exact_form") {
                    ExactThumbnailSettingForm(
                        minSizeInput = minSizeInput,
                        onMinSizeInputChange = { minSizeInput = sanitizeNumberDraftInput(it) },
                        minSizeUnit = minSizeUnit,
                        onMinSizeUnitChange = { minSizeUnit = it },
                        mediaScope = mediaScope,
                        onMediaScopeChange = { mediaScope = it },
                        frameSecondsInput = frameSecondsInput,
                        onFrameSecondsInputChange = { frameSecondsInput = sanitizeFrameSecondsInput(it) },
                        resizeWidthInput = resizeWidthInput,
                        onResizeWidthInputChange = { resizeWidthInput = sanitizeNumberDraftInput(it) },
                        resizeHeightInput = resizeHeightInput,
                        onResizeHeightInputChange = { resizeHeightInput = sanitizeNumberDraftInput(it) },
                        quantizationEnabled = quantizationEnabled,
                        onQuantizationEnabledChange = { quantizationEnabled = it },
                        quantizationInput = quantizationInput,
                        onQuantizationInputChange = { quantizationInput = sanitizeNumberDraftInput(it) },
                        grayscale = grayscale,
                        onGrayscaleChange = { grayscale = it },
                        statusText = displayedStatus,
                        onCreate = ::createExact
                    )
                }
            }
            SimilaritySettingsPane.DurationTolerance -> {
                item(key = "top_bar") {
                    AppTopBar(
                        title = "Duration tolerance setting",
                        onBack = { pane = SimilaritySettingsPane.Create }
                    )
                }
                item(key = "duration_form") {
                    DurationSettingForm(
                        description = "Tolerance is the maximum duration gap inside one group. Use 0 for exact millisecond duration matches.",
                        buttonText = "Create duration tolerance setting",
                        minSizeInput = minSizeInput,
                        onMinSizeInputChange = { minSizeInput = sanitizeNumberDraftInput(it) },
                        minSizeUnit = minSizeUnit,
                        onMinSizeUnitChange = { minSizeUnit = it },
                        toleranceInput = durationToleranceInput,
                        onToleranceInputChange = { durationToleranceInput = sanitizeNumberDraftInput(it) },
                        toleranceUnit = durationToleranceUnit,
                        onToleranceUnitChange = { durationToleranceUnit = it },
                        statusText = displayedStatus,
                        onCreate = ::createDurationTolerance
                    )
                }
            }
            SimilaritySettingsPane.DurationNeighbor -> {
                item(key = "top_bar") {
                    AppTopBar(
                        title = "Duration neighbor setting",
                        onBack = { pane = SimilaritySettingsPane.Create }
                    )
                }
                item(key = "neighbor_form") {
                    DurationSettingForm(
                        description = "Tolerance is the maximum duration gap between adjacent sorted videos. Isolated videos are omitted.",
                        buttonText = "Create duration neighbor setting",
                        minSizeInput = minSizeInput,
                        onMinSizeInputChange = { minSizeInput = sanitizeNumberDraftInput(it) },
                        minSizeUnit = minSizeUnit,
                        onMinSizeUnitChange = { minSizeUnit = it },
                        toleranceInput = durationToleranceInput,
                        onToleranceInputChange = { durationToleranceInput = sanitizeNumberDraftInput(it) },
                        toleranceUnit = durationToleranceUnit,
                        onToleranceUnitChange = { durationToleranceUnit = it },
                        statusText = displayedStatus,
                        onCreate = ::createDurationNeighbor
                    )
                }
            }
            SimilaritySettingsPane.SettingDetail -> {
                item(key = "top_bar") {
                    AppTopBar(
                        title = selectedSetting?.displayName ?: "Similarity setting",
                        onBack = ::openListPane
                    )
                }
                if (selectedSetting == null) {
                    item(key = "missing_setting") {
                        MissingSelectionCard(
                            message = "This similarity setting is no longer available.",
                            onBack = ::openListPane
                        )
                    }
                } else {
                    item(key = "setting_detail") {
                        SimilaritySettingDetailCard(
                            setting = selectedSetting,
                            clusterCount = selectedSettingClusters.size,
                            fileCount = selectedSettingClusters.sumOf { cluster -> cluster.fileCount },
                            statusText = displayedStatus,
                            running = activeTask != null,
                            onToggle = { enabled ->
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        repository.setEnabled(selectedSetting.settingId, enabled)
                                    }
                                    onChanged()
                                    refresh(selectedSetting.settingId)
                                }
                            },
                            onRun = { runMaintenance(selectedSetting.settingId, rebuild = false) },
                            onRebuild = { runMaintenance(selectedSetting.settingId, rebuild = true) },
                            onClear = {
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        repository.clearSettingResults(selectedSetting.settingId)
                                    }
                                    onChanged()
                                    refresh(selectedSetting.settingId)
                                }
                            }
                        )
                    }
                    item(key = "cluster_header") {
                        SimilarityGroupsHeader(
                            clusterCount = selectedSettingClusters.size,
                            fileCount = selectedSettingClusters.sumOf { cluster -> cluster.fileCount }
                        )
                    }
                    if (selectedSettingClusters.isEmpty()) {
                        item(key = "clusters_empty") {
                            Text(
                                text = "No similarity groups found for this setting.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    selectedSettingClusters.forEach { cluster ->
                        item(key = "cluster:${cluster.clusterId}") {
                            SimilarityClusterListCard(
                                cluster = cluster,
                                onOpenCluster = { openCluster(selectedSetting, cluster) }
                            )
                        }
                    }
                }
            }
            SimilaritySettingsPane.ClusterDetail -> {
                val cluster = selectedCluster
                item(key = "top_bar") {
                    AppTopBar(
                        title = "Similarity group",
                        onBack = {
                            selectedCluster = null
                            selectedClusterMembers.clear()
                            pane = SimilaritySettingsPane.SettingDetail
                        }
                    )
                }
                if (selectedSetting == null || cluster == null) {
                    item(key = "missing_cluster") {
                        MissingSelectionCard(
                            message = "This similarity group is no longer available.",
                            onBack = ::openListPane
                        )
                    }
                } else {
                    item(key = "cluster_detail") {
                        SimilarityClusterDetailCard(
                            setting = selectedSetting,
                            cluster = cluster,
                            members = selectedClusterMembers,
                            loading = memberLoading
                        )
                    }
                }
            }
        }
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                supportingText = { Text("Parsed: ${parsedFrameSeconds(frameSecondsInput).joinToString(", ")}") },
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = toleranceInput,
                    onValueChange = onToleranceInputChange,
                    label = { Text("Tolerance") },
                    modifier = Modifier.weight(1f)
                )
                SimilarityTimeUnit.entries.forEach { unit ->
                    ChoiceButton(
                        label = unit.label,
                        selected = toleranceUnit == unit,
                        onClick = { onToleranceUnitChange(unit) }
                    )
                }
            }
            Text(text = statusText, style = MaterialTheme.typography.bodySmall)
            Button(onClick = onCreate, modifier = Modifier.fillMaxWidth()) {
                Text(buttonText)
            }
        }
    }
}

@Composable
private fun SizeFloorControls(
    minSizeInput: String,
    onMinSizeInputChange: (String) -> Unit,
    minSizeUnit: SimilaritySizeUnit,
    onMinSizeUnitChange: (SimilaritySizeUnit) -> Unit
) {
    Text(text = "Size floor", style = MaterialTheme.typography.labelMedium)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = minSizeInput,
            onValueChange = onMinSizeInputChange,
            label = { Text("Min size") },
            modifier = Modifier.weight(1f)
        )
        SimilaritySizeUnit.entries.forEach { unit ->
            ChoiceButton(
                label = unit.label,
                selected = minSizeUnit == unit,
                onClick = { onMinSizeUnitChange(unit) }
            )
        }
    }
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
            Text(text = "Parameters: ${setting.paramsJson}", style = MaterialTheme.typography.bodySmall)
            Text(text = "$clusterCount groups, $fileCount files", style = MaterialTheme.typography.bodySmall)
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
private fun SimilarityClusterListCard(
    cluster: SimilarityClusterEntity,
    onOpenCluster: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenCluster)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "${cluster.fileCount} files, total ${formatBytes(cluster.totalBytes)}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Key ${cluster.clusterKey}",
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SimilarityClusterDetailCard(
    setting: SimilaritySettingEntity,
    cluster: SimilarityClusterEntity,
    members: List<SimilarityClusterMember>,
    loading: Boolean
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text = setting.displayName, style = MaterialTheme.typography.titleMedium)
            Text(
                text = "${cluster.fileCount} files, total ${formatBytes(cluster.totalBytes)}",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "Key ${cluster.clusterKey}",
                style = MaterialTheme.typography.bodySmall
            )
            if (loading) {
                Text(text = "Loading members...", style = MaterialTheme.typography.bodySmall)
            } else if (members.isEmpty()) {
                Text(text = "No active members.", style = MaterialTheme.typography.bodySmall)
            } else {
                members.forEachIndexed { index, member ->
                    SimilarityMemberRow(index = index + 1, metadata = member.metadata)
                }
            }
        }
    }
}

@Composable
private fun SimilarityMemberRow(index: Int, metadata: FileMetadata) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "$index. ${metadata.path.ifBlank { metadata.normalizedPath }}",
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "${formatBytes(metadata.sizeBytes)} | ${formatDate(metadata.lastModifiedMillis)}",
            style = MaterialTheme.typography.labelSmall
        )
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
