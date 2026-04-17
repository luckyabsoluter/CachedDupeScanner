package opensource.cached_dupe_scanner.ui.home

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ResultsScreenDbSummaryLayoutSourceTest {
    @Test
    fun resultsSummaryKeepsDedicatedSortButtonSpace() {
        val projectDir = File(requireNotNull(System.getProperty("user.dir")))
        val sourceFile = sequenceOf(
            File(projectDir, "app/src/main/java/opensource/cached_dupe_scanner/ui/home/ResultsScreenDb.kt"),
            File(projectDir.parentFile ?: projectDir, "app/src/main/java/opensource/cached_dupe_scanner/ui/home/ResultsScreenDb.kt")
        ).firstOrNull { it.exists() }

        assertTrue("ResultsScreenDb.kt should exist", sourceFile != null)

        val content = sourceFile!!.readText()

        assertTrue(
            "Summary column should use weight so the Sort button keeps its space",
            content.contains("Column(\n                        modifier = Modifier.weight(1f)")
        )
        assertTrue(
            "Sort button should reserve a minimum width",
            content.contains("modifier = Modifier.widthIn(min = 88.dp)")
        )
    }
}
