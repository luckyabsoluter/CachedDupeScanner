package opensource.cached_dupe_scanner.storage

enum class DbMaintenanceScope {
    AllCachedFiles,
    DuplicateResultGroups,
    SimilarityGroups
}

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

enum class RebuildGroupsPhase {
    RepairingMissingHashes,
    RebuildingGroups
}

data class RebuildGroupsProgress(
    val total: Int,
    val processed: Int,
    val phase: RebuildGroupsPhase = RebuildGroupsPhase.RebuildingGroups,
    val currentPath: String? = null
)

data class RebuildGroupsSummary(
    val total: Int,
    val processed: Int,
    val cancelled: Boolean,
    val phase: RebuildGroupsPhase = RebuildGroupsPhase.RebuildingGroups
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
