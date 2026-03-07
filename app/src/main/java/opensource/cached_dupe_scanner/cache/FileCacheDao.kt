package opensource.cached_dupe_scanner.cache

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * DAO for `cached_files`.
 *
 * Query groups:
 * - Point lookups and counts (`getByNormalizedPath`, `countAll`).
 * - Cursor paging for file manager and maintenance (`getPageAfter`, `getPageBy*`).
 * - Duplicate-member listing by (`sizeBytes`, `hashHex`).
 * - Mutations (`upsert`, `upsertAll`, `deleteByNormalizedPath`, `clear`).
 * - Projection helpers used by scanner/group synchronization (`countBySizes`, `findSizesByPaths`, `findGroupKeysByPaths`).
 */
@Dao
interface FileCacheDao {
    @Query("SELECT * FROM cached_files WHERE normalizedPath = :normalizedPath LIMIT 1")
    fun getByNormalizedPath(normalizedPath: String): CachedFileEntity?

    @Query("SELECT * FROM cached_files")
    fun getAll(): List<CachedFileEntity>

    @Query("SELECT COUNT(*) FROM cached_files")
    fun countAll(): Int

    @Query("SELECT * FROM cached_files WHERE normalizedPath > :afterPath ORDER BY normalizedPath LIMIT :limit")
    fun getPageAfter(afterPath: String, limit: Int): List<CachedFileEntity>

    @Query("SELECT * FROM cached_files ORDER BY normalizedPath DESC LIMIT :limit")
    fun getFirstPageByNameDesc(limit: Int): List<CachedFileEntity>

    @Query("SELECT * FROM cached_files WHERE normalizedPath < :beforePath ORDER BY normalizedPath DESC LIMIT :limit")
    fun getPageBefore(beforePath: String, limit: Int): List<CachedFileEntity>

    @Query("SELECT * FROM cached_files ORDER BY sizeBytes ASC, normalizedPath ASC LIMIT :limit")
    fun getFirstPageBySizeAsc(limit: Int): List<CachedFileEntity>

    @Query(
        """
        SELECT * FROM cached_files
        WHERE (sizeBytes > :afterSize)
           OR (sizeBytes = :afterSize AND normalizedPath > :afterPath)
        ORDER BY sizeBytes ASC, normalizedPath ASC
        LIMIT :limit
        """
    )
    fun getPageBySizeAsc(afterSize: Long, afterPath: String, limit: Int): List<CachedFileEntity>

    @Query("SELECT * FROM cached_files ORDER BY sizeBytes DESC, normalizedPath DESC LIMIT :limit")
    fun getFirstPageBySizeDesc(limit: Int): List<CachedFileEntity>

    @Query(
        """
        SELECT * FROM cached_files
        WHERE (sizeBytes < :beforeSize)
           OR (sizeBytes = :beforeSize AND normalizedPath < :beforePath)
        ORDER BY sizeBytes DESC, normalizedPath DESC
        LIMIT :limit
        """
    )
    fun getPageBySizeDesc(beforeSize: Long, beforePath: String, limit: Int): List<CachedFileEntity>

    @Query("SELECT * FROM cached_files ORDER BY lastModifiedMillis ASC, normalizedPath ASC LIMIT :limit")
    fun getFirstPageByModifiedAsc(limit: Int): List<CachedFileEntity>

    @Query(
        """
        SELECT * FROM cached_files
        WHERE (lastModifiedMillis > :afterModified)
           OR (lastModifiedMillis = :afterModified AND normalizedPath > :afterPath)
        ORDER BY lastModifiedMillis ASC, normalizedPath ASC
        LIMIT :limit
        """
    )
    fun getPageByModifiedAsc(afterModified: Long, afterPath: String, limit: Int): List<CachedFileEntity>

    @Query("SELECT * FROM cached_files ORDER BY lastModifiedMillis DESC, normalizedPath DESC LIMIT :limit")
    fun getFirstPageByModifiedDesc(limit: Int): List<CachedFileEntity>

    @Query(
        """
        SELECT * FROM cached_files
        WHERE (lastModifiedMillis < :beforeModified)
           OR (lastModifiedMillis = :beforeModified AND normalizedPath < :beforePath)
        ORDER BY lastModifiedMillis DESC, normalizedPath DESC
        LIMIT :limit
        """
    )
    fun getPageByModifiedDesc(beforeModified: Long, beforePath: String, limit: Int): List<CachedFileEntity>

    @Query(
        """
        SELECT * FROM cached_files
        WHERE sizeBytes = :sizeBytes AND hashHex = :hashHex
        ORDER BY normalizedPath ASC
        LIMIT :limit
        """
    )
    fun listMembersBySizeAndHash(sizeBytes: Long, hashHex: String, limit: Int): List<CachedFileEntity>

    @Query(
        """
        SELECT * FROM cached_files
        WHERE sizeBytes = :sizeBytes AND hashHex = :hashHex AND normalizedPath > :afterPath
        ORDER BY normalizedPath ASC
        LIMIT :limit
        """
    )
    fun listMembersBySizeAndHashAfter(sizeBytes: Long, hashHex: String, afterPath: String, limit: Int): List<CachedFileEntity>

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

    @Query(
        """
        SELECT normalizedPath as normalizedPath, sizeBytes as sizeBytes, hashHex as hashHex
        FROM cached_files
        WHERE normalizedPath IN (:paths)
        """
    )
    fun findGroupKeysByPaths(paths: List<String>): List<PathGroupKey>
}
