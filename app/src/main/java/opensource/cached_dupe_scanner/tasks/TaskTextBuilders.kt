package opensource.cached_dupe_scanner.tasks

import java.io.File
import opensource.cached_dupe_scanner.core.ScanResult
import opensource.cached_dupe_scanner.engine.ScanPhase
import opensource.cached_dupe_scanner.storage.ClearCacheProgress
import opensource.cached_dupe_scanner.storage.ClearCacheSummary
import opensource.cached_dupe_scanner.storage.DbMaintenanceProgress
import opensource.cached_dupe_scanner.storage.DbMaintenanceSummary
import opensource.cached_dupe_scanner.storage.RebuildGroupsPhase
import opensource.cached_dupe_scanner.storage.RebuildGroupsProgress
import opensource.cached_dupe_scanner.storage.RebuildGroupsSummary
import opensource.cached_dupe_scanner.storage.SimilarityClearMode
import opensource.cached_dupe_scanner.storage.SimilarityClearPhase
import opensource.cached_dupe_scanner.storage.SimilarityClearProgress
import opensource.cached_dupe_scanner.storage.SimilarityClearSummary
import opensource.cached_dupe_scanner.storage.SimilarityMaintenanceProgress
import opensource.cached_dupe_scanner.storage.SimilarityMaintenanceSummary
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

fun scanSimilarityTaskDetail(progress: SimilarityMaintenanceProgress): String {
    val totalText = if (progress.total > 0) progress.total.toString() else "?"
    val setting = progress.settingName?.let { " • $it" } ?: ""
    return "Generating similarity • ${progress.processed}/$totalText • Cluster candidates ${progress.clusterCandidates} • Skipped ${progress.skipped}$setting"
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
    return when (progress.phase) {
        RebuildGroupsPhase.RepairingMissingHashes ->
            "Repairing missing hashes ${progress.processed}/$totalText before rebuilding groups."
        RebuildGroupsPhase.RebuildingGroups ->
            "Processed ${progress.processed}/$totalText duplicate groups."
    }
}

fun rebuildGroupsCompletedDetail(summary: RebuildGroupsSummary): String {
    val totalText = if (summary.total > 0) summary.total.toString() else "?"
    return "Processed ${summary.processed}/$totalText duplicate groups."
}

fun rebuildGroupsCancelledDetail(summary: RebuildGroupsSummary): String {
    val totalText = if (summary.total > 0) summary.total.toString() else "?"
    return when (summary.phase) {
        RebuildGroupsPhase.RepairingMissingHashes ->
            "Cancelled after repairing ${summary.processed}/$totalText missing hashes."
        RebuildGroupsPhase.RebuildingGroups ->
            "Cancelled after ${summary.processed}/$totalText duplicate groups."
    }
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

fun bulkDeleteTaskTitle(): String = "Bulk deleting files"

fun bulkDeleteTaskDetail(processed: Int, total: Int, failed: Int): String {
    val totalText = if (total > 0) total.toString() else "?"
    return "Processed $processed/$totalText • Failed $failed"
}

fun bulkDeleteCompletedDetail(successCount: Int, failedCount: Int): String {
    return "Deleted $successCount • Failed $failedCount"
}

fun similarityGenerationTaskTitle(rebuild: Boolean): String {
    return if (rebuild) "Rebuilding similarity" else "Updating similarity"
}

fun similarityGenerationTaskDetail(progress: SimilarityMaintenanceProgress): String {
    val totalText = if (progress.total > 0) progress.total.toString() else "?"
    val setting = progress.settingName?.let { " • $it" } ?: ""
    return "Processed ${progress.processed}/$totalText • Cluster candidates ${progress.clusterCandidates} • Skipped ${progress.skipped}$setting"
}

fun similarityGenerationCompletedDetail(summary: SimilarityMaintenanceSummary): String {
    return "Clusters ${summary.clusterCount} • Files ${summary.duplicateFileCount} • Skipped ${summary.skippedCount}"
}

fun similarityGenerationCancelledDetail(summary: SimilarityMaintenanceSummary): String {
    val totalText = if (summary.candidateCount > 0) summary.candidateCount.toString() else "?"
    return "Cancelled after ${summary.processedCount}/$totalText candidates."
}

fun similarityClearTaskTitle(mode: SimilarityClearMode): String {
    return when (mode) {
        SimilarityClearMode.Standard -> "Clearing similarity results"
        SimilarityClearMode.Incremental -> "Incrementally clearing similarity"
    }
}

fun similarityClearTaskDetail(progress: SimilarityClearProgress): String {
    val phase = when (progress.phase) {
        SimilarityClearPhase.Preparing -> "Preparing clear"
        SimilarityClearPhase.ClusterMembers -> "Clearing cluster members"
        SimilarityClearPhase.Clusters -> "Clearing clusters"
        SimilarityClearPhase.FilesAndFeatures -> "Clearing files and features"
        SimilarityClearPhase.OrphanFeatures -> "Clearing remaining features"
        SimilarityClearPhase.History -> "Clearing maintenance history"
    }
    val totalText = if (progress.total > 0) progress.total.toString() else "?"
    return "$phase • ${progress.processed}/$totalText • Remaining ${progress.remaining}"
}

fun similarityClearCompletedDetail(summary: SimilarityClearSummary): String {
    return "Cleared ${summary.processed} generated rows."
}

fun similarityClearCancelledDetail(summary: SimilarityClearSummary): String {
    val totalText = if (summary.total > 0) summary.total.toString() else "?"
    return "Stopped after ${summary.processed}/$totalText • Remaining ${summary.remaining}"
}
