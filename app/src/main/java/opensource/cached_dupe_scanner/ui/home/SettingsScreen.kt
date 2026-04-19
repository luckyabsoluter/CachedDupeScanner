package opensource.cached_dupe_scanner.ui.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import opensource.cached_dupe_scanner.storage.AppSettings
import opensource.cached_dupe_scanner.storage.AppSettingsStore
import opensource.cached_dupe_scanner.storage.ScanTargetStore
import opensource.cached_dupe_scanner.ui.components.AppTopBar
import opensource.cached_dupe_scanner.ui.components.ScrollbarDefaults
import opensource.cached_dupe_scanner.ui.components.Spacing
import opensource.cached_dupe_scanner.ui.components.VerticalScrollbar
import androidx.compose.foundation.text.KeyboardOptions

@Composable
fun SettingsScreen(
    settingsStore: AppSettingsStore,
    onBack: () -> Unit,
    onSettingsChanged: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val settings = remember { mutableStateOf(settingsStore.load()) }
    val context = LocalContext.current
    val targetStore = remember { ScanTargetStore(context) }
    val message = remember { mutableStateOf<String?>(null) }
    val zeroSizeSection = zeroSizeSettingsSection(settings.value)
    val trashScanSection = trashScanSettingsSection(settings.value)
    val memoryOverlaySection = memoryOverlaySection(settings.value)
    val thumbnailMemorySection = thumbnailMemorySettingsSection(settings.value)
    val videoPreviewMemorySection = videoPreviewMemorySettingsSection(settings.value)
    val thumbnailSizeSection = thumbnailSizeSettingsSection()
    val videoPreviewSizeSection = videoPreviewSizeSettingsSection()
    val backupSection = backupSettingsSection()

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            val json = org.json.JSONObject()
                .put("settings", org.json.JSONObject(settingsStore.exportToJson()))
                .put("scanTargets", org.json.JSONObject(targetStore.exportToJson()))
                .toString()
            context.contentResolver.openOutputStream(uri)?.use { stream ->
                stream.write(json.toByteArray())
                stream.flush()
            }
            message.value = "Settings + targets exported."
        }.onFailure {
            message.value = "Export failed."
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            val json = context.contentResolver.openInputStream(uri)?.use { stream ->
                stream.bufferedReader().readText()
            }
            if (json.isNullOrBlank()) error("Empty file")
            val obj = org.json.JSONObject(json)
            val settingsObj = if (obj.has("settings")) obj.getJSONObject("settings") else obj
            val updated = settingsStore.importFromJson(settingsObj.toString())
            if (obj.has("scanTargets")) {
                targetStore.importFromJson(obj.getJSONObject("scanTargets").toString())
            }
            settings.value = updated
            onSettingsChanged?.invoke()
            message.value = "Settings + targets imported."
        }.onFailure {
            message.value = "Import failed."
        }
    }

    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .padding(Spacing.screenPadding)
                .padding(end = ScrollbarDefaults.ThumbWidth + 8.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AppTopBar(title = "Settings", onBack = onBack)

            SettingsSectionCard(section = zeroSizeSection) {
                zeroSizeSection.toggles.forEachIndexed { index, toggle ->
                    if (index > 0) {
                        HorizontalDivider()
                    }
                    ToggleSettingRow(
                        title = toggle.title,
                        description = toggle.description,
                        checked = toggle.checked,
                        onCheckedChange = { enabled ->
                            when (toggle.id) {
                                ToggleSettingId.SkipZeroSizeInDb -> {
                                    settingsStore.setSkipZeroSizeInDb(enabled)
                                    settings.value = settings.value.copy(skipZeroSizeInDb = enabled)
                                    onSettingsChanged?.invoke()
                                }
                                ToggleSettingId.SkipTrashBinContentsInScan -> Unit
                                ToggleSettingId.HideZeroSizeInResults -> {
                                    settingsStore.setHideZeroSizeInResults(enabled)
                                    settings.value = settings.value.copy(hideZeroSizeInResults = enabled)
                                    onSettingsChanged?.invoke()
                                }
                                ToggleSettingId.ShowMemoryOverlay -> Unit
                                ToggleSettingId.KeepLoadedThumbnailsInMemory -> Unit
                                ToggleSettingId.KeepLoadedVideoPreviewsInMemory -> Unit
                            }
                        }
                    )
                }
            }

            SettingsSectionCard(section = trashScanSection) {
                trashScanSection.toggles.forEachIndexed { index, toggle ->
                    if (index > 0) {
                        HorizontalDivider()
                    }
                    ToggleSettingRow(
                        title = toggle.title,
                        description = toggle.description,
                        checked = toggle.checked,
                        onCheckedChange = { enabled ->
                            when (toggle.id) {
                                ToggleSettingId.SkipTrashBinContentsInScan -> {
                                    settingsStore.setSkipTrashBinContentsInScan(enabled)
                                    settings.value = settings.value.copy(skipTrashBinContentsInScan = enabled)
                                    onSettingsChanged?.invoke()
                                }
                                ToggleSettingId.SkipZeroSizeInDb -> Unit
                                ToggleSettingId.HideZeroSizeInResults -> Unit
                                ToggleSettingId.ShowMemoryOverlay -> Unit
                                ToggleSettingId.KeepLoadedThumbnailsInMemory -> Unit
                                ToggleSettingId.KeepLoadedVideoPreviewsInMemory -> Unit
                            }
                        }
                    )
                }
            }

            SettingsSectionCard(section = memoryOverlaySection) {
                memoryOverlaySection.toggles.forEachIndexed { index, toggle ->
                    if (index > 0) {
                        HorizontalDivider()
                    }
                    ToggleSettingRow(
                        title = toggle.title,
                        description = toggle.description,
                        checked = toggle.checked,
                        onCheckedChange = { enabled ->
                            when (toggle.id) {
                                ToggleSettingId.ShowMemoryOverlay -> {
                                    settingsStore.setShowMemoryOverlay(enabled)
                                    settings.value = settings.value.copy(showMemoryOverlay = enabled)
                                    onSettingsChanged?.invoke()
                                }
                                else -> Unit
                            }
                        }
                    )
                }
            }

            SettingsSectionCard(section = thumbnailMemorySection) {
                thumbnailMemorySection.toggles.forEachIndexed { index, toggle ->
                    if (index > 0) {
                        HorizontalDivider()
                    }
                    ToggleSettingRow(
                        title = toggle.title,
                        description = toggle.description,
                        checked = toggle.checked,
                        onCheckedChange = { enabled ->
                            when (toggle.id) {
                                ToggleSettingId.KeepLoadedThumbnailsInMemory -> {
                                    settingsStore.setKeepLoadedThumbnailsInMemory(enabled)
                                    settings.value = settings.value.copy(keepLoadedThumbnailsInMemory = enabled)
                                    onSettingsChanged?.invoke()
                                }
                                ToggleSettingId.KeepLoadedVideoPreviewsInMemory -> Unit
                                else -> Unit
                            }
                        }
                    )
                }
            }

            SettingsSectionCard(section = videoPreviewMemorySection) {
                videoPreviewMemorySection.toggles.forEachIndexed { index, toggle ->
                    if (index > 0) {
                        HorizontalDivider()
                    }
                    ToggleSettingRow(
                        title = toggle.title,
                        description = toggle.description,
                        checked = toggle.checked,
                        onCheckedChange = { enabled ->
                            when (toggle.id) {
                                ToggleSettingId.KeepLoadedVideoPreviewsInMemory -> {
                                    settingsStore.setKeepLoadedVideoPreviewsInMemory(enabled)
                                    settings.value = settings.value.copy(keepLoadedVideoPreviewsInMemory = enabled)
                                    onSettingsChanged?.invoke()
                                }
                                else -> Unit
                            }
                        }
                    )
                }
            }

            SettingsSectionCard(section = thumbnailSizeSection) {
                PreviewSizeSettingControl(
                    selectedPercent = settings.value.thumbnailSizePercent,
                    onPercentSelected = { percent ->
                        settingsStore.setThumbnailSizePercent(percent)
                        settings.value = settings.value.copy(thumbnailSizePercent = percent)
                        onSettingsChanged?.invoke()
                    }
                )
            }

            SettingsSectionCard(section = videoPreviewSizeSection) {
                PreviewSizeSettingControl(
                    selectedPercent = settings.value.videoPreviewSizePercent,
                    onPercentSelected = { percent ->
                        settingsStore.setVideoPreviewSizePercent(percent)
                        settings.value = settings.value.copy(videoPreviewSizePercent = percent)
                        onSettingsChanged?.invoke()
                    }
                )
            }

            SettingsSectionCard(section = backupSection) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { exportLauncher.launch("cached_dupe_scanner_settings.json") },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Export prefs")
                    }
                    OutlinedButton(
                        onClick = { importLauncher.launch(arrayOf("application/json")) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Import prefs")
                    }
                }

                message.value?.let { text ->
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        VerticalScrollbar(
            scrollState = scrollState,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .padding(end = 4.dp)
        )
    }
}

