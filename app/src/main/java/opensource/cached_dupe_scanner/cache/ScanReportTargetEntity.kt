package opensource.cached_dupe_scanner.cache

import androidx.room.Entity

@Entity(tableName = "scan_report_targets", primaryKeys = ["reportId", "position"])
data class ScanReportTargetEntity(
    val reportId: String,
    val position: Int,
    val target: String
)