package opensource.cached_dupe_scanner.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import opensource.cached_dupe_scanner.cache.DuplicateGroupEntity
import opensource.cached_dupe_scanner.core.FileMetadata
import opensource.cached_dupe_scanner.storage.DuplicateGroupSortKey
import opensource.cached_dupe_scanner.storage.ResultsDbRepository
import opensource.cached_dupe_scanner.ui.components.AppTopBar
import opensource.cached_dupe_scanner.ui.components.ScrollbarDefaults
import opensource.cached_dupe_scanner.ui.components.Spacing
import opensource.cached_dupe_scanner.ui.components.VerticalLazyScrollbar

internal enum class ResultsBulkDeleteCommandType(
    val title: String,
    val description: String
) {
    KeepOneNonMatch(
        title = "Delete matches, keep 1 non-match",
        description = "Find duplicate groups where exactly one file does not match the text rule, then delete the matching files."
    )
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

internal data class ResultsBulkDeleteCandidate(
    val group: DuplicateGroupEntity,
    val survivor: FileMetadata,
    val deleteTargets: List<FileMetadata>
)

internal data class ResultsBulkDeletePreview(
    val snapshotUpdatedAtMillis: Long,
    val totalGroupCount: Int,
    val filterMatchedGroupCount: Int,
    val candidates: List<ResultsBulkDeleteCandidate>
)

internal data class ResultsBulkDeletePreviewProgress(
    val scannedGroupCount: Int = 0,
    val totalGroupCount: Int = 0,
    val filterMatchedGroupCount: Int = 0,
    val candidateGroupCount: Int = 0,
    val candidateFileCount: Int = 0
)

internal data class ResultsBulkDeleteExecutionOutcome(
    val successCount: Int,
    val failedPaths: Set<String>
)

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
    return groupsWithMembers.mapNotNull { (group, members) ->
        if (!matchesResultsFilter(filterDefinition, group, members)) {
            null
        } else {
            buildKeepOneNonMatchBulkDeleteCandidate(
                group = group,
                members = members,
                config = config
            )
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
    val candidates = mutableListOf<ResultsBulkDeleteCandidate>()
    var sourceOffset = 0
    var filterMatchedGroupCount = 0
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
            val members = withContext(Dispatchers.IO) {
                resultsRepo.listAllGroupMembers(
                    sizeBytes = group.sizeBytes,
                    hashHex = group.hashHex
                )
            }
            if (matchesResultsFilter(filterDefinition, group, members)) {
                filterMatchedGroupCount += 1
                buildKeepOneNonMatchBulkDeleteCandidate(
                    group = group,
                    members = members,
                    config = config
                )?.let { candidates += it }
            }
        }

        sourceOffset += page.size
        onProgress(
            ResultsBulkDeletePreviewProgress(
                scannedGroupCount = sourceOffset.coerceAtMost(safeTotalGroupCount),
                totalGroupCount = safeTotalGroupCount,
                filterMatchedGroupCount = filterMatchedGroupCount,
                candidateGroupCount = candidates.size,
                candidateFileCount = candidates.sumOf { it.deleteTargets.size }
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
        candidates = candidates
    )
}

internal suspend fun executeBulkDeletePreview(
    preview: ResultsBulkDeletePreview,
    onDeleteFile: suspend (FileMetadata) -> Boolean
): ResultsBulkDeleteExecutionOutcome {
    var successCount = 0
    val failedPaths = linkedSetOf<String>()
    preview.candidates.forEach { candidate ->
        candidate.deleteTargets.forEach { file ->
            val deleted = runCatching { onDeleteFile(file) }.getOrDefault(false)
            if (deleted) {
                successCount += 1
            } else {
                failedPaths += file.normalizedPath
            }
        }
    }
    return ResultsBulkDeleteExecutionOutcome(
        successCount = successCount,
        failedPaths = failedPaths
    )
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
                modifier = Modifier.padding(Spacing.screenPadding),
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
    resultsRepo: ResultsDbRepository,
    sortKey: DuplicateGroupSortKey,
    snapshotUpdatedAtMillis: Long?,
    totalGroupCount: Int,
    appliedFilter: ResultsFilterDefinition,
    onDeleteFile: (suspend (FileMetadata) -> Boolean)?,
    onBack: () -> Unit,
    onResultsChanged: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
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
    val previewDeleteCount = currentPreview?.candidates?.sumOf { it.deleteTargets.size } ?: 0
    val canBuildPreview = !isPreviewLoading.value &&
        !isExecuting.value &&
        config.value.phrase.trim().isNotEmpty() &&
        snapshotUpdatedAtMillis != null &&
        totalGroupCount > 0
    val canExecute = !isPreviewLoading.value &&
        !isExecuting.value &&
        onDeleteFile != null &&
        currentPreview != null &&
        currentPreview.candidates.isNotEmpty()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box {
            LazyColumn(
                state = listState,
                modifier = Modifier.padding(Spacing.screenPadding),
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
                        text = "Scan the current DB snapshot, keep the one file that does not match your rule, and delete the matching files from eligible duplicate groups.",
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
                            BulkDeleteOptionButtons(
                                options = ResultsBulkDeleteTextTarget.entries,
                                selected = config.value.target,
                                label = { it.label },
                                onSelect = { target ->
                                    updateConfig(config.value.copy(target = target))
                                }
                            )
                            Text("Operator")
                            BulkDeleteOptionButtons(
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
                if (snapshotUpdatedAtMillis == null || totalGroupCount <= 0) {
                    item {
                        Text(
                            text = "No duplicate-group snapshot is available yet.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                val snapshot = snapshotUpdatedAtMillis ?: return@Button
                                message.value = null
                                preview.value = null
                                isPreviewLoading.value = true
                                progress.value = ResultsBulkDeletePreviewProgress(
                                    totalGroupCount = totalGroupCount.coerceAtLeast(0)
                                )
                                scope.launch {
                                    try {
                                        val builtPreview = buildKeepOneNonMatchBulkDeletePreview(
                                            resultsRepo = resultsRepo,
                                            sortKey = sortKey,
                                            snapshotUpdatedAtMillis = snapshot,
                                            totalGroupCount = totalGroupCount,
                                            filterDefinition = appliedFilter,
                                            config = config.value,
                                            onProgress = { updated ->
                                                progress.value = updated
                                            }
                                        )
                                        preview.value = builtPreview
                                        message.value = if (builtPreview.candidates.isEmpty()) {
                                            "No groups matched this command."
                                        } else {
                                            "${builtPreview.candidates.size} groups and ${builtPreview.candidates.sumOf { it.deleteTargets.size }} files are ready."
                                        }
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
                                Text(
                                    text = "${progress.value.scannedGroupCount}/${progress.value.totalGroupCount} groups loaded before filtering",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = "${progress.value.filterMatchedGroupCount} groups passed the current filter",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = "${progress.value.candidateGroupCount} candidate groups · ${progress.value.candidateFileCount} files to delete",
                                    style = MaterialTheme.typography.bodyMedium
                                )
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
                                Text(
                                    text = "${builtPreview.totalGroupCount}/${builtPreview.totalGroupCount} groups loaded before filtering",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = "${builtPreview.filterMatchedGroupCount} groups passed the current filter",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = "${builtPreview.candidates.size} candidate groups · $previewDeleteCount files to delete",
                                    style = MaterialTheme.typography.bodyMedium
                                )
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
                            ResultsBulkDeleteCandidateCard(candidate = candidate)
                        }
                        item {
                            Button(
                                onClick = { confirmExecute.value = true },
                                enabled = canExecute
                            ) {
                                Text(if (isExecuting.value) "Deleting..." else "Delete listed files")
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
                    "${currentPreview.candidates.size} groups and $previewDeleteCount files from the preview will be deleted."
                )
            },
            confirmButton = {
                OutlinedButton(
                    onClick = {
                        val handler = onDeleteFile ?: return@OutlinedButton
                        isExecuting.value = true
                        message.value = null
                        scope.launch {
                            try {
                                val snapshotChanged = withContext(Dispatchers.IO) {
                                    resultsRepo.hasSnapshotChanged(currentPreview.snapshotUpdatedAtMillis)
                                }
                                if (snapshotChanged) {
                                    preview.value = null
                                    message.value = "The results snapshot changed. Build the preview again."
                                    return@launch
                                }

                                val outcome = executeBulkDeletePreview(
                                    preview = currentPreview,
                                    onDeleteFile = handler
                                )
                                withContext(Dispatchers.IO) {
                                    currentPreview.candidates.forEach { candidate ->
                                        resultsRepo.refreshSingleGroup(
                                            sizeBytes = candidate.group.sizeBytes,
                                            hashHex = candidate.group.hashHex
                                        )
                                    }
                                }
                                preview.value = null
                                progress.value = ResultsBulkDeletePreviewProgress(
                                    totalGroupCount = totalGroupCount.coerceAtLeast(0)
                                )
                                message.value = when {
                                    outcome.successCount == 0 && outcome.failedPaths.isEmpty() -> "No files were deleted."
                                    outcome.failedPaths.isEmpty() -> "${outcome.successCount} files deleted."
                                    outcome.successCount == 0 -> "Delete failed for ${outcome.failedPaths.size} files."
                                    else -> "${outcome.successCount} files deleted, ${outcome.failedPaths.size} failed."
                                }
                                onResultsChanged()
                            } catch (_: Exception) {
                                message.value = "Bulk delete failed."
                            } finally {
                                isExecuting.value = false
                                confirmExecute.value = false
                            }
                        }
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
private fun ResultsBulkDeleteCandidateCard(candidate: ResultsBulkDeleteCandidate) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
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

@Composable
private fun <T> BulkDeleteOptionButtons(
    options: Iterable<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.chunked(2).forEach { rowOptions ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowOptions.forEach { option ->
                    val isSelected = option == selected
                    if (isSelected) {
                        Button(onClick = { onSelect(option) }) {
                            Text(label(option))
                        }
                    } else {
                        OutlinedButton(onClick = { onSelect(option) }) {
                            Text(label(option))
                        }
                    }
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
