package opensource.cached_dupe_scanner.notifications

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TaskForegroundServiceSourceTest {
    @Test
    fun notificationControllerStartsForegroundServiceForEveryTaskArea() {
        val content = source(
            "app/src/main/java/opensource/cached_dupe_scanner/notifications/TaskNotificationController.kt"
        )

        assertTrue(
            "Active task notifications should promote every task area to the foreground service",
            content.contains("TaskForegroundService.show(appContext, effective)")
        )
        assertFalse(
            "Foreground service promotion should not be limited to scans",
            content.contains("if (effective.area == TaskArea.Scan)")
        )
    }

    @Test
    fun foregroundServiceUsesTaskAreaNotificationIds() {
        val content = source(
            "app/src/main/java/opensource/cached_dupe_scanner/notifications/TaskForegroundService.kt"
        )

        assertTrue(
            "The foreground service should carry the task area through the start intent",
            content.contains("EXTRA_AREA")
        )
        assertTrue(
            "The foreground service should start with the notification ID for the active task area",
            content.contains("startForeground(notificationIdFor(area), notification)")
        )
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
}
