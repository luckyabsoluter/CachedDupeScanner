package opensource.cached_dupe_scanner.ui.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ScrollbarSourceTest {
    @Test
    fun verticalScrollbarsUseStablePointerInputKeys() {
        val projectDir = File(requireNotNull(System.getProperty("user.dir")))
        val scrollbarFile = sequenceOf(
            File(projectDir, "app/src/main/java/opensource/cached_dupe_scanner/ui/components/Scrollbar.kt"),
            File(projectDir.parentFile ?: projectDir, "app/src/main/java/opensource/cached_dupe_scanner/ui/components/Scrollbar.kt")
        ).firstOrNull { it.exists() }

        assertTrue("Scrollbar.kt should exist", scrollbarFile != null)

        val content = scrollbarFile!!.readText()

        assertTrue(
            "VerticalScrollbar should keep pointerInput stable with Unit key",
            content.contains(".pointerInput(Unit)")
        )
        assertFalse(
            "Scrollbar pointerInput should not restart on changing drag metrics",
            content.contains(".pointerInput(maxScrollPx, viewportHeightPx, thumbHeightPx)")
        )
        assertFalse(
            "Lazy scrollbar pointerInput should not restart on changing drag metrics",
            content.contains(".pointerInput(maxScrollPxEstimate, trackHeightPx, thumbHeightPx)")
        )
        assertTrue(
            "Scrollbar drag handlers should read updated drag snapshots without restarting pointerInput",
            content.contains("rememberUpdatedState(")
        )
    }
}
