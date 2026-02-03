package opensource.cached_dupe_scanner.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.io.File

class StorageRootResolverTest {
    @Test
    fun volumeRootFromExternalFilesDirExtractsRoot() {
        val pkg = "opensource.cached_dupe_scanner"
        val externalFilesDir = File("/storage/emulated/0/Android/data/$pkg/files")

        val root = StorageRootResolver.volumeRootFromExternalFilesDir(externalFilesDir, pkg)
        assertNotNull(root)
        assertEquals("/storage/emulated/0", root?.rootPath)
    }

    @Test
    fun volumeRootFromExternalFilesDirRejectsUnexpectedPackage() {
        val pkg = "opensource.cached_dupe_scanner"
        val externalFilesDir = File("/storage/ABCD-EFGH/Android/data/other.pkg/files")

        val root = StorageRootResolver.volumeRootFromExternalFilesDir(externalFilesDir, pkg)
        assertEquals(null, root)
    }
}
