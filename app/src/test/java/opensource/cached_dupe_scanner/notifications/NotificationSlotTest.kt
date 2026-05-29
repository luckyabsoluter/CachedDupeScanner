package opensource.cached_dupe_scanner.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import opensource.cached_dupe_scanner.tasks.TaskArea

class NotificationSlotTest {
    @Test
    fun taskAreasUseDifferentNotificationIds() {
        val scanId = notificationIdFor(TaskArea.Scan)
        val dbTaskId = notificationIdFor(TaskArea.Db)
        val trashId = notificationIdFor(TaskArea.Trash)
        val similarityId = notificationIdFor(TaskArea.Similarity)

        assertEquals(1001, scanId)
        assertEquals(1002, dbTaskId)
        assertEquals(1003, trashId)
        assertEquals(1004, similarityId)
        assertNotEquals(scanId, dbTaskId)
        assertNotEquals(dbTaskId, trashId)
        assertNotEquals(trashId, similarityId)
    }
}
