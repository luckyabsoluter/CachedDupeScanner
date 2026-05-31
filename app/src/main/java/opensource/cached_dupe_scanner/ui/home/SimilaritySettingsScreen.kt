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
import opensource.cached_dupe_scanner.core.DEFAULT_SIMILARITY_MIN_SIZE_BYTES
import opensource.cached_dupe_scanner.core.DurationNeighborListStep
import opensource.cached_dupe_scanner.core.DurationToleranceStep
import opensource.cached_dupe_scanner.core.ExactThumbnailHashStep
import opensource.cached_dupe_scanner.core.SimilarityMediaScope
import opensource.cached_dupe_scanner.core.similarityMethodLabel
import opensource.cached_dupe_scanner.notifications.TaskNotificationController
import opensource.cached_dupe_scanner.storage.SimilaritySettingsRepository
import opensource.cached_dupe_scanner.tasks.TaskArea
import opensource.cached_dupe_scanner.tasks.TaskCoordinator
import opensource.cached_dupe_scanner.ui.components.AppTopBar
import opensource.cached_dupe_scanner.ui.components.ScreenScrollColumn
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
    val statusText = remember { mutableStateOf("No similarity maintenance running.") }
    val activeTask = taskCoordinator.activeTask(TaskArea.Similarity)
    val displayedStatus = activeTask?.detail ?: statusText.value

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

    fun createExact(width: Int, height: Int) {
        scope.launch {
            withContext(Dispatchers.IO) {
                repository.createExactThumbnailSetting(
                    mediaScope = SimilarityMediaScope.Video,
                    minSizeBytes = DEFAULT_SIMILARITY_MIN_SIZE_BYTES,
                    step = ExactThumbnailHashStep(
                        frameSeconds = listOf(0, 1, 10),
                        resizeWidthPx = width,
                        resizeHeightPx = height,
                        quantizationLevels = 16,
                        grayscale = false
                    )
                )
            }
            refresh()
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
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { createExact(width = 1, height = 1) }) {
                            Text("Exact 1x1")
                        }
                        OutlinedButton(onClick = { createExact(width = 2, height = 2) }) {
                            Text("Exact 2x2")
                        }
                        OutlinedButton(onClick = { createExact(width = 3, height = 3) }) {
                            Text("Exact 3x3")
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        repository.createDurationToleranceSetting(
                                            minSizeBytes = DEFAULT_SIMILARITY_MIN_SIZE_BYTES,
                                            step = DurationToleranceStep(toleranceSeconds = 1)
                                        )
                                    }
                                    refresh()
                                }
                            }
                        ) {
                            Text("Duration 1s")
                        }
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        repository.createDurationNeighborListSetting(
                                            minSizeBytes = DEFAULT_SIMILARITY_MIN_SIZE_BYTES,
                                            step = DurationNeighborListStep(toleranceSeconds = 1)
                                        )
                                    }
                                    refresh()
                                }
                            }
                        ) {
                            Text("Neighbor 1s")
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
    onClear: () -> Unit
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
            clusters.take(3).forEach { cluster ->
                Text(
                    text = "${cluster.clusterKey} • ${cluster.fileCount} files • ${cluster.totalBytes} bytes",
                    style = MaterialTheme.typography.bodySmall
                )
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

