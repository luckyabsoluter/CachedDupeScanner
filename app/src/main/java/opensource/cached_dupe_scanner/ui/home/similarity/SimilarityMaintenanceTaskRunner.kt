package opensource.cached_dupe_scanner.ui.home.similarity

import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import opensource.cached_dupe_scanner.notifications.TaskNotificationController
import opensource.cached_dupe_scanner.storage.SimilarityMaintenanceProgress
import opensource.cached_dupe_scanner.storage.SimilarityMaintenanceSummary
import opensource.cached_dupe_scanner.storage.SimilaritySettingsRepository
import opensource.cached_dupe_scanner.tasks.TaskArea
import opensource.cached_dupe_scanner.tasks.TaskCoordinator
import opensource.cached_dupe_scanner.tasks.TaskKind
import opensource.cached_dupe_scanner.tasks.similarityMaintenanceCancelledDetail
import opensource.cached_dupe_scanner.tasks.similarityMaintenanceCompletedDetail
import opensource.cached_dupe_scanner.tasks.similarityMaintenanceTaskDetail
import opensource.cached_dupe_scanner.tasks.similarityMaintenanceTaskTitle
import opensource.cached_dupe_scanner.tasks.withLinearProgress

internal fun startSimilarityMaintenanceTask(
    repository: SimilaritySettingsRepository,
    settingId: Long?,
    rebuild: Boolean,
    scope: CoroutineScope,
    taskCoordinator: TaskCoordinator,
    notificationController: TaskNotificationController,
    onStatusText: (String) -> Unit,
    onFinished: (SimilarityMaintenanceSummary) -> Unit
): Boolean {
    return startSimilarityMaintenanceTask(
        scope = scope,
        taskCoordinator = taskCoordinator,
        notificationController = notificationController,
        onStatusText = onStatusText,
        onFinished = onFinished
    ) { shouldContinue, onProgress ->
        if (settingId == null) {
            repository.runEnabledMaintenance(
                rebuild = rebuild,
                shouldContinue = shouldContinue,
                onProgress = onProgress
            )
        } else {
            repository.runSettingMaintenance(
                settingId = settingId,
                rebuild = rebuild,
                shouldContinue = shouldContinue,
                onProgress = onProgress
            )
        }
    }
}

internal fun startSimilarityMaintenanceTask(
    scope: CoroutineScope,
    taskCoordinator: TaskCoordinator,
    notificationController: TaskNotificationController,
    onStatusText: (String) -> Unit,
    onFinished: (SimilarityMaintenanceSummary) -> Unit,
    runMaintenance: ((() -> Boolean), (SimilarityMaintenanceProgress) -> Unit) -> SimilarityMaintenanceSummary
): Boolean {
    val cancelRequested = AtomicBoolean(false)
    val started = taskCoordinator.tryStart(
        area = TaskArea.Similarity,
        kind = TaskKind.SimilarityMaintenance,
        title = similarityMaintenanceTaskTitle(),
        detail = "Preparing similarity maintenance.",
        processed = 0,
        total = null,
        indeterminate = true,
        isCancellable = true,
        onCancel = {
            cancelRequested.set(true)
            requestImmediateSimilarityCancel(
                taskCoordinator = taskCoordinator,
                notificationController = notificationController
            )
        }
    ) ?: return false
    notificationController.showActive(started)
    onStatusText(started.detail)

    scope.launch {
        runCatching {
            withContext(Dispatchers.IO) {
                runMaintenance(
                    { !cancelRequested.get() },
                    { progress ->
                        val detail = similarityMaintenanceTaskDetail(progress)
                        taskCoordinator.update(TaskArea.Similarity) { task ->
                            task.withLinearProgress(
                                title = similarityMaintenanceTaskTitle(),
                                detail = detail,
                                currentPath = progress.currentPath,
                                processed = progress.processed,
                                total = progress.total
                            )
                        }?.let(notificationController::showActive)
                        scope.launch { onStatusText(detail) }
                    }
                )
            }
        }.onSuccess { summary ->
            val currentPath = taskCoordinator.activeTask(TaskArea.Similarity)?.currentPath
            if (summary.cancelled) {
                val detail = similarityMaintenanceCancelledDetail(summary)
                taskCoordinator.cancel(
                    area = TaskArea.Similarity,
                    title = "Similarity maintenance cancelled",
                    detail = detail,
                    currentPath = currentPath,
                    processed = summary.processedCount,
                    total = summary.candidateCount,
                    indeterminate = summary.candidateCount <= 0
                )?.let(notificationController::showTerminal)
                onStatusText(detail)
            } else {
                val detail = similarityMaintenanceCompletedDetail(summary)
                taskCoordinator.complete(
                    area = TaskArea.Similarity,
                    title = "Similarity maintenance complete",
                    detail = detail,
                    currentPath = currentPath,
                    processed = summary.processedCount,
                    total = summary.candidateCount,
                    indeterminate = summary.candidateCount <= 0
                )?.let(notificationController::showTerminal)
                onStatusText("Finished: ${summary.clusterCount} clusters, ${summary.duplicateFileCount} files.")
            }
            onFinished(summary)
        }.onFailure {
            taskCoordinator.fail(
                area = TaskArea.Similarity,
                title = "Similarity maintenance failed",
                detail = "The similarity maintenance run did not finish."
            )?.let(notificationController::showTerminal)
            onStatusText("Similarity maintenance failed.")
        }
    }
    return true
}

private fun requestImmediateSimilarityCancel(
    taskCoordinator: TaskCoordinator,
    notificationController: TaskNotificationController
) {
    val snapshot = taskCoordinator.activeTask(TaskArea.Similarity)
    taskCoordinator.cancel(
        area = TaskArea.Similarity,
        title = "Similarity maintenance cancelled",
        detail = "Cancelling similarity maintenance.",
        currentPath = snapshot?.currentPath,
        processed = snapshot?.processed,
        total = snapshot?.total,
        indeterminate = snapshot?.indeterminate ?: true
    )?.let(notificationController::showTerminal)
}

