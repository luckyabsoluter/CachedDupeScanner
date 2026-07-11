package opensource.cached_dupe_scanner.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import opensource.cached_dupe_scanner.cache.DuplicateGroupEntity
import opensource.cached_dupe_scanner.core.FileMetadata
import opensource.cached_dupe_scanner.notifications.TaskNotificationController
import opensource.cached_dupe_scanner.storage.DuplicateGroupSortKey
import opensource.cached_dupe_scanner.storage.ResultsDbRepository
import opensource.cached_dupe_scanner.tasks.TaskArea
import opensource.cached_dupe_scanner.tasks.TaskCoordinator
import opensource.cached_dupe_scanner.tasks.TaskKind
import opensource.cached_dupe_scanner.tasks.bulkDeleteCompletedDetail
import opensource.cached_dupe_scanner.tasks.bulkDeleteTaskDetail
import opensource.cached_dupe_scanner.tasks.bulkDeleteTaskTitle
import opensource.cached_dupe_scanner.tasks.withLinearProgress
import opensource.cached_dupe_scanner.ui.components.AppTopBar
import opensource.cached_dupe_scanner.ui.components.OptionButtonGrid
import opensource.cached_dupe_scanner.ui.components.ScrollbarDefaults
import opensource.cached_dupe_scanner.ui.components.Spacing
import opensource.cached_dupe_scanner.ui.components.VerticalLazyScrollbar

internal enum class ResultsBulkDeleteCommandType(
    val title: String,
    val description: String
) {
    KeepOneNonMatch(
        title = "Delete matches, keep 1 non-match",
        description = "Find result groups where exactly one file does not match the text rule, then delete the matching files."
    ),
    KeepByModified(
        title = "Keep by modified time",
        description = "In each eligible result group, choose whether to keep the oldest file or the newest file and delete the rest."
    )
}

internal enum class ResultsBulkDeleteModifiedKeepMode(val label: String) {
    Oldest("Keep oldest"),
    Newest("Keep newest")
}

internal enum class ResultsBulkDeleteTextTarget(val label: String) {
    FileName("File name"),
    FullPath("Path")
}

internal data class KeepOneNonMatchBulkDeleteCommandConfig(
    val target: ResultsBulkDeleteTextTarget = ResultsBulkDeleteTextTarget.FileName,
    val operator: ResultsFilterTextOperator = ResultsFilterTextOperator.Contains,
    val phrase: String = ""
)

internal const val BULK_DELETE_PREVIEW_SAMPLE_LIMIT = 50

internal data class ResultsBulkDeleteCandidate(
    val group: DuplicateGroupEntity,
    val survivor: FileMetadata,
    val deleteTargets: List<FileMetadata>
)

internal data class ResultsBulkDeletePreview(
    val snapshotUpdatedAtMillis: Long,
    val totalGroupCount: Int,
    val filterMatchedGroupCount: Int,
    val candidates: List<ResultsBulkDeleteCandidate>,
    val candidateGroupCount: Int = candidates.size,
    val candidateFileCount: Int = candidates.sumOf { it.deleteTargets.size },
    val sourceSnapshotId: String = snapshotUpdatedAtMillis.toString()
)

internal data class ResultsBulkDeletePreviewProgress(
    val scannedGroupCount: Int = 0,
    val totalGroupCount: Int = 0,
    val filterMatchedGroupCount: Int = 0,
    val candidateGroupCount: Int = 0,
    val candidateFileCount: Int = 0
)

internal data class ResultsBulkDeleteTouchedGroupKey(
    val sizeBytes: Long,
    val hashHex: String
)

internal data class ResultsBulkDeleteExecutionOutcome(
    val successCount: Int,
    val failedPaths: Set<String>,
    val touchedGroups: Set<ResultsBulkDeleteTouchedGroupKey> = emptySet(),
    val touchedSourceIds: Set<Long> = emptySet()
)

internal data class ResultsBulkDeleteExecutionProgress(
    val processed: Int = 0,
    val total: Int = 0,
    val failed: Int = 0,
    val currentPath: String? = null
)

internal interface BulkDeleteOperations {
    val totalGroupCount: Int
    val snapshotAvailable: Boolean

    suspend fun buildKeepOnePreview(
        filterDefinition: ResultsFilterDefinition,
        config: KeepOneNonMatchBulkDeleteCommandConfig,
        onProgress: (ResultsBulkDeletePreviewProgress) -> Unit
    ): ResultsBulkDeletePreview

    suspend fun buildKeepModifiedPreview(
        filterDefinition: ResultsFilterDefinition,
        keepNewest: Boolean,
        onProgress: (ResultsBulkDeletePreviewProgress) -> Unit
    ): ResultsBulkDeletePreview

    suspend fun executeKeepOne(
        preview: ResultsBulkDeletePreview,
        filterDefinition: ResultsFilterDefinition,
        config: KeepOneNonMatchBulkDeleteCommandConfig,
        onDeleteFile: suspend (FileMetadata) -> Boolean,
        onProgress: (ResultsBulkDeleteExecutionProgress) -> Unit
    ): ResultsBulkDeleteExecutionOutcome

    suspend fun executeKeepModified(
        preview: ResultsBulkDeletePreview,
        filterDefinition: ResultsFilterDefinition,
        keepNewest: Boolean,
        onDeleteFile: suspend (FileMetadata) -> Boolean,
        onProgress: (ResultsBulkDeleteExecutionProgress) -> Unit
    ): ResultsBulkDeleteExecutionOutcome

    suspend fun hasSnapshotChanged(preview: ResultsBulkDeletePreview): Boolean

    suspend fun refreshGroups(touchedGroups: Set<ResultsBulkDeleteTouchedGroupKey>)
}

