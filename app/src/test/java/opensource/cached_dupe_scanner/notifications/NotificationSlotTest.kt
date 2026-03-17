package opensource.cached_dupe_scanner.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class NotificationSlotTest {
    @Test
    fun scanAndDbTasksUseDifferentNotificationIds() {
        val scanId = notificationIdFor(NotificationSlot.Scan)
        val dbTaskId = notificationIdFor(NotificationSlot.DbTask)

        assertEquals(1001, scanId)
        assertEquals(1002, dbTaskId)
        assertNotEquals(scanId, dbTaskId)
    }
}
