package opensource.cached_dupe_scanner.storage

import android.content.Context
import android.content.SharedPreferences

class PathStore(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun save(path: String) {
        prefs.edit().putString(KEY_PATH, path).apply()
    }

    fun load(): String? {
        return prefs.getString(KEY_PATH, null)
    }

    fun clear() {
        prefs.edit().remove(KEY_PATH).apply()
    }

    companion object {
        private const val PREFS_NAME = "cached_dupe_scanner"
        private const val KEY_PATH = "target_path"
    }
}