internal class ResultsDbBulkDeleteOperations(
    private val resultsRepo: ResultsDbRepository,
    private val sortKey: DuplicateGroupSortKey,
    private val snapshotUpdatedAtMillis: Long?,
    override val totalGroupCount: Int
) : BulkDeleteOperations {
    override val snapshotAvailable: Boolean
        get() = snapshotUpdatedAtMillis != null

    override suspend fun buildKeepOnePreview(
        filterDefinition: ResultsFilterDefinition,
        config: KeepOneNonMatchBulkDeleteCommandConfig,
        onProgress: (ResultsBulkDeletePreviewProgress) -> Unit
    ): ResultsBulkDeletePreview {
        return buildKeepOneNonMatchBulkDeletePreview(
            resultsRepo = resultsRepo,
            sortKey = sortKey,
            snapshotUpdatedAtMillis = requireNotNull(snapshotUpdatedAtMillis),
            totalGroupCount = totalGroupCount,
            filterDefinition = filterDefinition,
            config = config,
            onProgress = onProgress
        )
    }

    override suspend fun buildKeepModifiedPreview(
        filterDefinition: ResultsFilterDefinition,
        keepNewest: Boolean,
        onProgress: (ResultsBulkDeletePreviewProgress) -> Unit
    ): ResultsBulkDeletePreview {
        return buildKeepModifiedBulkDeletePreview(
            resultsRepo = resultsRepo,
            sortKey = sortKey,
            snapshotUpdatedAtMillis = requireNotNull(snapshotUpdatedAtMillis),
            totalGroupCount = totalGroupCount,
            filterDefinition = filterDefinition,
            keepNewest = keepNewest,
            onProgress = onProgress
        )
    }

    override suspend fun executeKeepOne(
        preview: ResultsBulkDeletePreview,
        filterDefinition: ResultsFilterDefinition,
        config: KeepOneNonMatchBulkDeleteCommandConfig,
        onDeleteFile: suspend (FileMetadata) -> Boolean,
        onProgress: (ResultsBulkDeleteExecutionProgress) -> Unit
    ): ResultsBulkDeleteExecutionOutcome {
        return executeBulkDeleteCommand(
            resultsRepo = resultsRepo,
            sortKey = sortKey,
            snapshotUpdatedAtMillis = preview.snapshotUpdatedAtMillis,
            totalGroupCount = totalGroupCount,
            filterDefinition = filterDefinition,
            totalDeleteTargetCount = preview.candidateFileCount,
            onDeleteFile = onDeleteFile,
            onProgress = onProgress
        ) { group, members ->
            buildKeepOneNonMatchBulkDeleteCandidate(group, members, config)
        }
    }

    override suspend fun executeKeepModified(
        preview: ResultsBulkDeletePreview,
        filterDefinition: ResultsFilterDefinition,
        keepNewest: Boolean,
        onDeleteFile: suspend (FileMetadata) -> Boolean,
        onProgress: (ResultsBulkDeleteExecutionProgress) -> Unit
    ): ResultsBulkDeleteExecutionOutcome {
        return executeBulkDeleteCommand(
            resultsRepo = resultsRepo,
            sortKey = sortKey,
            snapshotUpdatedAtMillis = preview.snapshotUpdatedAtMillis,
            totalGroupCount = totalGroupCount,
            filterDefinition = filterDefinition,
            totalDeleteTargetCount = preview.candidateFileCount,
            onDeleteFile = onDeleteFile,
            onProgress = onProgress
        ) { group, members ->
            buildKeepModifiedBulkDeleteCandidate(group, members, keepNewest)
        }
    }

    override suspend fun hasSnapshotChanged(preview: ResultsBulkDeletePreview): Boolean {
        return resultsRepo.hasSnapshotChanged(preview.snapshotUpdatedAtMillis)
    }

    override suspend fun refreshGroups(touchedGroups: Set<ResultsBulkDeleteTouchedGroupKey>) {
        touchedGroups.forEach { group ->
            resultsRepo.refreshSingleGroup(
                sizeBytes = group.sizeBytes,
                hashHex = group.hashHex
            )
        }
    }
}

internal fun ResultsBulkDeletePreview.deleteTargetCount(): Int {
    return candidateFileCount
}

internal fun ResultsBulkDeletePreview.hasCappedCandidates(): Boolean {
    return candidateGroupCount > candidates.size
}

internal fun ResultsBulkDeletePreview.readyMessage(): String {
    return if (candidateGroupCount == 0) {
        "No groups matched this command."
    } else {
        "$candidateGroupCount groups and $candidateFileCount files are ready."
    }
}

internal fun ResultsBulkDeletePreview.progressSummaryLines(): List<String> {
    return bulkDeletePreviewProgressLines(
        ResultsBulkDeletePreviewProgress(
            scannedGroupCount = totalGroupCount,
            totalGroupCount = totalGroupCount,
            filterMatchedGroupCount = filterMatchedGroupCount,
            candidateGroupCount = candidateGroupCount,
            candidateFileCount = candidateFileCount
        )
    )
}

internal fun ResultsBulkDeletePreviewProgress.progressLines(): List<String> {
    return bulkDeletePreviewProgressLines(this)
}

private fun bulkDeletePreviewProgressLines(
    progress: ResultsBulkDeletePreviewProgress
): List<String> {
    return listOf(
        "${progress.scannedGroupCount}/${progress.totalGroupCount} groups loaded before filtering",
        "${progress.filterMatchedGroupCount} groups passed the current filter",
        "${progress.candidateGroupCount} candidate groups · ${progress.candidateFileCount} files to delete"
    )
}

internal fun ResultsBulkDeleteExecutionOutcome.message(): String {
    return when {
        successCount == 0 && failedPaths.isEmpty() -> "No files were deleted."
        failedPaths.isEmpty() -> "$successCount files deleted."
        successCount == 0 -> "Delete failed for ${failedPaths.size} files."
        else -> "$successCount files deleted, ${failedPaths.size} failed."
    }
}

internal fun ResultsBulkDeletePreview.firstDeleteTargetPath(): String? {
    return candidates.firstNotNullOfOrNull { candidate ->
        candidate.deleteTargets.firstOrNull()?.normalizedPath
    }
}

internal fun buildKeepModifiedBulkDeleteCandidate(
    group: DuplicateGroupEntity,
    members: List<FileMetadata>,
    keepNewest: Boolean
): ResultsBulkDeleteCandidate? {
    if (members.size <= 1) return null

    val sortedMembers = members.sortedWith(
        compareBy<FileMetadata> { it.lastModifiedMillis }
            .thenBy { it.normalizedPath }
    )
    val survivor = if (keepNewest) {
        sortedMembers.last()
    } else {
        sortedMembers.first()
    }
    val deleteTargets = sortedMembers.filterNot { it.normalizedPath == survivor.normalizedPath }
    if (deleteTargets.isEmpty()) return null

    return ResultsBulkDeleteCandidate(
        group = group,
        survivor = survivor,
        deleteTargets = deleteTargets
    )
}

