package opensource.cached_dupe_scanner.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import opensource.cached_dupe_scanner.storage.ScanReport
import opensource.cached_dupe_scanner.storage.ScanReportRepository
import opensource.cached_dupe_scanner.ui.components.AppTopBar
import opensource.cached_dupe_scanner.ui.components.Spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ReportsScreen(
    reportRepo: ScanReportRepository,
    refreshVersion: Int,
    onBack: () -> Unit,
    onOpenReport: ((String) -> Unit)? = null,
    selectedReportId: String? = null,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val reports = remember { mutableStateOf<List<ScanReport>>(emptyList()) }
    val isLoading = remember { mutableStateOf(true) }
    val selected = remember { mutableStateOf<ScanReport?>(null) }
    val selectedReport = selectedReportId?.let { id -> reports.value.firstOrNull { it.id == id } }

    LaunchedEffect(Unit) {
        isLoading.value = true
        val loaded = withContext(Dispatchers.IO) {
            reportRepo.loadAll()
        }
        reports.value = loaded
        isLoading.value = false
    }

    LaunchedEffect(refreshVersion) {
        isLoading.value = true
        val loaded = withContext(Dispatchers.IO) {
            reportRepo.loadAll()
        }
        reports.value = loaded
        isLoading.value = false
    }

    Column(
        modifier = modifier
            .padding(Spacing.screenPadding)
            .verticalScroll(scrollState)
    ) {
        AppTopBar(
            title = "Scan reports",
            onBack = {
                onBack()
            }
        )
        Spacer(modifier = Modifier.height(8.dp))

        selectedReport?.let { report ->
            ReportDetail(report)
            return@Column
        }

        if (isLoading.value) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(8.dp))
                Text("Loading reports...")
            }
            return@Column
        }

        if (reports.value.isEmpty()) {
            Text("No scan reports yet.")
            return@Column
        }

        reports.value.forEach { report ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (onOpenReport != null) {
                            onOpenReport(report.id)
                        } else {
                            selected.value = report
                        }
                    }
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = formatDate(report.startedAtMillis),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text("Targets: ${report.targets.size}")
                    Text("Mode: ${report.mode}")
                    Text("Cancelled: ${report.cancelled}")
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ReportDetail(report: ScanReport) {
    Text("Scan report")
    Spacer(modifier = Modifier.height(8.dp))
    Text("Started: ${formatDate(report.startedAtMillis)}")
    Text("Finished: ${formatDate(report.finishedAtMillis)}")
    Text("Mode: ${report.mode}")
    Text("Targets: ${report.targets.joinToString()}")
    Text("Cancelled: ${report.cancelled}")
    Spacer(modifier = Modifier.height(8.dp))
    Text("Collected: ${report.totals.collectedCount}")
    Text("Detected: ${report.totals.detectedCount}")
    Text("Hash candidates: ${report.totals.hashCandidates}")
    Text("Hashes computed: ${report.totals.hashesComputed}")
    Spacer(modifier = Modifier.height(8.dp))
    Text("Collecting: ${report.durations.collectingMillis} ms")
    Text("Detecting: ${report.durations.detectingMillis} ms")
    Text("Hashing: ${report.durations.hashingMillis} ms")
}

