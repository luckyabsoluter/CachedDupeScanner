package opensource.cached_dupe_scanner.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.room.Room
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import opensource.cached_dupe_scanner.cache.CacheDatabase
import opensource.cached_dupe_scanner.cache.CacheStore
import opensource.cached_dupe_scanner.cache.CacheMigrations
import opensource.cached_dupe_scanner.core.ScanResult
import opensource.cached_dupe_scanner.core.ScanResultMerger
import opensource.cached_dupe_scanner.engine.IncrementalScanner
import opensource.cached_dupe_scanner.engine.ScanPhase
import opensource.cached_dupe_scanner.storage.ScanTarget
import opensource.cached_dupe_scanner.storage.ScanTargetStore
import opensource.cached_dupe_scanner.storage.AppSettingsStore
import opensource.cached_dupe_scanner.storage.ScanReport
import opensource.cached_dupe_scanner.storage.ScanReportDurations
import opensource.cached_dupe_scanner.storage.ScanReportRepository
import opensource.cached_dupe_scanner.storage.ScanReportTotals
import opensource.cached_dupe_scanner.ui.components.AppTopBar
import opensource.cached_dupe_scanner.ui.components.Spacing
import opensource.cached_dupe_scanner.ui.results.ScanUiState
import java.io.File
import java.util.UUID

@Composable
fun ScanCommandScreen(
    state: MutableState<ScanUiState>,
    onScanComplete: (ScanResult) -> Unit,
    onScanCancelled: () -> Unit,
    reportRepo: ScanReportRepository,
    settingsStore: AppSettingsStore,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { ScanTargetStore(context) }
    val targets = remember { mutableStateOf(store.loadTargets()) }
    val currentJob = remember { mutableStateOf<Job?>(null) }
    val cancelRequested = remember { mutableStateOf(false) }
    val progressTarget = remember { mutableStateOf<String?>(null) }
    val progressCurrent = remember { mutableStateOf<String?>(null) }
    val progressSize = remember { mutableStateOf<Long?>(null) }
    val progressPhase = remember { mutableStateOf(ScanPhase.Collecting) }

    val database = remember {
        Room.databaseBuilder(context, CacheDatabase::class.java, "scan-cache.db")
            .addMigrations(
                CacheMigrations.MIGRATION_1_3,
                CacheMigrations.MIGRATION_2_3,
                CacheMigrations.MIGRATION_3_4,
                CacheMigrations.MIGRATION_4_5
            )
            .build()
    }
    val cacheStore = remember { CacheStore(database.fileCacheDao()) }
    val scanner = remember { IncrementalScanner(cacheStore) }

    LaunchedEffect(Unit) {
        targets.value = store.loadTargets()
    }

    Column(
        modifier = modifier
            .padding(Spacing.screenPadding)
            .verticalScroll(scrollState)
    ) {
        AppTopBar(title = "Scan command", onBack = onBack)
        Spacer(modifier = Modifier.height(8.dp))

        if (targets.value.isEmpty()) {
            Text("No scan targets yet. Add one first.")
            return@Column
        }

        Text("Select a target:")
        Spacer(modifier = Modifier.height(6.dp))

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            targets.value.forEach { target ->
                TargetScanRow(
                    target = target,
                    onScan = {
                        val skipZeroSizeInDb = settingsStore.load().skipZeroSizeInDb
                        runScanForTarget(
                            scope,
                            scanner,
                            state,
                            target,
                            onScanComplete,
                            onScanCancelled,
                            reportRepo,
                            skipZeroSizeInDb,
                            currentJob,
                            cancelRequested,
                            progressTarget,
                            progressCurrent,
                            progressSize,
                            progressPhase
                        )
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Button(onClick = {
            val skipZeroSizeInDb = settingsStore.load().skipZeroSizeInDb
            runScanForAllTargets(
                scope,
                scanner,
                state,
                targets.value,
                onScanComplete,
                onScanCancelled,
                reportRepo,
                skipZeroSizeInDb,
                currentJob,
                cancelRequested,
                progressTarget,
                progressCurrent,
                progressSize,
                progressPhase
            )
        }, modifier = Modifier.fillMaxWidth()) {
            Text("Scan all targets")
        }

        if (state.value is ScanUiState.Scanning) {
            Spacer(modifier = Modifier.height(12.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Progress", style = MaterialTheme.typography.titleSmall)
                    val targetText = progressTarget.value ?: "-"
                    val currentText = progressCurrent.value ?: "-"
                    val phaseText = when (progressPhase.value) {
                        ScanPhase.Collecting -> "Collecting files"
                        ScanPhase.Detecting -> "Detecting hash candidates"
                        ScanPhase.Hashing -> "Hashing"
                    }
                    Text("Target: $targetText")
                    Text("Phase: $phaseText")
                    val progress = state.value as ScanUiState.Scanning
                    val totalText = progress.total?.toString() ?: "?"
                    Text("Scanned: ${progress.scanned} / $totalText")
                    progressSize.value?.let { size ->
                        Text("Size: ${size} bytes")
                    }
                    Text("Current: $currentText")
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            cancelRequested.value = true
                            currentJob.value?.cancel()
                            progressTarget.value = null
                            progressCurrent.value = null
                            progressSize.value = null
                            onScanCancelled()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Stop scan")
                    }
                }
            }
        }
    }
}

@Composable
private fun TargetScanRow(target: ScanTarget, onScan: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = target.path, style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(6.dp))
            Button(onClick = onScan, modifier = Modifier.fillMaxWidth()) {
                Text("Scan target")
            }
        }
    }
}

