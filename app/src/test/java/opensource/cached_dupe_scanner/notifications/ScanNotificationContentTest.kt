package opensource.cached_dupe_scanner.notifications

import opensource.cached_dupe_scanner.engine.ScanPhase
import org.junit.Assert.assertEquals
import org.junit.Test

class ScanNotificationContentTest {
    @Test
    fun buildContentUsesPhaseAndProgress() {
        val content = buildScanNotificationContent(
            phase = ScanPhase.Hashing,
            scanned = 4,
            total = 10,
            targetPath = "/storage/emulated/0/DCIM",
            currentPath = "/storage/emulated/0/DCIM/IMG_0001.jpg"
        )

        assertEquals("Scanning files", content.title)
        assertEquals("Hashing • 4/10 • DCIM", content.text)
        assertEquals("IMG_0001.jpg", content.subText)
    }

    @Test
    fun buildContentFallsBackToAllTargets() {
        val content = buildScanNotificationContent(
            phase = ScanPhase.Collecting,
            scanned = 0,
            total = null,
            targetPath = null,
            currentPath = null
        )

        assertEquals("Scanning files", content.title)
        assertEquals("Collecting files • 0/? • All targets", content.text)
        assertEquals(null, content.subText)
    }
}
