package opensource.cached_dupe_scanner.storage

import opensource.cached_dupe_scanner.cache.ScanReportDao
import opensource.cached_dupe_scanner.cache.ScanReportEntity
import opensource.cached_dupe_scanner.cache.ScanReportTargetEntity
import opensource.cached_dupe_scanner.cache.ScanReportWithTargets

class ScanReportRepository(
    private val dao: ScanReportDao
) {
    suspend fun add(report: ScanReport) {
        dao.upsert(report.toEntity())
        dao.deleteTargets(report.id)
        if (report.targets.isNotEmpty()) {
            val targets = report.targets.map { path ->
                ScanReportTargetEntity(reportId = report.id, target = path)
            }
            dao.upsertTargets(targets)
        }
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

private fun ScanReportWithTargets.toModel(): ScanReport {
    return ScanReport(
        id = report.id,
        startedAtMillis = report.startedAtMillis,
        finishedAtMillis = report.finishedAtMillis,
        targets = targets.map { it.target },
        mode = report.mode,
        cancelled = report.cancelled,
        totals = ScanReportTotals(
            collectedCount = report.collectedCount,
            detectedCount = report.detectedCount,
            hashCandidates = report.hashCandidates,
            hashesComputed = report.hashesComputed
        ),
        durations = ScanReportDurations(
            collectingMillis = report.collectingMillis,
            detectingMillis = report.detectingMillis,
            hashingMillis = report.hashingMillis
        )
    )
}