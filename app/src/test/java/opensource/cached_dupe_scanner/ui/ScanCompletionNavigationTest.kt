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
        assertTrue("handleScanComplete should exist", content.contains("suspend fun handleScanComplete(scan: ScanResult)"))
        val handleScanCompleteBlock = content.substringAfter("suspend fun handleScanComplete(scan: ScanResult) {")
            .substringBefore("fun refreshSimilarityFromCache")
        assertFalse(
            "Scan persistence should be awaited by the scan task instead of relaunched asynchronously",
            handleScanCompleteBlock.contains("scope.launch")
        )
        assertFalse(
            "Scan completion should not auto-navigate to Results",
            handleScanCompleteBlock.contains("navigateTo(backStack, screenCache, Screen.Results)")
        )
    }

    @Test
    fun scanTaskCompletionWaitsForPersistenceCallback() {
        val projectDir = File(requireNotNull(System.getProperty("user.dir")))
        val screenFile = sequenceOf(
            File(projectDir, "app/src/main/java/opensource/cached_dupe_scanner/ui/home/ScanCommandScreen.kt"),
            File(
                projectDir.parentFile ?: projectDir,
                "app/src/main/java/opensource/cached_dupe_scanner/ui/home/ScanCommandScreen.kt"
            )
        ).firstOrNull { it.exists() }

        assertTrue("ScanCommandScreen.kt should exist", screenFile != null)

        val content = screenFile!!.readText()
        val singleScanCompletion = content.substringAfter("onScanComplete(result)")
            .substringBefore("private fun runScanForAllTargets")
        val allScanCompletion = content.substringAfter("onScanComplete(merged)")
            .substringBefore("private suspend fun persistScanReport")
        assertTrue(
            "Single-target scan should complete the task after onScanComplete returns",
            singleScanCompletion.contains("taskCoordinator.complete(")
        )
        assertTrue(
            "All-target scan should complete the task after onScanComplete returns",
            allScanCompletion.contains("taskCoordinator.complete(")
        )
    }
}
