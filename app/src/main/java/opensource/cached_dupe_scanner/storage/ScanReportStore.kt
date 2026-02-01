package opensource.cached_dupe_scanner.storage

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

class ScanReportStore(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun add(report: ScanReport) {
        val list = loadAll().toMutableList()
        list.add(0, report)
        saveAll(list)
    }

    fun loadAll(): List<ScanReport> {
        val raw = prefs.getString(KEY_REPORTS, null) ?: return emptyList()
        return runCatching { fromJsonArray(JSONArray(raw)) }.getOrDefault(emptyList())
    }

    private fun saveAll(reports: List<ScanReport>) {
        val array = JSONArray()
        reports.forEach { array.put(toJson(it)) }
        prefs.edit().putString(KEY_REPORTS, array.toString()).apply()
    }

    private fun fromJsonArray(array: JSONArray): List<ScanReport> {
        val reports = mutableListOf<ScanReport>()
        for (i in 0 until array.length()) {
            reports.add(fromJson(array.getJSONObject(i)))
        }
        return reports
    }

    private fun toJson(report: ScanReport): JSONObject {
        return JSONObject().apply {
            put("id", report.id)
            put("startedAtMillis", report.startedAtMillis)
            put("finishedAtMillis", report.finishedAtMillis)
            put("mode", report.mode)
            put("cancelled", report.cancelled)
            put("targets", JSONArray(report.targets))
            put("totals", JSONObject().apply {
                put("collectedCount", report.totals.collectedCount)
                put("detectedCount", report.totals.detectedCount)
                put("hashCandidates", report.totals.hashCandidates)
                put("hashesComputed", report.totals.hashesComputed)
            })
            put("durations", JSONObject().apply {
                put("collectingMillis", report.durations.collectingMillis)
                put("detectingMillis", report.durations.detectingMillis)
                put("hashingMillis", report.durations.hashingMillis)
            })
        }
    }

    private fun fromJson(json: JSONObject): ScanReport {
        val totals = json.getJSONObject("totals")
        val durations = json.getJSONObject("durations")
        val targetsArray = json.optJSONArray("targets") ?: JSONArray()
        val targets = (0 until targetsArray.length()).map { idx -> targetsArray.getString(idx) }

        return ScanReport(
            id = json.getString("id"),
            startedAtMillis = json.getLong("startedAtMillis"),
            finishedAtMillis = json.getLong("finishedAtMillis"),
            targets = targets,
            mode = json.getString("mode"),
            cancelled = json.getBoolean("cancelled"),
            totals = ScanReportTotals(
                collectedCount = totals.getInt("collectedCount"),
                detectedCount = totals.getInt("detectedCount"),
                hashCandidates = totals.getInt("hashCandidates"),
                hashesComputed = totals.getInt("hashesComputed")
            ),
            durations = ScanReportDurations(
                collectingMillis = durations.getLong("collectingMillis"),
                detectingMillis = durations.getLong("detectingMillis"),
                hashingMillis = durations.getLong("hashingMillis")
            )
        )
    }

    companion object {
        private const val PREFS_NAME = "cached_dupe_scanner"
        private const val KEY_REPORTS = "scan_reports"
    }
}
