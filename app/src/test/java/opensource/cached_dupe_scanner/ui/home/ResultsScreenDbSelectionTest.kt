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
    fun selectAllLoadedMemberPathsReturnsEveryLoadedPath() {
        val members = listOf(
            file("/a/file1.jpg"),
            file("/a/file2.jpg"),
            file("/b/file3.jpg")
        )

        val selected = selectAllLoadedMemberPaths(members)

        assertEquals(
            setOf("/a/file1.jpg", "/a/file2.jpg", "/b/file3.jpg"),
            selected
        )
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
