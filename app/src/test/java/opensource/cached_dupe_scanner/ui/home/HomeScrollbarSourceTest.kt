package opensource.cached_dupe_scanner.ui.home

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class HomeScrollbarSourceTest {
    @Test
    fun simpleHomeScreensDoNotRenderStandaloneScrollbars() {
        listOf(
            "DashboardScreen.kt",
            "AboutScreen.kt",
            "PermissionScreen.kt",
            "TargetsScreen.kt",
            "DbManagementScreen.kt",
            "ScanCommandScreen.kt",
            "SettingsScreen.kt"
        ).forEach { fileName ->
            val content = homeSource(fileName)
            assertFalse(
                "$fileName should not render standalone VerticalScrollbar; result-style list scrollbars are the app standard",
                content.contains("VerticalScrollbar(")
            )
            assertFalse(
                "$fileName should not reserve right-side scrollbar padding after removing standalone scrollbars",
                content.contains("ScrollbarDefaults")
            )
        }
    }

    @Test
    fun listHomeScreensKeepResultStyleLazyScrollbars() {
        listOf(
            "FilesScreenDb.kt",
            "ReportsScreen.kt",
            "ResultsScreenDb.kt",
            "ResultsScreenDbBulkDelete.kt",
            "TrashScreen.kt"
        ).forEach { fileName ->
            val content = homeSource(fileName)
            assertTrue(
                "$fileName should keep the result-style lazy scrollbar for long lists",
                content.contains("VerticalLazyScrollbar(")
            )
        }
    }

    private fun homeSource(fileName: String): String {
        val projectDir = File(requireNotNull(System.getProperty("user.dir")))
        val relativePath = "app/src/main/java/opensource/cached_dupe_scanner/ui/home/$fileName"
        val sourceFile = sequenceOf(
            File(projectDir, relativePath),
            File(projectDir.parentFile ?: projectDir, relativePath)
        ).firstOrNull { it.exists() }

        assertTrue("$relativePath should exist", sourceFile != null)
        return sourceFile!!.readText()
    }
}
