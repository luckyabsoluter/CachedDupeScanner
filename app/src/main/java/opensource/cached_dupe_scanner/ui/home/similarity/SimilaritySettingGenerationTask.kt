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
import opensource.cached_dupe_scanner.tasks.similarityGenerationCancelledDetail
import opensource.cached_dupe_scanner.tasks.similarityGenerationCompletedDetail
import opensource.cached_dupe_scanner.tasks.similarityGenerationTaskDetail
import opensource.cached_dupe_scanner.tasks.similarityGenerationTaskTitle
import opensource.cached_dupe_scanner.tasks.withLinearProgress

internal fun startSimilaritySettingGenerationTask(
    repository: SimilaritySettingsRepository,
    settingId: Long,
    rebuild: Boolean,
    scope: CoroutineScope,
    taskCoordinator: TaskCoordinator,
    notificationController: TaskNotificationController,
    onStatusText: (String) -> Unit,
    onFinished: (SimilarityMaintenanceSummary) -> Unit
): Boolean {
    return startSimilaritySettingGenerationTask(
        rebuild = rebuild,
        scope = scope,
        taskCoordinator = taskCoordinator,
        notificationController = notificationController,
        onStatusText = onStatusText,
        onFinished = onFinished
    ) { shouldContinue, onProgress ->
        repository.runSettingMaintenance(
            settingId = settingId,
            rebuild = rebuild,
            shouldContinue = shouldContinue,
            onProgress = onProgress
        )
    }
}

internal fun startSimilaritySettingGenerationTask(
    rebuild: Boolean,
    scope: CoroutineScope,
    taskCoordinator: TaskCoordinator,
    notificationController: TaskNotificationController,
    onStatusText: (String) -> Unit,
    onFinished: (SimilarityMaintenanceSummary) -> Unit,
    runGeneration: ((() -> Boolean), (SimilarityMaintenanceProgress) -> Unit) -> SimilarityMaintenanceSummary
): Boolean {
    val cancelRequested = AtomicBoolean(false)
    val taskTitle = similarityGenerationTaskTitle(rebuild)
    val started = taskCoordinator.tryStart(
        area = TaskArea.Similarity,
        kind = TaskKind.SimilarityGeneration,
        title = taskTitle,
        detail = if (rebuild) {
            "Preparing similarity rebuild."
        } else {
            "Preparing similarity update."
        },
        processed = 0,
        total = null,
        indeterminate = true,
        isCancellable = true,
        onCancel = {
            cancelRequested.set(true)
            requestImmediateSimilarityGenerationCancel(
                taskCoordinator = taskCoordinator,
                notificationController = notificationController,
                rebuild = rebuild
            )
        }
    ) ?: return false
    notificationController.showActive(started)
    onStatusText(started.detail)

    scope.launch {
        runCatching {
            withContext(Dispatchers.IO) {
                runGeneration(
                    { !cancelRequested.get() },
                    { progress ->
                        val detail = similarityGenerationTaskDetail(progress)
                        taskCoordinator.update(TaskArea.Similarity) { task ->
                            task.withLinearProgress(
                                title = taskTitle,
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
                val detail = similarityGenerationCancelledDetail(summary)
                taskCoordinator.cancel(
                    area = TaskArea.Similarity,
                    title = if (rebuild) "Similarity rebuild cancelled" else "Similarity update cancelled",
                    detail = detail,
                    currentPath = currentPath,
                    processed = summary.processedCount,
                    total = summary.candidateCount,
                    indeterminate = summary.candidateCount <= 0
                )?.let(notificationController::showTerminal)
                onStatusText(detail)
            } else {
                val detail = similarityGenerationCompletedDetail(summary)
                taskCoordinator.complete(
                    area = TaskArea.Similarity,
                    title = if (rebuild) "Similarity rebuild complete" else "Similarity update complete",
                    detail = detail,
                    currentPath = currentPath,
                    processed = summary.processedCount,
                    total = summary.candidateCount,
                    indeterminate = summary.candidateCount <= 0
                )?.let(notificationController::showTerminal)
                onStatusText(
                    if (rebuild) {
                        "Rebuild complete: ${summary.clusterCount} clusters, ${summary.duplicateFileCount} files."
                    } else {
                        "Update complete: ${summary.clusterCount} clusters, ${summary.duplicateFileCount} files."
                    }
                )
            }
            onFinished(summary)
        }.onFailure {
            taskCoordinator.fail(
                area = TaskArea.Similarity,
                title = if (rebuild) "Similarity rebuild failed" else "Similarity update failed",
                detail = if (rebuild) {
                    "The similarity rebuild did not finish."
                } else {
                    "The similarity update did not finish."
                }
            )?.let(notificationController::showTerminal)
            onStatusText(if (rebuild) "Rebuild failed." else "Update failed.")
        }
    }
    return true
}

private fun requestImmediateSimilarityGenerationCancel(
    taskCoordinator: TaskCoordinator,
    notificationController: TaskNotificationController,
    rebuild: Boolean
) {
    val snapshot = taskCoordinator.activeTask(TaskArea.Similarity)
    taskCoordinator.cancel(
        area = TaskArea.Similarity,
        title = if (rebuild) "Similarity rebuild cancelled" else "Similarity update cancelled",
        detail = if (rebuild) {
            "Cancelling similarity rebuild."
        } else {
            "Cancelling similarity update."
        },
        currentPath = snapshot?.currentPath,
        processed = snapshot?.processed,
        total = snapshot?.total,
        indeterminate = snapshot?.indeterminate ?: true
    )?.let(notificationController::showTerminal)
}
