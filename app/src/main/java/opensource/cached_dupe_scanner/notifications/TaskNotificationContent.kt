package opensource.cached_dupe_scanner.notifications

import java.io.File
import opensource.cached_dupe_scanner.tasks.TaskSnapshot
import opensource.cached_dupe_scanner.tasks.TaskTerminalSummary

data class TaskNotificationContent(
    val title: String,
    val text: String,
    val subText: String?
)

fun buildTaskNotificationContent(snapshot: TaskSnapshot): TaskNotificationContent {
    return TaskNotificationContent(
        title = snapshot.title,
        text = snapshot.detail,
        subText = snapshot.currentPath
            ?.let { File(it).name.ifBlank { it } }
    )
}

fun buildTaskTerminalNotificationContent(summary: TaskTerminalSummary): TaskNotificationContent {
    return TaskNotificationContent(
        title = summary.title,
        text = summary.detail,
        subText = summary.currentPath
            ?.let { File(it).name.ifBlank { it } }
    )
}
