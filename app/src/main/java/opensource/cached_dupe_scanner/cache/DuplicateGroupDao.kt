package opensource.cached_dupe_scanner.cache

import androidx.room.Dao
import androidx.room.Query

@Dao
interface DuplicateGroupDao {
    @Query("DELETE FROM dupe_groups")
    fun clear()

    /**
     * Rebuilds duplicate groups from cached_files. This is a bulk operation designed to avoid
     * loading cached_files into RAM.
     */
    @Query(
        """
        DELETE FROM dupe_groups
        """
    )
    fun clearInternal()

    @Query(
        """
        INSERT INTO dupe_groups (sizeBytes, hashHex, fileCount, totalBytes, updatedAtMillis)
        SELECT
            sizeBytes as sizeBytes,
            hashHex as hashHex,
            COUNT(*) as fileCount,
            (COUNT(*) * sizeBytes) as totalBytes,
            :updatedAtMillis as updatedAtMillis
        FROM cached_files
        WHERE hashHex IS NOT NULL
        GROUP BY sizeBytes, hashHex
        HAVING COUNT(*) > 1
        """
    )
    fun insertFromCache(updatedAtMillis: Long)

    fun rebuildFromCache(updatedAtMillis: Long) {
        clearInternal()
        insertFromCache(updatedAtMillis)
    }

    @Query("SELECT COUNT(*) FROM dupe_groups")
    fun countGroups(): Int

    @Query(
        """
        SELECT * FROM dupe_groups
        ORDER BY fileCount DESC, totalBytes DESC
        LIMIT :limit OFFSET :offset
        """
    )
    fun listByCountDesc(limit: Int, offset: Int): List<DuplicateGroupEntity>

    @Query(
        """
        SELECT * FROM dupe_groups
        ORDER BY totalBytes DESC, fileCount DESC
        LIMIT :limit OFFSET :offset
        """
    )
    fun listByTotalBytesDesc(limit: Int, offset: Int): List<DuplicateGroupEntity>

    @Query(
        """
        SELECT * FROM dupe_groups
        ORDER BY sizeBytes DESC, fileCount DESC
        LIMIT :limit OFFSET :offset
        """
    )
    fun listByPerFileSizeDesc(limit: Int, offset: Int): List<DuplicateGroupEntity>

    @Query(
        """
        SELECT * FROM dupe_groups
        WHERE sizeBytes = :sizeBytes AND hashHex = :hashHex
        LIMIT 1
        """
    )
    fun get(sizeBytes: Long, hashHex: String): DuplicateGroupEntity?

    @Query(
        """
        DELETE FROM dupe_groups
        WHERE sizeBytes = :sizeBytes AND hashHex = :hashHex
        """
    )
    fun delete(sizeBytes: Long, hashHex: String)

    @Query(
        """
        SELECT COUNT(*)
        FROM cached_files
        WHERE sizeBytes = :sizeBytes AND hashHex = :hashHex
        """
    )
    fun countMembers(sizeBytes: Long, hashHex: String): Int
}
