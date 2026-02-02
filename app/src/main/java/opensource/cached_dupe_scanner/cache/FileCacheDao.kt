package opensource.cached_dupe_scanner.cache

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface FileCacheDao {
    @Query("SELECT * FROM cached_files WHERE normalizedPath = :normalizedPath LIMIT 1")
    fun getByNormalizedPath(normalizedPath: String): CachedFileEntity?

    @Query("SELECT * FROM cached_files")
    fun getAll(): List<CachedFileEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(entity: CachedFileEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertAll(entities: List<CachedFileEntity>)

    @Query("DELETE FROM cached_files WHERE normalizedPath = :normalizedPath")
    fun deleteByNormalizedPath(normalizedPath: String)

    @Query("DELETE FROM cached_files")
    fun clear()

    @Query("SELECT sizeBytes as sizeBytes, COUNT(*) as count FROM cached_files WHERE sizeBytes IN (:sizes) GROUP BY sizeBytes")
    fun countBySizes(sizes: List<Long>): List<SizeCount>

    @Query("SELECT normalizedPath as normalizedPath, sizeBytes as sizeBytes FROM cached_files WHERE normalizedPath IN (:paths)")
    fun findSizesByPaths(paths: List<String>): List<PathSize>
}