internal fun buildKeepOneNonMatchBulkDeleteCandidate(
    group: DuplicateGroupEntity,
    members: List<FileMetadata>,
    config: KeepOneNonMatchBulkDeleteCommandConfig
): ResultsBulkDeleteCandidate? {
    val phrase = config.phrase.trim()
    if (phrase.isEmpty() || members.isEmpty()) return null

    val matching = mutableListOf<FileMetadata>()
    val nonMatching = mutableListOf<FileMetadata>()
    members.forEach { member ->
        if (matchesKeepOneNonMatchCommand(member, config)) {
            matching += member
        } else {
            nonMatching += member
        }
    }
    if (matching.isEmpty() || nonMatching.size != 1) return null

    return ResultsBulkDeleteCandidate(
        group = group,
        survivor = nonMatching.single(),
        deleteTargets = matching
    )
}

internal fun collectKeepOneNonMatchBulkDeleteCandidates(
    groupsWithMembers: List<Pair<DuplicateGroupEntity, List<FileMetadata>>>,
    filterDefinition: ResultsFilterDefinition,
    config: KeepOneNonMatchBulkDeleteCommandConfig
): List<ResultsBulkDeleteCandidate> {
    return collectBulkDeleteCandidates(groupsWithMembers, filterDefinition) { group, members ->
        buildKeepOneNonMatchBulkDeleteCandidate(
            group = group,
            members = members,
            config = config
        )
    }
}

internal fun collectKeepModifiedBulkDeleteCandidates(
    groupsWithMembers: List<Pair<DuplicateGroupEntity, List<FileMetadata>>>,
    filterDefinition: ResultsFilterDefinition,
    keepNewest: Boolean
): List<ResultsBulkDeleteCandidate> {
    return collectBulkDeleteCandidates(groupsWithMembers, filterDefinition) { group, members ->
        buildKeepModifiedBulkDeleteCandidate(
            group = group,
            members = members,
            keepNewest = keepNewest
        )
    }
}

private fun collectBulkDeleteCandidates(
    groupsWithMembers: List<Pair<DuplicateGroupEntity, List<FileMetadata>>>,
    filterDefinition: ResultsFilterDefinition,
    buildCandidate: (DuplicateGroupEntity, List<FileMetadata>) -> ResultsBulkDeleteCandidate?
): List<ResultsBulkDeleteCandidate> {
    return groupsWithMembers.mapNotNull { (group, members) ->
        if (!matchesResultsFilter(filterDefinition, group, members)) {
            null
        } else {
            buildCandidate(group, members)
        }
    }
}

internal suspend fun buildKeepOneNonMatchBulkDeletePreview(
    resultsRepo: ResultsDbRepository,
    sortKey: DuplicateGroupSortKey,
    snapshotUpdatedAtMillis: Long,
    totalGroupCount: Int,
    filterDefinition: ResultsFilterDefinition,
    config: KeepOneNonMatchBulkDeleteCommandConfig,
    sourcePageSize: Int = 100,
    onProgress: (ResultsBulkDeletePreviewProgress) -> Unit = {}
): ResultsBulkDeletePreview {
    return buildBulkDeletePreview(
        resultsRepo = resultsRepo,
        sortKey = sortKey,
        snapshotUpdatedAtMillis = snapshotUpdatedAtMillis,
        totalGroupCount = totalGroupCount,
        filterDefinition = filterDefinition,
        sourcePageSize = sourcePageSize,
        onProgress = onProgress
    ) { group, members ->
        buildKeepOneNonMatchBulkDeleteCandidate(
            group = group,
            members = members,
            config = config
        )
    }
}

internal suspend fun buildKeepModifiedBulkDeletePreview(
    resultsRepo: ResultsDbRepository,
    sortKey: DuplicateGroupSortKey,
    snapshotUpdatedAtMillis: Long,
    totalGroupCount: Int,
    filterDefinition: ResultsFilterDefinition,
    keepNewest: Boolean,
    sourcePageSize: Int = 100,
    onProgress: (ResultsBulkDeletePreviewProgress) -> Unit = {}
): ResultsBulkDeletePreview {
    return buildBulkDeletePreview(
        resultsRepo = resultsRepo,
        sortKey = sortKey,
        snapshotUpdatedAtMillis = snapshotUpdatedAtMillis,
        totalGroupCount = totalGroupCount,
        filterDefinition = filterDefinition,
        sourcePageSize = sourcePageSize,
        onProgress = onProgress
    ) { group, members ->
        buildKeepModifiedBulkDeleteCandidate(
            group = group,
            members = members,
            keepNewest = keepNewest
        )
    }
}

