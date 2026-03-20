package opensource.cached_dupe_scanner.cache

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "scan_reports",
    indices = [
        Index(value = ["startedAtMillis"], name = "index_scan_reports_startedAtMillis")
    ]
)
/**
 * Immutable summary row for one scan execution.
 *
 * Stores timing and count metrics displayed in the Reports UI.
 */
data class ScanReportEntity(
    @PrimaryKey
    val id: String,
    val startedAtMillis: Long,
    val finishedAtMillis: Long,
    val targetsText: String,
    val mode: String,
    val cancelled: Boolean,
    val collectedCount: Int,
    val detectedCount: Int,
    val hashCandidates: Int,
    val hashesComputed: Int,
    val collectingMillis: Long,
    val detectingMillis: Long,
    val hashingMillis: Long
)
