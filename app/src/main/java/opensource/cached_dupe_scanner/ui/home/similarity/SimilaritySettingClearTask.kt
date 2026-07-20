package opensource.cached_dupe_scanner.ui.home.similarity

import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import opensource.cached_dupe_scanner.notifications.TaskNotificationController
import opensource.cached_dupe_scanner.storage.SimilarityClearMode
import opensource.cached_dupe_scanner.storage.SimilarityClearProgress
import opensource.cached_dupe_scanner.storage.SimilarityClearSummary
import opensource.cached_dupe_scanner.storage.SimilaritySettingsRepository
import opensource.cached_dupe_scanner.tasks.TaskArea
import opensource.cached_dupe_scanner.tasks.TaskCoordinator
import opensource.cached_dupe_scanner.tasks.TaskKind
import opensource.cached_dupe_scanner.tasks.similarityClearCancelledDetail
import opensource.cached_dupe_scanner.tasks.similarityClearCompletedDetail
import opensource.cached_dupe_scanner.tasks.similarityClearTaskDetail
import opensource.cached_dupe_scanner.tasks.similarityClearTaskTitle
import opensource.cached_dupe_scanner.tasks.withLinearProgress

internal fun startSimilaritySettingClearTask(
    repository: SimilaritySettingsRepository,
    settingId: Long,
    mode: SimilarityClearMode,
    scope: CoroutineScope,
    taskCoordinator: TaskCoordinator,
    notificationController: TaskNotificationController,
    onStatusText: (String) -> Unit,
    onFinished: () -> Unit
): Boolean {
    return startSimilaritySettingClearTask(
        mode = mode,
        scope = scope,
        taskCoordinator = taskCoordinator,
        notificationController = notificationController,
        onStatusText = onStatusText,
        onFinished = onFinished
    ) { shouldContinue, onProgress ->
        repository.clearSettingResults(
            settingId = settingId,
            mode = mode,
            shouldContinue = shouldContinue,
            onProgress = onProgress
        )
    }
}

internal fun startSimilaritySettingClearTask(
    mode: SimilarityClearMode,
    scope: CoroutineScope,
    taskCoordinator: TaskCoordinator,
    notificationController: TaskNotificationController,
    onStatusText: (String) -> Unit,
    onFinished: () -> Unit,
    runClear: ((() -> Boolean), (SimilarityClearProgress) -> Unit) -> SimilarityClearSummary
): Boolean {
    val cancelRequested = AtomicBoolean(false)
    val taskTitle = similarityClearTaskTitle(mode)
    val started = taskCoordinator.tryStart(
        area = TaskArea.Similarity,
        kind = TaskKind.SimilarityClear,
        title = taskTitle,
        detail = if (mode == SimilarityClearMode.Incremental) {
            "Preparing bounded clear batches."
        } else {
            "Preparing similarity clear."
        },
        processed = 0,
        total = null,
        indeterminate = true,
        isCancellable = true,
        onCancel = {
            cancelRequested.set(true)
            val detail = if (mode == SimilarityClearMode.Incremental) {
                "Stopping after the current committed batch."
            } else {
                "Stopping after the current committed stage."
            }
            taskCoordinator.update(TaskArea.Similarity) { task ->
                task.copy(detail = detail)
            }?.let(notificationController::showActive)
            onStatusText(detail)
        }
    ) ?: return false
    notificationController.showActive(started)
    onStatusText(started.detail)

    scope.launch {
        runCatching {
            withContext(Dispatchers.IO) {
                runClear(
                    { !cancelRequested.get() },
                    { progress ->
                        val detail = similarityClearTaskDetail(progress)
                        taskCoordinator.update(TaskArea.Similarity) { task ->
                            task.withLinearProgress(
                                title = taskTitle,
                                detail = detail,
                                processed = progress.processed,
                                total = progress.total
                            )
                        }?.let(notificationController::showActive)
                    }
                )
            }
        }.onSuccess { summary ->
            if (summary.cancelled) {
                val detail = similarityClearCancelledDetail(summary)
                taskCoordinator.cancel(
                    area = TaskArea.Similarity,
                    title = if (mode == SimilarityClearMode.Incremental) {
                        "Incremental similarity clear stopped"
                    } else {
                        "Similarity clear stopped"
                    },
                    detail = detail,
                    processed = summary.processed,
                    total = summary.total,
                    indeterminate = summary.total <= 0
                )?.let(notificationController::showTerminal)
                onStatusText(detail)
            } else {
                val detail = similarityClearCompletedDetail(summary)
                taskCoordinator.complete(
                    area = TaskArea.Similarity,
                    title = "Similarity results cleared",
                    detail = detail,
                    processed = summary.processed,
                    total = summary.total,
                    indeterminate = summary.total <= 0
                )?.let(notificationController::showTerminal)
                onStatusText(detail)
            }
            onFinished()
        }.onFailure {
            val snapshot = taskCoordinator.activeTask(TaskArea.Similarity)
            val detail = if (mode == SimilarityClearMode.Incremental) {
                "Some generated data remains. Incremental clear can resume from the committed batches."
            } else {
                "Some generated data may remain. Incremental clear can continue from the committed stages."
            }
            taskCoordinator.fail(
                area = TaskArea.Similarity,
                title = "Similarity clear failed",
                detail = detail,
                processed = snapshot?.processed,
                total = snapshot?.total,
                indeterminate = snapshot?.indeterminate ?: true
            )?.let(notificationController::showTerminal)
            onStatusText(detail)
            onFinished()
        }
    }
    return true
}