@Composable
private fun SettingsSectionCard(
    section: SettingsSectionModel,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = {
                Text(
                    text = section.title,
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = section.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                content()
            }
        )
    }
}

@Composable
private fun ToggleSettingRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                role = Role.Switch,
                onValueChange = onCheckedChange
            )
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = null
        )
    }
}

@Composable
private fun PreviewSizeSettingControl(
    selectedPercent: Int,
    onPercentSelected: (Int) -> Unit
) {
    val draftPercent = remember(selectedPercent) { mutableStateOf(selectedPercent) }
    val inputValue = remember(selectedPercent) { mutableStateOf(selectedPercent.toString()) }

    fun applyPercent(value: Int) {
        val normalized = value.coerceAtLeast(0)
        draftPercent.value = normalized
        inputValue.value = normalized.toString()
        if (normalized != selectedPercent) {
            onPercentSelected(normalized)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = { applyPercent(draftPercent.value - 10) },
                modifier = Modifier.weight(1f)
            ) {
                Text("-10%")
            }
            OutlinedButton(
                onClick = { applyPercent(draftPercent.value - 1) },
                modifier = Modifier.weight(1f)
            ) {
                Text("-1%")
            }
            Text(
                text = "${draftPercent.value}%",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            OutlinedButton(
                onClick = { applyPercent(draftPercent.value + 1) },
                modifier = Modifier.weight(1f)
            ) {
                Text("+1%")
            }
            OutlinedButton(
                onClick = { applyPercent(draftPercent.value + 10) },
                modifier = Modifier.weight(1f)
            ) {
                Text("+10%")
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = inputValue.value,
                onValueChange = { raw ->
                    val digits = raw.filter { it.isDigit() }
                    inputValue.value = digits
                    digits.toIntOrNull()?.let { parsed ->
                        draftPercent.value = parsed
                    }
                },
                modifier = Modifier.weight(1f),
                label = { Text("Exact size (%)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            OutlinedButton(
                onClick = {
                    val parsed = inputValue.value.toIntOrNull() ?: selectedPercent
                    applyPercent(parsed)
                }
            ) {
                Text("Apply")
            }
        }

        Text(
            text = "Current: ${selectedPercent}%",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

internal data class SettingsSectionModel(
    val title: String,
    val description: String,
    val toggles: List<ToggleSettingModel> = emptyList()
)

internal data class ToggleSettingModel(
    val id: ToggleSettingId,
    val title: String,
    val description: String,
    val checked: Boolean
)

internal enum class ToggleSettingId {
    SkipZeroSizeInDb,
    SkipTrashBinContentsInScan,
    HideZeroSizeInResults,
    ShowMemoryOverlay,
    KeepLoadedThumbnailsInMemory,
    KeepLoadedVideoPreviewsInMemory
}

internal fun zeroSizeSettingsSection(settings: AppSettings): SettingsSectionModel {
    return SettingsSectionModel(
        title = "Zero-size handling",
        description = "Keep storage behavior and result filtering together so related file-size options stay in one place.",
        toggles = listOf(
            ToggleSettingModel(
                id = ToggleSettingId.SkipZeroSizeInDb,
                title = "Skip zero-size in DB",
                description = "Do not store size 0 files in the database.",
                checked = settings.skipZeroSizeInDb
            ),
            ToggleSettingModel(
                id = ToggleSettingId.HideZeroSizeInResults,
                title = "Hide zero-size in results",
                description = "Do not display size 0 files in result lists.",
                checked = settings.hideZeroSizeInResults
            )
        )
    )
}

internal fun trashScanSettingsSection(settings: AppSettings): SettingsSectionModel {
    return SettingsSectionModel(
        title = "Trash scan exclusion",
        description = "Keep app-managed trash content out of scans unless you explicitly want to include it.",
        toggles = listOf(
            ToggleSettingModel(
                id = ToggleSettingId.SkipTrashBinContentsInScan,
                title = "Skip trash contents in scan",
                description = "Do not scan files inside .CachedDupeScanner/trashbin.",
                checked = settings.skipTrashBinContentsInScan
            )
        )
    )
}

internal fun memoryOverlaySection(settings: AppSettings): SettingsSectionModel {
    return SettingsSectionModel(
        title = "Memory overlay",
        description = "Show the current heap allocation as a small overlay in the top-left corner of the app.",
        toggles = listOf(
            ToggleSettingModel(
                id = ToggleSettingId.ShowMemoryOverlay,
                title = "Show RAM allocation overlay",
                description = "Displays the current allocated and maximum heap size above screen content.",
                checked = settings.showMemoryOverlay
            )
        )
    )
}

internal fun thumbnailMemorySettingsSection(settings: AppSettings): SettingsSectionModel {
    return SettingsSectionModel(
        title = "Thumbnail memory",
        description = "Keep already loaded thumbnails in RAM so scrolling back to earlier items can reuse them without decoding again.",
        toggles = listOf(
            ToggleSettingModel(
                id = ToggleSettingId.KeepLoadedThumbnailsInMemory,
                title = "Keep loaded thumbnails in RAM",
                description = "Uses more memory, but previously shown thumbnails stay available while the screen remains open.",
                checked = settings.keepLoadedThumbnailsInMemory
            )
        )
    )
}

internal fun videoPreviewMemorySettingsSection(settings: AppSettings): SettingsSectionModel {
    return SettingsSectionModel(
        title = "Video preview memory",
        description = "Keep generated video timeline preview frames in RAM for faster revisit while browsing files. Enabled by default.",
        toggles = listOf(
            ToggleSettingModel(
                id = ToggleSettingId.KeepLoadedVideoPreviewsInMemory,
                title = "Keep video previews in RAM",
                description = "Caches start/middle/end timeline frames for video preview mode in the file list.",
                checked = settings.keepLoadedVideoPreviewsInMemory
            )
        )
    )
}

internal fun thumbnailSizeSettingsSection(): SettingsSectionModel {
    return SettingsSectionModel(
        title = "Thumbnail size",
        description = "Choose how large file thumbnails appear across files, results, and bulk-delete previews."
    )
}

internal fun videoPreviewSizeSettingsSection(): SettingsSectionModel {
    return SettingsSectionModel(
        title = "Video preview size",
        description = "Choose the timeline frame size used in the video preview mode on the files screen."
    )
}

internal fun backupSettingsSection(): SettingsSectionModel {
    return SettingsSectionModel(
        title = "Settings backup",
        description = "Export current preferences and scan targets to JSON, or import a saved backup."
    )
}
