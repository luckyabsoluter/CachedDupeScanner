package opensource.cached_dupe_scanner.ui.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import opensource.cached_dupe_scanner.storage.DbMaintenanceProgress
import opensource.cached_dupe_scanner.storage.DbMaintenanceSummary
import opensource.cached_dupe_scanner.storage.ClearCacheSummary
import opensource.cached_dupe_scanner.storage.RebuildGroupsSummary

class DbManagementUiState {
    var isRunning by mutableStateOf(false)
        private set
    var isRebuilding by mutableStateOf(false)
        private set
    var isClearing by mutableStateOf(false)
        private set

    var maintenanceStatusMessage by mutableStateOf<String?>(null)
        private set
    var groupStatusMessage by mutableStateOf<String?>(null)
        private set

    var dbCount by mutableStateOf<Int?>(null)
        private set
    var groupCount by mutableStateOf<Int?>(null)
        private set

    var progressTotal by mutableIntStateOf(0)
        private set
    var progressProcessed by mutableIntStateOf(0)
        private set
    var progressDeleted by mutableIntStateOf(0)
        private set
    var progressRehashed by mutableIntStateOf(0)
        private set
    var progressMissingHashed by mutableIntStateOf(0)
        private set
    var progressCurrentPath by mutableStateOf<String?>(null)
        private set

    fun updateOverview(dbCount: Int, groupCount: Int) {
        this.dbCount = dbCount
        this.groupCount = groupCount
    }

    fun startRebuild() {
        isRebuilding = true
        groupStatusMessage = "Rebuilding duplicate groups..."
    }

    fun completeRebuild() {
        isRebuilding = false
        groupStatusMessage = "Duplicate groups rebuilt."
    }

    fun failRebuild() {
        isRebuilding = false
        groupStatusMessage = "Failed to rebuild duplicate groups."
    }

    fun cancelRebuild(summary: RebuildGroupsSummary) {
        isRebuilding = false
        groupStatusMessage =
            "Duplicate group rebuild cancelled after ${summary.processed}/${summary.total} groups."
    }

    fun startMaintenance() {
        isRunning = true
        progressTotal = 0
        progressProcessed = 0
        progressDeleted = 0
        progressRehashed = 0
        progressMissingHashed = 0
        progressCurrentPath = null
    }

    fun applyMaintenanceProgress(progress: DbMaintenanceProgress) {
        progressTotal = progress.total
        progressProcessed = progress.processed
        progressDeleted = progress.deleted
        progressRehashed = progress.rehashed
        progressMissingHashed = progress.missingHashed
        progressCurrentPath = progress.currentPath
    }

    fun completeMaintenance(summary: DbMaintenanceSummary) {
        isRunning = false
        maintenanceStatusMessage =
            "Maintenance complete. Deleted ${summary.deleted}, rehashed ${summary.rehashed}, missing hashes ${summary.missingHashed}."
        applyMaintenanceProgress(
            DbMaintenanceProgress(
                total = summary.total,
                processed = summary.processed,
                deleted = summary.deleted,
                rehashed = summary.rehashed,
                missingHashed = summary.missingHashed,
                currentPath = summary.currentPath
            )
        )
    }

    fun failMaintenance() {
        isRunning = false
        maintenanceStatusMessage = "Failed to run maintenance."
    }

    fun cancelMaintenance(summary: DbMaintenanceSummary) {
        isRunning = false
        maintenanceStatusMessage =
            "Maintenance cancelled after ${summary.processed}/${summary.total} items."
        applyMaintenanceProgress(
            DbMaintenanceProgress(
                total = summary.total,
                processed = summary.processed,
                deleted = summary.deleted,
                rehashed = summary.rehashed,
                missingHashed = summary.missingHashed,
                currentPath = summary.currentPath
            )
        )
    }

    fun startClearing() {
        isClearing = true
        maintenanceStatusMessage = "Clearing all cached results..."
    }

    fun completeClearing() {
        isClearing = false
        maintenanceStatusMessage = "Cleared all cached results."
        progressTotal = 0
        progressProcessed = 0
        progressDeleted = 0
        progressRehashed = 0
        progressMissingHashed = 0
        progressCurrentPath = null
    }

    fun failClearing() {
        isClearing = false
        maintenanceStatusMessage = "Failed to clear cached results."
    }

    fun cancelClearing(summary: ClearCacheSummary) {
        isClearing = false
        maintenanceStatusMessage =
            "Clear cached results cancelled after ${summary.processed}/${summary.total} items."
    }
}
