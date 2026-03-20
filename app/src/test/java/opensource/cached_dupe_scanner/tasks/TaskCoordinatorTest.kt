package opensource.cached_dupe_scanner.tasks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskCoordinatorTest {
    @Test
    fun rejectsSecondTaskInSameArea() {
        val coordinator = TaskCoordinator()

        val first = coordinator.tryStart(
            area = TaskArea.Scan,
            kind = TaskKind.ScanTarget,
            title = "Scan",
            detail = "Starting"
        )
        val second = coordinator.tryStart(
            area = TaskArea.Scan,
            kind = TaskKind.ScanAll,
            title = "Scan all",
            detail = "Starting"
        )

        assertNotNull(first)
        assertNull(second)
        assertTrue(coordinator.isAreaBusy(TaskArea.Scan))
    }

    @Test
    fun allowsTasksInDifferentAreasAndKeepsLatestFirst() {
        val coordinator = TaskCoordinator()

        val scan = coordinator.tryStart(
            area = TaskArea.Scan,
            kind = TaskKind.ScanTarget,
            title = "Scan",
            detail = "Starting"
        )
        val trash = coordinator.tryStart(
            area = TaskArea.Trash,
            kind = TaskKind.EmptyTrash,
            title = "Trash",
            detail = "Starting"
        )

        assertNotNull(scan)
        assertNotNull(trash)
        assertEquals(listOf(TaskArea.Trash, TaskArea.Scan), coordinator.activeTasks.map { it.area })
    }

    @Test
    fun updatesCompletesAndStoresTerminalSummary() {
        val coordinator = TaskCoordinator()
        coordinator.tryStart(
            area = TaskArea.Db,
            kind = TaskKind.DbMaintenance,
            title = "DB maintenance",
            detail = "Preparing"
        )

        val updated = coordinator.update(TaskArea.Db) {
            it.copy(detail = "Processed 5/10", processed = 5, total = 10, indeterminate = false)
        }
        val summary = coordinator.complete(
            area = TaskArea.Db,
            title = "DB maintenance complete",
            detail = "Deleted 1 • Rehashed 2",
            processed = 10,
            total = 10,
            indeterminate = false
        )

        assertEquals("Processed 5/10", updated?.detail)
        assertFalse(coordinator.isAreaBusy(TaskArea.Db))
        assertEquals("DB maintenance complete", summary?.title)
        assertEquals(TaskStatus.Completed, coordinator.terminalSummary(TaskArea.Db)?.status)
    }

    @Test
    fun requestCancelInvokesRegisteredCallback() {
        val coordinator = TaskCoordinator()
        var cancelled = false
        coordinator.tryStart(
            area = TaskArea.Trash,
            kind = TaskKind.EmptyTrash,
            title = "Emptying trash",
            detail = "Starting",
            isCancellable = true,
            onCancel = { cancelled = true }
        )

        val requested = coordinator.requestCancel(TaskArea.Trash)

        assertTrue(requested)
        assertTrue(cancelled)
    }
}
