package opensource.cached_dupe_scanner.ui.home

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import opensource.cached_dupe_scanner.cache.DuplicateGroupEntity
import opensource.cached_dupe_scanner.core.FileMetadata
import opensource.cached_dupe_scanner.notifications.TaskNotificationController
import opensource.cached_dupe_scanner.tasks.TaskArea
import opensource.cached_dupe_scanner.tasks.TaskCoordinator
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ResultsScreenDbBulkDeleteTest {
    private val taskScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @After
    fun tearDown() {
        taskScope.cancel()
    }

    @Test
    fun bulkDeletePreviewReadyMessageReportsEmptyAndReadyStates() {
        val emptyPreview = ResultsBulkDeletePreview(
            snapshotUpdatedAtMillis = 1L,
            totalGroupCount = 2,
            filterMatchedGroupCount = 0,
            candidates = emptyList()
        )
        val readyPreview = ResultsBulkDeletePreview(
            snapshotUpdatedAtMillis = 1L,
            totalGroupCount = 2,
            filterMatchedGroupCount = 2,
            candidates = listOf(
                ResultsBulkDeleteCandidate(
                    group = group(size = 10L, hash = "a", count = 3),
                    survivor = file("/keep/original.mkv"),
                    deleteTargets = listOf(
                        file("/delete/first.mkv"),
                        file("/delete/second.mkv")
                    )
                )
            )
        )

        assertEquals("No groups matched this command.", emptyPreview.readyMessage())
        assertEquals("1 groups and 2 files are ready.", readyPreview.readyMessage())
    }

    @Test
    fun bulkDeletePreviewProgressLinesDescribeLoadedFilterAndCandidateCounts() {
        val progress = ResultsBulkDeletePreviewProgress(
            scannedGroupCount = 3,
            totalGroupCount = 5,
            filterMatchedGroupCount = 2,
            candidateGroupCount = 1,
            candidateFileCount = 4
        )

        assertEquals(
            listOf(
                "3/5 groups loaded before filtering",
                "2 groups passed the current filter",
                "1 candidate groups · 4 files to delete"
            ),
            progress.progressLines()
        )
    }

    @Test
    fun bulkDeletePreviewSummaryLinesUsePreviewTotals() {
        val preview = ResultsBulkDeletePreview(
            snapshotUpdatedAtMillis = 1L,
            totalGroupCount = 3,
            filterMatchedGroupCount = 2,
            candidates = listOf(
                ResultsBulkDeleteCandidate(
                    group = group(size = 10L, hash = "a", count = 3),
                    survivor = file("/keep/original.mkv"),
                    deleteTargets = listOf(
                        file("/delete/first.mkv"),
                        file("/delete/second.mkv")
                    )
                )
            )
        )

        assertEquals(
            listOf(
                "3/3 groups loaded before filtering",
                "2 groups passed the current filter",
                "1 candidate groups · 2 files to delete"
            ),
            preview.progressSummaryLines()
        )
    }

    @Test
    fun bulkDeleteExecutionOutcomeMessageReportsSuccessAndFailureCounts() {
        assertEquals(
            "No files were deleted.",
            ResultsBulkDeleteExecutionOutcome(
                successCount = 0,
                failedPaths = emptySet()
            ).message()
        )
        assertEquals(
            "2 files deleted.",
            ResultsBulkDeleteExecutionOutcome(
                successCount = 2,
                failedPaths = emptySet()
            ).message()
        )
        assertEquals(
            "Delete failed for 2 files.",
            ResultsBulkDeleteExecutionOutcome(
                successCount = 0,
                failedPaths = setOf("/first", "/second")
            ).message()
        )
        assertEquals(
            "2 files deleted, 1 failed.",
            ResultsBulkDeleteExecutionOutcome(
                successCount = 2,
                failedPaths = setOf("/failed")
            ).message()
        )
    }

    @Test
    fun buildKeepOneNonMatchBulkDeleteCandidateDeletesMatchingFileNames() {
        val candidate = buildKeepOneNonMatchBulkDeleteCandidate(
            group = group(size = 10L, hash = "a", count = 3),
            members = listOf(
                file("/library/keep/final-cut.mkv"),
                file("/library/tmp/sample-1.mkv"),
                file("/library/tmp/sample-2.mkv")
            ),
            config = KeepOneNonMatchBulkDeleteCommandConfig(
                target = ResultsBulkDeleteTextTarget.FileName,
                operator = ResultsFilterTextOperator.Contains,
                phrase = "sample"
            )
        )

        requireNotNull(candidate)
        assertEquals("/library/keep/final-cut.mkv", candidate.survivor.normalizedPath)
        assertEquals(
            listOf("/library/tmp/sample-1.mkv", "/library/tmp/sample-2.mkv"),
            candidate.deleteTargets.map { it.normalizedPath }
        )
    }

    @Test
    fun buildKeepOneNonMatchBulkDeleteCandidateReturnsNullWhenTwoSurvivorsRemain() {
        val candidate = buildKeepOneNonMatchBulkDeleteCandidate(
            group = group(size = 10L, hash = "a", count = 3),
            members = listOf(
                file("/library/keep/final-cut.mkv"),
                file("/library/keep/backup.mkv"),
                file("/library/tmp/sample-1.mkv")
            ),
            config = KeepOneNonMatchBulkDeleteCommandConfig(
                target = ResultsBulkDeleteTextTarget.FileName,
                operator = ResultsFilterTextOperator.Contains,
                phrase = "sample"
            )
        )

        assertNull(candidate)
    }

    @Test
    fun buildKeepOneNonMatchBulkDeleteCandidateMatchesFullPathStartsWith() {
        val candidate = buildKeepOneNonMatchBulkDeleteCandidate(
            group = group(size = 10L, hash = "a", count = 3),
            members = listOf(
                file("/keep/final-cut.mkv"),
                file("/trash/sample-1.mkv"),
                file("/trash/sample-2.mkv")
            ),
            config = KeepOneNonMatchBulkDeleteCommandConfig(
                target = ResultsBulkDeleteTextTarget.FullPath,
                operator = ResultsFilterTextOperator.StartsWith,
                phrase = "/trash/"
            )
        )

        requireNotNull(candidate)
        assertEquals("/keep/final-cut.mkv", candidate.survivor.normalizedPath)
        assertEquals(
            listOf("/trash/sample-1.mkv", "/trash/sample-2.mkv"),
            candidate.deleteTargets.map { it.normalizedPath }
        )
    }

    @Test
    fun buildKeepModifiedBulkDeleteCandidateKeepsOldestFile() {
        val candidate = buildKeepModifiedBulkDeleteCandidate(
            group = group(size = 10L, hash = "a", count = 3),
            members = listOf(
                file("/keep/oldest.mkv", modified = 10L),
                file("/keep/middle.mkv", modified = 20L),
                file("/keep/newest.mkv", modified = 30L)
            ),
            keepNewest = false
        )

        requireNotNull(candidate)
        assertEquals("/keep/oldest.mkv", candidate.survivor.normalizedPath)
        assertEquals(
            listOf("/keep/middle.mkv", "/keep/newest.mkv"),
            candidate.deleteTargets.map { it.normalizedPath }
        )
    }

    @Test
    fun buildKeepModifiedBulkDeleteCandidateKeepsNewestFile() {
        val candidate = buildKeepModifiedBulkDeleteCandidate(
            group = group(size = 10L, hash = "a", count = 3),
            members = listOf(
                file("/keep/oldest.mkv", modified = 10L),
                file("/keep/middle.mkv", modified = 20L),
                file("/keep/newest.mkv", modified = 30L)
            ),
            keepNewest = true
        )

        requireNotNull(candidate)
        assertEquals("/keep/newest.mkv", candidate.survivor.normalizedPath)
        assertEquals(
            listOf("/keep/oldest.mkv", "/keep/middle.mkv"),
            candidate.deleteTargets.map { it.normalizedPath }
        )
    }

    @Test
    fun collectKeepOneNonMatchBulkDeleteCandidatesHonorsCurrentResultsFilter() {
        val candidates = collectKeepOneNonMatchBulkDeleteCandidates(
            groupsWithMembers = listOf(
                group(size = 10L, hash = "a", count = 3) to listOf(
                    file("/show/episode-final.mkv"),
                    file("/show/sample-1.mkv"),
                    file("/show/sample-2.mkv")
                ),
                group(size = 10L, hash = "b", count = 3) to listOf(
                    file("/movie/final.mkv"),
                    file("/movie/sample-1.mkv"),
                    file("/movie/sample-2.mkv")
                )
            ),
            filterDefinition = ResultsFilterDefinition(
                clusters = listOf(
                    ResultsFilterCluster(
                        id = "cluster_1",
                        name = "Episode only",
                        rules = listOf(
                            ResultsFilterRule(
                                id = "rule_1",
                                target = ResultsFilterTarget.FileName,
                                textOperator = ResultsFilterTextOperator.Contains,
                                value = "episode"
                            )
                        )
                    )
                )
            ),
            config = KeepOneNonMatchBulkDeleteCommandConfig(
                target = ResultsBulkDeleteTextTarget.FileName,
                operator = ResultsFilterTextOperator.Contains,
                phrase = "sample"
            )
        )

        assertEquals(1, candidates.size)
        assertEquals("10:a", "${candidates.single().group.sizeBytes}:${candidates.single().group.hashHex}")
    }

    @Test
    fun collectKeepModifiedBulkDeleteCandidatesHonorsCurrentResultsFilter() {
        val candidates = collectKeepModifiedBulkDeleteCandidates(
            groupsWithMembers = listOf(
                group(size = 10L, hash = "a", count = 3) to listOf(
                    file("/show/episode-old.mkv", modified = 10L),
                    file("/show/episode-mid.mkv", modified = 20L),
                    file("/show/episode-new.mkv", modified = 30L)
                ),
                group(size = 10L, hash = "b", count = 3) to listOf(
                    file("/movie/old.mkv", modified = 10L),
                    file("/movie/mid.mkv", modified = 20L),
                    file("/movie/new.mkv", modified = 30L)
                )
            ),
            filterDefinition = ResultsFilterDefinition(
                clusters = listOf(
                    ResultsFilterCluster(
                        id = "cluster_1",
                        name = "Episode only",
                        rules = listOf(
                            ResultsFilterRule(
                                id = "rule_1",
                                target = ResultsFilterTarget.FileName,
                                textOperator = ResultsFilterTextOperator.Contains,
                                value = "episode"
                            )
                        )
                    )
                )
            ),
            keepNewest = true
        )

        assertEquals(1, candidates.size)
        assertEquals("/show/episode-new.mkv", candidates.single().survivor.normalizedPath)
    }

    @Test
    fun executeBulkDeletePreviewReportsProgressForEachDeleteTarget() = runBlocking {
        val preview = ResultsBulkDeletePreview(
            snapshotUpdatedAtMillis = 1L,
            totalGroupCount = 1,
            filterMatchedGroupCount = 1,
            candidates = listOf(
                ResultsBulkDeleteCandidate(
                    group = group(size = 10L, hash = "a", count = 3),
                    survivor = file("/keep/original.mkv"),
                    deleteTargets = listOf(
                        file("/delete/first.mkv"),
                        file("/delete/second.mkv")
                    )
                )
            )
        )
        val progressEvents = mutableListOf<ResultsBulkDeleteExecutionProgress>()

        val outcome = executeBulkDeletePreview(
            preview = preview,
            onDeleteFile = { file -> file.normalizedPath.endsWith("first.mkv") },
            onProgress = { progressEvents += it }
        )

        assertEquals(1, outcome.successCount)
        assertEquals(setOf("/delete/second.mkv"), outcome.failedPaths)
        assertEquals(
            listOf(
                ResultsBulkDeleteExecutionProgress(total = 2),
                ResultsBulkDeleteExecutionProgress(
                    processed = 1,
                    total = 2,
                    failed = 0,
                    currentPath = "/delete/first.mkv"
                ),
                ResultsBulkDeleteExecutionProgress(
                    processed = 2,
                    total = 2,
                    failed = 1,
                    currentPath = "/delete/second.mkv"
                )
            ),
            progressEvents
        )
    }

    @Test
    fun startBulkDeleteTaskUsesDeleteCallbackWhileTrashAreaIsBusy() {
        val coordinator = TaskCoordinator()
        val notificationController = TaskNotificationController(RuntimeEnvironment.getApplication())
        val deleteSawBusyTrash = AtomicBoolean(false)
        val finished = CountDownLatch(1)
        val preview = ResultsBulkDeletePreview(
            snapshotUpdatedAtMillis = 1L,
            totalGroupCount = 1,
            filterMatchedGroupCount = 1,
            candidates = listOf(
                ResultsBulkDeleteCandidate(
                    group = group(size = 10L, hash = "a", count = 3),
                    survivor = file("/keep/original.mkv"),
                    deleteTargets = listOf(file("/delete/first.mkv"))
                )
            )
        )

        startBulkDeleteTask(
            preview = preview,
            scope = taskScope,
            taskCoordinator = coordinator,
            notificationController = notificationController,
            onDeleteFile = {
                deleteSawBusyTrash.set(coordinator.isAreaBusy(TaskArea.Trash))
                true
            },
            onSnapshotChanged = { false },
            onRefreshGroups = {},
            onSuccess = { outcome -> assertEquals(1, outcome.successCount) },
            onSnapshotStale = {},
            onFailure = { error("Bulk delete should not fail") },
            onFinished = { finished.countDown() }
        )

        assertEquals(true, finished.await(5, TimeUnit.SECONDS))

        assertEquals(true, deleteSawBusyTrash.get())
        assertEquals(false, coordinator.isAreaBusy(TaskArea.Trash))
    }
    private fun file(path: String, modified: Long = 1L): FileMetadata {
        return FileMetadata(
            path = path,
            normalizedPath = path,
            sizeBytes = 10L,
            lastModifiedMillis = modified,
            hashHex = "hash"
        )
    }

    private fun group(size: Long, hash: String, count: Int): DuplicateGroupEntity {
        return DuplicateGroupEntity(
            sizeBytes = size,
            hashHex = hash,
            fileCount = count,
            totalBytes = size * count,
            updatedAtMillis = 1L
        )
    }
}
