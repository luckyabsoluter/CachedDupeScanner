package opensource.cached_dupe_scanner.cache

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        CachedFileEntity::class,
        ScanReportEntity::class,
        ScanReportTargetEntity::class
    ],
    version = 6,
    exportSchema = false
)
abstract class CacheDatabase : RoomDatabase() {
    abstract fun fileCacheDao(): FileCacheDao
    abstract fun scanReportDao(): ScanReportDao
}
