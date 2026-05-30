package opensource.cached_dupe_scanner.ui.home

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.room.Room
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import kotlin.math.roundToInt
import opensource.cached_dupe_scanner.cache.CacheDatabase
import opensource.cached_dupe_scanner.cache.CacheMigrations
import opensource.cached_dupe_scanner.cache.CacheStore
import opensource.cached_dupe_scanner.core.ScanResult
import opensource.cached_dupe_scanner.core.ScanResultMerger
import opensource.cached_dupe_scanner.engine.IncrementalScanner
import opensource.cached_dupe_scanner.engine.ScanPhase
import opensource.cached_dupe_scanner.notifications.TaskNotificationController
import opensource.cached_dupe_scanner.storage.AppSettingsStore
import opensource.cached_dupe_scanner.storage.ScanReport
import opensource.cached_dupe_scanner.storage.ScanReportDurations
import opensource.cached_dupe_scanner.storage.ScanReportRepository
import opensource.cached_dupe_scanner.storage.ScanReportTotals
import opensource.cached_dupe_scanner.storage.ScanTarget
import opensource.cached_dupe_scanner.storage.ScanTargetStore
import opensource.cached_dupe_scanner.tasks.TaskArea
import opensource.cached_dupe_scanner.tasks.TaskCoordinator
import opensource.cached_dupe_scanner.tasks.TaskKind
import opensource.cached_dupe_scanner.tasks.scanTaskCancelledDetail
import opensource.cached_dupe_scanner.tasks.scanTaskCompletedDetail
import opensource.cached_dupe_scanner.tasks.scanTaskDetail
import opensource.cached_dupe_scanner.tasks.scanTaskTitle
import opensource.cached_dupe_scanner.ui.components.AppTopBar
import opensource.cached_dupe_scanner.ui.components.ScreenScrollColumn
import opensource.cached_dupe_scanner.ui.components.Spacing
import opensource.cached_dupe_scanner.ui.components.TaskProgressCard
import opensource.cached_dupe_scanner.ui.results.ScanUiState
import opensource.cached_dupe_scanner.storage.TrashPaths

@Composable
fun ScanCommandScreen(
    state: MutableState<ScanUiState>,
    onScanComplete: (ScanResult) -> Unit,
    onScanCancelled: () -> Unit,
    reportRepo: ScanReportRepository,
    settingsStore: AppSettingsStore,
    targetsVersion: Int,
    scanScope: CoroutineScope,
    onReportSaved: () -> Unit,
    taskCoordinator: TaskCoordinator,
    notificationController: TaskNotificationController,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val database = remember {
        Room.databaseBuilder(context, CacheDatabase::class.java, "scan-cache.db")
            .addMigrations(
                CacheMigrations.MIGRATION_1_3,
                CacheMigrations.MIGRATION_2_3,
                CacheMigrations.MIGRATION_3_4,
                CacheMigrations.MIGRATION_4_5,
                CacheMigrations.MIGRATION_5_6,
                CacheMigrations.MIGRATION_6_7,
                CacheMigrations.MIGRATION_7_8,
                CacheMigrations.MIGRATION_8_9,
                CacheMigrations.MIGRATION_9_10,
                CacheMigrations.MIGRATION_10_11,
                CacheMigrations.MIGRATION_11_12,
                CacheMigrations.MIGRATION_12_13,
                CacheMigrations.MIGRATION_13_14,
                CacheMigrations.MIGRATION_14_15,
                CacheMigrations.MIGRATION_15_16,
                CacheMigrations.MIGRATION_16_17
            )
            .build()
    }
    val cacheStore = remember { CacheStore(database.fileCacheDao()) }
    val scanner = remember { IncrementalScanner(cacheStore) }
    ScanCommandScreen(
        state = state,
        onScanComplete = onScanComplete,
        onScanCancelled = onScanCancelled,
        reportRepo = reportRepo,
        settingsStore = settingsStore,
        targetsVersion = targetsVersion,
        scanScope = scanScope,
        onReportSaved = onReportSaved,
        taskCoordinator = taskCoordinator,
        notificationController = notificationController,
        onBack = onBack,
        scanner = scanner,
        modifier = modifier
    )
}

