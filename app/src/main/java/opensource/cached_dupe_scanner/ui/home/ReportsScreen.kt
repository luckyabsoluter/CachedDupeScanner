package opensource.cached_dupe_scanner.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import opensource.cached_dupe_scanner.ui.components.ScrollbarDefaults
import opensource.cached_dupe_scanner.ui.components.Spacing
import opensource.cached_dupe_scanner.ui.components.VerticalLazyScrollbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import androidx.compose.runtime.snapshotFlow

@Composable
fun ReportsScreen(
    reportRepo: ScanReportRepository,
    refreshVersion: Int,
    onBack: () -> Unit,
    onOpenReport: ((String) -> Unit)? = null,
    selectedReportId: String? = null,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val reports = remember { mutableStateOf<List<ScanReport>>(emptyList()) }
    val isLoading = remember { mutableStateOf(true) }
    val selected = remember { mutableStateOf<ScanReport?>(null) }
    val selectedReport = selectedReportId?.let { id -> reports.value.firstOrNull { it.id == id } }
    val pageSize = 50
    val buffer = 20
    val visibleCount = remember { mutableStateOf(0) }
    val topVisibleIndex = remember { mutableStateOf(0) }

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

    LaunchedEffect(reports.value.size) {
        if (reports.value.isNotEmpty()) {
            val initial = pageSize.coerceAtMost(reports.value.size)
            if (visibleCount.value == 0) {
                visibleCount.value = initial
            } else {
                visibleCount.value = visibleCount.value.coerceAtMost(reports.value.size)
            }
        }
    }

    val reportIndexById = remember(reports.value) {
        reports.value.mapIndexed { index, report -> report.id to index }.toMap()
    }

    LaunchedEffect(reports.value.size, selectedReportId) {
        if (selectedReportId != null) return@LaunchedEffect
        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo
                .firstOrNull { it.key is String }
                ?.key as? String
        }
            .distinctUntilChanged()
            .filter { it != null }
            .collect { key ->
                val index = reportIndexById[key] ?: 0
                topVisibleIndex.value = index
            }
    }

    LaunchedEffect(reports.value.size) {
        if (reports.value.isEmpty()) return@LaunchedEffect
        snapshotFlow {
            val layoutInfo = listState.layoutInfo
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val totalItems = layoutInfo.totalItemsCount
            val remaining = reports.value.size - visibleCount.value
            lastVisible >= (totalItems - buffer) && remaining > 0
        }
            .distinctUntilChanged()
            .filter { it }
            .collect {
                visibleCount.value = (visibleCount.value + pageSize)
                    .coerceAtMost(reports.value.size)
            }
    }

    val loadIndicatorText = run {
        val total = reports.value.size
        if (selectedReportId != null || total == 0) {
            null
        } else {
            val loaded = visibleCount.value.coerceAtMost(total).coerceAtLeast(1)
            val current = (topVisibleIndex.value + 1).coerceAtLeast(1)
            val currentPercent = ((current.toDouble() / loaded.toDouble()) * 100).toInt()
            val loadedPercent = ((loaded.toDouble() / total.toDouble()) * 100).toInt()
            "$current/$loaded/$total (${currentPercent}%/${loadedPercent}%)"
        }
    }
    Box(modifier = modifier) {
        LazyColumn(
            state = listState,
            modifier = Modifier.padding(Spacing.screenPadding),
            contentPadding = PaddingValues(end = ScrollbarDefaults.ThumbWidth + 8.dp)
        ) {
            item {
                AppTopBar(
                    title = "Scan reports",
                    onBack = {
                        onBack()
                    }
                )
            }
            item { Spacer(modifier = Modifier.height(8.dp)) }

            selectedReport?.let { report ->
                item {
                    ReportDetail(report)
                }
                return@LazyColumn
            }

            if (isLoading.value) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Loading reports...")
                    }
                }
            } else if (reports.value.isEmpty()) {
                item { Text("No scan reports yet.") }
            } else {
                val reportsToShow = reports.value.take(visibleCount.value)
                items(reportsToShow, key = { it.id }) { report ->
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

        loadIndicatorText?.let { indicator ->
            Text(
                text = indicator,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = ScrollbarDefaults.ThumbWidth + 12.dp, top = 12.dp)
            )
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

