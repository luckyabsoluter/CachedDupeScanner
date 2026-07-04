package opensource.cached_dupe_scanner.ui.home.similarity

import kotlinx.coroutines.CoroutineScope
import opensource.cached_dupe_scanner.notifications.TaskNotificationController
import opensource.cached_dupe_scanner.storage.SimilarityMaintenanceProgress
import opensource.cached_dupe_scanner.storage.SimilarityMaintenanceSummary
import opensource.cached_dupe_scanner.storage.SimilaritySettingsRepository
import opensource.cached_dupe_scanner.tasks.TaskCoordinator

internal fun startScanGeneratedSimilarityTask(
    repository: SimilaritySettingsRepository,
    scope: CoroutineScope,
    taskCoordinator: TaskCoordinator,
    notificationController: TaskNotificationController,
    onFinished: (SimilarityMaintenanceSummary) -> Unit
): Boolean {
    return startScanGeneratedSimilarityTask(
        scope = scope,
        taskCoordinator = taskCoordinator,
        notificationController = notificationController,
        onFinished = onFinished
    ) { shouldContinue, onProgress ->
        repository.generateEnabledResults(
            shouldContinue = shouldContinue,
            onProgress = onProgress
        )
    }
}

internal fun startScanGeneratedSimilarityTask(
    scope: CoroutineScope,
    taskCoordinator: TaskCoordinator,
    notificationController: TaskNotificationController,
    onFinished: (SimilarityMaintenanceSummary) -> Unit,
    runGeneration: ((() -> Boolean), (SimilarityMaintenanceProgress) -> Unit) -> SimilarityMaintenanceSummary
): Boolean {
    return startSimilaritySettingGenerationTask(
        rebuild = false,
        scope = scope,
        taskCoordinator = taskCoordinator,
        notificationController = notificationController,
        onStatusText = {},
        onFinished = onFinished,
        runGeneration = runGeneration
    )
}
