package opensource.cached_dupe_scanner.ui.home

import opensource.cached_dupe_scanner.core.FileMetadata
import org.junit.Assert.assertEquals
import org.junit.Test

class FileUiUtilsTest {
    @Test
    fun missingFilePathsReturnsOnlyDistinctUnavailablePaths() {
        val files = listOf(
            file("/present/a.jpg"),
            file("/missing/b.jpg"),
            file("/missing/b.jpg"),
            file("/present/c.jpg")
        )

        val missingPaths = missingFilePaths(
            files = files,
            pathExists = { path -> path.startsWith("/present/") }
        )

        assertEquals(linkedSetOf("/missing/b.jpg"), missingPaths)
    }

    @Test
    fun missingFilePathsReturnsEmptyWhenEveryFileExists() {
        val missingPaths = missingFilePaths(
            files = listOf(file("/present/a.jpg"), file("/present/b.jpg")),
            pathExists = { true }
        )

        assertEquals(emptySet<String>(), missingPaths)
    }

    private fun file(path: String): FileMetadata {
        return FileMetadata(
            path = path,
            normalizedPath = path,
            sizeBytes = 10L,
            lastModifiedMillis = 20L
        )
    }
}
