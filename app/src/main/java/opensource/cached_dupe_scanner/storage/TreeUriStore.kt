package opensource.cached_dupe_scanner.storage

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri

class TreeUriStore(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun save(uri: Uri) {
        prefs.edit().putString(KEY_URI, uri.toString()).apply()
    }

    fun load(): Uri? {
        val raw = prefs.getString(KEY_URI, null) ?: return null
        return runCatching { Uri.parse(raw) }.getOrNull()
    }

    fun clear() {
        prefs.edit().remove(KEY_URI).apply()
    }

    companion object {
        private const val PREFS_NAME = "cached_dupe_scanner"
        private const val KEY_URI = "tree_uri"
    }
}
