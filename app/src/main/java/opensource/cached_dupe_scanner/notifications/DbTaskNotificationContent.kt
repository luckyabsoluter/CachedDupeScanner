package opensource.cached_dupe_scanner.notifications

import java.io.File
import opensource.cached_dupe_scanner.storage.DbMaintenanceProgress

enum class DbTaskNotificationKind {
    RebuildGroups,
    Maintenance,
    ClearAll
}

data class DbTaskNotificationContent(
    val title: String,
    val text: String,
    val subText: String?
)

fun buildDbTaskStartedNotificationContent(kind: DbTaskNotificationKind): DbTaskNotificationContent {
    return when (kind) {
        DbTaskNotificationKind.RebuildGroups -> DbTaskNotificationContent(
            title = "Rebuilding groups",
            text = "Refreshing the duplicate group snapshot.",
            subText = null
        )
        DbTaskNotificationKind.Maintenance -> DbTaskNotificationContent(
            title = "DB maintenance",
            text = "Checking cached files and hashes.",
            subText = null
        )
        DbTaskNotificationKind.ClearAll -> DbTaskNotificationContent(
            title = "Clearing cached results",
            text = "Removing cached files and duplicate groups.",
            subText = null
        )
    }
}

fun buildDbMaintenanceNotificationContent(progress: DbMaintenanceProgress): DbTaskNotificationContent {
    val totalText = if (progress.total > 0) progress.total.toString() else "?"
    val currentName = progress.currentPath
        ?.let { File(it).name.ifBlank { it } }
    return DbTaskNotificationContent(
        title = "DB maintenance",
        text = "Processed ${progress.processed}/$totalText • Deleted ${progress.deleted} • Rehashed ${progress.rehashed} • Missing hash ${progress.missingHashed}",
        subText = currentName
    )
}
