package opensource.cached_dupe_scanner.notifications

import opensource.cached_dupe_scanner.tasks.TaskArea
import opensource.cached_dupe_scanner.tasks.TaskKind
import opensource.cached_dupe_scanner.tasks.TaskSnapshot
import opensource.cached_dupe_scanner.tasks.TaskStatus
import opensource.cached_dupe_scanner.tasks.TaskTerminalSummary
import org.junit.Assert.assertEquals
import org.junit.Test

class TaskNotificationContentTest {
    @Test
    fun buildTaskNotificationContentUsesSnapshotValues() {
        val content = buildTaskNotificationContent(
            TaskSnapshot(
                area = TaskArea.Scan,
                kind = TaskKind.ScanTarget,
                title = "Scanning files",
                detail = "Hashing • 4/10 • DCIM",
                currentPath = "/storage/emulated/0/DCIM/IMG_0001.jpg",
                processed = 4,
                total = 10,
                indeterminate = false,
                startedAt = 1L,
                isCancellable = true,
                status = TaskStatus.Running
            )
        )

        assertEquals("Scanning files", content.title)
        assertEquals("Hashing • 4/10 • DCIM", content.text)
        assertEquals("IMG_0001.jpg", content.subText)
    }

    @Test
    fun buildTaskTerminalNotificationContentUsesSummaryValues() {
        val content = buildTaskTerminalNotificationContent(
            TaskTerminalSummary(
                area = TaskArea.Trash,
                kind = TaskKind.EmptyTrash,
                title = "Trash empty complete",
                detail = "Deleted 12 • Failed 1",
                currentPath = "/storage/emulated/0/Download/sample.bin",
                processed = 13,
                total = 13,
                indeterminate = false,
                startedAt = 1L,
                finishedAt = 2L,
                status = TaskStatus.Completed
            )
        )

        assertEquals("Trash empty complete", content.title)
        assertEquals("Deleted 12 • Failed 1", content.text)
        assertEquals("sample.bin", content.subText)
    }
}
