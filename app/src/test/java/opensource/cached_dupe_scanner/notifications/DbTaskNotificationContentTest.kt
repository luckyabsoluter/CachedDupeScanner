package opensource.cached_dupe_scanner.notifications

import opensource.cached_dupe_scanner.storage.DbMaintenanceProgress
import org.junit.Assert.assertEquals
import org.junit.Test

class DbTaskNotificationContentTest {
    @Test
    fun buildStartedContentUsesTaskSpecificText() {
        val content = buildDbTaskStartedNotificationContent(DbTaskNotificationKind.ClearAll)

        assertEquals("Clearing cached results", content.title)
        assertEquals("Removing cached files and duplicate groups.", content.text)
        assertEquals(null, content.subText)
    }

    @Test
    fun buildMaintenanceContentIncludesCountsAndCurrentFileName() {
        val content = buildDbMaintenanceNotificationContent(
            DbMaintenanceProgress(
                total = 120,
                processed = 40,
                deleted = 2,
                rehashed = 5,
                missingHashed = 1,
                currentPath = "/storage/emulated/0/DCIM/IMG_2048.jpg"
            )
        )

        assertEquals("DB maintenance", content.title)
        assertEquals("Processed 40/120 • Deleted 2 • Rehashed 5 • Missing hash 1", content.text)
        assertEquals("IMG_2048.jpg", content.subText)
    }
}
