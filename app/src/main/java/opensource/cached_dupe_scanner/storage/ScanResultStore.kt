package opensource.cached_dupe_scanner.storage

import android.content.Context
import android.content.SharedPreferences
import opensource.cached_dupe_scanner.core.DuplicateGroup
import opensource.cached_dupe_scanner.core.FileMetadata
import opensource.cached_dupe_scanner.core.ScanResult
import org.json.JSONArray
import org.json.JSONObject

class ScanResultStore(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun save(result: ScanResult) {
        val json = toJson(result).toString()
        prefs.edit().putString(KEY_LAST_RESULT, json).apply()
    }

    fun load(): ScanResult? {
        val raw = prefs.getString(KEY_LAST_RESULT, null) ?: return null
        return runCatching { fromJson(JSONObject(raw)) }.getOrNull()
    }

    fun clear() {
        prefs.edit().remove(KEY_LAST_RESULT).apply()
    }

    private fun toJson(result: ScanResult): JSONObject {
        val json = JSONObject()
        json.put("scannedAtMillis", result.scannedAtMillis)

        val files = JSONArray()
        result.files.forEach { files.put(fileToJson(it)) }
        json.put("files", files)

        val groups = JSONArray()
        result.duplicateGroups.forEach { groups.put(groupToJson(it)) }
        json.put("duplicateGroups", groups)

        return json
    }

    private fun fromJson(json: JSONObject): ScanResult {
        val scannedAtMillis = json.optLong("scannedAtMillis")
        val files = mutableListOf<FileMetadata>()
        val filesArray = json.optJSONArray("files") ?: JSONArray()
        for (i in 0 until filesArray.length()) {
            files.add(fileFromJson(filesArray.getJSONObject(i)))
        }

        val groups = mutableListOf<DuplicateGroup>()
        val groupsArray = json.optJSONArray("duplicateGroups") ?: JSONArray()
        for (i in 0 until groupsArray.length()) {
            groups.add(groupFromJson(groupsArray.getJSONObject(i)))
        }

        return ScanResult(scannedAtMillis, files, groups)
    }

    private fun fileToJson(file: FileMetadata): JSONObject {
        val json = JSONObject()
        json.put("path", file.path)
        json.put("normalizedPath", file.normalizedPath)
        json.put("sizeBytes", file.sizeBytes)
        json.put("lastModifiedMillis", file.lastModifiedMillis)
        if (file.hashHex != null) {
            json.put("hashHex", file.hashHex)
        } else {
            json.put("hashHex", JSONObject.NULL)
        }
        return json
    }

    private fun fileFromJson(json: JSONObject): FileMetadata {
        val hashHex = if (json.isNull("hashHex")) {
            null
        } else {
            json.optString("hashHex")
        }
        return FileMetadata(
            path = json.optString("path"),
            normalizedPath = json.optString("normalizedPath"),
            sizeBytes = json.optLong("sizeBytes"),
            lastModifiedMillis = json.optLong("lastModifiedMillis"),
            hashHex = hashHex
        )
    }

    private fun groupToJson(group: DuplicateGroup): JSONObject {
        val json = JSONObject()
        json.put("hashHex", group.hashHex)
        val files = JSONArray()
        group.files.forEach { files.put(fileToJson(it)) }
        json.put("files", files)
        return json
    }

    private fun groupFromJson(json: JSONObject): DuplicateGroup {
        val hash = json.optString("hashHex")
        val files = mutableListOf<FileMetadata>()
        val filesArray = json.optJSONArray("files") ?: JSONArray()
        for (i in 0 until filesArray.length()) {
            files.add(fileFromJson(filesArray.getJSONObject(i)))
        }
        return DuplicateGroup(hash, files)
    }

    companion object {
        private const val PREFS_NAME = "cached_dupe_scanner"
        private const val KEY_LAST_RESULT = "last_scan_result"
    }
}
