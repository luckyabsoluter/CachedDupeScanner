package opensource.cached_dupe_scanner.storage

data class ScanReport(
    val id: String,
    val startedAtMillis: Long,
    val finishedAtMillis: Long,
    val targets: List<String>,
    val mode: String,
    val cancelled: Boolean,
    val totals: ScanReportTotals,
    val durations: ScanReportDurations
)

data class ScanReportTotals(
    val collectedCount: Int,
    val detectedCount: Int,
    val hashCandidates: Int,
    val hashesComputed: Int
)

data class ScanReportDurations(
    val collectingMillis: Long,
    val detectingMillis: Long,
    val hashingMillis: Long
)
