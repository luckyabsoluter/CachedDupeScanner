package opensource.cached_dupe_scanner

import org.junit.Assert.assertEquals
import org.junit.Test

class MainActivityTrashRestoreTest {
    @Test
    fun deletedPathsAfterTrashRestoreRemovesRestoredPathOnly() {
        val deletedPaths = setOf("/files/restored.mp4", "/files/still-deleted.mp4")

        val updated = deletedPathsAfterTrashRestore(
            deletedPaths = deletedPaths,
            restoredPath = "/files/restored.mp4"
        )

        assertEquals(setOf("/files/still-deleted.mp4"), updated)
    }

    @Test
    fun deletedPathsAfterTrashRestoreIgnoresBlankPath() {
        val deletedPaths = setOf("/files/deleted.mp4")

        val updated = deletedPathsAfterTrashRestore(
            deletedPaths = deletedPaths,
            restoredPath = ""
        )

        assertEquals(deletedPaths, updated)
    }
}
