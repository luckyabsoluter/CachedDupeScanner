package opensource.cached_dupe_scanner.ui.home

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DbManagementScreenSourceTest {
    @Test
    fun dbManagementScreenUsesLazyListScrollbarPattern() {
        val projectDir = File(System.getProperty("user.dir") ?: ".")
        val sourceFile = sequenceOf(
            File(projectDir, "app/src/main/java/opensource/cached_dupe_scanner/ui/home/DbManagementScreen.kt"),
            File(projectDir.parentFile ?: projectDir, "app/src/main/java/opensource/cached_dupe_scanner/ui/home/DbManagementScreen.kt")
        ).firstOrNull { it.exists() }

        val source = sourceFile ?: error("DbManagementScreen.kt should exist")
        val content = source.readText()

        assertTrue(content.contains("rememberLazyListState()"))
        assertTrue(content.contains("LazyColumn("))
        assertTrue(content.contains("VerticalLazyScrollbar("))
        assertFalse(content.contains(".verticalScroll("))
        assertFalse(content.contains("VerticalScrollbar("))
    }
}
