package opensource.cached_dupe_scanner.ui.home

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DuplicateGroupDetailContentSourceTest {
    @Test
    fun eagerDuplicateGroupDetailSupportsLongPressSelection() {
        val detailContent = sourceText("DuplicateGroupDetailContent.kt")

        assertTrue(detailContent.contains("internal fun EagerDuplicateGroupDetailContent("))
        assertTrue(detailContent.contains("Use only when the complete member list is already loaded."))
        assertTrue(detailContent.contains("import androidx.compose.foundation.combinedClickable"))
        assertTrue(detailContent.contains("val selectedPaths = remember"))
        assertTrue(detailContent.contains("val selectionMode = selectedPaths.value.isNotEmpty()"))
        assertTrue(detailContent.contains(".combinedClickable("))
        assertTrue(detailContent.contains("onLongClick = {"))
        assertTrue(detailContent.contains("Checkbox("))
        assertTrue(detailContent.contains("Delete selected"))
        assertTrue(detailContent.contains("selectedFilesForDelete("))
        assertTrue(detailContent.contains("FileDetailsDialogWithDeleteConfirm("))
    }

    private fun sourceText(fileName: String): String {
        val projectDir = File(requireNotNull(System.getProperty("user.dir")))
        val sourceFile = sequenceOf(
            File(projectDir, "app/src/main/java/opensource/cached_dupe_scanner/ui/home/$fileName"),
            File(projectDir.parentFile ?: projectDir, "app/src/main/java/opensource/cached_dupe_scanner/ui/home/$fileName")
        ).firstOrNull { it.exists() }

        assertTrue("$fileName should exist", sourceFile != null)
        return sourceFile!!.readText()
    }
}
