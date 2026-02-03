package opensource.cached_dupe_scanner.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import opensource.cached_dupe_scanner.storage.ScanHistoryRepository
import opensource.cached_dupe_scanner.ui.components.AppTopBar
import opensource.cached_dupe_scanner.ui.components.Spacing

@Composable
fun DbManagementScreen(
    historyRepo: ScanHistoryRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val deleteMissing = remember { mutableStateOf(true) }
    val rehashStale = remember { mutableStateOf(false) }
    val isRunning = remember { mutableStateOf(false) }
    val statusMessage = remember { mutableStateOf<String?>(null) }
    val dbCount = remember { mutableStateOf<Int?>(null) }

    val refreshCount: () -> Unit = {
        scope.launch {
            dbCount.value = withContext(Dispatchers.IO) { historyRepo.countAll() }
        }
    }

    LaunchedEffect(Unit) {
        refreshCount()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(Spacing.screenPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AppTopBar(title = "DB management", onBack = onBack)

        Text(
            text = "Choose policies, then run. The app scans storage based on DB entries.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "DB entries: ${dbCount.value ?: "-"}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = deleteMissing.value,
                    onCheckedChange = { deleteMissing.value = it }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Delete DB entries missing on storage")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = rehashStale.value,
                    onCheckedChange = { rehashStale.value = it }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Rehash entries with stale size/date")
            }
        }

        statusMessage.value?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }

        val canRun = (deleteMissing.value || rehashStale.value) && !isRunning.value
        Button(
            onClick = {
                scope.launch {
                    isRunning.value = true
                    val deleted = if (deleteMissing.value) {
                        withContext(Dispatchers.IO) { historyRepo.deleteMissingAll() }
                    } else {
                        0
                    }
                    val rehashed = if (rehashStale.value) {
                        withContext(Dispatchers.IO) { historyRepo.rehashIfChangedAll() }
                    } else {
                        0
                    }
                    statusMessage.value = "Maintenance complete. Deleted ${deleted}, rehashed ${rehashed}."
                    isRunning.value = false
                    refreshCount()
                }
            },
            enabled = canRun,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isRunning.value) "Running..." else "Run maintenance")
        }
    }
}
