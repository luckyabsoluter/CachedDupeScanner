package opensource.cached_dupe_scanner.storage

import android.content.Context
import android.content.SharedPreferences

data class AppSettings(
    val skipZeroSizeInDb: Boolean,
    val hideZeroSizeInResults: Boolean,
    val resultSortKey: String,
    val resultSortDirection: String
)

class AppSettingsStore(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): AppSettings {
        return AppSettings(
            skipZeroSizeInDb = prefs.getBoolean(KEY_SKIP_ZERO_SIZE_DB, false),
            hideZeroSizeInResults = prefs.getBoolean(KEY_HIDE_ZERO_SIZE_RESULTS, false),
            resultSortKey = prefs.getString(KEY_RESULT_SORT_KEY, "Count") ?: "Count",
            resultSortDirection = prefs.getString(KEY_RESULT_SORT_DIR, "Desc") ?: "Desc"
        )
    }

    fun setSkipZeroSizeInDb(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SKIP_ZERO_SIZE_DB, enabled).apply()
    }

    fun setHideZeroSizeInResults(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_HIDE_ZERO_SIZE_RESULTS, enabled).apply()
    }

    fun setResultSortKey(value: String) {
        prefs.edit().putString(KEY_RESULT_SORT_KEY, value).apply()
    }

    fun setResultSortDirection(value: String) {
        prefs.edit().putString(KEY_RESULT_SORT_DIR, value).apply()
    }

    companion object {
        private const val PREFS_NAME = "cached_dupe_scanner"
        private const val KEY_SKIP_ZERO_SIZE_DB = "skip_zero_size_db"
        private const val KEY_HIDE_ZERO_SIZE_RESULTS = "hide_zero_size_results"
        private const val KEY_RESULT_SORT_KEY = "result_sort_key"
        private const val KEY_RESULT_SORT_DIR = "result_sort_dir"
    }
}
