package opensource.cached_dupe_scanner.ui.home

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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    val statusText = remember { mutableStateOf("No similarity maintenance running.") }
    val selectedCluster = remember { mutableStateOf<SimilarityClusterEntity?>(null) }
    val selectedClusterSetting = remember { mutableStateOf<SimilaritySettingEntity?>(null) }
    val memberLoading = remember { mutableStateOf(false) }
    val minSizeInput = remember { mutableStateOf("100") }
    val minSizeUnit = remember { mutableStateOf(SimilaritySizeUnit.MB) }
    val mediaScope = remember { mutableStateOf(SimilarityMediaScope.Video) }
    val frameSecondsInput = remember { mutableStateOf("0,1,10") }
    val resizeWidthInput = remember { mutableStateOf("1") }
    val resizeHeightInput = remember { mutableStateOf("1") }
    val quantizationEnabled = remember { mutableStateOf(true) }
    val quantizationInput = remember { mutableStateOf("16") }
    val grayscale = remember { mutableStateOf(false) }
    val durationToleranceInput = remember { mutableStateOf("1") }
    val durationToleranceUnit = remember { mutableStateOf(SimilarityTimeUnit.S) }
    val activeTask = taskCoordinator.activeTask(TaskArea.Similarity)
    val displayedStatus = activeTask?.detail ?: statusText.value
    val minSizeBytes = parsedMinSizeBytes(minSizeInput.value, minSizeUnit.value)
    val exactStep = parsedExactThumbnailStep(
        frameSecondsInput = frameSecondsInput.value,
        resizeWidthInput = resizeWidthInput.value,
        resizeHeightInput = resizeHeightInput.value,
        quantizationEnabled = quantizationEnabled.value,
        quantizationInput = quantizationInput.value,
        grayscale = grayscale.value
    )
    val durationToleranceStep = parsedDurationToleranceStep(
        input = durationToleranceInput.value,
        unit = durationToleranceUnit.value
    )
    val durationNeighborStep = parsedDurationNeighborListStep(
        input = durationToleranceInput.value,
        unit = durationToleranceUnit.value
    )

    fun refresh() {
        scope.launch {
            val loaded = withContext(Dispatchers.IO) {
                repository.listSettings()
            }
            settings.clear()
            settings.addAll(loaded)
            clustersBySetting.clear()
            loaded.forEach { setting ->
                clustersBySetting[setting.settingId] = withContext(Dispatchers.IO) {
                    repository.listClusters(setting.settingId)
                }
            }
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
            onStatusText = { status -> statusText.value = status },
            onFinished = {
                onChanged()
                refresh()
            }
        )
        if (!started) {
            statusText.value = "Similarity maintenance is already running."
        }
    }

    fun createExact() {
        if (exactStep.frameSeconds.isEmpty()) {
            statusText.value = "Add at least one frame timestamp."
            return
        }
        scope.launch {
            withContext(Dispatchers.IO) {
                repository.createExactThumbnailSetting(
                    mediaScope = mediaScope.value,
                    minSizeBytes = minSizeBytes,
                    step = exactStep
                )
            }
            onChanged()
            refresh()
        }
    }

    fun createDurationTolerance() {
        scope.launch {
            withContext(Dispatchers.IO) {
                repository.createDurationToleranceSetting(
                    minSizeBytes = minSizeBytes,
                    step = durationToleranceStep
                )
            }
            onChanged()
            refresh()
        }
    }

    fun createDurationNeighbor() {
        scope.launch {
            withContext(Dispatchers.IO) {
                repository.createDurationNeighborListSetting(
                    minSizeBytes = minSizeBytes,
                    step = durationNeighborStep
                )
            }
            onChanged()
            refresh()
        }
    }

    fun openCluster(setting: SimilaritySettingEntity, cluster: SimilarityClusterEntity) {
        selectedCluster.value = cluster
        selectedClusterSetting.value = setting
        selectedClusterMembers.clear()
        memberLoading.value = true
        scope.launch {
            val members = withContext(Dispatchers.IO) {
                repository.listClusterMembers(cluster.clusterId)
            }
            selectedClusterMembers.clear()
            selectedClusterMembers.addAll(members)
            memberLoading.value = false
        }
    }

    LaunchedEffect(refreshVersion) {
        refresh()
    }

    ScreenScrollColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            AppTopBar(
                title = "Similarity settings",
                onBack = onBack
            )
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(text = "Create setting", style = MaterialTheme.typography.titleMedium)
                    Text(text = "Size floor", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = minSizeInput.value,
                            onValueChange = { minSizeInput.value = sanitizeNumberDraftInput(it) },
                            label = { Text("Min size") },
                            modifier = Modifier.weight(1f)
                        )
                        SimilaritySizeUnit.entries.forEach { unit ->
                            UnitButton(
                                label = unit.label,
                                selected = minSizeUnit.value == unit,
                                onClick = { minSizeUnit.value = unit }
                            )
                        }
                    }
                    Text(text = "Exact thumbnail", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        UnitButton(
                            label = "Video",
                            selected = mediaScope.value == SimilarityMediaScope.Video,
                            onClick = { mediaScope.value = SimilarityMediaScope.Video }
                        )
                        UnitButton(
                            label = "Image",
                            selected = mediaScope.value == SimilarityMediaScope.Image,
                            onClick = { mediaScope.value = SimilarityMediaScope.Image }
                        )
                    }
                    OutlinedTextField(
                        value = frameSecondsInput.value,
                        onValueChange = { frameSecondsInput.value = sanitizeFrameSecondsInput(it) },
                        label = { Text("Frame seconds") },
                        supportingText = { Text("Parsed: ${parsedFrameSeconds(frameSecondsInput.value).joinToString(", ")}") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = resizeWidthInput.value,
                            onValueChange = { resizeWidthInput.value = sanitizeNumberDraftInput(it) },
                            label = { Text("Width") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = resizeHeightInput.value,
                            onValueChange = { resizeHeightInput.value = sanitizeNumberDraftInput(it) },
                            label = { Text("Height") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "Quantization", modifier = Modifier.weight(1f))
                        Switch(
                            checked = quantizationEnabled.value,
                            onCheckedChange = { quantizationEnabled.value = it }
                        )
                    }
                    if (quantizationEnabled.value) {
                        OutlinedTextField(
                            value = quantizationInput.value,
                            onValueChange = { quantizationInput.value = sanitizeNumberDraftInput(it) },
                            label = { Text("Quantization levels") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "Grayscale", modifier = Modifier.weight(1f))
                        Switch(
                            checked = grayscale.value,
                            onCheckedChange = { grayscale.value = it }
                        )
                    }
                    Button(onClick = ::createExact, modifier = Modifier.fillMaxWidth()) {
                        Text("Create exact thumbnail setting")
                    }
                    Text(text = "Duration", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = durationToleranceInput.value,
                            onValueChange = { durationToleranceInput.value = sanitizeNumberDraftInput(it) },
                            label = { Text("Tolerance") },
                            modifier = Modifier.weight(1f)
                        )
                        SimilarityTimeUnit.entries.forEach { unit ->
                            UnitButton(
                                label = unit.label,
                                selected = durationToleranceUnit.value == unit,
                                onClick = { durationToleranceUnit.value = unit }
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = ::createDurationTolerance) {
                            Text("Create duration tolerance")
                        }
                        OutlinedButton(
                            onClick = ::createDurationNeighbor
                        ) {
                            Text("Create neighbor list")
                        }
                    }
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(text = "Maintenance", style = MaterialTheme.typography.titleMedium)
                    Text(text = displayedStatus, style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { runMaintenance(settingId = null, rebuild = false) }) {
                            Text("Run enabled")
                        }
                        OutlinedButton(onClick = { runMaintenance(settingId = null, rebuild = true) }) {
                            Text("Rebuild enabled")
                        }
                    }
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                withContext(Dispatchers.IO) { repository.clearAllResults() }
                                onChanged()
                                refresh()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Clear all similarity data")
                    }
                }
            }
        }

        settings.forEach { setting ->
            item(key = setting.settingId) {
                SimilaritySettingCard(
                    setting = setting,
                    clusters = clustersBySetting[setting.settingId].orEmpty(),
                    onToggle = { enabled ->
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                repository.setEnabled(setting.settingId, enabled)
                            }
                            onChanged()
                            refresh()
                        }
                    },
                    onRun = { runMaintenance(setting.settingId, rebuild = false) },
                    onRebuild = { runMaintenance(setting.settingId, rebuild = true) },
                    onClear = {
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                repository.clearSettingResults(setting.settingId)
                            }
                            onChanged()
                            refresh()
                        }
                    },
                    onOpenCluster = { cluster -> openCluster(setting, cluster) }
                )
            }
        }

        selectedCluster.value?.let { cluster ->
            item(key = "selected-cluster-${cluster.clusterId}") {
                SimilarityClusterDetailCard(
                    setting = selectedClusterSetting.value,
                    cluster = cluster,
                    members = selectedClusterMembers,
                    loading = memberLoading.value,
                    onClose = {
                        selectedCluster.value = null
                        selectedClusterSetting.value = null
                        selectedClusterMembers.clear()
                    }
                )
            }
        }
    }
}

