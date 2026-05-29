package opensource.cached_dupe_scanner

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MainActivityRestoreLastResultSourceTest {
    @Test
    fun mainActivityRestoreLastResultDoesNotEagerLoadMergedHistory() {
        val projectDir = File(requireNotNull(System.getProperty("user.dir")))
        val sourceFile = sequenceOf(
            File(projectDir, "app/src/main/java/opensource/cached_dupe_scanner/MainActivity.kt"),
            File(projectDir.parentFile ?: projectDir, "app/src/main/java/opensource/cached_dupe_scanner/MainActivity.kt")
        ).firstOrNull { it.exists() }

        assertTrue("MainActivity.kt should exist", sourceFile != null)

        val content = sourceFile!!.readText()

        assertTrue(
            "MainActivity should still define restoreLastResult for scan cancellation",
            content.contains("val restoreLastResult: () -> Unit")
        )
        assertFalse(
            "restoreLastResult should not eager-load merged scan history",
            content.contains("historyRepo.loadMergedHistory()")
        )
        assertTrue(
            "restoreLastResult should preserve an in-memory success state",
            content.contains("state.value !is ScanUiState.Success")
        )
        assertTrue(
            "restoreLastResult should fall back to idle without rebuilding cached results",
            content.contains("state.value = ScanUiState.Idle")
        )
    }
}
