package opensource.cached_dupe_scanner.ui.home

import opensource.cached_dupe_scanner.core.FileMetadata
import org.junit.Assert.assertEquals
import org.junit.Test

class FilesScreenDbStateReducerTest {
    @Test
    fun removeDeletedFileFromLoadedItemsAndTotal() {
        val files = listOf(
            fileMeta("a"),
            fileMeta("b"),
            fileMeta("c")
        )

        val reduced = reduceFilesAfterDelete(
            currentItems = files,
            currentTotalCount = 10,
            deletedPath = "b"
        )

        assertEquals(listOf("a", "c"), reduced.items.map { it.normalizedPath })
        assertEquals(9, reduced.totalCount)
    }

    @Test
    fun whenDeletedPathNotInLoadedItemsTotalRemainsSame() {
        val files = listOf(fileMeta("a"), fileMeta("b"))

        val reduced = reduceFilesAfterDelete(
            currentItems = files,
            currentTotalCount = 5,
            deletedPath = "x"
        )

        assertEquals(listOf("a", "b"), reduced.items.map { it.normalizedPath })
        assertEquals(5, reduced.totalCount)
    }

    @Test
    fun totalNeverGoesBelowZero() {
        val files = listOf(fileMeta("a"))

        val reduced = reduceFilesAfterDelete(
            currentItems = files,
            currentTotalCount = 0,
            deletedPath = "a"
        )

        assertEquals(emptyList<String>(), reduced.items.map { it.normalizedPath })
        assertEquals(0, reduced.totalCount)
    }

    private fun fileMeta(path: String): FileMetadata = FileMetadata(
        path = path,
        normalizedPath = path,
        sizeBytes = 1L,
        lastModifiedMillis = 1L
    )
}
