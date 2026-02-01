package opensource.cached_dupe_scanner.storage

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class ScanTargetStore(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun loadTargets(): List<ScanTarget> {
        val raw = prefs.getString(KEY_TARGETS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            val targets = mutableListOf<ScanTarget>()
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                targets.add(
                    ScanTarget(
                        id = item.optString("id"),
                        path = item.optString("path")
                    )
                )
            }
            targets
        }.getOrDefault(emptyList())
    }

    fun saveTargets(targets: List<ScanTarget>) {
        val array = JSONArray()
        targets.forEach { target ->
            val item = JSONObject()
            item.put("id", target.id)
            item.put("path", target.path)
            array.put(item)
        }
        prefs.edit().putString(KEY_TARGETS, array.toString()).apply()
    }

    fun addTarget(path: String): ScanTarget {
        val target = ScanTarget(UUID.randomUUID().toString(), path)
        val targets = loadTargets().toMutableList()
        targets.add(target)
        saveTargets(targets)
        return target
    }

    fun updateTarget(id: String, path: String) {
        val targets = loadTargets().map { target ->
            if (target.id == id) target.copy(path = path) else target
        }
        saveTargets(targets)
    }

    fun removeTarget(id: String) {
        val targets = loadTargets().filterNot { it.id == id }
        saveTargets(targets)
        if (loadSelectedTargetId() == id) {
            saveSelectedTargetId(null)
        }
    }

    fun loadSelectedTargetId(): String? {
        return prefs.getString(KEY_SELECTED, null)
    }

    fun saveSelectedTargetId(id: String?) {
        prefs.edit().putString(KEY_SELECTED, id).apply()
    }

    companion object {
        private const val PREFS_NAME = "cached_dupe_scanner"
        private const val KEY_TARGETS = "scan_targets"
        private const val KEY_SELECTED = "selected_target_id"
    }
}

data class ScanTarget(
    val id: String,
    val path: String
)
