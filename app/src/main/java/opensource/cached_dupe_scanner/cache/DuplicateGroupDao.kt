package opensource.cached_dupe_scanner.cache

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

/**
 * DAO for materialized duplicate-group snapshot table `dupe_groups`.
 *
 * Operational policy:
 * - Full rebuild (`rebuildFromCache`) is reserved for explicit DB management actions.
 * - Runtime cache mutations should refresh only touched group keys via `delete` + `insertSingleGroupFromCache`.
 */
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
        INSERT OR REPLACE INTO dupe_groups (sizeBytes, hashBytes, fileCount, totalBytes, updatedAtMillis)
        SELECT
            sizeBytes as sizeBytes,
            hashBytes as hashBytes,
            COUNT(*) as fileCount,
            (COUNT(*) * sizeBytes) as totalBytes,
            :updatedAtMillis as updatedAtMillis
        FROM cached_files
        WHERE hashBytes IS NOT NULL
        GROUP BY sizeBytes, hashBytes
        HAVING COUNT(*) > 1
        """
    )
    fun insertFromCache(updatedAtMillis: Long)

    @Query(
        """
        INSERT OR REPLACE INTO dupe_groups (sizeBytes, hashBytes, fileCount, totalBytes, updatedAtMillis)
        SELECT
            sizeBytes as sizeBytes,
            hashBytes as hashBytes,
            COUNT(*) as fileCount,
            (COUNT(*) * sizeBytes) as totalBytes,
            :updatedAtMillis as updatedAtMillis
        FROM cached_files
        WHERE hashBytes = :hashBytes AND sizeBytes = :sizeBytes
        GROUP BY sizeBytes, hashBytes
        HAVING COUNT(*) > 1
        """
    )
    fun insertSingleGroupFromCacheByStoredHash(
        sizeBytes: Long,
        hashBytes: StoredHash,
        updatedAtMillis: Long
    )

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertAll(groups: List<DuplicateGroupEntity>)

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

    fun insertSingleGroupFromCache(sizeBytes: Long, hashHex: String, updatedAtMillis: Long) {
        insertSingleGroupFromCacheByStoredHash(
            sizeBytes,
            StoredHash.fromExternalString(hashHex),
            updatedAtMillis
        )
    }

    @Query("SELECT COUNT(*) FROM dupe_groups")
    fun countGroups(): Int

    @Query(
        """
        SELECT COUNT(*) FROM (
            SELECT sizeBytes, hashBytes
            FROM cached_files
            WHERE hashBytes IS NOT NULL
            GROUP BY sizeBytes, hashBytes
            HAVING COUNT(*) > 1
        )
        """
    )
    fun countGroupsFromCache(): Int

    @Query("SELECT MAX(updatedAtMillis) FROM dupe_groups")
    fun latestUpdatedAtMillis(): Long?

    @Query("SELECT COUNT(*) FROM dupe_groups WHERE updatedAtMillis = :updatedAtMillis")
    fun countGroupsAt(updatedAtMillis: Long): Int

    @Query(
        """
        SELECT * FROM dupe_groups
        ORDER BY fileCount DESC, totalBytes DESC, sizeBytes DESC, hashBytes DESC
        LIMIT :limit OFFSET :offset
        """
    )
    fun listByCountDesc(limit: Int, offset: Int): List<DuplicateGroupEntity>

    @Query(
        """
        SELECT * FROM dupe_groups
        ORDER BY fileCount ASC, totalBytes ASC, sizeBytes ASC, hashBytes ASC
        LIMIT :limit OFFSET :offset
        """
    )
    fun listByCountAsc(limit: Int, offset: Int): List<DuplicateGroupEntity>

    @Query(
        """
        SELECT * FROM dupe_groups
        ORDER BY totalBytes DESC, fileCount DESC, sizeBytes DESC, hashBytes DESC
        LIMIT :limit OFFSET :offset
        """
    )
    fun listByTotalBytesDesc(limit: Int, offset: Int): List<DuplicateGroupEntity>

    @Query(
        """
        SELECT * FROM dupe_groups
        ORDER BY totalBytes ASC, fileCount ASC, sizeBytes ASC, hashBytes ASC
        LIMIT :limit OFFSET :offset
        """
    )
    fun listByTotalBytesAsc(limit: Int, offset: Int): List<DuplicateGroupEntity>

    @Query(
        """
        SELECT * FROM dupe_groups
        ORDER BY sizeBytes DESC, fileCount DESC, totalBytes DESC, hashBytes DESC
        LIMIT :limit OFFSET :offset
        """
    )
    fun listByPerFileSizeDesc(limit: Int, offset: Int): List<DuplicateGroupEntity>

    @Query(
        """
        SELECT * FROM dupe_groups
        ORDER BY sizeBytes ASC, fileCount ASC, totalBytes ASC, hashBytes ASC
        LIMIT :limit OFFSET :offset
        """
    )
    fun listByPerFileSizeAsc(limit: Int, offset: Int): List<DuplicateGroupEntity>

    @Query(
        """
        SELECT * FROM dupe_groups
        WHERE updatedAtMillis = :updatedAtMillis
        ORDER BY fileCount DESC, totalBytes DESC, sizeBytes DESC, hashBytes DESC
        LIMIT :limit OFFSET :offset
        """
    )
    fun listByCountDescAt(updatedAtMillis: Long, limit: Int, offset: Int): List<DuplicateGroupEntity>

    @Query(
        """
        SELECT * FROM dupe_groups
        WHERE updatedAtMillis = :updatedAtMillis
        ORDER BY fileCount ASC, totalBytes ASC, sizeBytes ASC, hashBytes ASC
        LIMIT :limit OFFSET :offset
        """
    )
    fun listByCountAscAt(updatedAtMillis: Long, limit: Int, offset: Int): List<DuplicateGroupEntity>

    @Query(
        """
        SELECT * FROM dupe_groups
        WHERE updatedAtMillis = :updatedAtMillis
        ORDER BY totalBytes DESC, fileCount DESC, sizeBytes DESC, hashBytes DESC
        LIMIT :limit OFFSET :offset
        """
    )
    fun listByTotalBytesDescAt(updatedAtMillis: Long, limit: Int, offset: Int): List<DuplicateGroupEntity>

    @Query(
        """
        SELECT * FROM dupe_groups
        WHERE updatedAtMillis = :updatedAtMillis
        ORDER BY totalBytes ASC, fileCount ASC, sizeBytes ASC, hashBytes ASC
        LIMIT :limit OFFSET :offset
        """
    )
    fun listByTotalBytesAscAt(updatedAtMillis: Long, limit: Int, offset: Int): List<DuplicateGroupEntity>

    @Query(
        """
        SELECT * FROM dupe_groups
        WHERE updatedAtMillis = :updatedAtMillis
        ORDER BY sizeBytes DESC, fileCount DESC, totalBytes DESC, hashBytes DESC
        LIMIT :limit OFFSET :offset
        """
    )
    fun listByPerFileSizeDescAt(updatedAtMillis: Long, limit: Int, offset: Int): List<DuplicateGroupEntity>

    @Query(
        """
        SELECT * FROM dupe_groups
        WHERE updatedAtMillis = :updatedAtMillis
        ORDER BY sizeBytes ASC, fileCount ASC, totalBytes ASC, hashBytes ASC
        LIMIT :limit OFFSET :offset
        """
    )
    fun listByPerFileSizeAscAt(updatedAtMillis: Long, limit: Int, offset: Int): List<DuplicateGroupEntity>

    @Query(
        """
        SELECT * FROM dupe_groups
        WHERE sizeBytes = :sizeBytes AND hashBytes = :hashBytes
        LIMIT 1
        """
    )
    fun getByStoredHash(sizeBytes: Long, hashBytes: StoredHash): DuplicateGroupEntity?

    fun get(sizeBytes: Long, hashHex: String): DuplicateGroupEntity? {
        return getByStoredHash(sizeBytes, StoredHash.fromExternalString(hashHex))
    }

    @Query(
        """
        DELETE FROM dupe_groups
        WHERE sizeBytes = :sizeBytes AND hashBytes = :hashBytes
        """
    )
    fun deleteByStoredHash(sizeBytes: Long, hashBytes: StoredHash)

    fun delete(sizeBytes: Long, hashHex: String) {
        deleteByStoredHash(sizeBytes, StoredHash.fromExternalString(hashHex))
    }

    @Query(
        """
        SELECT *
        FROM dupe_groups
        ORDER BY sizeBytes ASC, hashBytes ASC
        LIMIT :limit OFFSET :offset
        """
    )
    fun listPageByKey(limit: Int, offset: Int): List<DuplicateGroupEntity>

    @Query(
        """
        SELECT *
        FROM dupe_groups
        WHERE updatedAtMillis = :updatedAtMillis
        ORDER BY sizeBytes ASC, hashBytes ASC
        LIMIT :limit
        """
    )
    fun listPageByKeyAt(updatedAtMillis: Long, limit: Int): List<DuplicateGroupEntity>

    @Query(
        """
        SELECT *
        FROM dupe_groups
        WHERE updatedAtMillis = :updatedAtMillis
          AND (
              sizeBytes > :afterSizeBytes
              OR (sizeBytes = :afterSizeBytes AND hashBytes > :afterHashBytes)
          )
        ORDER BY sizeBytes ASC, hashBytes ASC
        LIMIT :limit
        """
    )
    fun listPageByKeyAtAfterStoredHash(
        updatedAtMillis: Long,
        afterSizeBytes: Long,
        afterHashBytes: StoredHash,
        limit: Int
    ): List<DuplicateGroupEntity>

    fun listPageByKeyAtAfter(
        updatedAtMillis: Long,
        afterSizeBytes: Long,
        afterHashHex: String,
        limit: Int
    ): List<DuplicateGroupEntity> {
        return listPageByKeyAtAfterStoredHash(
            updatedAtMillis,
            afterSizeBytes,
            StoredHash.fromExternalString(afterHashHex),
            limit
        )
    }

    @Query(
        """
        SELECT
            sizeBytes as sizeBytes,
            hashBytes as hashBytes,
            COUNT(*) as fileCount,
            (COUNT(*) * sizeBytes) as totalBytes
        FROM cached_files
        WHERE hashBytes IS NOT NULL
        GROUP BY sizeBytes, hashBytes
        HAVING COUNT(*) > 1
        ORDER BY sizeBytes ASC, hashBytes ASC
        LIMIT :limit OFFSET :offset
        """
    )
    fun listGroupsFromCache(limit: Int, offset: Int): List<DuplicateGroupSnapshotRow>

    @Query(
        """
        SELECT COUNT(*)
        FROM cached_files
        WHERE sizeBytes = :sizeBytes AND hashBytes = :hashBytes
        """
    )
    fun countMembersByStoredHash(sizeBytes: Long, hashBytes: StoredHash): Int

    fun countMembers(sizeBytes: Long, hashHex: String): Int {
        return countMembersByStoredHash(sizeBytes, StoredHash.fromExternalString(hashHex))
    }
}

data class DuplicateGroupSnapshotRow(
    val sizeBytes: Long,
    val hashBytes: StoredHash,
    val fileCount: Int,
    val totalBytes: Long
) {
    val hashHex: String
        get() = hashBytes.toExternalString()
}