@Composable
internal fun ScanCommandScreen(
    state: MutableState<ScanUiState>,
    onScanComplete: (ScanResult) -> Unit,
    onScanCancelled: () -> Unit,
    reportRepo: ScanReportRepository,
    settingsStore: AppSettingsStore,
    targetsVersion: Int,
    scanScope: CoroutineScope,
    onReportSaved: () -> Unit,
    taskCoordinator: TaskCoordinator,
    notificationController: TaskNotificationController,
    onBack: () -> Unit,
    scanner: IncrementalScanner,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val store = remember { ScanTargetStore(context) }
    val targets = remember { mutableStateOf(store.loadTargets()) }
    val currentJob = remember { mutableStateOf<Job?>(null) }
    val cancelRequested = remember { mutableStateOf(false) }
    val activeTask = taskCoordinator.activeTask(TaskArea.Scan)
    val isBusy = activeTask != null || currentJob.value != null

    LaunchedEffect(Unit) {
        targets.value = store.loadTargets()
    }

    LaunchedEffect(targetsVersion) {
        targets.value = store.loadTargets()
    }

    ScreenScrollColumn(modifier = modifier) {
        item {
            AppTopBar(title = "Scan command", onBack = onBack)
        }

        item {
            Spacer(modifier = Modifier.height(Spacing.itemGap))
        }

        if (targets.value.isEmpty()) {
            item {
                Text("No scan targets yet. Add one first.")
            }
        } else {
            item {
                Text("Select a target:")
            }

            item {
                Spacer(modifier = Modifier.height(Spacing.compactGap))
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.itemGap)) {
                    targets.value.forEach { target ->
                        TargetScanRow(
                            target = target,
                            enabled = !isBusy,
                            onScan = {
                                val settings = settingsStore.load()
                                runScanForTarget(
                                    scope = scanScope,
                                    scanner = scanner,
                                    state = state,
                                    target = target,
                                    onScanComplete = onScanComplete,
                                    onScanCancelled = onScanCancelled,
                                    reportRepo = reportRepo,
                                    skipZeroSizeInDb = settings.skipZeroSizeInDb,
                                    skipTrashBinContentsInScan = settings.skipTrashBinContentsInScan,
                                    onReportSaved = onReportSaved,
                                    taskCoordinator = taskCoordinator,
                                    notificationController = notificationController,
                                    currentJob = currentJob,
                                    cancelRequested = cancelRequested
                                )
                            }
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(Spacing.sectionGap))
            }

            item {
                Button(
                    onClick = {
                        val settings = settingsStore.load()
                        runScanForAllTargets(
                            scope = scanScope,
                            scanner = scanner,
                            state = state,
                            targets = targets.value,
                            onScanComplete = onScanComplete,
                            onScanCancelled = onScanCancelled,
                            reportRepo = reportRepo,
                            skipZeroSizeInDb = settings.skipZeroSizeInDb,
                            skipTrashBinContentsInScan = settings.skipTrashBinContentsInScan,
                            onReportSaved = onReportSaved,
                            taskCoordinator = taskCoordinator,
                            notificationController = notificationController,
                            currentJob = currentJob,
                            cancelRequested = cancelRequested
                        )
                    },
                    enabled = !isBusy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Scan all targets")
                }
            }

            activeTask?.let { task ->
                item {
                    Spacer(modifier = Modifier.height(Spacing.sectionGap))
                    TaskProgressCard(
                        task = task,
                        onCancel = { taskCoordinator.requestCancel(TaskArea.Scan) },
                        cancelText = "Stop scan",
                        currentPathText = { current -> "Current: $current" },
                        extraContent = {
                            Text("Scanned: ${task.processed ?: 0} / ${task.total?.toString() ?: "?"}")
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun TargetScanRow(
    target: ScanTarget,
    enabled: Boolean,
    onScan: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(Spacing.cardPadding),
            verticalArrangement = Arrangement.spacedBy(Spacing.compactGap)
        ) {
            Text(text = target.path, style = MaterialTheme.typography.bodyMedium)
            Button(
                onClick = onScan,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Scan target")
            }
        }
    }
}

private fun runScanForTarget(
    scope: CoroutineScope,
    scanner: IncrementalScanner,
    state: MutableState<ScanUiState>,
    target: ScanTarget,
    onScanComplete: (ScanResult) -> Unit,
    onScanCancelled: () -> Unit,
    reportRepo: ScanReportRepository,
    skipZeroSizeInDb: Boolean,
    skipTrashBinContentsInScan: Boolean,
    onReportSaved: () -> Unit,
    taskCoordinator: TaskCoordinator,
    notificationController: TaskNotificationController,
    currentJob: MutableState<Job?>,
    cancelRequested: MutableState<Boolean>
) {
    val bubbleTotal = scanBubbleTotal(targetCount = 1)
    val started = taskCoordinator.tryStart(
        area = TaskArea.Scan,
        kind = TaskKind.ScanTarget,
        title = scanTaskTitle(),
        detail = scanTaskDetail(
            phase = ScanPhase.Collecting,
            scanned = 0,
            total = null,
            targetPath = target.path
        ),
        processed = 0,
        total = null,
        indeterminate = true,
        bubbleProcessed = 0,
        bubbleTotal = bubbleTotal,
        bubbleIndeterminate = false,
        isCancellable = true,
        onCancel = {
            cancelRequested.value = true
            currentJob.value?.cancel()
            requestImmediateScanCancel(taskCoordinator, notificationController)
        }
    ) ?: return
    notificationController.showActive(started)

    launchTrackedScanJob(
        scope = scope,
        onJobStarted = { currentJob.value = it }
    ) scanJob@{
        try {
            cancelRequested.value = false
            val scanJob = coroutineContext[Job]
            state.value = ScanUiState.Scanning(scanned = 0, total = null)
            val startedAt = System.currentTimeMillis()
            var collectingStart = startedAt
            var detectingStart = 0L
            var hashingStart = 0L
            var collectingEnd = 0L
            var detectingEnd = 0L
            var hashingEnd = 0L
            var lastPhase: ScanPhase? = null
            var detectedCount = 0
            var hashCandidates = 0
            var hashesComputed = 0
            val targetFile = File(target.path)
            if (!targetFile.exists()) {
                state.value = ScanUiState.Error("Target path not found")
                taskCoordinator.fail(
                    area = TaskArea.Scan,
                    title = "Scan failed",
                    detail = "Target path not found."
                )?.let(notificationController::showTerminal)
                return@scanJob
            }

            val execution = captureScanExecution {
                withContext(Dispatchers.IO) {
                    scanner.scan(
                        targetFile,
                        ignore = { file -> shouldIgnoreScanPath(file, skipTrashBinContentsInScan) },
                        skipZeroSizeInDb = skipZeroSizeInDb,
                        onProgress = { scanned, total, current, phase ->
                            if (cancelRequested.value) return@scan
                            if (phase != lastPhase) {
                                Log.d("ScanCommand", "Phase $phase (scanned=$scanned total=${total ?: "?"})")
                                lastPhase = phase
                            }
                            state.value = ScanUiState.Scanning(scanned = scanned, total = total)
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
                                ScanPhase.Saving -> {
                                    if (hashingEnd == 0L) hashingEnd = System.currentTimeMillis()
                                }
                            }
                            val bubbleProgress = scanBubbleProgress(
                                phase = phase,
                                scanned = scanned,
                                total = total,
                                targetIndex = 0,
                                targetCount = 1
                            )
                            taskCoordinator.update(TaskArea.Scan) { task ->
                                task.copy(
                                    title = scanTaskTitle(),
                                    detail = scanTaskDetail(
                                        phase = phase,
                                        scanned = scanned,
                                        total = total,
                                        targetPath = target.path
                                    ),
                                    currentPath = current.normalizedPath,
                                    processed = scanned,
                                    total = total,
                                    indeterminate = total == null || total <= 0,
                                    bubbleProcessed = bubbleProgress.processed,
                                    bubbleTotal = bubbleProgress.total,
                                    bubbleIndeterminate = false
                                )
                            }?.let(notificationController::showActive)
                        },
                        shouldContinue = { scanJob?.isActive != false }
                    )
                }
            }

            hashingEnd = if (hashingStart > 0) System.currentTimeMillis() else hashingEnd
            detectingEnd = if (detectingStart > 0 && detectingEnd == 0L) System.currentTimeMillis() else detectingEnd
            collectingEnd = if (collectingEnd == 0L) System.currentTimeMillis() else collectingEnd
            val finishedAt = System.currentTimeMillis()

            if (execution === ScanExecution.Cancelled) {
                val report = ScanReport(
                    id = UUID.randomUUID().toString(),
                    startedAtMillis = startedAt,
                    finishedAtMillis = finishedAt,
                    targets = listOf(target.path),
                    mode = "single",
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
                persistScanReport(reportRepo, report)
                onReportSaved()
                finishCancelledScan(taskCoordinator, notificationController)
                onScanCancelled()
                return@scanJob
            }

            val result = (execution as ScanExecution.Completed).value
            val report = ScanReport(
                id = UUID.randomUUID().toString(),
                startedAtMillis = startedAt,
                finishedAtMillis = finishedAt,
                targets = listOf(target.path),
                mode = "single",
                cancelled = cancelRequested.value || (scanJob?.isActive == false && result.files.isEmpty()),
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
            persistScanReport(reportRepo, report)
            onReportSaved()
            if (report.cancelled) {
                finishCancelledScan(taskCoordinator, notificationController)
                onScanCancelled()
                return@scanJob
            }
            taskCoordinator.complete(
                area = TaskArea.Scan,
                title = "Scan complete",
                detail = scanTaskCompletedDetail(result),
                processed = result.files.size,
                total = result.files.size,
                indeterminate = false
            )?.let(notificationController::showTerminal)
            onScanComplete(result)
        } finally {
            currentJob.value = null
        }
    }
}

private fun runScanForAllTargets(
    scope: CoroutineScope,
    scanner: IncrementalScanner,
    state: MutableState<ScanUiState>,
    targets: List<ScanTarget>,
    onScanComplete: (ScanResult) -> Unit,
    onScanCancelled: () -> Unit,
    reportRepo: ScanReportRepository,
    skipZeroSizeInDb: Boolean,
    skipTrashBinContentsInScan: Boolean,
    onReportSaved: () -> Unit,
    taskCoordinator: TaskCoordinator,
    notificationController: TaskNotificationController,
    currentJob: MutableState<Job?>,
    cancelRequested: MutableState<Boolean>
) {
    val validTargets = targets.filter { File(it.path).exists() }
    if (validTargets.isEmpty()) {
        state.value = ScanUiState.Error("No scan targets")
        notificationController.clear(TaskArea.Scan)
        return
    }

    val bubbleTotal = scanBubbleTotal(targetCount = validTargets.size)
    val started = taskCoordinator.tryStart(
        area = TaskArea.Scan,
        kind = TaskKind.ScanAll,
        title = scanTaskTitle(),
        detail = scanTaskDetail(
            phase = ScanPhase.Collecting,
            scanned = 0,
            total = null,
            targetPath = null
        ),
        processed = 0,
        total = null,
        indeterminate = true,
        bubbleProcessed = 0,
        bubbleTotal = bubbleTotal,
        bubbleIndeterminate = false,
        isCancellable = true,
        onCancel = {
            cancelRequested.value = true
            currentJob.value?.cancel()
            requestImmediateScanCancel(taskCoordinator, notificationController)
        }
    ) ?: return
    notificationController.showActive(started)

    launchTrackedScanJob(
        scope = scope,
        onJobStarted = { currentJob.value = it }
    ) scanJob@{
        try {
            cancelRequested.value = false
            val scanJob = coroutineContext[Job]
            state.value = ScanUiState.Scanning(scanned = 0, total = null)
            val startedAt = System.currentTimeMillis()
            var collectingStart = startedAt
            var detectingStart = 0L
            var hashingStart = 0L
            var collectingEnd = 0L
            var detectingEnd = 0L
            var hashingEnd = 0L
            var lastPhase: ScanPhase? = null
            var detectedCount = 0
            var hashCandidates = 0
            var hashesComputed = 0
            val results = mutableListOf<ScanResult>()

            validTargets.forEachIndexed { targetIndex, target ->
                val targetFile = File(target.path)
                val execution = captureScanExecution {
                    withContext(Dispatchers.IO) {
                        scanner.scan(
                            targetFile,
                            ignore = { file -> shouldIgnoreScanPath(file, skipTrashBinContentsInScan) },
                            skipZeroSizeInDb = skipZeroSizeInDb,
                            onProgress = { scanned, total, current, phase ->
                                if (cancelRequested.value) return@scan
                                if (phase != lastPhase) {
                                    Log.d("ScanCommand", "Phase $phase (scanned=$scanned total=${total ?: "?"})")
                                    lastPhase = phase
                                }
                                state.value = ScanUiState.Scanning(scanned = scanned, total = total)
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
                                    ScanPhase.Saving -> {
                                        if (hashingEnd == 0L) hashingEnd = System.currentTimeMillis()
                                    }
                                }
                                val bubbleProgress = scanBubbleProgress(
                                    phase = phase,
                                    scanned = scanned,
                                    total = total,
                                    targetIndex = targetIndex,
                                    targetCount = validTargets.size
                                )
                                taskCoordinator.update(TaskArea.Scan) { task ->
                                    task.copy(
                                        title = scanTaskTitle(),
                                        detail = scanTaskDetail(
                                            phase = phase,
                                            scanned = scanned,
                                            total = total,
                                            targetPath = target.path
                                        ),
                                        currentPath = current.normalizedPath,
                                        processed = scanned,
                                        total = total,
                                        indeterminate = total == null || total <= 0,
                                        bubbleProcessed = bubbleProgress.processed,
                                        bubbleTotal = bubbleProgress.total,
                                        bubbleIndeterminate = false
                                    )
                                }?.let(notificationController::showActive)
                            },
                            shouldContinue = { scanJob?.isActive != false }
                        )
                    }
                }
                if (execution === ScanExecution.Cancelled) {
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
                    persistScanReport(reportRepo, report)
                    onReportSaved()
                    finishCancelledScan(taskCoordinator, notificationController)
                    onScanCancelled()
                    return@scanJob
                }

                val result = (execution as ScanExecution.Completed).value
                if (cancelRequested.value || (scanJob?.isActive == false && result.files.isEmpty())) {
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
                    persistScanReport(reportRepo, report)
                    onReportSaved()
                    finishCancelledScan(taskCoordinator, notificationController)
                    onScanCancelled()
                    return@scanJob
                }
                results.add(result)
            }

            if (results.isEmpty()) {
                state.value = ScanUiState.Error("No valid targets to scan")
                taskCoordinator.fail(
                    area = TaskArea.Scan,
                    title = "Scan failed",
                    detail = "No valid targets to scan."
                )?.let(notificationController::showTerminal)
                return@scanJob
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
            persistScanReport(reportRepo, report)
            onReportSaved()
            taskCoordinator.complete(
                area = TaskArea.Scan,
                title = "Scan complete",
                detail = scanTaskCompletedDetail(merged),
                processed = merged.files.size,
                total = merged.files.size,
                indeterminate = false
            )?.let(notificationController::showTerminal)
            onScanComplete(merged)
        } finally {
            currentJob.value = null
        }
    }
}

private suspend fun persistScanReport(
    reportRepo: ScanReportRepository,
    report: ScanReport
) {
    val saveContext = if (report.cancelled) {
        Dispatchers.IO + NonCancellable
    } else {
        Dispatchers.IO
    }
    withContext(saveContext) {
        reportRepo.add(report)
    }
}

private fun finishCancelledScan(
    taskCoordinator: TaskCoordinator,
    notificationController: TaskNotificationController
) {
    val snapshot = taskCoordinator.activeTask(TaskArea.Scan)
    val terminal = taskCoordinator.cancel(
        area = TaskArea.Scan,
        title = "Scan cancelled",
        detail = scanTaskCancelledDetail(snapshot?.processed, snapshot?.total),
        currentPath = snapshot?.currentPath,
        processed = snapshot?.processed,
        total = snapshot?.total,
        indeterminate = snapshot?.indeterminate ?: true
    )
    if (terminal != null) {
        notificationController.showTerminal(terminal)
    }
}

private fun requestImmediateScanCancel(
    taskCoordinator: TaskCoordinator,
    notificationController: TaskNotificationController
) {
    val snapshot = taskCoordinator.activeTask(TaskArea.Scan)
    taskCoordinator.cancel(
        area = TaskArea.Scan,
        title = "Scan cancelled",
        detail = "Cancelling scan.",
        currentPath = snapshot?.currentPath,
        processed = snapshot?.processed,
        total = snapshot?.total,
        indeterminate = snapshot?.indeterminate ?: true
    )?.let(notificationController::showTerminal)
}

private fun shouldIgnoreScanPath(file: File, skipTrashBinContentsInScan: Boolean): Boolean {
    return skipTrashBinContentsInScan && TrashPaths.isInTrashBin(file)
}

private data class ScanBubbleProgress(
    val processed: Int,
    val total: Int
)

private const val SCAN_BUBBLE_COLLECTING_WEIGHT = 250
private const val SCAN_BUBBLE_DETECTING_WEIGHT = 250
private const val SCAN_BUBBLE_HASHING_WEIGHT = 400
private const val SCAN_BUBBLE_SAVING_WEIGHT = 100
private const val SCAN_BUBBLE_TARGET_TOTAL =
    SCAN_BUBBLE_COLLECTING_WEIGHT +
        SCAN_BUBBLE_DETECTING_WEIGHT +
        SCAN_BUBBLE_HASHING_WEIGHT +
        SCAN_BUBBLE_SAVING_WEIGHT

private fun scanBubbleTotal(targetCount: Int): Int {
    return (targetCount.coerceAtLeast(1)) * SCAN_BUBBLE_TARGET_TOTAL
}

private fun scanBubbleProgress(
    phase: ScanPhase,
    scanned: Int,
    total: Int?,
    targetIndex: Int,
    targetCount: Int
): ScanBubbleProgress {
    val phaseStart = when (phase) {
        ScanPhase.Collecting -> 0
        ScanPhase.Detecting -> SCAN_BUBBLE_COLLECTING_WEIGHT
        ScanPhase.Hashing -> SCAN_BUBBLE_COLLECTING_WEIGHT + SCAN_BUBBLE_DETECTING_WEIGHT
        ScanPhase.Saving -> SCAN_BUBBLE_COLLECTING_WEIGHT +
            SCAN_BUBBLE_DETECTING_WEIGHT +
            SCAN_BUBBLE_HASHING_WEIGHT
    }
    val phaseWeight = when (phase) {
        ScanPhase.Collecting -> SCAN_BUBBLE_COLLECTING_WEIGHT
        ScanPhase.Detecting -> SCAN_BUBBLE_DETECTING_WEIGHT
        ScanPhase.Hashing -> SCAN_BUBBLE_HASHING_WEIGHT
        ScanPhase.Saving -> SCAN_BUBBLE_SAVING_WEIGHT
    }
    val phaseFraction = if ((total ?: 0) > 0) {
        (scanned.toFloat() / total!!.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val processed = (targetIndex * SCAN_BUBBLE_TARGET_TOTAL) +
        phaseStart +
        (phaseWeight * phaseFraction).roundToInt()
    return ScanBubbleProgress(
        processed = processed.coerceIn(0, scanBubbleTotal(targetCount)),
        total = scanBubbleTotal(targetCount)
    )
}
