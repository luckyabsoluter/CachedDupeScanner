package opensource.cached_dupe_scanner.notifications

import opensource.cached_dupe_scanner.tasks.TaskArea

internal fun notificationIdFor(area: TaskArea): Int {
    return when (area) {
        TaskArea.Scan -> 1001
        TaskArea.Db -> 1002
        TaskArea.Trash -> 1003
    }
}
