package opensource.cached_dupe_scanner.ui.home

import java.io.File
import opensource.cached_dupe_scanner.cache.SimilarityClusterEntity
import opensource.cached_dupe_scanner.core.FileMetadata
import opensource.cached_dupe_scanner.core.SortDirection
import opensource.cached_dupe_scanner.storage.SimilarityClusterMember
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SimilaritySettingsScreenTest {
    @Test
    fun similarityScreensUseSeparateRouteLevelComposables() {
        val content = sourceText("SimilaritySettingsScreen.kt")

        assertTrue(content.contains("fun SimilaritySettingsScreen("))
        assertTrue(content.contains("fun SimilarityMaintenanceScreen("))
        assertTrue(content.contains("fun SimilaritySettingCreateScreen("))
        assertTrue(content.contains("fun SimilarityExactThumbnailSettingScreen("))
        assertTrue(content.contains("fun SimilarityDurationSettingScreen("))
        assertTrue(content.contains("fun SimilaritySettingDetailScreen("))
        assertTrue(content.contains("fun SimilarityClusterDetailScreen("))
        assertFalse(content.contains("private enum class SimilaritySettingsPane"))
        assertTrue(content.contains("startSimilarityMaintenanceTask("))
        assertTrue(content.contains("repository.setEnabled("))
        assertTrue(content.contains("repository.clearSettingResults("))
        assertTrue(content.contains("repository.clearAllResults()"))
        assertTrue(content.contains("ConfirmationDialog("))
        assertTrue(content.contains("parsedExactThumbnailStep("))
        assertTrue(content.contains("parsedDurationToleranceStep("))
        assertTrue(content.contains("parsedDurationNeighborListStep("))
        assertTrue(content.contains("repository.createExactThumbnailSetting("))
        assertTrue(content.contains("repository.createDurationToleranceSetting("))
        assertTrue(content.contains("repository.createDurationNeighborListSetting("))
        assertFalse(content.contains("title = \"Experiment"))
        assertFalse(content.contains("title = \"Template"))
    }

    @Test
    fun similaritySettingsScreenShowsClusterMembers() {
        val content = sourceText("SimilaritySettingsScreen.kt")

        assertTrue(content.contains("repository.listClusterMembersPage("))
        assertTrue(content.contains("SimilarityClusterSummaryCard("))
        assertTrue(content.contains("SimilarityMemberCard("))
        assertTrue(content.contains("GroupPreviewThumbnail("))
        assertTrue(content.contains("FileDetailsDialogWithDeleteConfirm("))
        assertTrue(content.contains("SimilarityClusterSortButton("))
        assertTrue(content.contains("sortSimilarityClusters("))
        assertTrue(content.contains("GroupMemberSortButton("))
        assertTrue(content.contains("sortGroupMembers("))
        assertTrue(content.contains("DurationNeighborSortDirectionCard("))
        assertTrue(content.contains("sortSimilarityClusterMembers("))
        assertTrue(content.contains("shouldTriggerSimilarityMemberAutoLoad("))
        assertTrue(content.contains("listState = memberListState"))
        assertTrue(content.contains("rememberLazyDetailSelectionState("))
        assertTrue(content.contains("SimilaritySelectionControls("))
        assertTrue(content.contains("combinedClickable("))
        assertTrue(content.contains("selectedFilesForDelete("))
        assertTrue(content.contains("VideoTimelinePreviewStrip("))
        assertTrue(content.contains("VideoMetadataLabelText("))
        assertTrue(content.contains("DropdownMenuItem("))
        assertTrue(content.contains("onOpenCluster"))
        assertFalse(content.contains("clusters.take("))
        assertFalse(content.contains("repository.listClusterMembers(clusterId)"))
    }

    @Test
    fun similaritySettingsScreenDoesNotExposeRawSimilarityInternals() {
        val content = sourceText("SimilaritySettingsScreen.kt")

        assertTrue(content.contains("settingParametersSummary(setting)"))
        assertFalse(content.contains("Parameters: \${setting.paramsJson}"))
        assertFalse(content.contains("Key \${cluster.clusterKey}"))
        assertFalse(content.contains("thumbnailSignature"))
    }

    @Test
    fun similarityClustersSortByFileCountAndTotalSize() {
        val clusters = listOf(
            cluster(key = "small", fileCount = 2, totalBytes = 500L),
            cluster(key = "large", fileCount = 3, totalBytes = 300L),
            cluster(key = "huge", fileCount = 2, totalBytes = 900L)
        )

        assertEquals(
            listOf("large", "huge", "small"),
            sortSimilarityClusters(
                clusters = clusters,
                sortKey = SimilarityClusterSortKey.FileCount,
                direction = SortDirection.Desc
            ).map { it.clusterKey }
        )
        assertEquals(
            listOf("large", "small", "huge"),
            sortSimilarityClusters(
                clusters = clusters,
                sortKey = SimilarityClusterSortKey.TotalSize,
                direction = SortDirection.Asc
            ).map { it.clusterKey }
        )
    }

    @Test
    fun similarityClusterMembersSortByDurationForDurationNeighborMode() {
        val members = listOf(
            member(path = "b.mp4", durationMillis = 20_000L),
            member(path = "a.mp4", durationMillis = 10_000L),
            member(path = "c.mp4", durationMillis = 15_000L)
        )

        assertEquals(
            listOf("a.mp4", "c.mp4", "b.mp4"),
            sortSimilarityClusterMembers(
                members = members,
                durationNeighborMode = true,
                durationDirection = SortDirection.Asc,
                sortKey = ResultGroupMemberSortKey.Path,
                sortDirection = SortDirection.Asc
            ).map { member -> member.metadata.normalizedPath }
        )
        assertEquals(
            listOf("b.mp4", "c.mp4", "a.mp4"),
            sortSimilarityClusterMembers(
                members = members,
                durationNeighborMode = true,
                durationDirection = SortDirection.Desc,
                sortKey = ResultGroupMemberSortKey.Path,
                sortDirection = SortDirection.Asc
            ).map { member -> member.metadata.normalizedPath }
        )
    }

    @Test
    fun similarityMemberAutoLoadTriggersNearBottomOnlyWhenReady() {
        assertTrue(
            shouldTriggerSimilarityMemberAutoLoad(
                lastVisibleItemIndex = 8,
                totalItemsCount = 10,
                thresholdItems = 3,
                isLoading = false,
                isComplete = false
            )
        )
        assertFalse(
            shouldTriggerSimilarityMemberAutoLoad(
                lastVisibleItemIndex = 4,
                totalItemsCount = 10,
                thresholdItems = 3,
                isLoading = false,
                isComplete = false
            )
        )
        assertFalse(
            shouldTriggerSimilarityMemberAutoLoad(
                lastVisibleItemIndex = 8,
                totalItemsCount = 10,
                thresholdItems = 3,
                isLoading = true,
                isComplete = false
            )
        )
        assertFalse(
            shouldTriggerSimilarityMemberAutoLoad(
                lastVisibleItemIndex = 8,
                totalItemsCount = 10,
                thresholdItems = 3,
                isLoading = false,
                isComplete = true
            )
        )
    }

    private fun cluster(
        key: String,
        fileCount: Int,
        totalBytes: Long
    ): SimilarityClusterEntity {
        return SimilarityClusterEntity(
            settingId = 1L,
            clusterKey = key,
            fileCount = fileCount,
            totalBytes = totalBytes,
            updatedAtMillis = 0L
        )
    }

    private fun member(path: String, durationMillis: Long): SimilarityClusterMember {
        return SimilarityClusterMember(
            metadata = FileMetadata(
                path = path,
                normalizedPath = path,
                sizeBytes = 1L,
                lastModifiedMillis = 0L
            ),
            durationMillis = durationMillis
        )
    }

    private fun sourceText(fileName: String): String {
        val projectDir = File(requireNotNull(System.getProperty("user.dir")))
        val sourceFile = sequenceOf(
            File(projectDir, "app/src/main/java/opensource/cached_dupe_scanner/ui/home/$fileName"),
            File(projectDir.parentFile ?: projectDir, "app/src/main/java/opensource/cached_dupe_scanner/ui/home/$fileName")
        ).firstOrNull { it.exists() }

        assertTrue("$fileName should exist", sourceFile != null)
        return sourceFile!!.readText()
    }
}
