package opensource.cached_dupe_scanner.storage

import android.content.Context
import android.content.SharedPreferences

data class AppSettings(
    val skipZeroSizeInDb: Boolean,
    val hideZeroSizeInResults: Boolean,
    val resultSortKey: String,
    val resultSortDirection: String,
    val showFullPaths: Boolean,
    val filesSortKey: String,
    val filesSortDirection: String
)

class AppSettingsStore(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): AppSettings {
        return AppSettings(
            skipZeroSizeInDb = prefs.getBoolean(KEY_SKIP_ZERO_SIZE_DB, false),
            hideZeroSizeInResults = prefs.getBoolean(KEY_HIDE_ZERO_SIZE_RESULTS, false),
            resultSortKey = prefs.getString(KEY_RESULT_SORT_KEY, "Count") ?: "Count",
            resultSortDirection = prefs.getString(KEY_RESULT_SORT_DIR, "Desc") ?: "Desc",
            showFullPaths = prefs.getBoolean(KEY_SHOW_FULL_PATHS, false),
            filesSortKey = prefs.getString(KEY_FILES_SORT_KEY, "Name") ?: "Name",
            filesSortDirection = prefs.getString(KEY_FILES_SORT_DIR, "Asc") ?: "Asc"
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

    fun setShowFullPaths(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_FULL_PATHS, enabled).apply()
    }

    fun setFilesSortKey(value: String) {
        prefs.edit().putString(KEY_FILES_SORT_KEY, value).apply()
    }

    fun setFilesSortDirection(value: String) {
        prefs.edit().putString(KEY_FILES_SORT_DIR, value).apply()
    }

    fun exportToJson(): String {
        val settings = load()
        return org.json.JSONObject()
            .put(KEY_SKIP_ZERO_SIZE_DB, settings.skipZeroSizeInDb)
            .put(KEY_HIDE_ZERO_SIZE_RESULTS, settings.hideZeroSizeInResults)
            .put(KEY_RESULT_SORT_KEY, settings.resultSortKey)
            .put(KEY_RESULT_SORT_DIR, settings.resultSortDirection)
            .put(KEY_SHOW_FULL_PATHS, settings.showFullPaths)
            .put(KEY_FILES_SORT_KEY, settings.filesSortKey)
            .put(KEY_FILES_SORT_DIR, settings.filesSortDirection)
            .toString()
    }

    fun importFromJson(json: String): AppSettings {
        val obj = org.json.JSONObject(json)
        val settings = AppSettings(
            skipZeroSizeInDb = obj.optBoolean(KEY_SKIP_ZERO_SIZE_DB, false),
            hideZeroSizeInResults = obj.optBoolean(KEY_HIDE_ZERO_SIZE_RESULTS, false),
            resultSortKey = obj.optString(KEY_RESULT_SORT_KEY, "Count"),
            resultSortDirection = obj.optString(KEY_RESULT_SORT_DIR, "Desc"),
            showFullPaths = obj.optBoolean(KEY_SHOW_FULL_PATHS, false),
            filesSortKey = obj.optString(KEY_FILES_SORT_KEY, "Name"),
            filesSortDirection = obj.optString(KEY_FILES_SORT_DIR, "Asc")
        )
        prefs.edit()
            .putBoolean(KEY_SKIP_ZERO_SIZE_DB, settings.skipZeroSizeInDb)
            .putBoolean(KEY_HIDE_ZERO_SIZE_RESULTS, settings.hideZeroSizeInResults)
            .putString(KEY_RESULT_SORT_KEY, settings.resultSortKey)
            .putString(KEY_RESULT_SORT_DIR, settings.resultSortDirection)
            .putBoolean(KEY_SHOW_FULL_PATHS, settings.showFullPaths)
            .putString(KEY_FILES_SORT_KEY, settings.filesSortKey)
            .putString(KEY_FILES_SORT_DIR, settings.filesSortDirection)
            .apply()
        return settings
    }

    companion object {
        private const val PREFS_NAME = "cached_dupe_scanner"
        private const val KEY_SKIP_ZERO_SIZE_DB = "skip_zero_size_db"
        private const val KEY_HIDE_ZERO_SIZE_RESULTS = "hide_zero_size_results"
        private const val KEY_RESULT_SORT_KEY = "result_sort_key"
        private const val KEY_RESULT_SORT_DIR = "result_sort_dir"
        private const val KEY_SHOW_FULL_PATHS = "show_full_paths"
        private const val KEY_FILES_SORT_KEY = "files_sort_key"
        private const val KEY_FILES_SORT_DIR = "files_sort_dir"
    }
}
