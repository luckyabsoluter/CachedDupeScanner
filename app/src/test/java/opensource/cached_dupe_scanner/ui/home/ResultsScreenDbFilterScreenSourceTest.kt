package opensource.cached_dupe_scanner.ui.home

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ResultsScreenDbFilterScreenSourceTest {
    @Test
    fun resultsFilterUsesDedicatedScreenInsteadOfDialog() {
        val projectDir = File(requireNotNull(System.getProperty("user.dir")))
        val sourceFile = sequenceOf(
            File(projectDir, "app/src/main/java/opensource/cached_dupe_scanner/ui/home/ResultsScreenDbFilterDialog.kt"),
            File(projectDir.parentFile ?: projectDir, "app/src/main/java/opensource/cached_dupe_scanner/ui/home/ResultsScreenDbFilterDialog.kt")
        ).firstOrNull { it.exists() }

        assertTrue("ResultsScreenDbFilterDialog.kt should exist", sourceFile != null)

        val content = sourceFile!!.readText()

        assertTrue(
            "Filter editor should expose a dedicated full screen composable",
            content.contains("internal fun ResultsFilterScreen(")
        )
        assertTrue(
            "Filter editor should use AppTopBar for full screen navigation",
            content.contains("AppTopBar(title = \"Result filters\", onBack = onBack)")
        )
        assertFalse(
            "Filter editor should no longer use AlertDialog",
            content.contains("AlertDialog(")
        )
    }
}
