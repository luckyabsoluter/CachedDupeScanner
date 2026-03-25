package opensource.cached_dupe_scanner.storage

import android.content.Context
import android.content.SharedPreferences

data class AppSettings(
    val skipZeroSizeInDb: Boolean,
    val skipTrashBinContentsInScan: Boolean,
    val hideZeroSizeInResults: Boolean,
    val showMemoryOverlay: Boolean,
    val keepLoadedThumbnailsInMemory: Boolean,
    val resultSortKey: String,
    val resultSortDirection: String,
    val resultGroupSortKey: String,
    val resultGroupSortDirection: String,
    val showFullPaths: Boolean,
    val resultsFilterDefinitionJson: String,
    val filesSortKey: String,
    val filesSortDirection: String
)

class AppSettingsStore(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): AppSettings {
        return readFromPreferences()
    }

    fun setSkipZeroSizeInDb(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SKIP_ZERO_SIZE_DB, enabled).apply()
    }

    fun setSkipTrashBinContentsInScan(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SKIP_TRASH_BIN_CONTENTS_IN_SCAN, enabled).apply()
    }

    fun setHideZeroSizeInResults(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_HIDE_ZERO_SIZE_RESULTS, enabled).apply()
    }

    fun setShowMemoryOverlay(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_MEMORY_OVERLAY, enabled).apply()
    }

    fun setKeepLoadedThumbnailsInMemory(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_KEEP_LOADED_THUMBNAILS_IN_MEMORY, enabled).apply()
    }

    fun setResultSortKey(value: String) {
        prefs.edit().putString(KEY_RESULT_SORT_KEY, value).apply()
    }

    fun setResultSortDirection(value: String) {
        prefs.edit().putString(KEY_RESULT_SORT_DIR, value).apply()
    }

    fun setResultGroupSortKey(value: String) {
        prefs.edit().putString(KEY_RESULT_GROUP_SORT_KEY, value).apply()
    }

    fun setResultGroupSortDirection(value: String) {
        prefs.edit().putString(KEY_RESULT_GROUP_SORT_DIR, value).apply()
    }

    fun setShowFullPaths(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_FULL_PATHS, enabled).apply()
    }

    fun setResultsFilterDefinitionJson(value: String) {
        prefs.edit().putString(KEY_RESULTS_FILTER_DEFINITION_JSON, value).apply()
    }

    fun setFilesSortKey(value: String) {
        prefs.edit().putString(KEY_FILES_SORT_KEY, value).apply()
    }

    fun setFilesSortDirection(value: String) {
        prefs.edit().putString(KEY_FILES_SORT_DIR, value).apply()
    }

    fun exportToJson(): String {
        return toJson(load()).toString()
    }

    fun importFromJson(json: String): AppSettings {
        val obj = org.json.JSONObject(json)
        val settings = readFromJson(obj)
        writeToPreferences(settings)
        return settings
    }

    private fun readFromPreferences(): AppSettings {
        return AppSettings(
            skipZeroSizeInDb = prefs.getBoolean(KEY_SKIP_ZERO_SIZE_DB, DEFAULT_SETTINGS.skipZeroSizeInDb),
            skipTrashBinContentsInScan = prefs.getBoolean(
                KEY_SKIP_TRASH_BIN_CONTENTS_IN_SCAN,
                DEFAULT_SETTINGS.skipTrashBinContentsInScan
            ),
            hideZeroSizeInResults = prefs.getBoolean(
                KEY_HIDE_ZERO_SIZE_RESULTS,
                DEFAULT_SETTINGS.hideZeroSizeInResults
            ),
            showMemoryOverlay = prefs.getBoolean(
                KEY_SHOW_MEMORY_OVERLAY,
                DEFAULT_SETTINGS.showMemoryOverlay
            ),
            keepLoadedThumbnailsInMemory = prefs.getBoolean(
                KEY_KEEP_LOADED_THUMBNAILS_IN_MEMORY,
                DEFAULT_SETTINGS.keepLoadedThumbnailsInMemory
            ),
            resultSortKey = prefs.getString(KEY_RESULT_SORT_KEY, DEFAULT_SETTINGS.resultSortKey)
                ?: DEFAULT_SETTINGS.resultSortKey,
            resultSortDirection = prefs.getString(KEY_RESULT_SORT_DIR, DEFAULT_SETTINGS.resultSortDirection)
                ?: DEFAULT_SETTINGS.resultSortDirection,
            resultGroupSortKey = prefs.getString(
                KEY_RESULT_GROUP_SORT_KEY,
                DEFAULT_SETTINGS.resultGroupSortKey
            ) ?: DEFAULT_SETTINGS.resultGroupSortKey,
            resultGroupSortDirection = prefs.getString(
                KEY_RESULT_GROUP_SORT_DIR,
                DEFAULT_SETTINGS.resultGroupSortDirection
            ) ?: DEFAULT_SETTINGS.resultGroupSortDirection,
            showFullPaths = prefs.getBoolean(KEY_SHOW_FULL_PATHS, DEFAULT_SETTINGS.showFullPaths),
            resultsFilterDefinitionJson = prefs.getString(
                KEY_RESULTS_FILTER_DEFINITION_JSON,
                DEFAULT_SETTINGS.resultsFilterDefinitionJson
            ) ?: DEFAULT_SETTINGS.resultsFilterDefinitionJson,
            filesSortKey = prefs.getString(KEY_FILES_SORT_KEY, DEFAULT_SETTINGS.filesSortKey)
                ?: DEFAULT_SETTINGS.filesSortKey,
            filesSortDirection = prefs.getString(KEY_FILES_SORT_DIR, DEFAULT_SETTINGS.filesSortDirection)
                ?: DEFAULT_SETTINGS.filesSortDirection
        )
    }

    private fun readFromJson(obj: org.json.JSONObject): AppSettings {
        return AppSettings(
            skipZeroSizeInDb = obj.optBoolean(KEY_SKIP_ZERO_SIZE_DB, DEFAULT_SETTINGS.skipZeroSizeInDb),
            skipTrashBinContentsInScan = obj.optBoolean(
                KEY_SKIP_TRASH_BIN_CONTENTS_IN_SCAN,
                DEFAULT_SETTINGS.skipTrashBinContentsInScan
            ),
            hideZeroSizeInResults = obj.optBoolean(
                KEY_HIDE_ZERO_SIZE_RESULTS,
                DEFAULT_SETTINGS.hideZeroSizeInResults
            ),
            showMemoryOverlay = obj.optBoolean(
                KEY_SHOW_MEMORY_OVERLAY,
                DEFAULT_SETTINGS.showMemoryOverlay
            ),
            keepLoadedThumbnailsInMemory = obj.optBoolean(
                KEY_KEEP_LOADED_THUMBNAILS_IN_MEMORY,
                DEFAULT_SETTINGS.keepLoadedThumbnailsInMemory
            ),
            resultSortKey = obj.optString(KEY_RESULT_SORT_KEY, DEFAULT_SETTINGS.resultSortKey),
            resultSortDirection = obj.optString(KEY_RESULT_SORT_DIR, DEFAULT_SETTINGS.resultSortDirection),
            resultGroupSortKey = obj.optString(
                KEY_RESULT_GROUP_SORT_KEY,
                DEFAULT_SETTINGS.resultGroupSortKey
            ),
            resultGroupSortDirection = obj.optString(
                KEY_RESULT_GROUP_SORT_DIR,
                DEFAULT_SETTINGS.resultGroupSortDirection
            ),
            showFullPaths = obj.optBoolean(KEY_SHOW_FULL_PATHS, DEFAULT_SETTINGS.showFullPaths),
            resultsFilterDefinitionJson = obj.optString(
                KEY_RESULTS_FILTER_DEFINITION_JSON,
                DEFAULT_SETTINGS.resultsFilterDefinitionJson
            ),
            filesSortKey = obj.optString(KEY_FILES_SORT_KEY, DEFAULT_SETTINGS.filesSortKey),
            filesSortDirection = obj.optString(KEY_FILES_SORT_DIR, DEFAULT_SETTINGS.filesSortDirection)
        )
    }

    private fun writeToPreferences(settings: AppSettings) {
        prefs.edit()
            .putBoolean(KEY_SKIP_ZERO_SIZE_DB, settings.skipZeroSizeInDb)
            .putBoolean(KEY_SKIP_TRASH_BIN_CONTENTS_IN_SCAN, settings.skipTrashBinContentsInScan)
            .putBoolean(KEY_HIDE_ZERO_SIZE_RESULTS, settings.hideZeroSizeInResults)
            .putBoolean(KEY_SHOW_MEMORY_OVERLAY, settings.showMemoryOverlay)
            .putBoolean(KEY_KEEP_LOADED_THUMBNAILS_IN_MEMORY, settings.keepLoadedThumbnailsInMemory)
            .putString(KEY_RESULT_SORT_KEY, settings.resultSortKey)
            .putString(KEY_RESULT_SORT_DIR, settings.resultSortDirection)
            .putString(KEY_RESULT_GROUP_SORT_KEY, settings.resultGroupSortKey)
            .putString(KEY_RESULT_GROUP_SORT_DIR, settings.resultGroupSortDirection)
            .putBoolean(KEY_SHOW_FULL_PATHS, settings.showFullPaths)
            .putString(KEY_RESULTS_FILTER_DEFINITION_JSON, settings.resultsFilterDefinitionJson)
            .putString(KEY_FILES_SORT_KEY, settings.filesSortKey)
            .putString(KEY_FILES_SORT_DIR, settings.filesSortDirection)
            .apply()
    }

    private fun toJson(settings: AppSettings): org.json.JSONObject {
        return org.json.JSONObject()
            .put(KEY_SKIP_ZERO_SIZE_DB, settings.skipZeroSizeInDb)
            .put(KEY_SKIP_TRASH_BIN_CONTENTS_IN_SCAN, settings.skipTrashBinContentsInScan)
            .put(KEY_HIDE_ZERO_SIZE_RESULTS, settings.hideZeroSizeInResults)
            .put(KEY_SHOW_MEMORY_OVERLAY, settings.showMemoryOverlay)
            .put(KEY_KEEP_LOADED_THUMBNAILS_IN_MEMORY, settings.keepLoadedThumbnailsInMemory)
            .put(KEY_RESULT_SORT_KEY, settings.resultSortKey)
            .put(KEY_RESULT_SORT_DIR, settings.resultSortDirection)
            .put(KEY_RESULT_GROUP_SORT_KEY, settings.resultGroupSortKey)
            .put(KEY_RESULT_GROUP_SORT_DIR, settings.resultGroupSortDirection)
            .put(KEY_SHOW_FULL_PATHS, settings.showFullPaths)
            .put(KEY_RESULTS_FILTER_DEFINITION_JSON, settings.resultsFilterDefinitionJson)
            .put(KEY_FILES_SORT_KEY, settings.filesSortKey)
            .put(KEY_FILES_SORT_DIR, settings.filesSortDirection)
    }

    companion object {
        private val DEFAULT_SETTINGS = AppSettings(
            skipZeroSizeInDb = true,
            skipTrashBinContentsInScan = true,
            hideZeroSizeInResults = false,
            showMemoryOverlay = false,
            keepLoadedThumbnailsInMemory = false,
            resultSortKey = "Count",
            resultSortDirection = "Desc",
            resultGroupSortKey = "Path",
            resultGroupSortDirection = "Asc",
            showFullPaths = false,
            resultsFilterDefinitionJson = "",
            filesSortKey = "Name",
            filesSortDirection = "Asc"
        )
        private const val PREFS_NAME = "cached_dupe_scanner"
        private const val KEY_SKIP_ZERO_SIZE_DB = "skip_zero_size_db"
        private const val KEY_SKIP_TRASH_BIN_CONTENTS_IN_SCAN = "skip_trash_bin_contents_in_scan"
        private const val KEY_HIDE_ZERO_SIZE_RESULTS = "hide_zero_size_results"
        private const val KEY_SHOW_MEMORY_OVERLAY = "show_memory_overlay"
        private const val KEY_KEEP_LOADED_THUMBNAILS_IN_MEMORY = "keep_loaded_thumbnails_in_memory"
        private const val KEY_RESULT_SORT_KEY = "result_sort_key"
        private const val KEY_RESULT_SORT_DIR = "result_sort_dir"
        private const val KEY_RESULT_GROUP_SORT_KEY = "result_group_sort_key"
        private const val KEY_RESULT_GROUP_SORT_DIR = "result_group_sort_dir"
        private const val KEY_SHOW_FULL_PATHS = "show_full_paths"
        private const val KEY_RESULTS_FILTER_DEFINITION_JSON = "results_filter_definition_json"
        private const val KEY_FILES_SORT_KEY = "files_sort_key"
        private const val KEY_FILES_SORT_DIR = "files_sort_dir"
    }
}