private fun runScanForTarget(
    scope: kotlinx.coroutines.CoroutineScope,
    scanner: IncrementalScanner,
    state: MutableState<ScanUiState>,
    target: ScanTarget,
    onScanComplete: (ScanResult) -> Unit,
    onScanCancelled: () -> Unit,
    reportRepo: ScanReportRepository,
    skipZeroSizeInDb: Boolean,
    currentJob: MutableState<Job?>,
    cancelRequested: MutableState<Boolean>,
    progressTarget: MutableState<String?>,
    progressCurrent: MutableState<String?>,
    progressSize: MutableState<Long?>,
    progressPhase: MutableState<ScanPhase>
) {
    var job: Job? = null
    job = scope.launch {
        cancelRequested.value = false
        state.value = ScanUiState.Scanning(scanned = 0, total = null)
        val startedAt = System.currentTimeMillis()
        var collectingStart = startedAt
        var detectingStart = 0L
        var hashingStart = 0L
        var collectingEnd = 0L
        var detectingEnd = 0L
        var hashingEnd = 0L
        var detectedCount = 0
        var hashCandidates = 0
        var hashesComputed = 0
        val targetFile = File(target.path)
        if (!targetFile.exists()) {
            state.value = ScanUiState.Error("Target path not found")
            return@launch
        }
        progressTarget.value = target.path
        val result = withContext(Dispatchers.IO) {
            scanner.scan(
                targetFile,
                skipZeroSizeInDb = skipZeroSizeInDb,
                onProgress = { scanned, total, current, phase ->
                    if (cancelRequested.value) return@scan
                    state.value = ScanUiState.Scanning(scanned = scanned, total = total)
                    progressCurrent.value = current.normalizedPath
                    progressSize.value = current.sizeBytes
                    progressPhase.value = phase
                    when (phase) {
                        ScanPhase.Collecting -> {
                            if (collectingStart == 0L) collectingStart = System.currentTimeMillis()
                        }
                        ScanPhase.Detecting -> {
                            if (collectingEnd == 0L) collectingEnd = System.currentTimeMillis()
                            if (detectingStart == 0L) detectingStart = System.currentTimeMillis()
                            detectedCount = scanned
                            hashCandidates = total ?: 0
                        }
                        ScanPhase.Hashing -> {
                            if (detectingEnd == 0L) detectingEnd = System.currentTimeMillis()
                            if (hashingStart == 0L) hashingStart = System.currentTimeMillis()
                            hashesComputed = scanned
                            hashCandidates = total ?: hashCandidates
                        }
                    }
                },
                shouldContinue = { job?.isActive == true }
            )
        }
        hashingEnd = if (hashingStart > 0) System.currentTimeMillis() else hashingEnd
        detectingEnd = if (detectingStart > 0 && detectingEnd == 0L) System.currentTimeMillis() else detectingEnd
        collectingEnd = if (collectingEnd == 0L) System.currentTimeMillis() else collectingEnd
        val finishedAt = System.currentTimeMillis()
        val report = ScanReport(
            id = UUID.randomUUID().toString(),
            startedAtMillis = startedAt,
            finishedAtMillis = finishedAt,
            targets = listOf(target.path),
            mode = "single",
            cancelled = cancelRequested.value || (job?.isActive == false && result.files.isEmpty()),
            totals = ScanReportTotals(
                collectedCount = detectedCount,
                detectedCount = detectedCount,
                hashCandidates = hashCandidates,
                hashesComputed = hashesComputed
            ),
            durations = ScanReportDurations(
                collectingMillis = collectingEnd - collectingStart,
                detectingMillis = if (detectingStart == 0L) 0L else detectingEnd - detectingStart,
                hashingMillis = if (hashingStart == 0L) 0L else hashingEnd - hashingStart
            )
        )
        withContext(Dispatchers.IO) {
            reportRepo.add(report)
        }
        if (cancelRequested.value || (job?.isActive == false && result.files.isEmpty())) {
            progressTarget.value = null
            progressCurrent.value = null
            progressSize.value = null
            onScanCancelled()
            return@launch
        }
        onScanComplete(result)
    }
    currentJob.value = job
}