private suspend fun buildBulkDeletePreview(
    resultsRepo: ResultsDbRepository,
    sortKey: DuplicateGroupSortKey,
    snapshotUpdatedAtMillis: Long,
    totalGroupCount: Int,
    filterDefinition: ResultsFilterDefinition,
    sourcePageSize: Int,
    onProgress: (ResultsBulkDeletePreviewProgress) -> Unit,
    buildCandidate: (DuplicateGroupEntity, List<FileMetadata>) -> ResultsBulkDeleteCandidate?
): ResultsBulkDeletePreview {
    val candidates = mutableListOf<ResultsBulkDeleteCandidate>()
    var sourceOffset = 0
    var filterMatchedGroupCount = 0
    var candidateGroupCount = 0
    var candidateFileCount = 0
    val safeTotalGroupCount = totalGroupCount.coerceAtLeast(0)

    onProgress(
        ResultsBulkDeletePreviewProgress(
            totalGroupCount = safeTotalGroupCount
        )
    )

    while (true) {
        val page = withContext(Dispatchers.IO) {
            resultsRepo.loadPageAtSnapshot(
                sortKey = sortKey,
                snapshotUpdatedAtMillis = snapshotUpdatedAtMillis,
                offset = sourceOffset,
                limit = sourcePageSize
            )
        }
        if (page.isEmpty()) {
            break
        }

        page.forEach { group ->
            val matchesFilter = withContext(Dispatchers.IO) {
                matchesResultsFilterPagedMembers(
                    definition = filterDefinition,
                    group = group,
                    memberPages = {
                        resultsRepo.groupMemberPages(
                            sizeBytes = group.sizeBytes,
                            hashHex = group.hashHex
                        )
                    }
                )
            }
            if (matchesFilter) {
                val members = withContext(Dispatchers.IO) {
                    resultsRepo.listAllGroupMembers(
                        sizeBytes = group.sizeBytes,
                        hashHex = group.hashHex
                    )
                }
                filterMatchedGroupCount += 1
                buildCandidate(group, members)?.let { candidate ->
                    candidateGroupCount += 1
                    candidateFileCount += candidate.deleteTargets.size
                    if (candidates.size < BULK_DELETE_PREVIEW_SAMPLE_LIMIT) {
                        candidates += candidate
                    }
                }
            }
        }

        sourceOffset += page.size
        onProgress(
            ResultsBulkDeletePreviewProgress(
                scannedGroupCount = sourceOffset.coerceAtMost(safeTotalGroupCount),
                totalGroupCount = safeTotalGroupCount,
                filterMatchedGroupCount = filterMatchedGroupCount,
                candidateGroupCount = candidateGroupCount,
                candidateFileCount = candidateFileCount
            )
        )
        if (page.size < sourcePageSize) {
            break
        }
    }

    return ResultsBulkDeletePreview(
        snapshotUpdatedAtMillis = snapshotUpdatedAtMillis,
        totalGroupCount = safeTotalGroupCount,
        filterMatchedGroupCount = filterMatchedGroupCount,
        candidates = candidates,
        candidateGroupCount = candidateGroupCount,
        candidateFileCount = candidateFileCount
    )
}

internal suspend fun executeBulkDeleteCommand(
    resultsRepo: ResultsDbRepository,
    sortKey: DuplicateGroupSortKey,
    snapshotUpdatedAtMillis: Long,
    totalGroupCount: Int,
    filterDefinition: ResultsFilterDefinition,
    sourcePageSize: Int = 100,
    totalDeleteTargetCount: Int = 0,
    onDeleteFile: suspend (FileMetadata) -> Boolean,
    onProgress: (ResultsBulkDeleteExecutionProgress) -> Unit = {},
    buildCandidate: (DuplicateGroupEntity, List<FileMetadata>) -> ResultsBulkDeleteCandidate?
): ResultsBulkDeleteExecutionOutcome {
    val total = totalDeleteTargetCount.coerceAtLeast(0)
    var afterSizeBytes: Long? = null
    var afterHashHex: String? = null
    var visitedGroupCount = 0
    var processed = 0
    var successCount = 0
    val failedPaths = linkedSetOf<String>()
    val touchedGroups = linkedSetOf<ResultsBulkDeleteTouchedGroupKey>()
    val safeTotalGroupCount = totalGroupCount.coerceAtLeast(0)

    onProgress(ResultsBulkDeleteExecutionProgress(total = total))

    while (true) {
        val page = withContext(Dispatchers.IO) {
            resultsRepo.loadKeyPageAtSnapshot(
                snapshotUpdatedAtMillis = snapshotUpdatedAtMillis,
                afterSizeBytes = afterSizeBytes,
                afterHashHex = afterHashHex,
                limit = sourcePageSize
            )
        }
        if (page.isEmpty()) {
            break
        }

        page.forEach { group ->
            afterSizeBytes = group.sizeBytes
            afterHashHex = group.hashHex
            visitedGroupCount += 1
            val matchesFilter = withContext(Dispatchers.IO) {
                matchesResultsFilterPagedMembers(
                    definition = filterDefinition,
                    group = group,
                    memberPages = {
                        resultsRepo.groupMemberPages(
                            sizeBytes = group.sizeBytes,
                            hashHex = group.hashHex
                        )
                    }
                )
            }
            if (matchesFilter) {
                val members = withContext(Dispatchers.IO) {
                    resultsRepo.listAllGroupMembers(
                        sizeBytes = group.sizeBytes,
                        hashHex = group.hashHex
                    )
                }
                buildCandidate(group, members)?.let { candidate ->
                    touchedGroups += ResultsBulkDeleteTouchedGroupKey(
                        sizeBytes = candidate.group.sizeBytes,
                        hashHex = candidate.group.hashHex
                    )
                    candidate.deleteTargets.forEach { file ->
                        val deleted = runCatching { onDeleteFile(file) }.getOrDefault(false)
                        processed += 1
                        if (deleted) {
                            successCount += 1
                        } else {
                            failedPaths += file.normalizedPath
                        }
                        onProgress(
                            ResultsBulkDeleteExecutionProgress(
                                processed = processed,
                                total = total,
                                failed = failedPaths.size,
                                currentPath = file.normalizedPath
                            )
                        )
                    }
                }
            }
        }

        if (page.size < sourcePageSize ||
            (safeTotalGroupCount > 0 && visitedGroupCount >= safeTotalGroupCount)
        ) {
            break
        }
    }

    return ResultsBulkDeleteExecutionOutcome(
        successCount = successCount,
        failedPaths = failedPaths,
        touchedGroups = touchedGroups
    )
}

internal suspend fun executeBulkDeletePreview(
    preview: ResultsBulkDeletePreview,
    onDeleteFile: suspend (FileMetadata) -> Boolean,
    onProgress: (ResultsBulkDeleteExecutionProgress) -> Unit = {}
): ResultsBulkDeleteExecutionOutcome {
    val total = preview.deleteTargetCount()
    var processed = 0
    var successCount = 0
    val failedPaths = linkedSetOf<String>()
    onProgress(ResultsBulkDeleteExecutionProgress(total = total))
    preview.candidates.forEach { candidate ->
        candidate.deleteTargets.forEach { file ->
            val deleted = runCatching { onDeleteFile(file) }.getOrDefault(false)
            processed += 1
            if (deleted) {
                successCount += 1
            } else {
                failedPaths += file.normalizedPath
            }
            onProgress(
                ResultsBulkDeleteExecutionProgress(
                    processed = processed,
                    total = total,
                    failed = failedPaths.size,
                    currentPath = file.normalizedPath
                )
            )
        }
    }
    return ResultsBulkDeleteExecutionOutcome(
        successCount = successCount,
        failedPaths = failedPaths
    )
}

