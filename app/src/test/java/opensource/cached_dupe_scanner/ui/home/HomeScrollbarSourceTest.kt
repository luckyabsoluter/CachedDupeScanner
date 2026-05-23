package opensource.cached_dupe_scanner.ui.home

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class HomeScrollbarSourceTest {
    @Test
    fun simpleHomeScreensUseResultStyleLazySideScrollbars() {
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
            assertTrue(
                "$fileName should use the shared result-style lazy scrollbar container",
                content.contains("ScreenScrollColumn(")
            )
            assertTrue(
                "$fileName should not use the standalone ScrollState scrollbar",
                !content.contains("VerticalScrollbar(")
            )
            assertTrue(
                "$fileName should not use verticalScroll with a separate scrollbar implementation",
                !content.contains(".verticalScroll(")
            )
        }
    }

    @Test
    fun sharedSimpleScreenScrollbarUsesVerticalLazyScrollbar() {
        val content = source("app/src/main/java/opensource/cached_dupe_scanner/ui/components/ScreenScrollColumn.kt")

        assertTrue(
            "Simple screens should share the same lazy scrollbar implementation used by result lists",
            content.contains("VerticalLazyScrollbar(")
        )
        assertTrue(
            "The shared scrollbar container should reserve the result-style gutter",
            content.contains("ScrollbarDefaults.ThumbWidth")
        )
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

    private fun source(relativePath: String): String {
        val projectDir = File(requireNotNull(System.getProperty("user.dir")))
        val sourceFile = sequenceOf(
            File(projectDir, relativePath),
            File(projectDir.parentFile ?: projectDir, relativePath)
        ).firstOrNull { it.exists() }

        assertTrue("$relativePath should exist", sourceFile != null)
        return sourceFile!!.readText()
    }

    private fun homeSource(fileName: String): String {
        return source("app/src/main/java/opensource/cached_dupe_scanner/ui/home/$fileName")
    }
}
