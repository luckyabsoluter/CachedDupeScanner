package opensource.cached_dupe_scanner.cache

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update

/**
 * DAO for `cached_files`.
 *
 * Query groups:
 * - Point lookups and counts (`getByNormalizedPath`, `countAll`).
 * - Cursor paging for file manager and maintenance (`getPageAfter`, `getPageBy*`).
 * - Duplicate-member listing by (`sizeBytes`, `hashBytes`).
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

    @Query(
        """
        SELECT COUNT(*)
        FROM cached_files
        WHERE sizeBytes >= :minSizeBytes
          AND (
              lower(normalizedPath) LIKE '%.3gp'
              OR lower(normalizedPath) LIKE '%.avi'
              OR lower(normalizedPath) LIKE '%.flv'
              OR lower(normalizedPath) LIKE '%.m2ts'
              OR lower(normalizedPath) LIKE '%.m4v'
              OR lower(normalizedPath) LIKE '%.mkv'
              OR lower(normalizedPath) LIKE '%.mov'
              OR lower(normalizedPath) LIKE '%.mp4'
              OR lower(normalizedPath) LIKE '%.mpeg'
              OR lower(normalizedPath) LIKE '%.mpg'
              OR lower(normalizedPath) LIKE '%.mts'
              OR lower(normalizedPath) LIKE '%.ts'
              OR lower(normalizedPath) LIKE '%.webm'
              OR lower(normalizedPath) LIKE '%.wmv'
          )
        """
    )
    fun countVideoCandidates(minSizeBytes: Long): Int

    @Query(
        """
        SELECT COUNT(*)
        FROM cached_files
        WHERE sizeBytes >= :minSizeBytes
          AND (
              lower(normalizedPath) LIKE '%.bmp'
              OR lower(normalizedPath) LIKE '%.gif'
              OR lower(normalizedPath) LIKE '%.heic'
              OR lower(normalizedPath) LIKE '%.heif'
              OR lower(normalizedPath) LIKE '%.jpeg'
              OR lower(normalizedPath) LIKE '%.jpg'
              OR lower(normalizedPath) LIKE '%.png'
              OR lower(normalizedPath) LIKE '%.webp'
          )
        """
    )
    fun countImageCandidates(minSizeBytes: Long): Int

    @Query(
        """
        SELECT *
        FROM cached_files
        WHERE normalizedPath > :afterPath
          AND sizeBytes >= :minSizeBytes
          AND (
              lower(normalizedPath) LIKE '%.3gp'
              OR lower(normalizedPath) LIKE '%.avi'
              OR lower(normalizedPath) LIKE '%.flv'
              OR lower(normalizedPath) LIKE '%.m2ts'
              OR lower(normalizedPath) LIKE '%.m4v'
              OR lower(normalizedPath) LIKE '%.mkv'
              OR lower(normalizedPath) LIKE '%.mov'
              OR lower(normalizedPath) LIKE '%.mp4'
              OR lower(normalizedPath) LIKE '%.mpeg'
              OR lower(normalizedPath) LIKE '%.mpg'
              OR lower(normalizedPath) LIKE '%.mts'
              OR lower(normalizedPath) LIKE '%.ts'
              OR lower(normalizedPath) LIKE '%.webm'
              OR lower(normalizedPath) LIKE '%.wmv'
          )
        ORDER BY normalizedPath ASC
        LIMIT :limit
        """
    )
    fun listVideoCandidatesAfter(
        minSizeBytes: Long,
        afterPath: String,
        limit: Int
    ): List<CachedFileEntity>

    @Query(
        """
        SELECT *
        FROM cached_files
        WHERE normalizedPath > :afterPath
          AND sizeBytes >= :minSizeBytes
          AND (
              lower(normalizedPath) LIKE '%.bmp'
              OR lower(normalizedPath) LIKE '%.gif'
              OR lower(normalizedPath) LIKE '%.heic'
              OR lower(normalizedPath) LIKE '%.heif'
              OR lower(normalizedPath) LIKE '%.jpeg'
              OR lower(normalizedPath) LIKE '%.jpg'
              OR lower(normalizedPath) LIKE '%.png'
              OR lower(normalizedPath) LIKE '%.webp'
          )
        ORDER BY normalizedPath ASC
        LIMIT :limit
        """
    )
    fun listImageCandidatesAfter(
        minSizeBytes: Long,
        afterPath: String,
        limit: Int
    ): List<CachedFileEntity>

    @Query("SELECT * FROM cached_files WHERE normalizedPath > :afterPath ORDER BY normalizedPath LIMIT :limit")
    fun getPageAfter(afterPath: String, limit: Int): List<CachedFileEntity>

    @Query(
        """
        SELECT *
        FROM cached_files AS candidate
        WHERE candidate.normalizedPath > :afterPath
          AND candidate.hashBytes IS NULL
          AND EXISTS (
              SELECT 1
              FROM cached_files AS peer
              WHERE peer.sizeBytes = candidate.sizeBytes
                AND peer.normalizedPath != candidate.normalizedPath
          )
        ORDER BY candidate.normalizedPath ASC
        LIMIT :limit
        """
    )
    fun listMissingHashSizeCollisionCandidatesAfter(
        afterPath: String,
        limit: Int
    ): List<CachedFileEntity>

    @Query(
        """
        SELECT COUNT(*)
        FROM cached_files AS candidate
        WHERE candidate.hashBytes IS NULL
          AND EXISTS (
              SELECT 1
              FROM cached_files AS peer
              WHERE peer.sizeBytes = candidate.sizeBytes
                AND peer.normalizedPath != candidate.normalizedPath
          )
        """
    )
    fun countMissingHashSizeCollisionCandidates(): Int

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
        WHERE sizeBytes = :sizeBytes AND hashBytes = :hashBytes
        ORDER BY normalizedPath ASC
        LIMIT :limit
        """
    )
    fun listMembersBySizeAndStoredHash(
        sizeBytes: Long,
        hashBytes: StoredHash,
        limit: Int
    ): List<CachedFileEntity>

    fun listMembersBySizeAndHash(
        sizeBytes: Long,
        hashHex: String,
        limit: Int
    ): List<CachedFileEntity> {
        return listMembersBySizeAndStoredHash(sizeBytes, StoredHash.fromExternalString(hashHex), limit)
    }

    @Query(
        """
        SELECT * FROM cached_files
        WHERE sizeBytes = :sizeBytes AND hashBytes = :hashBytes AND normalizedPath > :afterPath
        ORDER BY normalizedPath ASC
        LIMIT :limit
        """
    )
    fun listMembersBySizeAndStoredHashAfter(
        sizeBytes: Long,
        hashBytes: StoredHash,
        afterPath: String,
        limit: Int
    ): List<CachedFileEntity>

    fun listMembersBySizeAndHashAfter(
        sizeBytes: Long,
        hashHex: String,
        afterPath: String,
        limit: Int
    ): List<CachedFileEntity> {
        return listMembersBySizeAndStoredHashAfter(
            sizeBytes,
            StoredHash.fromExternalString(hashHex),
            afterPath,
            limit
        )
    }

    @Query(
        """
        SELECT
            sizeBytes as sizeBytes,
            hashBytes as hashBytes
        FROM cached_files
        WHERE hashBytes IS NOT NULL
        GROUP BY sizeBytes, hashBytes
        HAVING COUNT(*) > 1
        ORDER BY sizeBytes ASC, hashBytes ASC
        """
    )
    fun listDuplicateGroupKeysFromCache(): List<DuplicateGroupKeyRow>

    @Query(
        """
        SELECT
            sizeBytes as sizeBytes,
            hashBytes as hashBytes
        FROM cached_files
        WHERE hashBytes IS NOT NULL
        GROUP BY sizeBytes, hashBytes
        HAVING COUNT(*) > 1
        ORDER BY sizeBytes ASC, hashBytes ASC
        LIMIT :limit
        """
    )
    fun listDuplicateGroupKeysFromCachePage(limit: Int): List<DuplicateGroupKeyRow>

    @Query(
        """
        SELECT
            sizeBytes as sizeBytes,
            hashBytes as hashBytes
        FROM cached_files
        WHERE hashBytes IS NOT NULL
        GROUP BY sizeBytes, hashBytes
        HAVING COUNT(*) > 1
           AND (
               sizeBytes > :afterSizeBytes
               OR (sizeBytes = :afterSizeBytes AND hashBytes > :afterHashBytes)
           )
        ORDER BY sizeBytes ASC, hashBytes ASC
        LIMIT :limit
        """
    )
    fun listDuplicateGroupKeysFromCachePageAfterStoredHash(
        afterSizeBytes: Long,
        afterHashBytes: StoredHash,
        limit: Int
    ): List<DuplicateGroupKeyRow>

    fun listDuplicateGroupKeysFromCachePageAfter(
        afterSizeBytes: Long,
        afterHashHex: String,
        limit: Int
    ): List<DuplicateGroupKeyRow> {
        return listDuplicateGroupKeysFromCachePageAfterStoredHash(
            afterSizeBytes,
            StoredHash.fromExternalString(afterHashHex),
            limit
        )
    }

    @Query(
        """
        SELECT COALESCE(SUM(groupCount), 0)
        FROM (
            SELECT COUNT(*) as groupCount
            FROM cached_files
            WHERE hashBytes IS NOT NULL
            GROUP BY sizeBytes, hashBytes
            HAVING COUNT(*) > 1
        )
        """
    )
    fun countDuplicateMembersFromCache(): Int

    @Query(
        """
        SELECT COUNT(*)
        FROM cached_files
        WHERE sizeBytes = :sizeBytes AND hashBytes = :hashBytes
        """
    )
    fun countBySizeAndStoredHash(sizeBytes: Long, hashBytes: StoredHash): Int

    fun countBySizeAndHash(sizeBytes: Long, hashHex: String): Int {
        return countBySizeAndStoredHash(sizeBytes, StoredHash.fromExternalString(hashHex))
    }

    @Query("SELECT fileId, normalizedPath FROM cached_files WHERE normalizedPath IN (:normalizedPaths)")
    fun findFileIdsByPaths(normalizedPaths: List<String>): List<CachedFileIdRow>

    @Insert
    fun insertAll(entities: List<CachedFileEntity>)

    @Update
    fun updateAll(entities: List<CachedFileEntity>)

    @Transaction
    fun upsert(entity: CachedFileEntity) {
        upsertAll(listOf(entity))
    }

    @Transaction
    fun upsertAll(entities: List<CachedFileEntity>) {
        if (entities.isEmpty()) return
        val latestByPath = linkedMapOf<String, CachedFileEntity>()
        entities.forEach { entity -> latestByPath[entity.normalizedPath] = entity }
        val existingIdsByPath = latestByPath.keys
            .chunked(FILE_ID_LOOKUP_BIND_LIMIT)
            .flatMap(::findFileIdsByPaths)
            .associate { row -> row.normalizedPath to row.fileId }
        val inserts = mutableListOf<CachedFileEntity>()
        val updates = mutableListOf<CachedFileEntity>()
        latestByPath.values.forEach { entity ->
            val existingId = existingIdsByPath[entity.normalizedPath]
            if (existingId == null) {
                inserts += entity.copy(fileId = 0L)
            } else {
                updates += entity.copy(fileId = existingId)
            }
        }
        if (inserts.isNotEmpty()) insertAll(inserts)
        if (updates.isNotEmpty()) updateAll(updates)
    }

    @Query("DELETE FROM cached_files WHERE normalizedPath = :normalizedPath")
    fun deleteByNormalizedPath(normalizedPath: String)

    @Query("DELETE FROM cached_files WHERE normalizedPath IN (:normalizedPaths)")
    fun deleteByNormalizedPaths(normalizedPaths: List<String>)

    @Query("DELETE FROM cached_files")
    fun clear()

    @Query("SELECT sizeBytes as sizeBytes, COUNT(*) as count FROM cached_files WHERE sizeBytes IN (:sizes) GROUP BY sizeBytes")
    fun countBySizes(sizes: List<Long>): List<SizeCount>

    @Query(
        """
        SELECT *
        FROM cached_files
        WHERE normalizedPath > :afterPath
          AND sizeBytes IN (:sizes)
          AND hashBytes IS NULL
        ORDER BY normalizedPath ASC
        LIMIT :limit
        """
    )
    fun listMissingHashCandidatesBySizesAfter(
        sizes: List<Long>,
        afterPath: String,
        limit: Int
    ): List<CachedFileEntity>

    @Query("SELECT normalizedPath as normalizedPath, sizeBytes as sizeBytes FROM cached_files WHERE normalizedPath IN (:paths)")
    fun findSizesByPaths(paths: List<String>): List<PathSize>

    @Query("SELECT * FROM cached_files WHERE normalizedPath IN (:paths) OR path IN (:paths)")
    fun findByNormalizedOrDisplayPaths(paths: List<String>): List<CachedFileEntity>

    @Query(
        """
        SELECT normalizedPath as normalizedPath, sizeBytes as sizeBytes, hashBytes as hashBytes
        FROM cached_files
        WHERE normalizedPath IN (:paths)
        """
    )
    fun findGroupKeysByPaths(paths: List<String>): List<PathGroupKey>
}

data class DuplicateGroupKeyRow(
    val sizeBytes: Long,
    val hashBytes: StoredHash
) {
    val hashHex: String
        get() = hashBytes.toExternalString()
}

data class CachedFileIdRow(
    val fileId: Long,
    val normalizedPath: String
)

private const val FILE_ID_LOOKUP_BIND_LIMIT = 900
