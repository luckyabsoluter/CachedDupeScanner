package opensource.cached_dupe_scanner.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import opensource.cached_dupe_scanner.export.ExportFormat
import opensource.cached_dupe_scanner.export.ScanExporter
import opensource.cached_dupe_scanner.ui.components.AppTopBar
import opensource.cached_dupe_scanner.ui.results.ScanUiState

@Composable
fun ResultsScreen(
    state: MutableState<ScanUiState>,
    exportText: MutableState<String?>,
    onBackToDashboard: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(16.dp)) {
        AppTopBar(title = "Results", onBack = onBackToDashboard)
        Spacer(modifier = Modifier.height(8.dp))
        when (val current = state.value) {
            ScanUiState.Idle -> Text("No results yet.")
            is ScanUiState.Scanning -> Text("Scanning…")
            is ScanUiState.Error -> Text("Error: ${current.message}")
            is ScanUiState.Success -> {
                val result = current.result
                Text("Files scanned: ${result.files.size}")
                Text("Duplicate groups: ${result.duplicateGroups.size}")
                Spacer(modifier = Modifier.height(8.dp))

                Button(onClick = {
                    exportText.value = ScanExporter.export(result, ExportFormat.JSON)
                }, modifier = Modifier.fillMaxWidth()) {
                    Text("Export JSON")
                }
                Button(onClick = {
                    exportText.value = ScanExporter.export(result, ExportFormat.CSV)
                }, modifier = Modifier.fillMaxWidth()) {
                    Text("Export CSV")
                }

                Spacer(modifier = Modifier.height(12.dp))
                if (result.duplicateGroups.isEmpty()) {
                    Text("No duplicates found.")
                } else {
                    LazyColumn {
                        items(result.duplicateGroups) { group ->
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = "Hash: ${group.hashHex}",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
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

        exportText.value?.let { text ->
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = "Export", style = MaterialTheme.typography.titleMedium)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Text(
                    text = text,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
