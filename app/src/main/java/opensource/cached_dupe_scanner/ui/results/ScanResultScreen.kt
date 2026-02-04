package opensource.cached_dupe_scanner.ui.results

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import opensource.cached_dupe_scanner.core.DuplicateGroup
import opensource.cached_dupe_scanner.core.FileMetadata
import opensource.cached_dupe_scanner.ui.components.ScrollbarDefaults
import opensource.cached_dupe_scanner.ui.components.VerticalLazyScrollbar

@Composable
fun ScanResultScreen(state: ScanUiState, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(16.dp)) {
        Text(
            text = "CachedDupeScanner",
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(modifier = Modifier.height(12.dp))

        when (state) {
            ScanUiState.Idle -> {
                Text(text = "Ready to scan.")
            }
            is ScanUiState.Scanning -> {
                Text(text = "Scanning… ${state.scanned}${state.total?.let { " / $it" } ?: ""}")
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            is ScanUiState.Error -> {
                Text(text = "Error: ${state.message}")
            }
            is ScanUiState.Success -> {
                val result = state.result
                Text(text = "Files: ${result.files.size}")
                Text(text = "Duplicate groups: ${result.duplicateGroups.size}")
                Spacer(modifier = Modifier.height(12.dp))

                DuplicateGroupList(groups = result.duplicateGroups)
            }
        }
    }
}

@Composable
private fun DuplicateGroupList(groups: List<DuplicateGroup>) {
    if (groups.isEmpty()) {
        Text(text = "No duplicates found.")
        return
    }

    val listState = rememberLazyListState()
    Box {
        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(end = ScrollbarDefaults.ThumbWidth + 8.dp)
        ) {
            items(groups) { group ->
                DuplicateGroupCard(group)
            }
        }

        VerticalLazyScrollbar(
            listState = listState,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .padding(end = 4.dp)
        )
    }
}

@Composable
private fun DuplicateGroupCard(group: DuplicateGroup) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = "Hash: ${group.hashHex}")
            Spacer(modifier = Modifier.height(6.dp))
            group.files.sortedBy { it.normalizedPath }.forEach { file ->
                FileRow(file)
            }
        }
    }
}

@Composable
private fun FileRow(file: FileMetadata) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(text = file.normalizedPath, style = MaterialTheme.typography.bodyMedium)
    }
}
