package opensource.cached_dupe_scanner.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test

class FilesScreenDbStateReducerTest {
    @Test
    fun addDeletedPathToSet() {
        val marked = markDeletedPath(
            currentDeletedPaths = setOf("a", "b"),
            deletedPath = "c"
        )

        assertEquals(setOf("a", "b", "c"), marked)
    }

    @Test
    fun duplicateDeletedPathDoesNotDuplicateSetEntry() {
        val marked = markDeletedPath(
            currentDeletedPaths = setOf("a", "b"),
            deletedPath = "b"
        )

        assertEquals(setOf("a", "b"), marked)
    }

    @Test
    fun supportsEmptyInitialSet() {
        val marked = markDeletedPath(
            currentDeletedPaths = emptySet(),
            deletedPath = "x"
        )

        assertEquals(setOf("x"), marked)
    }
}
