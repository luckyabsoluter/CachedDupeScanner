package opensource.cached_dupe_scanner.cache

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface FileCacheDao {
    @Query("SELECT * FROM cached_files WHERE normalizedPath = :normalizedPath LIMIT 1")
    fun getByNormalizedPath(normalizedPath: String): CachedFileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(entity: CachedFileEntity)

    @Query("DELETE FROM cached_files")
    fun clear()
}