internal fun startBulkDeleteTask(
    preview: ResultsBulkDeletePreview,
    scope: kotlinx.coroutines.CoroutineScope,
    taskCoordinator: TaskCoordinator,
    notificationController: TaskNotificationController,
    executeDelete: suspend (
        onProgress: (ResultsBulkDeleteExecutionProgress) -> Unit
    ) -> ResultsBulkDeleteExecutionOutcome,
    onSnapshotChanged: suspend () -> Boolean,
    onRefreshGroups: suspend (Set<ResultsBulkDeleteTouchedGroupKey>) -> Unit,
    onSuccess: (ResultsBulkDeleteExecutionOutcome) -> Unit,
    onSnapshotStale: () -> Unit,
    onFailure: () -> Unit,
    onFinished: () -> Unit
) {
    val total = preview.deleteTargetCount()
    val started = taskCoordinator.tryStart(
        area = TaskArea.Trash,
        kind = TaskKind.BulkDelete,
        title = bulkDeleteTaskTitle(),
        detail = bulkDeleteTaskDetail(processed = 0, total = total, failed = 0),
        currentPath = preview.firstDeleteTargetPath(),
        processed = 0,
        total = total,
        indeterminate = total <= 0,
        isCancellable = false
    ) ?: run {
        onFailure()
        onFinished()
        return
    }
    notificationController.showActive(started)
    scope.launch {
        var terminalShown = false
        try {
            val snapshotChanged = withContext(Dispatchers.IO) { onSnapshotChanged() }
            if (snapshotChanged) {
                taskCoordinator.fail(
                    area = TaskArea.Trash,
                    title = "Bulk delete skipped",
                    detail = "The results snapshot changed. Build the preview again.",
                    processed = 0,
                    total = total,
                    indeterminate = total <= 0
                )?.let(notificationController::showTerminal)
                terminalShown = true
                onSnapshotStale()
                return@launch
            }

            val outcome = executeDelete { progress ->
                taskCoordinator.update(TaskArea.Trash) { task ->
                    task.withLinearProgress(
                        title = bulkDeleteTaskTitle(),
                        detail = bulkDeleteTaskDetail(
                            processed = progress.processed,
                            total = progress.total,
                            failed = progress.failed
                        ),
                        currentPath = progress.currentPath,
                        processed = progress.processed,
                        total = progress.total
                    )
                }?.let(notificationController::showActive)
            }
            withContext(Dispatchers.IO) { onRefreshGroups(outcome.touchedGroups) }
            taskCoordinator.complete(
                area = TaskArea.Trash,
                title = "Bulk delete complete",
                detail = bulkDeleteCompletedDetail(
                    successCount = outcome.successCount,
                    failedCount = outcome.failedPaths.size
                ),
                processed = total,
                total = total,
                indeterminate = total <= 0
            )?.let(notificationController::showTerminal)
            terminalShown = true
            onSuccess(outcome)
        } catch (_: Exception) {
            if (!terminalShown) {
                val task = taskCoordinator.activeTask(TaskArea.Trash)
                taskCoordinator.fail(
                    area = TaskArea.Trash,
                    title = "Bulk delete failed",
                    detail = "Unable to delete the selected files.",
                    currentPath = task?.currentPath,
                    processed = task?.processed,
                    total = task?.total,
                    indeterminate = task?.indeterminate ?: true
                )?.let(notificationController::showTerminal)
            }
            onFailure()
        } finally {
            onFinished()
        }
    }
}

