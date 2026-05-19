package opensource.cached_dupe_scanner.ui.home.similarity

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import opensource.cached_dupe_scanner.notifications.TaskNotificationController
import opensource.cached_dupe_scanner.storage.SimilarityExperimentProgress
import opensource.cached_dupe_scanner.storage.SimilarityExperimentRepository
import opensource.cached_dupe_scanner.storage.SimilarityExperimentRunRequest
import opensource.cached_dupe_scanner.storage.SimilarityExperimentSummary
import opensource.cached_dupe_scanner.tasks.TaskArea
import opensource.cached_dupe_scanner.tasks.TaskCoordinator
import opensource.cached_dupe_scanner.tasks.TaskKind
import opensource.cached_dupe_scanner.tasks.similarityExperimentCancelledDetail
import opensource.cached_dupe_scanner.tasks.similarityExperimentCompletedDetail
import opensource.cached_dupe_scanner.tasks.similarityExperimentTaskDetail
import opensource.cached_dupe_scanner.tasks.similarityExperimentTaskTitle
import opensource.cached_dupe_scanner.tasks.withLinearProgress
import java.util.concurrent.atomic.AtomicBoolean

internal fun startSimilarityExperimentTask(
    repository: SimilarityExperimentRepository,
    request: SimilarityExperimentRunRequest,
    scope: CoroutineScope,
    taskCoordinator: TaskCoordinator,
    notificationController: TaskNotificationController,
    onStatusText: (String) -> Unit,
    onRunFinished: (SimilarityExperimentSummary) -> Unit
): Boolean {
    return startSimilarityExperimentTask(
        request = request,
        scope = scope,
        taskCoordinator = taskCoordinator,
        notificationController = notificationController,
        onStatusText = onStatusText,
        onRunFinished = onRunFinished
    ) { shouldContinue, onProgress ->
        when {
            request.exactThumbnailStep != null -> repository.runExactThumbnailHashExperiment(
                request = request,
                shouldContinue = shouldContinue,
                onProgress = onProgress
            )
            request.durationToleranceStep != null -> repository.runDurationToleranceExperiment(
                request = request,
                shouldContinue = shouldContinue,
                onProgress = onProgress
            )
            request.durationNeighborListStep != null -> repository.runDurationNeighborListExperiment(
                request = request,
                shouldContinue = shouldContinue,
                onProgress = onProgress
            )
            else -> error("Similarity experiment request has no executable step.")
        }
    }
}

internal fun startSimilarityExperimentTask(
    request: SimilarityExperimentRunRequest,
    scope: CoroutineScope,
    taskCoordinator: TaskCoordinator,
    notificationController: TaskNotificationController,
    onStatusText: (String) -> Unit,
    onRunFinished: (SimilarityExperimentSummary) -> Unit,
    runExperiment: ((() -> Boolean), (SimilarityExperimentProgress) -> Unit) -> SimilarityExperimentSummary
): Boolean {
    val cancelRequested = AtomicBoolean(false)
    val started = taskCoordinator.tryStart(
        area = TaskArea.Similarity,
        kind = TaskKind.SimilarityExperiment,
        title = similarityExperimentTaskTitle(),
        detail = "Starting ${request.experiment.name}.",
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
                runExperiment(
                    { !cancelRequested.get() },
                    { progress ->
                        val detail = similarityExperimentTaskDetail(progress)
                        taskCoordinator.update(TaskArea.Similarity) { task ->
                            task.withLinearProgress(
                                title = similarityExperimentTaskTitle(),
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
                val detail = similarityExperimentCancelledDetail(summary)
                taskCoordinator.cancel(
                    area = TaskArea.Similarity,
                    title = "Similarity experiment cancelled",
                    detail = detail,
                    currentPath = currentPath,
                    processed = summary.processedCount,
                    total = summary.candidateCount,
                    indeterminate = summary.candidateCount <= 0
                )?.let(notificationController::showTerminal)
                onStatusText(detail)
            } else {
                val detail = similarityExperimentCompletedDetail(summary)
                taskCoordinator.complete(
                    area = TaskArea.Similarity,
                    title = "Similarity experiment complete",
                    detail = detail,
                    currentPath = currentPath,
                    processed = summary.processedCount,
                    total = summary.candidateCount,
                    indeterminate = summary.candidateCount <= 0
                )?.let(notificationController::showTerminal)
                onStatusText("Finished: ${summary.clusterCount} clusters, ${summary.duplicateFileCount} files.")
            }
            onRunFinished(summary)
        }.onFailure {
            taskCoordinator.fail(
                area = TaskArea.Similarity,
                title = "Similarity experiment failed",
                detail = "The similarity experiment did not finish."
            )?.let(notificationController::showTerminal)
            onStatusText("Similarity experiment failed.")
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
        title = "Similarity experiment cancelled",
        detail = "Cancelling similarity experiment.",
        currentPath = snapshot?.currentPath,
        processed = snapshot?.processed,
        total = snapshot?.total,
        indeterminate = snapshot?.indeterminate ?: true
    )?.let(notificationController::showTerminal)
}
