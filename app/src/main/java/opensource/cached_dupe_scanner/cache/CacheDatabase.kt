package opensource.cached_dupe_scanner.cache

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [CachedFileEntity::class, ScanSessionEntity::class, ScanFileEntity::class],
    version = 2,
    exportSchema = false
)
abstract class CacheDatabase : RoomDatabase() {
    abstract fun fileCacheDao(): FileCacheDao
    abstract fun scanHistoryDao(): ScanHistoryDao
}
