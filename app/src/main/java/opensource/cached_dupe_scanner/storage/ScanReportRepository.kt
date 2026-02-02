package opensource.cached_dupe_scanner.storage

import opensource.cached_dupe_scanner.cache.ScanReportDao
import opensource.cached_dupe_scanner.cache.ScanReportEntity

class ScanReportRepository(
    private val dao: ScanReportDao
) {
    suspend fun add(report: ScanReport) {
        dao.upsert(report.toEntity())
    }

    suspend fun loadAll(): List<ScanReport> {
        return dao.getAll().map { it.toModel() }
    }

    suspend fun loadById(id: String): ScanReport? {
        return dao.getById(id)?.toModel()
    }

    suspend fun clearAll() {
        dao.clearAll()
    }
}

private fun ScanReport.toEntity(): ScanReportEntity {
    return ScanReportEntity(
        id = id,
        startedAtMillis = startedAtMillis,
        finishedAtMillis = finishedAtMillis,
        targetsText = targets.joinToString("\n"),
        mode = mode,
        cancelled = cancelled,
        collectedCount = totals.collectedCount,
        detectedCount = totals.detectedCount,
        hashCandidates = totals.hashCandidates,
        hashesComputed = totals.hashesComputed,
        collectingMillis = durations.collectingMillis,
        detectingMillis = durations.detectingMillis,
        hashingMillis = durations.hashingMillis
    )
}

private fun ScanReportEntity.toModel(): ScanReport {
    val targets = if (targetsText.isBlank()) {
        emptyList()
    } else {
        targetsText.split("\n")
    }
    return ScanReport(
        id = id,
        startedAtMillis = startedAtMillis,
        finishedAtMillis = finishedAtMillis,
        targets = targets,
        mode = mode,
        cancelled = cancelled,
        totals = ScanReportTotals(
            collectedCount = collectedCount,
            detectedCount = detectedCount,
            hashCandidates = hashCandidates,
            hashesComputed = hashesComputed
        ),
        durations = ScanReportDurations(
            collectingMillis = collectingMillis,
            detectingMillis = detectingMillis,
            hashingMillis = hashingMillis
        )
    )
}