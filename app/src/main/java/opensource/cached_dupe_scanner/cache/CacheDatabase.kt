package opensource.cached_dupe_scanner.cache

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Room database for scan cache, duplicate-group snapshot, reports, and trash metadata.
 *
 * Table ownership:
 * - `cached_files`: canonical per-file cache rows.
 * - `dupe_groups`: derived duplicate-group snapshot for fast Results paging.
 * - `scan_reports`: historical scan execution summaries.
 * - `trash_entries`: application trash ledger for restore/delete operations.
 * - `similarity_settings`: configured similarity methods maintained alongside the file cache.
 * - `similarity_cluster_members`: sidecar links between similarity clusters and cached files.
 */
@Database(
    entities = [
        CachedFileEntity::class,
        ScanReportEntity::class,
        TrashEntryEntity::class,
        DuplicateGroupEntity::class,
        SimilaritySettingEntity::class,
        SimilaritySettingFileEntity::class,
        SimilarityExactThumbnailFeatureEntity::class,
        SimilarityDurationFeatureEntity::class,
        SimilarityClusterEntity::class,
        SimilarityClusterMemberEntity::class,
        SimilarityMaintenanceRunEntity::class
    ],
    version = 19,
    exportSchema = false
)
abstract class CacheDatabase : RoomDatabase() {
    abstract fun fileCacheDao(): FileCacheDao
    abstract fun scanReportDao(): ScanReportDao
    abstract fun trashDao(): TrashDao
    abstract fun duplicateGroupDao(): DuplicateGroupDao
    abstract fun similaritySettingsDao(): SimilaritySettingsDao
}
