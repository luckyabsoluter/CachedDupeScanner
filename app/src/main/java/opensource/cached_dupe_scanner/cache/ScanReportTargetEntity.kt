package opensource.cached_dupe_scanner.cache

import androidx.room.Entity

@Entity(tableName = "scan_report_targets", primaryKeys = ["reportId", "target"])
data class ScanReportTargetEntity(
    val reportId: String,
    val target: String
)