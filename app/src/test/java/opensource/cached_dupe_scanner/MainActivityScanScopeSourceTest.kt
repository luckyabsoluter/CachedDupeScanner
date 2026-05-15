package opensource.cached_dupe_scanner

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MainActivityScanScopeSourceTest {
    @Test
    fun mainActivityUsesAppOwnedScopeForScans() {
        val projectDir = File(requireNotNull(System.getProperty("user.dir")))
        val activityFile = sequenceOf(
            File(projectDir, "app/src/main/java/opensource/cached_dupe_scanner/MainActivity.kt"),
            File(projectDir.parentFile ?: projectDir, "app/src/main/java/opensource/cached_dupe_scanner/MainActivity.kt")
        ).firstOrNull { it.exists() }
        assertTrue("MainActivity.kt should exist", activityFile != null)

        val content = activityFile!!.readText()
        assertTrue(
            "ScanCommandScreen should use app-owned scan scope instead of the Compose scope",
            content.contains("scanScope = AppWorkScopes.scanScope")
        )
    }
}
