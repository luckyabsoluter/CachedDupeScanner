package opensource.cached_dupe_scanner.tasks

import java.io.File
import opensource.cached_dupe_scanner.core.ScanResult
import opensource.cached_dupe_scanner.engine.ScanPhase
import opensource.cached_dupe_scanner.storage.ClearCacheProgress
import opensource.cached_dupe_scanner.storage.ClearCacheSummary
import opensource.cached_dupe_scanner.storage.DbMaintenanceProgress
import opensource.cached_dupe_scanner.storage.DbMaintenanceSummary
import opensource.cached_dupe_scanner.storage.RebuildGroupsProgress
import opensource.cached_dupe_scanner.storage.RebuildGroupsSummary
import opensource.cached_dupe_scanner.storage.TrashProgress
import opensource.cached_dupe_scanner.storage.TrashRunSummary

fun scanTaskTitle(): String = "Scanning files"

fun scanTaskDetail(
    phase: ScanPhase,
    scanned: Int,
    total: Int?,
    targetPath: String?
): String {
    val phaseText = when (phase) {
        ScanPhase.Collecting -> "Collecting files"
        ScanPhase.Detecting -> "Detecting hash candidates"
        ScanPhase.Hashing -> "Hashing"
        ScanPhase.Saving -> "Saving cache"
    }
    val totalText = total?.toString() ?: "?"
    val targetName = when {
        targetPath.isNullOrBlank() -> "All targets"
        else -> File(targetPath).name.ifBlank { targetPath }
    }
    return "$phaseText • $scanned/$totalText • $targetName"
}

fun scanTaskCompletedDetail(result: ScanResult): String {
    return "Files: ${result.files.size} • Groups: ${result.duplicateGroups.size}"
}

fun scanTaskCancelledDetail(processed: Int?, total: Int?): String {
    val processedText = processed ?: 0
    val totalText = total?.toString() ?: "?"
    return "Cancelled after $processedText/$totalText."
}

fun dbMaintenanceTaskTitle(): String = "DB maintenance"

fun dbMaintenanceTaskDetail(progress: DbMaintenanceProgress): String {
    val totalText = if (progress.total > 0) progress.total.toString() else "?"
    return "Processed ${progress.processed}/$totalText • Deleted ${progress.deleted} • Rehashed ${progress.rehashed} • Missing hash ${progress.missingHashed}"
}

fun dbMaintenanceCompletedDetail(summary: DbMaintenanceSummary): String {
    return "Deleted ${summary.deleted} • Rehashed ${summary.rehashed} • Missing hash ${summary.missingHashed}"
}

fun rebuildGroupsTaskTitle(): String = "Rebuilding groups"

fun rebuildGroupsTaskDetail(progress: RebuildGroupsProgress): String {
    val totalText = if (progress.total > 0) progress.total.toString() else "?"
    return "Processed ${progress.processed}/$totalText duplicate groups."
}

fun rebuildGroupsCompletedDetail(summary: RebuildGroupsSummary): String {
    val totalText = if (summary.total > 0) summary.total.toString() else "?"
    return "Processed ${summary.processed}/$totalText duplicate groups."
}

fun clearCacheTaskTitle(): String = "Clearing cached results"

fun clearCacheTaskDetail(progress: ClearCacheProgress): String {
    val totalText = if (progress.total > 0) progress.total.toString() else "?"
    return "Processed ${progress.processed}/$totalText • Files ${progress.clearedFiles} • Groups ${progress.clearedGroups}"
}

fun clearCacheCompletedDetail(summary: ClearCacheSummary): String {
    return "Files ${summary.clearedFiles} • Groups ${summary.clearedGroups}"
}

fun trashTaskTitle(): String = "Emptying trash"

fun trashTaskDetail(progress: TrashProgress): String {
    val totalText = if (progress.total > 0) progress.total.toString() else "?"
    return "Processed ${progress.processed}/$totalText • Deleted ${progress.deleted} • Failed ${progress.failed}"
}

fun trashTaskCompletedDetail(summary: TrashRunSummary): String {
    return "Deleted ${summary.deleted} • Failed ${summary.failed}"
}
