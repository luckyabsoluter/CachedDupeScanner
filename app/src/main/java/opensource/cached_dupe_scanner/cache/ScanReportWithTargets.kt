package opensource.cached_dupe_scanner.cache

import androidx.room.Embedded
import androidx.room.Relation

data class ScanReportWithTargets(
    @Embedded
    val report: ScanReportEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "reportId"
    )
    val targets: List<ScanReportTargetEntity>
)