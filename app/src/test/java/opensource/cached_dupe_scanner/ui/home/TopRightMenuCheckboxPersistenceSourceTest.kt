package opensource.cached_dupe_scanner.ui.home

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TopRightMenuCheckboxPersistenceSourceTest {
    @Test
    fun filesMenuCheckboxTogglesKeepMenuOpen() {
        assertToggleDoesNotDismissMenu(
            fileName = "FilesScreenDb.kt",
            label = "Video preview",
            toggleLine = "previewMode.value = if (isVideoTimelinePreviewEnabled()) {",
            dismissLine = "menuExpanded.value = false"
        )
        assertToggleDoesNotDismissMenu(
            fileName = "FilesScreenDb.kt",
            label = "Video duration",
            toggleLine = "showVideoPreviewDuration.value = !showVideoPreviewDuration.value",
            dismissLine = "menuExpanded.value = false"
        )
        assertToggleDoesNotDismissMenu(
            fileName = "FilesScreenDb.kt",
            label = "Video resolution",
            toggleLine = "showVideoPreviewResolution.value = !showVideoPreviewResolution.value",
            dismissLine = "menuExpanded.value = false"
        )
    }

    @Test
    fun resultsMenuCheckboxTogglesKeepMenuOpen() {
        assertToggleDoesNotDismissMenu(
            fileName = "ResultsScreen.kt",
            label = "Show full paths",
            toggleLine = "settingsStore.setShowFullPaths(showFullPaths.value)",
            dismissLine = "menuExpanded.value = false"
        )
        assertToggleDoesNotDismissMenu(
            fileName = "ResultsScreenDb.kt",
            label = "Show full paths",
            toggleLine = "settingsStore.setShowFullPaths(showFullPaths.value)",
            dismissLine = "menuExpanded.value = false"
        )
    }

    private fun assertToggleDoesNotDismissMenu(
        fileName: String,
        label: String,
        toggleLine: String,
        dismissLine: String
    ) {
        val content = homeSource(fileName)
        val labelIndex = content.indexOf("Text(\"$label\")")
        assertTrue("$fileName should expose $label in a menu item", labelIndex >= 0)

        val toggleIndex = content.indexOf(toggleLine, labelIndex)
        assertTrue("$fileName should toggle $label from that menu item", toggleIndex > labelIndex)

        val toggleWindow = content.substring(
            toggleIndex,
            minOf(content.length, toggleIndex + 220)
        )
        assertFalse(
            "$fileName should keep the menu open after toggling $label",
            toggleWindow.contains(dismissLine)
        )
    }

    private fun homeSource(fileName: String): String {
        val projectDir = File(requireNotNull(System.getProperty("user.dir")))
        val sourceFile = sequenceOf(
            File(projectDir, "app/src/main/java/opensource/cached_dupe_scanner/ui/home/$fileName"),
            File(projectDir.parentFile ?: projectDir, "app/src/main/java/opensource/cached_dupe_scanner/ui/home/$fileName")
        ).firstOrNull { it.exists() }

        assertTrue("$fileName should exist", sourceFile != null)
        return sourceFile!!.readText()
    }
}
