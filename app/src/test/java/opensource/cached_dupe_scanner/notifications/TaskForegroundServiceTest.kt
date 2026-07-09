package opensource.cached_dupe_scanner.notifications

import android.app.Application
import opensource.cached_dupe_scanner.tasks.TaskArea
import opensource.cached_dupe_scanner.tasks.TaskKind
import opensource.cached_dupe_scanner.tasks.TaskSnapshot
import opensource.cached_dupe_scanner.tasks.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TaskForegroundServiceTest {
    @Test
    fun everyTaskAreaStartsForegroundServiceWithItsNotificationSlot() {
        val application = RuntimeEnvironment.getApplication() as Application

        TaskArea.entries.forEach { area ->
            TaskForegroundService.show(application, taskSnapshot(area))
            val startIntent = shadowOf(application).nextStartedService
            assertNotNull(startIntent)
            assertEquals(TaskForegroundService::class.java.name, startIntent.component?.className)
            assertEquals(area.name, startIntent.getStringExtra("area"))

            val serviceController = Robolectric.buildService(TaskForegroundService::class.java).create()
            val service = serviceController.get()
            service.onStartCommand(startIntent, 0, 1)

            assertEquals(
                notificationIdFor(area),
                shadowOf(service).lastForegroundNotificationId
            )
            serviceController.destroy()
        }
    }

    private fun taskSnapshot(area: TaskArea): TaskSnapshot {
        return TaskSnapshot(
            area = area,
            kind = when (area) {
                TaskArea.Scan -> TaskKind.ScanTarget
                TaskArea.Db -> TaskKind.DbMaintenance
                TaskArea.Trash -> TaskKind.EmptyTrash
                TaskArea.Similarity -> TaskKind.SimilarityGeneration
            },
            title = "Task ${area.name}",
            detail = "Running",
            currentPath = null,
            processed = 1,
            total = 2,
            indeterminate = false,
            startedAt = 1L,
            isCancellable = true,
            status = TaskStatus.Running
        )
    }
}
