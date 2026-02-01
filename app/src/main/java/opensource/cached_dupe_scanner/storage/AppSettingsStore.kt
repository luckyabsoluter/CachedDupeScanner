package opensource.cached_dupe_scanner.storage

import android.content.Context
import android.content.SharedPreferences

data class AppSettings(
    val excludeZeroSizeDuplicates: Boolean
)

class AppSettingsStore(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): AppSettings {
        return AppSettings(
            excludeZeroSizeDuplicates = prefs.getBoolean(KEY_EXCLUDE_ZERO_SIZE, true)
        )
    }

    fun setExcludeZeroSizeDuplicates(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_EXCLUDE_ZERO_SIZE, enabled).apply()
    }

    companion object {
        private const val PREFS_NAME = "cached_dupe_scanner"
        private const val KEY_EXCLUDE_ZERO_SIZE = "exclude_zero_size_duplicates"
    }
}