@Composable
internal fun ResultsBulkDeleteCatalogScreen(
    appliedFilter: ResultsFilterDefinition,
    onBack: () -> Unit,
    onOpenCommand: (ResultsBulkDeleteCommandType) -> Unit
) {
    val listState = rememberLazyListState()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .testTag("bulk-delete-catalog-list")
                    .padding(Spacing.screenPadding),
                contentPadding = PaddingValues(
                    end = ScrollbarDefaults.ThumbWidth + 8.dp,
                    bottom = 24.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    AppTopBar(title = "Bulk delete", onBack = onBack)
                }
                item {
                    Text(
                        text = "Choose a bulk delete command. Commands only act on the current results snapshot, and active result filters limit which groups can be touched.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                if (appliedFilter.hasActiveRules()) {
                    item {
                        Card {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text("Active result filter", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = summarizeResultsFilter(appliedFilter),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = "Bulk delete commands skip groups that do not match the current filter.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                items(ResultsBulkDeleteCommandType.entries) { command ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenCommand(command) }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(command.title, style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = command.description,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            OutlinedButton(onClick = { onOpenCommand(command) }) {
                                Text("Open")
                            }
                        }
                    }
                }
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

    BackHandler(onBack = onBack)
}

@Composable
internal fun KeepOneNonMatchBulkDeleteScreen(
    operations: BulkDeleteOperations,
    appliedFilter: ResultsFilterDefinition,
    imageLoader: ImageLoader,
    keepLoadedThumbnailsInMemory: Boolean,
    thumbnailSizeScale: Float,
    rememberedPreviewCache: MutableMap<String, ImageBitmap>,
    taskScope: CoroutineScope,
    taskCoordinator: TaskCoordinator,
    notificationController: TaskNotificationController,
    onDeleteFile: (suspend (FileMetadata) -> Boolean)?,
    onBack: () -> Unit,
    onResultsChanged: (ResultsBulkDeleteExecutionOutcome) -> Unit
) {
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val totalGroupCount = operations.totalGroupCount
    val config = remember { mutableStateOf(KeepOneNonMatchBulkDeleteCommandConfig()) }
    val preview = remember { mutableStateOf<ResultsBulkDeletePreview?>(null) }
    val progress = remember {
        mutableStateOf(
            ResultsBulkDeletePreviewProgress(totalGroupCount = totalGroupCount.coerceAtLeast(0))
        )
    }
    val isPreviewLoading = remember { mutableStateOf(false) }
    val isExecuting = remember { mutableStateOf(false) }
    val confirmExecute = remember { mutableStateOf(false) }
    val message = remember { mutableStateOf<String?>(null) }

    fun updateConfig(updated: KeepOneNonMatchBulkDeleteCommandConfig) {
        config.value = updated
        preview.value = null
        progress.value = ResultsBulkDeletePreviewProgress(totalGroupCount = totalGroupCount.coerceAtLeast(0))
        message.value = null
    }

    val currentPreview = preview.value
    val previewDeleteCount = currentPreview?.deleteTargetCount() ?: 0
    val canBuildPreview = !isPreviewLoading.value &&
        !isExecuting.value &&
        config.value.phrase.trim().isNotEmpty() &&
        operations.snapshotAvailable &&
        totalGroupCount > 0
    val canExecute = !isPreviewLoading.value &&
        !isExecuting.value &&
        onDeleteFile != null &&
        currentPreview != null &&
        currentPreview.candidateFileCount > 0

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .testTag("bulk-delete-keep-one-list")
                    .padding(Spacing.screenPadding),
                contentPadding = PaddingValues(
                    end = ScrollbarDefaults.ThumbWidth + 8.dp,
                    bottom = 24.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    AppTopBar(title = "Delete matches, keep 1 non-match", onBack = onBack)
                }
                item {
                    Text(
                        text = "Scan the current results snapshot, keep the one file that does not match your rule, and delete the matching files from eligible result groups.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                if (appliedFilter.hasActiveRules()) {
                    item {
                        Card {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text("Current filter applies", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = summarizeResultsFilter(appliedFilter),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = "Groups outside the active result filter are excluded from preview and execution.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                item {
                    Card {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text("Command rule", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "Matching files are deleted only when exactly one non-matching file remains in the group.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text("Target")
                            OptionButtonGrid(
                                options = ResultsBulkDeleteTextTarget.entries,
                                selected = config.value.target,
                                label = { it.label },
                                onSelect = { target ->
                                    updateConfig(config.value.copy(target = target))
                                }
                            )
                            Text("Operator")
                            OptionButtonGrid(
                                options = ResultsFilterTextOperator.entries,
                                selected = config.value.operator,
                                label = { it.label },
                                onSelect = { operator ->
                                    updateConfig(config.value.copy(operator = operator))
                                }
                            )
                            OutlinedTextField(
                                value = config.value.phrase,
                                onValueChange = { phrase ->
                                    updateConfig(config.value.copy(phrase = phrase))
                                },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Text") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    capitalization = KeyboardCapitalization.None
                                )
                            )
                        }
                    }
                }
                if (!operations.snapshotAvailable || totalGroupCount <= 0) {
                    item {
                        Text(
                            text = "No result-group snapshot is available yet.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                message.value = null
                                preview.value = null
                                isPreviewLoading.value = true
                                progress.value = ResultsBulkDeletePreviewProgress(
                                    totalGroupCount = totalGroupCount.coerceAtLeast(0)
                                )
                                scope.launch {
                                    try {
                                        val builtPreview = operations.buildKeepOnePreview(
                                            filterDefinition = appliedFilter,
                                            config = config.value,
                                            onProgress = { updated ->
                                                progress.value = updated
                                            }
                                        )
                                        preview.value = builtPreview
                                        message.value = builtPreview.readyMessage()
                                    } catch (_: Exception) {
                                        message.value = "Failed to build the bulk delete preview."
                                    } finally {
                                        isPreviewLoading.value = false
                                    }
                                }
                            },
                            enabled = canBuildPreview
                        ) {
                            Text(if (isPreviewLoading.value) "Building preview..." else "Build preview")
                        }
                        OutlinedButton(onClick = onBack, enabled = !isExecuting.value) {
                            Text("Back")
                        }
                    }
                }
                if (isPreviewLoading.value) {
                    item {
                        Card {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text("Scanning", style = MaterialTheme.typography.titleMedium)
                                progress.value.progressLines().forEach { line ->
                                    Text(
                                        text = line,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                    }
                }
                currentPreview?.let { builtPreview ->
                    item {
                        Card {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text("Preview summary", style = MaterialTheme.typography.titleMedium)
                                builtPreview.progressSummaryLines().forEach { line ->
                                    Text(
                                        text = line,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                    }
                }
                message.value?.let { currentMessage ->
                    item {
                        Text(
                            text = currentMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                currentPreview?.let { builtPreview ->
                    if (builtPreview.candidates.isNotEmpty()) {
                        item {
                            Text("Preview list", style = MaterialTheme.typography.titleMedium)
                        }
                        items(
                            items = builtPreview.candidates,
                            key = { candidate -> "${candidate.group.sizeBytes}:${candidate.group.hashHex}" }
                        ) { candidate ->
                            ResultsBulkDeleteCandidateCard(
                                candidate = candidate,
                                imageLoader = imageLoader,
                                keepLoadedThumbnailsInMemory = keepLoadedThumbnailsInMemory,
                                thumbnailSizeScale = thumbnailSizeScale,
                                rememberedPreviewCache = rememberedPreviewCache
                            )
                        }
                        item {
                            Button(
                                onClick = { confirmExecute.value = true },
                                enabled = canExecute
                            ) {
                                Text(if (isExecuting.value) "Deleting..." else "Delete matching files")
                            }
                            if (builtPreview.hasCappedCandidates()) {
                                Text(
                                    text = "Only the first ${BULK_DELETE_PREVIEW_SAMPLE_LIMIT} candidate groups are shown. Execution will rescan and delete all matching files.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                if (onDeleteFile == null) {
                    item {
                        Text(
                            text = "Delete action is unavailable in this session.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
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

    if (confirmExecute.value && currentPreview != null) {
        AlertDialog(
            onDismissRequest = {
                if (!isExecuting.value) {
                    confirmExecute.value = false
                }
            },
            title = { Text("Run bulk delete?") },
            text = {
                Text(
                    "${currentPreview.candidateGroupCount} groups and $previewDeleteCount matching files will be deleted. The list shows only a preview sample."
                )
            },
            confirmButton = {
                OutlinedButton(
                    onClick = {
                        val handler = onDeleteFile ?: return@OutlinedButton
                        isExecuting.value = true
                        message.value = null
                        startBulkDeleteTask(
                            preview = currentPreview,
                            scope = taskScope,
                            taskCoordinator = taskCoordinator,
                            notificationController = notificationController,
                            executeDelete = { executionProgress ->
                                operations.executeKeepOne(
                                    preview = currentPreview,
                                    filterDefinition = appliedFilter,
                                    config = config.value,
                                    onDeleteFile = handler,
                                    onProgress = executionProgress
                                )
                            },
                            onSnapshotChanged = {
                                operations.hasSnapshotChanged(currentPreview)
                            },
                            onRefreshGroups = { touchedGroups ->
                                operations.refreshGroups(touchedGroups)
                            },
                            onSuccess = { outcome ->
                                preview.value = null
                                progress.value = ResultsBulkDeletePreviewProgress(
                                    totalGroupCount = totalGroupCount.coerceAtLeast(0)
                                )
                                message.value = outcome.message()
                                onResultsChanged(outcome)
                            },
                            onSnapshotStale = {
                                preview.value = null
                                message.value = "The results snapshot changed. Build the preview again."
                            },
                            onFailure = {
                                message.value = "Bulk delete failed."
                            },
                            onFinished = {
                                isExecuting.value = false
                                confirmExecute.value = false
                            }
                        )
                    },
                    enabled = !isExecuting.value
                ) {
                    Text(if (isExecuting.value) "Deleting..." else "Delete")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { confirmExecute.value = false },
                    enabled = !isExecuting.value
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    BackHandler(onBack = onBack)
}

@Composable
internal fun KeepByModifiedBulkDeleteScreen(
    operations: BulkDeleteOperations,
    appliedFilter: ResultsFilterDefinition,
    imageLoader: ImageLoader,
    keepLoadedThumbnailsInMemory: Boolean,
    thumbnailSizeScale: Float,
    rememberedPreviewCache: MutableMap<String, ImageBitmap>,
    taskScope: CoroutineScope,
    taskCoordinator: TaskCoordinator,
    notificationController: TaskNotificationController,
    onDeleteFile: (suspend (FileMetadata) -> Boolean)?,
    onBack: () -> Unit,
    onResultsChanged: (ResultsBulkDeleteExecutionOutcome) -> Unit
) {
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val totalGroupCount = operations.totalGroupCount
    val keepMode = remember { mutableStateOf(ResultsBulkDeleteModifiedKeepMode.Oldest) }
    val preview = remember { mutableStateOf<ResultsBulkDeletePreview?>(null) }
    val progress = remember {
        mutableStateOf(
            ResultsBulkDeletePreviewProgress(totalGroupCount = totalGroupCount.coerceAtLeast(0))
        )
    }
    val isPreviewLoading = remember { mutableStateOf(false) }
    val isExecuting = remember { mutableStateOf(false) }
    val confirmExecute = remember { mutableStateOf(false) }
    val message = remember { mutableStateOf<String?>(null) }

    fun updateKeepMode(updated: ResultsBulkDeleteModifiedKeepMode) {
        keepMode.value = updated
        preview.value = null
        progress.value = ResultsBulkDeletePreviewProgress(totalGroupCount = totalGroupCount.coerceAtLeast(0))
        message.value = null
    }

    val currentPreview = preview.value
    val previewDeleteCount = currentPreview?.deleteTargetCount() ?: 0
    val canBuildPreview = !isPreviewLoading.value &&
        !isExecuting.value &&
        operations.snapshotAvailable &&
        totalGroupCount > 0
    val canExecute = !isPreviewLoading.value &&
        !isExecuting.value &&
        onDeleteFile != null &&
        currentPreview != null &&
        currentPreview.candidateFileCount > 0

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .testTag("bulk-delete-keep-modified-list")
                    .padding(Spacing.screenPadding),
                contentPadding = PaddingValues(
                    end = ScrollbarDefaults.ThumbWidth + 8.dp,
                    bottom = 24.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    AppTopBar(title = "Keep by modified time", onBack = onBack)
                }
                item {
                    Text(
                        text = "Scan the current results snapshot, keep either the oldest file or the newest file in each eligible result group, and delete the rest.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                if (appliedFilter.hasActiveRules()) {
                    item {
                        Card {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text("Current filter applies", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = summarizeResultsFilter(appliedFilter),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = "Groups outside the active result filter are excluded from preview and execution.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                item {
                    Card {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("Command rule", style = MaterialTheme.typography.titleMedium)
                            Text("Keep mode")
                            OptionButtonGrid(
                                options = ResultsBulkDeleteModifiedKeepMode.entries,
                                selected = keepMode.value,
                                label = { it.label },
                                onSelect = { mode ->
                                    updateKeepMode(mode)
                                }
                            )
                            Text(
                                text = if (keepMode.value == ResultsBulkDeleteModifiedKeepMode.Newest) {
                                    "The newest modified file survives in each eligible group. If modified times tie, normalized path order breaks the tie."
                                } else {
                                    "The oldest modified file survives in each eligible group. If modified times tie, normalized path order breaks the tie."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                if (!operations.snapshotAvailable || totalGroupCount <= 0) {
                    item {
                        Text(
                            text = "No result-group snapshot is available yet.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                message.value = null
                                preview.value = null
                                isPreviewLoading.value = true
                                progress.value = ResultsBulkDeletePreviewProgress(
                                    totalGroupCount = totalGroupCount.coerceAtLeast(0)
                                )
                                scope.launch {
                                    try {
                                        val builtPreview = operations.buildKeepModifiedPreview(
                                            filterDefinition = appliedFilter,
                                            keepNewest = keepMode.value == ResultsBulkDeleteModifiedKeepMode.Newest,
                                            onProgress = { updated ->
                                                progress.value = updated
                                            }
                                        )
                                        preview.value = builtPreview
                                        message.value = builtPreview.readyMessage()
                                    } catch (_: Exception) {
                                        message.value = "Failed to build the bulk delete preview."
                                    } finally {
                                        isPreviewLoading.value = false
                                    }
                                }
                            },
                            enabled = canBuildPreview
                        ) {
                            Text(if (isPreviewLoading.value) "Building preview..." else "Build preview")
                        }
                        OutlinedButton(onClick = onBack, enabled = !isExecuting.value) {
                            Text("Back")
                        }
                    }
                }
                if (isPreviewLoading.value) {
                    item {
                        Card {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text("Scanning", style = MaterialTheme.typography.titleMedium)
                                progress.value.progressLines().forEach { line ->
                                    Text(
                                        text = line,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                    }
                }
                currentPreview?.let { builtPreview ->
                    item {
                        Card {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text("Preview summary", style = MaterialTheme.typography.titleMedium)
                                builtPreview.progressSummaryLines().forEach { line ->
                                    Text(
                                        text = line,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                    }
                }
                message.value?.let { currentMessage ->
                    item {
                        Text(
                            text = currentMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                currentPreview?.let { builtPreview ->
                    if (builtPreview.candidates.isNotEmpty()) {
                        item {
                            Text("Preview list", style = MaterialTheme.typography.titleMedium)
                        }
                        items(
                            items = builtPreview.candidates,
                            key = { candidate -> "${candidate.group.sizeBytes}:${candidate.group.hashHex}" }
                        ) { candidate ->
                            ResultsBulkDeleteCandidateCard(
                                candidate = candidate,
                                imageLoader = imageLoader,
                                keepLoadedThumbnailsInMemory = keepLoadedThumbnailsInMemory,
                                thumbnailSizeScale = thumbnailSizeScale,
                                rememberedPreviewCache = rememberedPreviewCache
                            )
                        }
                        item {
                            Button(
                                onClick = { confirmExecute.value = true },
                                enabled = canExecute
                            ) {
                                Text(if (isExecuting.value) "Deleting..." else "Delete matching files")
                            }
                            if (builtPreview.hasCappedCandidates()) {
                                Text(
                                    text = "Only the first ${BULK_DELETE_PREVIEW_SAMPLE_LIMIT} candidate groups are shown. Execution will rescan and delete all matching files.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                if (onDeleteFile == null) {
                    item {
                        Text(
                            text = "Delete action is unavailable in this session.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
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

    if (confirmExecute.value && currentPreview != null) {
        AlertDialog(
            onDismissRequest = {
                if (!isExecuting.value) {
                    confirmExecute.value = false
                }
            },
            title = { Text("Run bulk delete?") },
            text = {
                Text(
                    "${currentPreview.candidateGroupCount} groups and $previewDeleteCount matching files will be deleted. The list shows only a preview sample."
                )
            },
            confirmButton = {
                OutlinedButton(
                    onClick = {
                        val handler = onDeleteFile ?: return@OutlinedButton
                        isExecuting.value = true
                        message.value = null
                        startBulkDeleteTask(
                            preview = currentPreview,
                            scope = taskScope,
                            taskCoordinator = taskCoordinator,
                            notificationController = notificationController,
                            executeDelete = { executionProgress ->
                                operations.executeKeepModified(
                                    preview = currentPreview,
                                    filterDefinition = appliedFilter,
                                    keepNewest = keepMode.value == ResultsBulkDeleteModifiedKeepMode.Newest,
                                    onDeleteFile = handler,
                                    onProgress = executionProgress
                                )
                            },
                            onSnapshotChanged = {
                                operations.hasSnapshotChanged(currentPreview)
                            },
                            onRefreshGroups = { touchedGroups ->
                                operations.refreshGroups(touchedGroups)
                            },
                            onSuccess = { outcome ->
                                preview.value = null
                                progress.value = ResultsBulkDeletePreviewProgress(
                                    totalGroupCount = totalGroupCount.coerceAtLeast(0)
                                )
                                message.value = outcome.message()
                                onResultsChanged(outcome)
                            },
                            onSnapshotStale = {
                                preview.value = null
                                message.value = "The results snapshot changed. Build the preview again."
                            },
                            onFailure = {
                                message.value = "Bulk delete failed."
                            },
                            onFinished = {
                                isExecuting.value = false
                                confirmExecute.value = false
                            }
                        )
                    },
                    enabled = !isExecuting.value
                ) {
                    Text(if (isExecuting.value) "Deleting..." else "Delete")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { confirmExecute.value = false },
                    enabled = !isExecuting.value
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    BackHandler(onBack = onBack)
}

@Composable
private fun ResultsBulkDeleteCandidateCard(
    candidate: ResultsBulkDeleteCandidate,
    imageLoader: ImageLoader,
    keepLoadedThumbnailsInMemory: Boolean,
    thumbnailSizeScale: Float,
    rememberedPreviewCache: MutableMap<String, ImageBitmap>
) {
    val normalizedThumbnailScale = thumbnailSizeScale.coerceAtLeast(0f)
    val thumbnailSizeDp = 72.dp * normalizedThumbnailScale
    val previewFiles = remember(candidate.group.sizeBytes, candidate.group.hashHex) {
        listOf(candidate.survivor) + candidate.deleteTargets
    }
    val previewCandidates = remember(previewFiles) {
        mediaPreviewCandidates(
            files = previewFiles,
            deletedPaths = emptySet()
        )
    }
    val previewMemoryKey = remember(candidate.group.sizeBytes, candidate.group.hashHex) {
        "bulk:${candidate.group.sizeBytes}:${candidate.group.hashHex}"
    }

    Card {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (previewCandidates.isNotEmpty()) {
                GroupPreviewThumbnail(
                    candidatePaths = previewCandidates,
                    previewMemoryKey = previewMemoryKey,
                    rememberedPreviewCache = rememberedPreviewCache,
                    imageLoader = imageLoader,
                    keepLoadedInMemory = keepLoadedThumbnailsInMemory,
                    contentDescription = "Bulk delete preview thumbnail",
                    modifier = Modifier
                        .height(thumbnailSizeDp)
                        .width(thumbnailSizeDp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "${candidate.group.fileCount} files · Total ${formatBytes(candidate.group.totalBytes)}",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "Keep: ${candidate.survivor.normalizedPath}",
                    style = MaterialTheme.typography.bodyMedium
                )
                candidate.deleteTargets.forEach { target ->
                    Text(
                        text = "Delete: ${target.normalizedPath}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

private fun matchesKeepOneNonMatchCommand(
    file: FileMetadata,
    config: KeepOneNonMatchBulkDeleteCommandConfig
): Boolean {
    val source = when (config.target) {
        ResultsBulkDeleteTextTarget.FileName -> fileNameFromPath(file.normalizedPath)
        ResultsBulkDeleteTextTarget.FullPath -> file.normalizedPath
    }
    return matchesTextOperator(
        source = source,
        expected = config.phrase,
        operator = config.operator
    )
}
