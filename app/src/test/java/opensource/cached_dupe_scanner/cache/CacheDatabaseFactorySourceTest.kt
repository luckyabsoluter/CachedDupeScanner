package opensource.cached_dupe_scanner.cache

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CacheDatabaseFactorySourceTest {
    @Test
    fun productionDatabaseBuildersUseSharedFactory() {
        val projectDir = File(requireNotNull(System.getProperty("user.dir")))
        val mainActivity = source(projectDir, "app/src/main/java/opensource/cached_dupe_scanner/MainActivity.kt")
        val scanCommand = source(
            projectDir,
            "app/src/main/java/opensource/cached_dupe_scanner/ui/home/ScanCommandScreen.kt"
        )
        val factory = source(
            projectDir,
            "app/src/main/java/opensource/cached_dupe_scanner/cache/CacheDatabaseFactory.kt"
        )

        assertTrue(mainActivity.contains("buildCacheDatabase(context)"))
        assertTrue(mainActivity.contains("IncrementalScanner(scanCacheStore)"))
        assertFalse(scanCommand.contains("Room.databaseBuilder"))
        assertTrue(factory.contains("CacheMigrations.MIGRATION_18_19"))
    }

    private fun source(projectDir: File, relativePath: String): String {
        return sequenceOf(
            File(projectDir, relativePath),
            File(projectDir.parentFile ?: projectDir, relativePath)
        ).firstOrNull { file -> file.exists() }?.readText()
            ?: error("Missing source file: $relativePath")
    }
}
