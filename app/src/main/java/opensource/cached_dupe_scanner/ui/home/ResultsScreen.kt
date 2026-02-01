package opensource.cached_dupe_scanner.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import opensource.cached_dupe_scanner.core.ScanResultViewFilter
import opensource.cached_dupe_scanner.storage.AppSettingsStore
import opensource.cached_dupe_scanner.ui.components.AppTopBar
import opensource.cached_dupe_scanner.ui.components.Spacing
import opensource.cached_dupe_scanner.ui.results.ScanUiState

@Composable
fun ResultsScreen(
    state: MutableState<ScanUiState>,
    onBackToDashboard: () -> Unit,
    onClearResults: () -> Unit,
    settingsStore: AppSettingsStore,
    modifier: Modifier = Modifier
) {
    val menuExpanded = remember { mutableStateOf(false) }
    Column(
        modifier = modifier
            .padding(Spacing.screenPadding)
            .verticalScroll(rememberScrollState())
    ) {
        AppTopBar(
            title = "Results",
            onBack = onBackToDashboard,
            actions = {
                IconButton(onClick = { menuExpanded.value = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Menu")
                }
                DropdownMenu(
                    expanded = menuExpanded.value,
                    onDismissRequest = { menuExpanded.value = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Clear all results") },
                        onClick = {
                            menuExpanded.value = false
                            onClearResults()
                        }
                    )
                }
            }
        )
        Spacer(modifier = Modifier.height(8.dp))
        when (val current = state.value) {
            ScanUiState.Idle -> Text("No results yet.")
            is ScanUiState.Scanning -> Text("Scanning…")
            is ScanUiState.Error -> Text("Error: ${current.message}")
            is ScanUiState.Success -> {
                val settings = settingsStore.load()
                val result = ScanResultViewFilter.filterForDisplay(
                    result = current.result,
                    hideZeroSizeInResults = settings.hideZeroSizeInResults
                )
                Text("Files scanned: ${result.files.size}")
                Text("Duplicate groups: ${result.duplicateGroups.size}")
                Spacer(modifier = Modifier.height(8.dp))

                if (result.duplicateGroups.isEmpty()) {
                    Text("No duplicates found.")
                } else {
                    result.duplicateGroups.forEach { group ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                group.files.sortedBy { it.normalizedPath }.forEach { file ->
                                    Text(
                                        text = file.normalizedPath,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }

    }
}
