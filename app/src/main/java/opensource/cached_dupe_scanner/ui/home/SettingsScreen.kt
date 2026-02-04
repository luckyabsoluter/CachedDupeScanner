package opensource.cached_dupe_scanner.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
                .verticalScroll(scrollState)
        ) {
            AppTopBar(title = "Settings", onBack = onBack)
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Skip zero-size in DB")
                    Text("Do not store size 0 files in the database")
                }
                Switch(
                    checked = settings.value.skipZeroSizeInDb,
                    onCheckedChange = { enabled ->
                        settingsStore.setSkipZeroSizeInDb(enabled)
                        settings.value = settings.value.copy(skipZeroSizeInDb = enabled)
                    }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Hide zero-size in results")
                    Text("Do not display size 0 files in results")
                }
                Switch(
                    checked = settings.value.hideZeroSizeInResults,
                    onCheckedChange = { enabled ->
                        settingsStore.setHideZeroSizeInResults(enabled)
                        settings.value = settings.value.copy(hideZeroSizeInResults = enabled)
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text("Settings backup")
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { exportLauncher.launch("cached_dupe_scanner_settings.json") }
                ) {
                    Text("Export prefs")
                }
                Spacer(modifier = Modifier.weight(1f))
                OutlinedButton(
                    onClick = { importLauncher.launch(arrayOf("application/json")) }
                ) {
                    Text("Import prefs")
                }
            }

            message.value?.let { text ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(text)
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
