package opensource.cached_dupe_scanner.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ScanCompletionNavigationTest {
    @Test
    fun scanCompletionDoesNotAutoNavigateToResults() {
        val projectDir = File(requireNotNull(System.getProperty("user.dir")))
        val activityFile = sequenceOf(
            File(projectDir, "app/src/main/java/opensource/cached_dupe_scanner/MainActivity.kt"),
            File(projectDir.parentFile ?: projectDir, "app/src/main/java/opensource/cached_dupe_scanner/MainActivity.kt")
        ).firstOrNull { it.exists() }

        assertTrue("MainActivity.kt should exist", activityFile != null)

        val content = activityFile!!.readText()
        assertTrue("handleScanComplete should exist", content.contains("fun handleScanComplete(scan: ScanResult)"))
        val handleScanCompleteBlock = content.substringAfter("fun handleScanComplete(scan: ScanResult) {")
            .substringBefore("LaunchedEffect(state.value, sortSettingsVersion.value)")
        assertFalse(
            "Scan completion should not auto-navigate to Results",
            handleScanCompleteBlock.contains("navigateTo(backStack, screenCache, Screen.Results)")
        )
    }
}
