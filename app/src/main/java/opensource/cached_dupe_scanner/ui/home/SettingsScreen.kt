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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import opensource.cached_dupe_scanner.storage.AppSettings
import opensource.cached_dupe_scanner.storage.AppSettingsStore
import opensource.cached_dupe_scanner.storage.ScanTargetStore
import opensource.cached_dupe_scanner.ui.components.AppTopBar
import opensource.cached_dupe_scanner.ui.components.ScrollbarDefaults
import opensource.cached_dupe_scanner.ui.components.Spacing
import opensource.cached_dupe_scanner.ui.components.VerticalScrollbar

@Composable
fun SettingsScreen(
    settingsStore: AppSettingsStore,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val settings = remember { mutableStateOf(settingsStore.load()) }
    val context = LocalContext.current
    val targetStore = remember { ScanTargetStore(context) }
    val message = remember { mutableStateOf<String?>(null) }
    val zeroSizeSection = zeroSizeSettingsSection(settings.value)
    val trashScanSection = trashScanSettingsSection(settings.value)
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
                                }
                                ToggleSettingId.SkipTrashBinContentsInScan -> Unit
                                ToggleSettingId.HideZeroSizeInResults -> {
                                    settingsStore.setHideZeroSizeInResults(enabled)
                                    settings.value = settings.value.copy(hideZeroSizeInResults = enabled)
                                }
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
                                }
                                else -> Unit
                            }
                        }
                    )
                }
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
    HideZeroSizeInResults
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

internal fun backupSettingsSection(): SettingsSectionModel {
    return SettingsSectionModel(
        title = "Settings backup",
        description = "Export current preferences and scan targets to JSON, or import a saved backup."
    )
}
