package opensource.cached_dupe_scanner

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import opensource.cached_dupe_scanner.notifications.TaskNotificationController
import opensource.cached_dupe_scanner.tasks.TaskCoordinator

object AppWorkScopes {
    val scanScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val taskScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @Volatile
    private var sharedTaskCoordinator: TaskCoordinator? = null

    @Volatile
    private var sharedNotificationController: TaskNotificationController? = null

    fun taskCoordinator(context: Context): TaskCoordinator {
        return sharedTaskCoordinator ?: synchronized(this) {
            sharedTaskCoordinator ?: TaskCoordinator(context.applicationContext).also { coordinator ->
                sharedTaskCoordinator = coordinator
            }
        }
    }

    fun notificationController(context: Context): TaskNotificationController {
        return sharedNotificationController ?: synchronized(this) {
            sharedNotificationController ?: TaskNotificationController(context.applicationContext).also { controller ->
                sharedNotificationController = controller
            }
        }
    }
}