private fun runScanForAllTargets(
    scope: kotlinx.coroutines.CoroutineScope,
    scanner: IncrementalScanner,
    state: MutableState<ScanUiState>,
    targets: List<ScanTarget>,
    onScanComplete: (ScanResult) -> Unit,
    onScanCancelled: () -> Unit,
    reportRepo: ScanReportRepository,
    skipZeroSizeInDb: Boolean,
    currentJob: MutableState<Job?>,
    cancelRequested: MutableState<Boolean>,
    progressTarget: MutableState<String?>,
    progressCurrent: MutableState<String?>,
    progressSize: MutableState<Long?>,
    progressPhase: MutableState<ScanPhase>
) {
    if (targets.isEmpty()) {
        state.value = ScanUiState.Error("No scan targets")
        return
    }

    var job: Job? = null
    job = scope.launch {
        cancelRequested.value = false
        state.value = ScanUiState.Scanning(scanned = 0, total = null)
        val startedAt = System.currentTimeMillis()
        var collectingStart = startedAt
        var detectingStart = 0L
        var hashingStart = 0L
        var collectingEnd = 0L
        var detectingEnd = 0L
        var hashingEnd = 0L
        var detectedCount = 0
        var hashCandidates = 0
        var hashesComputed = 0
        val results = mutableListOf<ScanResult>()
        for (target in targets) {
            val targetFile = File(target.path)
            if (!targetFile.exists()) {
                continue
            }
            progressTarget.value = target.path
            val result = withContext(Dispatchers.IO) {
                scanner.scan(
                    targetFile,
                    skipZeroSizeInDb = skipZeroSizeInDb,
                    onProgress = { scanned, total, current, phase ->
                        if (cancelRequested.value) return@scan
                        state.value = ScanUiState.Scanning(scanned = scanned, total = total)
                        progressCurrent.value = current.normalizedPath
                        progressSize.value = current.sizeBytes
                        progressPhase.value = phase
                        when (phase) {
                            ScanPhase.Collecting -> {
                                if (collectingStart == 0L) collectingStart = System.currentTimeMillis()
                            }
                            ScanPhase.Detecting -> {
                                if (collectingEnd == 0L) collectingEnd = System.currentTimeMillis()
                                if (detectingStart == 0L) detectingStart = System.currentTimeMillis()
                                detectedCount = scanned
                                hashCandidates = total ?: hashCandidates
                            }
                            ScanPhase.Hashing -> {
                                if (detectingEnd == 0L) detectingEnd = System.currentTimeMillis()
                                if (hashingStart == 0L) hashingStart = System.currentTimeMillis()
                                hashesComputed = scanned
                                hashCandidates = total ?: hashCandidates
                            }
                        }
                    },
                    shouldContinue = { job?.isActive == true }
                )
            }
            if (cancelRequested.value || (job?.isActive == false && result.files.isEmpty())) {
                progressTarget.value = null
                progressCurrent.value = null
                progressSize.value = null
                val finishedAt = System.currentTimeMillis()
                val report = ScanReport(
                    id = UUID.randomUUID().toString(),
                    startedAtMillis = startedAt,
                    finishedAtMillis = finishedAt,
                    targets = targets.map { it.path },
                    mode = "all",
                    cancelled = true,
                    totals = ScanReportTotals(
                        collectedCount = detectedCount,
                        detectedCount = detectedCount,
                        hashCandidates = hashCandidates,
                        hashesComputed = hashesComputed
                    ),
                    durations = ScanReportDurations(
                        collectingMillis = collectingEnd - collectingStart,
                        detectingMillis = if (detectingStart == 0L) 0L else detectingEnd - detectingStart,
                        hashingMillis = if (hashingStart == 0L) 0L else hashingEnd - hashingStart
                    )
                )
                withContext(Dispatchers.IO) {
                    reportRepo.add(report)
                }
                onScanCancelled()
                return@launch
            }
            results.add(result)
        }

        if (results.isEmpty()) {
            state.value = ScanUiState.Error("No valid targets to scan")
            return@launch
        }

        val merged = ScanResultMerger.merge(
            System.currentTimeMillis(),
            results
        )
        hashingEnd = if (hashingStart > 0) System.currentTimeMillis() else hashingEnd
        detectingEnd = if (detectingStart > 0 && detectingEnd == 0L) System.currentTimeMillis() else detectingEnd
        collectingEnd = if (collectingEnd == 0L) System.currentTimeMillis() else collectingEnd
        val finishedAt = System.currentTimeMillis()
        val report = ScanReport(
            id = UUID.randomUUID().toString(),
            startedAtMillis = startedAt,
            finishedAtMillis = finishedAt,
            targets = targets.map { it.path },
            mode = "all",
            cancelled = false,
            totals = ScanReportTotals(
                collectedCount = detectedCount,
                detectedCount = detectedCount,
                hashCandidates = hashCandidates,
                hashesComputed = hashesComputed
            ),
            durations = ScanReportDurations(
                collectingMillis = collectingEnd - collectingStart,
                detectingMillis = if (detectingStart == 0L) 0L else detectingEnd - detectingStart,
                hashingMillis = if (hashingStart == 0L) 0L else hashingEnd - hashingStart
            )
        )
        withContext(Dispatchers.IO) {
            reportRepo.add(report)
        }
        onScanComplete(merged)
    }
    currentJob.value = job
}
