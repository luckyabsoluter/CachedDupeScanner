package opensource.cached_dupe_scanner.ui.home.similarity

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import opensource.cached_dupe_scanner.notifications.TaskNotificationController
import opensource.cached_dupe_scanner.storage.SimilarityMaintenanceProgress
import opensource.cached_dupe_scanner.storage.SimilarityMaintenanceSummary
import opensource.cached_dupe_scanner.storage.SimilaritySettingsRepository
import opensource.cached_dupe_scanner.tasks.TaskArea
import opensource.cached_dupe_scanner.tasks.TaskCoordinator
import opensource.cached_dupe_scanner.tasks.scanSimilarityTaskDetail

internal suspend fun runScanIntegratedSimilarityGeneration(
    repository: SimilaritySettingsRepository,
    taskCoordinator: TaskCoordinator,
    notificationController: TaskNotificationController,
    shouldContinue: () -> Boolean,
    onFinished: (SimilarityMaintenanceSummary) -> Unit
): Boolean {
    return runScanIntegratedSimilarityGeneration(
        hasEnabledSettings = {
            withContext(Dispatchers.IO) { repository.hasEnabledSettings() }
        },
        taskCoordinator = taskCoordinator,
        notificationController = notificationController,
        shouldContinue = shouldContinue,
        onFinished = onFinished
    ) { continueCheck, onProgress ->
        withContext(Dispatchers.IO) {
            repository.generateEnabledResults(
                shouldContinue = continueCheck,
                onProgress = onProgress
            )
        }
    }
}

internal suspend fun runScanIntegratedSimilarityGeneration(
    hasEnabledSettings: suspend () -> Boolean,
    taskCoordinator: TaskCoordinator,
    notificationController: TaskNotificationController,
    shouldContinue: () -> Boolean,
    onFinished: (SimilarityMaintenanceSummary) -> Unit,
    runGeneration: suspend ((() -> Boolean), (SimilarityMaintenanceProgress) -> Unit) -> SimilarityMaintenanceSummary
): Boolean {
    if (!hasEnabledSettings()) return false
    val summary = runGeneration(
        shouldContinue,
        { progress ->
            taskCoordinator.update(TaskArea.Scan) { task ->
                val indeterminate = progress.total <= 0
                task.copy(
                    detail = scanSimilarityTaskDetail(progress),
                    currentPath = progress.currentPath,
                    processed = progress.processed,
                    total = progress.total,
                    indeterminate = indeterminate,
                    bubbleProcessed = progress.processed,
                    bubbleTotal = progress.total,
                    bubbleIndeterminate = indeterminate
                )
            }?.let(notificationController::showActive)
        }
    )
    onFinished(summary)
    return true
}
