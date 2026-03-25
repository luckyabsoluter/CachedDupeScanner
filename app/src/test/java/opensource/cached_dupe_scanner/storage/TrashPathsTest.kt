package opensource.cached_dupe_scanner.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TrashPathsTest {
    @Test
    fun ensureTrashLayoutCreatesNomediaAndTrashDir() {
        val volumeRoot = createTempDir(prefix = "volumeRoot_")
        try {
            val result = TrashPaths.ensureTrashLayout(volumeRoot)
            assertTrue(result.isSuccess)

            val appRoot = File(volumeRoot, ".CachedDupeScanner")
            val nomedia = File(appRoot, ".nomedia")
            val trashDir = File(appRoot, "trashbin")

            assertTrue(appRoot.exists())
            assertTrue(nomedia.exists())
            assertTrue(trashDir.exists())

            // Idempotent
            val result2 = TrashPaths.ensureTrashLayout(volumeRoot)
            assertTrue(result2.isSuccess)
            assertEquals(trashDir.absolutePath, result2.getOrNull()?.absolutePath)
        } finally {
            volumeRoot.deleteRecursively()
        }
    }

    @Test
    fun isInTrashBinDetectsTrashDirectoryAndChildrenOnly() {
        val volumeRoot = createTempDir(prefix = "volumeRoot_")
        try {
            val trashDir = TrashPaths.trashBinDir(volumeRoot).apply { mkdirs() }
            val trashedFile = File(trashDir, "item.txt")
            val regularFile = File(volumeRoot, "item.txt")

            assertTrue(TrashPaths.isInTrashBin(trashDir))
            assertTrue(TrashPaths.isInTrashBin(trashedFile))
            assertFalse(TrashPaths.isInTrashBin(regularFile))
        } finally {
            volumeRoot.deleteRecursively()
        }
    }
}
