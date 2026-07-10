package opensource.cached_dupe_scanner.ui.home

import opensource.cached_dupe_scanner.cache.SimilarityClusterEntity
import opensource.cached_dupe_scanner.core.DurationNeighborClusterExplanation
import opensource.cached_dupe_scanner.core.ExactThumbnailClusterExplanation
import opensource.cached_dupe_scanner.core.FileMetadata
import opensource.cached_dupe_scanner.core.SortDirection
import opensource.cached_dupe_scanner.storage.SimilarityClusterMember
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SimilaritySettingsScreenTest {
    @Test
    fun similarityClusterExplanationKeepsSettingDetailsOutOfClusterSummaries() {
        val exact = ExactThumbnailClusterExplanation(
            mediaScope = "video",
            colorMode = "color",
            resize = "2x1",
            quantization = "q16",
            frameSeconds = listOf("0", "1"),
            sampleSignatures = listOf("0f0f0f,000000", "ffffff,101010")
        )
        val neighbor = DurationNeighborClusterExplanation(
            toleranceMillis = 500L,
            minDurationMillis = 1_000L,
            maxDurationMillis = 1_500L
        )

        assertEquals(
            "Matched thumbnail signature: 0f0f0f,000000 | ffffff,101010",
            exactHashClusterSummary(exact)
        )
        assertEquals(
            "Visible duration span: 1s - 1.500s",
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

    @Test
    fun similarityClusterAutoLoadUsesSameGuardRulesAsMemberPaging() {
        assertTrue(
            shouldTriggerSimilarityClusterAutoLoad(
                lastVisibleItemIndex = 8,
                totalItemsCount = 10,
                thresholdItems = 3,
                isLoading = false,
                isComplete = false
            )
        )
        assertFalse(
            shouldTriggerSimilarityClusterAutoLoad(
                lastVisibleItemIndex = 4,
                totalItemsCount = 10,
                thresholdItems = 3,
                isLoading = false,
                isComplete = false
            )
        )
        assertFalse(
            shouldTriggerSimilarityClusterAutoLoad(
                lastVisibleItemIndex = 8,
                totalItemsCount = 10,
                thresholdItems = 3,
                isLoading = true,
                isComplete = false
            )
        )
        assertFalse(
            shouldTriggerSimilarityClusterAutoLoad(
                lastVisibleItemIndex = 8,
                totalItemsCount = 10,
                thresholdItems = 3,
                isLoading = false,
                isComplete = true
            )
        )
    }

    @Test
    fun similarityLoadIndicatorTextUsesLoadedWindowAndHeaderOffset() {
        assertEquals(
            "3/10/25 (30%/40%)",
            similarityLoadIndicatorText(
                firstVisibleItemIndex = 4,
                loadedCount = 10,
                totalCount = 25,
                nonDataItemCount = 2
            )
        )
        assertEquals(
            null,
            similarityLoadIndicatorText(
                firstVisibleItemIndex = 4,
                loadedCount = 10,
                totalCount = 25,
                nonDataItemCount = 2,
                hidden = true
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

}
