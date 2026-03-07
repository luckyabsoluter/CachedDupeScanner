package opensource.cached_dupe_scanner.ui.home

import opensource.cached_dupe_scanner.core.FileMetadata
import opensource.cached_dupe_scanner.cache.DuplicateGroupEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class ResultsScreenDbSelectionTest {
    @Test
    fun togglePathSelectionAddsAndRemovesPath() {
        val added = togglePathSelection(
            selectedPaths = emptySet(),
            path = "/a/file1.jpg"
        )
        val removed = togglePathSelection(
            selectedPaths = added,
            path = "/a/file1.jpg"
        )

        assertEquals(setOf("/a/file1.jpg"), added)
        assertEquals(emptySet<String>(), removed)
    }

    @Test
    fun countSelectedForDisplayUsesTotalMinusExclusionsInSelectAllMode() {
        val selectedCount = countSelectedForDisplay(
            totalCount = 10,
            selectAllMode = true,
            selectedPaths = emptySet(),
            deselectedPaths = setOf("/a", "/b", "/c")
        )

        assertEquals(7, selectedCount)
    }

    @Test
    fun countSelectedForDisplayDoesNotGoNegative() {
        val selectedCount = countSelectedForDisplay(
            totalCount = 2,
            selectAllMode = true,
            selectedPaths = emptySet(),
            deselectedPaths = setOf("/a", "/b", "/c")
        )

        assertEquals(0, selectedCount)
    }

    @Test
    fun isPathSelectedForModeReflectsSelectAllAndExclusions() {
        val selectedInAll = isPathSelectedForMode(
            path = "/a/file1.jpg",
            selectAllMode = true,
            selectedPaths = emptySet(),
            deselectedPaths = emptySet()
        )
        val excludedInAll = isPathSelectedForMode(
            path = "/a/file2.jpg",
            selectAllMode = true,
            selectedPaths = emptySet(),
            deselectedPaths = setOf("/a/file2.jpg")
        )
        val selectedInPartial = isPathSelectedForMode(
            path = "/a/file3.jpg",
            selectAllMode = false,
            selectedPaths = setOf("/a/file3.jpg"),
            deselectedPaths = emptySet()
        )

        assertEquals(true, selectedInAll)
        assertEquals(false, excludedInAll)
        assertEquals(true, selectedInPartial)
    }

    @Test
    fun selectionStatusTextShowsSelectAllState() {
        val full = selectionStatusText(
            totalCount = 5,
            selectAllMode = true,
            selectedPaths = emptySet(),
            deselectedPaths = emptySet()
        )
        val partial = selectionStatusText(
            totalCount = 5,
            selectAllMode = true,
            selectedPaths = emptySet(),
            deselectedPaths = setOf("/a/file1.jpg")
        )

        assertEquals("Select all active · 5 selected", full)
        assertEquals("Select all active · 1 excluded · 4 selected", partial)
    }

    @Test
    fun filterSelectionToLoadedMembersRemovesStaleSelections() {
        val members = listOf(
            file("/a/file1.jpg"),
            file("/a/file2.jpg")
        )

        val filtered = filterSelectionToLoadedMembers(
            selectedPaths = setOf("/a/file1.jpg", "/old/file9.jpg"),
            members = members
        )

        assertEquals(setOf("/a/file1.jpg"), filtered)
    }

    @Test
    fun selectedFilesForDeleteKeepsOnlySelectedAndNotDeleted() {
        val members = listOf(
            file("/a/file1.jpg"),
            file("/a/file2.jpg"),
            file("/a/file3.jpg")
        )

        val targets = selectedFilesForDelete(
            members = members,
            selectedPaths = setOf("/a/file1.jpg", "/a/file2.jpg"),
            deletedPaths = setOf("/a/file2.jpg")
        )

        assertEquals(listOf("/a/file1.jpg"), targets.map { it.normalizedPath })
    }

    @Test
    fun shouldTriggerDetailAutoLoadWhenNearBottomAndNotLoading() {
        val trigger = shouldTriggerDetailAutoLoad(
            scrollValue = 980,
            maxScrollValue = 1000,
            thresholdPx = 40,
            isLoading = false,
            isComplete = false
        )

        assertEquals(true, trigger)
    }

    @Test
    fun shouldNotTriggerDetailAutoLoadWhenAlreadyLoadingOrComplete() {
        val loading = shouldTriggerDetailAutoLoad(
            scrollValue = 980,
            maxScrollValue = 1000,
            thresholdPx = 40,
            isLoading = true,
            isComplete = false
        )
        val complete = shouldTriggerDetailAutoLoad(
            scrollValue = 980,
            maxScrollValue = 1000,
            thresholdPx = 40,
            isLoading = false,
            isComplete = true
        )

        assertEquals(false, loading)
        assertEquals(false, complete)
    }

    @Test
    fun estimateCurrentFromScrollMapsTopAndBottomToRange() {
        val top = estimateCurrentFromScroll(
            scrollValue = 0,
            maxScrollValue = 1000,
            loadedCount = 10
        )
        val bottom = estimateCurrentFromScroll(
            scrollValue = 1000,
            maxScrollValue = 1000,
            loadedCount = 10
        )

        assertEquals(1, top)
        assertEquals(10, bottom)
    }

    @Test
    fun sortGroupMembersSortsByPathAsc() {
        val members = listOf(
            file("/b/file2.jpg", modified = 2L),
            file("/a/file1.jpg", modified = 3L),
            file("/c/file3.jpg", modified = 1L)
        )

        val sorted = sortGroupMembers(
            members = members,
            sortKey = ResultGroupMemberSortKey.Path,
            direction = opensource.cached_dupe_scanner.core.SortDirection.Asc
        )

        assertEquals(
            listOf("/a/file1.jpg", "/b/file2.jpg", "/c/file3.jpg"),
            sorted.map { it.normalizedPath }
        )
    }

    @Test
    fun sortGroupMembersSortsByModifiedDesc() {
        val members = listOf(
            file("/b/file2.jpg", modified = 2L),
            file("/a/file1.jpg", modified = 3L),
            file("/c/file3.jpg", modified = 1L)
        )

        val sorted = sortGroupMembers(
            members = members,
            sortKey = ResultGroupMemberSortKey.Modified,
            direction = opensource.cached_dupe_scanner.core.SortDirection.Desc
        )

        assertEquals(
            listOf("/a/file1.jpg", "/b/file2.jpg", "/c/file3.jpg"),
            sorted.map { it.normalizedPath }
        )
    }

    @Test
    fun findIndexByGroupKeyOrFallbackReturnsMatchingKeyIndex() {
        val groups = listOf(
            group(size = 10L, hash = "a"),
            group(size = 20L, hash = "b"),
            group(size = 30L, hash = "c")
        )

        val index = findIndexByGroupKeyOrFallback(
            groups = groups,
            key = "20:b",
            fallbackIndex = 0
        )

        assertEquals(1, index)
    }

    @Test
    fun findIndexByGroupKeyOrFallbackClampsFallbackWhenKeyMissing() {
        val groups = listOf(
            group(size = 10L, hash = "a"),
            group(size = 20L, hash = "b"),
            group(size = 30L, hash = "c")
        )

        val index = findIndexByGroupKeyOrFallback(
            groups = groups,
            key = "99:z",
            fallbackIndex = 99
        )

        assertEquals(2, index)
    }

    @Test
    fun uniqueGroupsToAppendSkipsOverlapAndKeepsOrder() {
        val existing = listOf(
            group(size = 10L, hash = "a"),
            group(size = 20L, hash = "b"),
            group(size = 30L, hash = "c")
        )
        val fetchedWithOverlap = listOf(
            group(size = 30L, hash = "c"),
            group(size = 40L, hash = "d"),
            group(size = 50L, hash = "e")
        )

        val appended = uniqueGroupsToAppend(
            existing = existing,
            fetched = fetchedWithOverlap,
            maxAppend = 2
        )

        assertEquals(listOf("40:d", "50:e"), appended.map { "${it.sizeBytes}:${it.hashHex}" })
    }

    @Test
    fun uniqueGroupsToAppendPreventsDuplicateAppendInsideFetchedPage() {
        val existing = listOf(
            group(size = 10L, hash = "a")
        )
        val fetched = listOf(
            group(size = 20L, hash = "b"),
            group(size = 20L, hash = "b"),
            group(size = 30L, hash = "c")
        )

        val appended = uniqueGroupsToAppend(
            existing = existing,
            fetched = fetched,
            maxAppend = 10
        )

        assertEquals(listOf("20:b", "30:c"), appended.map { "${it.sizeBytes}:${it.hashHex}" })
    }

    private fun file(path: String, modified: Long = 1L): FileMetadata {
        return FileMetadata(
            path = path,
            normalizedPath = path,
            sizeBytes = 10L,
            lastModifiedMillis = modified,
            hashHex = "h"
        )
    }

    private fun group(size: Long, hash: String): DuplicateGroupEntity {
        return DuplicateGroupEntity(
            sizeBytes = size,
            hashHex = hash,
            fileCount = 2,
            totalBytes = size * 2,
            updatedAtMillis = 1L
        )
    }
}
