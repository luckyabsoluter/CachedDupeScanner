package opensource.cached_dupe_scanner.cache

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction

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
        INSERT OR REPLACE INTO dupe_groups (sizeBytes, hashHex, fileCount, totalBytes, updatedAtMillis)
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

    @Query(
        """
        INSERT OR REPLACE INTO dupe_groups (sizeBytes, hashHex, fileCount, totalBytes, updatedAtMillis)
        SELECT
            sizeBytes as sizeBytes,
            hashHex as hashHex,
            COUNT(*) as fileCount,
            (COUNT(*) * sizeBytes) as totalBytes,
            :updatedAtMillis as updatedAtMillis
        FROM cached_files
        WHERE hashHex = :hashHex AND sizeBytes = :sizeBytes
        GROUP BY sizeBytes, hashHex
        HAVING COUNT(*) > 1
        """
    )
    fun insertSingleGroupFromCache(sizeBytes: Long, hashHex: String, updatedAtMillis: Long)

    @Transaction
    fun rebuildFromCache(updatedAtMillis: Long) {
        clearInternal()
        insertFromCache(updatedAtMillis)
    }

    @Transaction
    fun refreshSingleGroup(sizeBytes: Long, hashHex: String, updatedAtMillis: Long) {
        val snapshotUpdatedAtMillis = latestUpdatedAtMillis() ?: updatedAtMillis
        delete(sizeBytes, hashHex)
        insertSingleGroupFromCache(sizeBytes, hashHex, snapshotUpdatedAtMillis)
    }

    @Query("SELECT COUNT(*) FROM dupe_groups")
    fun countGroups(): Int

    @Query("SELECT MAX(updatedAtMillis) FROM dupe_groups")
    fun latestUpdatedAtMillis(): Long?

    @Query("SELECT COUNT(*) FROM dupe_groups WHERE updatedAtMillis = :updatedAtMillis")
    fun countGroupsAt(updatedAtMillis: Long): Int

    @Query(
        """
        SELECT * FROM dupe_groups
        ORDER BY fileCount DESC, totalBytes DESC, sizeBytes DESC, hashHex DESC
        LIMIT :limit OFFSET :offset
        """
    )
    fun listByCountDesc(limit: Int, offset: Int): List<DuplicateGroupEntity>

    @Query(
        """
        SELECT * FROM dupe_groups
        ORDER BY fileCount ASC, totalBytes ASC, sizeBytes ASC, hashHex ASC
        LIMIT :limit OFFSET :offset
        """
    )
    fun listByCountAsc(limit: Int, offset: Int): List<DuplicateGroupEntity>

    @Query(
        """
        SELECT * FROM dupe_groups
        ORDER BY totalBytes DESC, fileCount DESC, sizeBytes DESC, hashHex DESC
        LIMIT :limit OFFSET :offset
        """
    )
    fun listByTotalBytesDesc(limit: Int, offset: Int): List<DuplicateGroupEntity>

    @Query(
        """
        SELECT * FROM dupe_groups
        ORDER BY totalBytes ASC, fileCount ASC, sizeBytes ASC, hashHex ASC
        LIMIT :limit OFFSET :offset
        """
    )
    fun listByTotalBytesAsc(limit: Int, offset: Int): List<DuplicateGroupEntity>

    @Query(
        """
        SELECT * FROM dupe_groups
        ORDER BY sizeBytes DESC, fileCount DESC, totalBytes DESC, hashHex DESC
        LIMIT :limit OFFSET :offset
        """
    )
    fun listByPerFileSizeDesc(limit: Int, offset: Int): List<DuplicateGroupEntity>

    @Query(
        """
        SELECT * FROM dupe_groups
        ORDER BY sizeBytes ASC, fileCount ASC, totalBytes ASC, hashHex ASC
        LIMIT :limit OFFSET :offset
        """
    )
    fun listByPerFileSizeAsc(limit: Int, offset: Int): List<DuplicateGroupEntity>

    @Query(
        """
        SELECT * FROM dupe_groups
        WHERE updatedAtMillis = :updatedAtMillis
        ORDER BY fileCount DESC, totalBytes DESC, sizeBytes DESC, hashHex DESC
        LIMIT :limit OFFSET :offset
        """
    )
    fun listByCountDescAt(updatedAtMillis: Long, limit: Int, offset: Int): List<DuplicateGroupEntity>

    @Query(
        """
        SELECT * FROM dupe_groups
        WHERE updatedAtMillis = :updatedAtMillis
        ORDER BY fileCount ASC, totalBytes ASC, sizeBytes ASC, hashHex ASC
        LIMIT :limit OFFSET :offset
        """
    )
    fun listByCountAscAt(updatedAtMillis: Long, limit: Int, offset: Int): List<DuplicateGroupEntity>

    @Query(
        """
        SELECT * FROM dupe_groups
        WHERE updatedAtMillis = :updatedAtMillis
        ORDER BY totalBytes DESC, fileCount DESC, sizeBytes DESC, hashHex DESC
        LIMIT :limit OFFSET :offset
        """
    )
    fun listByTotalBytesDescAt(updatedAtMillis: Long, limit: Int, offset: Int): List<DuplicateGroupEntity>

    @Query(
        """
        SELECT * FROM dupe_groups
        WHERE updatedAtMillis = :updatedAtMillis
        ORDER BY totalBytes ASC, fileCount ASC, sizeBytes ASC, hashHex ASC
        LIMIT :limit OFFSET :offset
        """
    )
    fun listByTotalBytesAscAt(updatedAtMillis: Long, limit: Int, offset: Int): List<DuplicateGroupEntity>

    @Query(
        """
        SELECT * FROM dupe_groups
        WHERE updatedAtMillis = :updatedAtMillis
        ORDER BY sizeBytes DESC, fileCount DESC, totalBytes DESC, hashHex DESC
        LIMIT :limit OFFSET :offset
        """
    )
    fun listByPerFileSizeDescAt(updatedAtMillis: Long, limit: Int, offset: Int): List<DuplicateGroupEntity>

    @Query(
        """
        SELECT * FROM dupe_groups
        WHERE updatedAtMillis = :updatedAtMillis
        ORDER BY sizeBytes ASC, fileCount ASC, totalBytes ASC, hashHex ASC
        LIMIT :limit OFFSET :offset
        """
    )
    fun listByPerFileSizeAscAt(updatedAtMillis: Long, limit: Int, offset: Int): List<DuplicateGroupEntity>

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