@Composable
private fun SimilaritySettingCard(
    setting: SimilaritySettingEntity,
    clusters: List<SimilarityClusterEntity>,
    onToggle: (Boolean) -> Unit,
    onRun: () -> Unit,
    onRebuild: () -> Unit,
    onClear: () -> Unit,
    onOpenCluster: (SimilarityClusterEntity) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = setting.displayName, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "${similarityMethodLabel(setting.methodId)} • ${setting.mediaScope} • Min ${setting.minSizeBytes} bytes",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Switch(checked = setting.enabled, onCheckedChange = onToggle)
            }
            Text(
                text = "Clusters ${clusters.size} • Files ${clusters.sumOf { it.fileCount }}",
                style = MaterialTheme.typography.bodySmall
            )
            if (clusters.isEmpty()) {
                Text(
                    text = "No similarity groups.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            clusters.forEach { cluster ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${cluster.clusterKey} • ${cluster.fileCount} files • ${formatBytes(cluster.totalBytes)}",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedButton(onClick = { onOpenCluster(cluster) }) {
                        Text("Open")
                    }
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onRun) {
                    Text("Run")
                }
                OutlinedButton(onClick = onRebuild) {
                    Text("Rebuild")
                }
                OutlinedButton(onClick = onClear) {
                    Text("Clear")
                }
            }
        }
    }
}

@Composable
private fun SimilarityClusterDetailCard(
    setting: SimilaritySettingEntity?,
    cluster: SimilarityClusterEntity,
    members: List<SimilarityClusterMember>,
    loading: Boolean,
    onClose: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Similarity group", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = listOfNotNull(
                            setting?.displayName,
                            "${cluster.fileCount} files",
                            formatBytes(cluster.totalBytes)
                        ).joinToString(" • "),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                OutlinedButton(onClick = onClose) {
                    Text("Close")
                }
            }
            Text(text = cluster.clusterKey, style = MaterialTheme.typography.bodySmall)
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
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            text = "${formatBytes(metadata.sizeBytes)} • mtime ${metadata.lastModifiedMillis}",
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
private fun UnitButton(
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
