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
    fun similarityScreensUseRouteLevelComposablesWithSettingsFlowText() {
        val content = sourceText("SimilaritySettingsScreen.kt")

        assertTrue(content.contains("fun SimilaritySettingsScreen("))
        assertFalse(content.contains("fun SimilarityMaintenanceScreen("))
        assertTrue(content.contains("fun SimilaritySettingCreateScreen("))
        assertTrue(content.contains("fun SimilarityExactThumbnailSettingScreen("))
        assertTrue(content.contains("fun SimilarityDurationSettingScreen("))
        assertTrue(content.contains("fun SimilaritySettingDetailScreen("))
        assertTrue(content.contains("fun SimilaritySettingGroupsScreen("))
        assertTrue(content.contains("fun SimilarityClusterDetailScreen("))
        assertFalse(content.contains("private enum class SimilaritySettingsPane"))
        assertFalse(content.contains("startSimilarityMaintenanceTask("))
        assertTrue(content.contains("repository.setEnabled("))
        assertTrue(content.contains("repository.clearSettingResults("))
        assertTrue(content.contains("repository.deleteSetting("))
        assertFalse(content.contains("repository.clearAllResults()"))
        assertTrue(content.contains("ConfirmationDialog("))
        assertFalse(content.contains("Text(\"Maintenance\")"))
        assertFalse(content.contains("Text(\"Run\")"))
        assertFalse(content.contains("Text(\"Rebuild\")"))
        assertTrue(content.contains("Text(\"Delete setting\")"))
        assertTrue(content.contains("title = \"Delete this similarity setting?\""))
        assertFalse(content.contains("ensureDefaultSettings()"))
        assertTrue(content.contains("parsedExactThumbnailStep("))
        assertTrue(content.contains("parsedDurationToleranceStep("))
        assertTrue(content.contains("parsedDurationNeighborListStep("))
        assertTrue(content.contains("repository.createExactThumbnailSetting("))
        assertTrue(content.contains("repository.createDurationToleranceSetting("))
        assertTrue(content.contains("repository.createDurationNeighborListSetting("))
        assertTrue(content.contains("title = \"Similarity settings\""))
        assertTrue(content.contains("Text(\"New setting\")"))
        assertTrue(content.contains("text = \"Similarity settings\""))
        assertTrue(content.contains("text = \"Similarity templates\""))
        assertTrue(content.contains("Similarity method template"))
        val legacySingular = "experi" + "ment"
        val legacyPlural = legacySingular + "s"
        assertFalse(content.contains("title = \"Similarity $legacyPlural\""))
        assertFalse(content.contains("Text(\"New $legacySingular\")"))
        assertFalse(content.contains("Executable $legacySingular template"))
    }

    @Test
    fun similaritySettingsScreenShowsClusterMembers() {
        val content = sourceText("SimilaritySettingsScreen.kt")
        val settingDetailContent = content
            .substringAfter("fun SimilaritySettingDetailScreen(")
            .substringBefore("@Composable\nfun SimilaritySettingGroupsScreen(")
        val settingGroupsContent = content
            .substringAfter("fun SimilaritySettingGroupsScreen(")
            .substringBefore("@Composable\nfun SimilarityClusterDetailScreen(")

        assertTrue(content.contains("repository.listClusterMembersPage("))
        assertTrue(settingDetailContent.contains("SimilarityGroupsEntryCard("))
        assertFalse(settingDetailContent.contains("SimilarityGroupsHeader("))
        assertFalse(settingDetailContent.contains("SimilarityClusterListCard("))
        assertTrue(settingGroupsContent.contains("SimilarityGroupsHeader("))
        assertTrue(settingGroupsContent.contains("SimilarityClusterListCard("))
        assertTrue(content.contains("SimilarityClusterDetailOverviewCard("))
        assertTrue(content.contains("ExactHashReductionPreviewCard("))
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
        assertTrue(content.contains("SimilarityClusterMemberVideoMetadata("))
        assertTrue(content.contains("SimilarityClusterMemberVideoPreview("))
        assertTrue(content.contains("showVideoPreviews: Boolean"))
        assertTrue(content.contains("onShowVideoPreviewsChange: (Boolean) -> Unit"))
        assertFalse(content.contains("var showVideoPreviews by remember { mutableStateOf(false) }"))
        assertFalse(content.contains("var showVideoPreviewDurations by remember { mutableStateOf(false) }"))
        assertFalse(content.contains("var showVideoPreviewResolutions by remember { mutableStateOf(false) }"))
        assertTrue(content.contains("formatBytesWithExact(metadata.sizeBytes)"))
        assertTrue(content.contains("MaterialTheme.colorScheme.secondaryContainer"))
        assertTrue(content.contains("DropdownMenuItem("))
        assertTrue(content.contains("onOpenCluster"))
        assertTrue(content.contains("exactThumbnailClusterExplanation("))
        assertTrue(content.contains("durationNeighborClusterExplanation("))
        assertTrue(content.contains("similarityClusterPreviewLineTexts("))
        assertTrue(content.contains("Group rule: exact thumbnail hash equality"))
        assertTrue(content.contains("List rule: duration-sorted neighbor filter"))
        assertFalse(content.contains("clusters.take("))
        assertFalse(content.contains("repository.listClusterMembers(clusterId)"))
        assertFalse(content.contains("\"${'$'}index. "))
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
    fun similarityClusterExplanationRestoresReadableSettingSummaries() {
        val exact = exactThumbnailClusterExplanation("thumb-v1:video:color:2x1:q16:0,1:0f0f0f,000000|ffffff,101010")
        val neighbor = durationNeighborClusterExplanation("duration-neighbor-list-v1:500:1000-1500")

        requireNotNull(exact)
        requireNotNull(neighbor)
        assertEquals(
            "Exact hash: Video, 0s, 1s, 2x1, color, 16 levels",
            exactHashClusterSummary(exact)
        )
        assertEquals(
            "Duration neighbor list: 1s - 1.500s, tolerance 0.500s",
            durationNeighborClusterSummary(neighbor)
        )
        assertEquals(
            listOf("1s | a.mp4  |  2s | b.mp4"),
            similarityClusterPreviewLineTexts(
                members = listOf(
                    file(path = "b.mp4"),
                    file(path = "a.mp4")
                ),
                showFullPaths = false,
                durationMillisByNormalizedPath = mapOf(
                    "a.mp4" to 1_000L,
                    "b.mp4" to 2_000L
                )
            )
        )
    }

    @Test
    fun exactHashReductionSamplesDecodeRawAndQuantizedColors() {
        assertEquals(
            ExactHashReductionColor(red = 255, green = 128, blue = 64),
            exactHashReductionColor(
                colorMode = "color",
                quantization = "raw",
                signature = "ff8040"
            )
        )
        assertEquals(
            ExactHashReductionColor(red = 255, green = 255, blue = 255),
            exactHashReductionColor(
                colorMode = "color",
                quantization = "q16",
                signature = "fff"
            )
        )
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
            metadata = file(path = path),
            durationMillis = durationMillis
        )
    }

    private fun file(path: String): FileMetadata {
        return FileMetadata(
            path = path,
            normalizedPath = path,
            sizeBytes = 1L,
            lastModifiedMillis = 0L
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
