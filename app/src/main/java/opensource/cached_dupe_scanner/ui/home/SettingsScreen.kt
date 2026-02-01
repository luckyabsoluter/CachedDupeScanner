package opensource.cached_dupe_scanner.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import opensource.cached_dupe_scanner.storage.AppSettingsStore
import opensource.cached_dupe_scanner.ui.components.AppTopBar
import opensource.cached_dupe_scanner.ui.components.Spacing

@Composable
fun SettingsScreen(
    settingsStore: AppSettingsStore,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val settings = remember { mutableStateOf(settingsStore.load()) }

    Column(
        modifier = modifier
            .padding(Spacing.screenPadding)
            .verticalScroll(rememberScrollState())
    ) {
        AppTopBar(title = "Settings", onBack = onBack)
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Exclude zero-size duplicates")
                Text("Do not count files with size 0 as duplicates")
            }
            Switch(
                checked = settings.value.excludeZeroSizeDuplicates,
                onCheckedChange = { enabled ->
                    settingsStore.setExcludeZeroSizeDuplicates(enabled)
                    settings.value = settings.value.copy(excludeZeroSizeDuplicates = enabled)
                }
            )
        }
    }
}
