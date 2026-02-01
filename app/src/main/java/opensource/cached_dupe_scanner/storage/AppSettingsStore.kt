package opensource.cached_dupe_scanner.storage

import android.content.Context
import android.content.SharedPreferences

data class AppSettings(
    val excludeZeroSizeDuplicates: Boolean,
    val skipZeroSizeInDb: Boolean,
    val hideZeroSizeInResults: Boolean
)

class AppSettingsStore(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): AppSettings {
        return AppSettings(
            excludeZeroSizeDuplicates = prefs.getBoolean(KEY_EXCLUDE_ZERO_SIZE, true),
            skipZeroSizeInDb = prefs.getBoolean(KEY_SKIP_ZERO_SIZE_DB, false),
            hideZeroSizeInResults = prefs.getBoolean(KEY_HIDE_ZERO_SIZE_RESULTS, false)
        )
    }

    fun setExcludeZeroSizeDuplicates(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_EXCLUDE_ZERO_SIZE, enabled).apply()
    }

    fun setSkipZeroSizeInDb(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SKIP_ZERO_SIZE_DB, enabled).apply()
    }

    fun setHideZeroSizeInResults(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_HIDE_ZERO_SIZE_RESULTS, enabled).apply()
    }

    companion object {
        private const val PREFS_NAME = "cached_dupe_scanner"
        private const val KEY_EXCLUDE_ZERO_SIZE = "exclude_zero_size_duplicates"
        private const val KEY_SKIP_ZERO_SIZE_DB = "skip_zero_size_db"
        private const val KEY_HIDE_ZERO_SIZE_RESULTS = "hide_zero_size_results"
    }
}
