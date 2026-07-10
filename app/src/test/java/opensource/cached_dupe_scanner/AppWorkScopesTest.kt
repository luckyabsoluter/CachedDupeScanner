package opensource.cached_dupe_scanner

import android.app.Activity
import opensource.cached_dupe_scanner.tasks.TaskArea
import opensource.cached_dupe_scanner.tasks.TaskKind
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppWorkScopesTest {
    @Test
    fun taskCoordinatorAndActiveTaskSurviveActivityRecreation() {
        val firstActivity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val firstCoordinator = AppWorkScopes.taskCoordinator(firstActivity)
        firstCoordinator.tryStart(
            area = TaskArea.Db,
            kind = TaskKind.DbMaintenance,
            title = "Database maintenance",
            detail = "Running"
        )
        firstActivity.finish()

        val secondActivity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val secondCoordinator = AppWorkScopes.taskCoordinator(secondActivity)

        assertSame(firstCoordinator, secondCoordinator)
        assertTrue(secondCoordinator.isAreaBusy(TaskArea.Db))

        secondCoordinator.complete(
            area = TaskArea.Db,
            title = "Database maintenance complete",
            detail = "Done"
        )
        secondActivity.finish()
    }

    @Test
    fun notificationControllerIsSharedAcrossActivityContexts() {
        val firstActivity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val secondActivity = Robolectric.buildActivity(Activity::class.java).setup().get()

        val firstController = AppWorkScopes.notificationController(firstActivity)
        val secondController = AppWorkScopes.notificationController(secondActivity)

        assertSame(firstController, secondController)

        firstActivity.finish()
        secondActivity.finish()
    }
}
