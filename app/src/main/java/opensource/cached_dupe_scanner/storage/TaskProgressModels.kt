package opensource.cached_dupe_scanner.storage

data class DbMaintenanceProgress(
    val total: Int,
    val processed: Int,
    val deleted: Int,
    val rehashed: Int,
    val missingHashed: Int,
    val currentPath: String?
)

data class DbMaintenanceSummary(
    val total: Int,
    val processed: Int,
    val deleted: Int,
    val rehashed: Int,
    val missingHashed: Int,
    val cancelled: Boolean,
    val currentPath: String?
)

data class RebuildGroupsProgress(
    val total: Int,
    val processed: Int
)

data class RebuildGroupsSummary(
    val total: Int,
    val processed: Int,
    val cancelled: Boolean
)

data class ClearCacheProgress(
    val total: Int,
    val processed: Int,
    val clearedFiles: Int,
    val clearedGroups: Int
)

data class ClearCacheSummary(
    val total: Int,
    val processed: Int,
    val clearedFiles: Int,
    val clearedGroups: Int,
    val cancelled: Boolean
)

data class TrashProgress(
    val total: Int,
    val processed: Int,
    val deleted: Int,
    val failed: Int,
    val currentPath: String?
)

data class TrashRunSummary(
    val total: Int,
    val processed: Int,
    val deleted: Int,
    val failed: Int,
    val cancelled: Boolean,
    val currentPath: String?
)
