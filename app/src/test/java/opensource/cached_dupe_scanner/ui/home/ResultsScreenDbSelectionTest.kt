package opensource.cached_dupe_scanner.ui.home

import opensource.cached_dupe_scanner.core.FileMetadata
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

    private fun file(path: String): FileMetadata {
        return FileMetadata(
            path = path,
            normalizedPath = path,
            sizeBytes = 10L,
            lastModifiedMillis = 1L,
            hashHex = "h"
        )
    }
}
