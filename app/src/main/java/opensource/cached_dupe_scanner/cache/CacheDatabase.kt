package opensource.cached_dupe_scanner.cache

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        CachedFileEntity::class,
        ScanReportEntity::class,
        TrashEntryEntity::class,
        DuplicateGroupEntity::class
    ],
    version = 12,
    exportSchema = false
)
abstract class CacheDatabase : RoomDatabase() {
    abstract fun fileCacheDao(): FileCacheDao
    abstract fun scanReportDao(): ScanReportDao
    abstract fun trashDao(): TrashDao
    abstract fun duplicateGroupDao(): DuplicateGroupDao
}
