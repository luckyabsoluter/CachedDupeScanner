package opensource.cached_dupe_scanner.cache

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SimilarityExperimentDao {
    @Query("SELECT * FROM similarity_experiment_runs ORDER BY finishedAtMillis DESC")
    fun listRuns(): List<SimilarityExperimentRunEntity>

    @Query("SELECT * FROM similarity_experiment_runs WHERE experimentId = :experimentId LIMIT 1")
    fun getRun(experimentId: String): SimilarityExperimentRunEntity?

    @Query("SELECT COUNT(*) FROM similarity_duration_candidates WHERE experimentId = :experimentId")
    fun countDurationCandidates(experimentId: String): Int

    @Query(
        """
        SELECT * FROM similarity_clusters
        WHERE experimentId = :experimentId
        ORDER BY fileCount DESC, totalBytes DESC, signature ASC
        """
    )
    fun listClusters(experimentId: String): List<SimilarityClusterEntity>

    @Query(
        """
        SELECT * FROM similarity_clusters
        WHERE experimentId = :experimentId
        ORDER BY fileCount DESC, totalBytes DESC, signature ASC
        LIMIT :limit
        """
    )
    fun listFirstClusters(experimentId: String, limit: Int): List<SimilarityClusterEntity>

    @Query(
        """
        SELECT * FROM similarity_clusters
        WHERE experimentId = :experimentId
        ORDER BY fileCount ASC, totalBytes ASC, signature ASC
        LIMIT :limit
        """
    )
    fun listFirstClustersByCountAsc(experimentId: String, limit: Int): List<SimilarityClusterEntity>

    @Query(
        """
        SELECT * FROM similarity_clusters
        WHERE experimentId = :experimentId
        ORDER BY totalBytes DESC, fileCount DESC, signature ASC
        LIMIT :limit
        """
    )
    fun listFirstClustersByTotalBytesDesc(experimentId: String, limit: Int): List<SimilarityClusterEntity>

    @Query(
        """
        SELECT * FROM similarity_clusters
        WHERE experimentId = :experimentId
        ORDER BY totalBytes ASC, fileCount ASC, signature ASC
        LIMIT :limit
        """
    )
    fun listFirstClustersByTotalBytesAsc(experimentId: String, limit: Int): List<SimilarityClusterEntity>

    @Query(
        """
        SELECT * FROM similarity_clusters
        WHERE experimentId = :experimentId
          AND (
            fileCount < :afterFileCount
            OR (fileCount = :afterFileCount AND totalBytes < :afterTotalBytes)
            OR (fileCount = :afterFileCount AND totalBytes = :afterTotalBytes AND signature > :afterSignature)
          )
        ORDER BY fileCount DESC, totalBytes DESC, signature ASC
        LIMIT :limit
        """
    )
    fun listClustersAfter(
        experimentId: String,
        afterFileCount: Int,
        afterTotalBytes: Long,
        afterSignature: String,
        limit: Int
    ): List<SimilarityClusterEntity>

    @Query(
        """
        SELECT * FROM similarity_clusters
        WHERE experimentId = :experimentId
          AND (
            fileCount > :afterFileCount
            OR (fileCount = :afterFileCount AND totalBytes > :afterTotalBytes)
            OR (fileCount = :afterFileCount AND totalBytes = :afterTotalBytes AND signature > :afterSignature)
          )
        ORDER BY fileCount ASC, totalBytes ASC, signature ASC
        LIMIT :limit
        """
    )
    fun listClustersAfterCountAsc(
        experimentId: String,
        afterFileCount: Int,
        afterTotalBytes: Long,
        afterSignature: String,
        limit: Int
    ): List<SimilarityClusterEntity>

    @Query(
        """
        SELECT * FROM similarity_clusters
        WHERE experimentId = :experimentId
          AND (
            totalBytes < :afterTotalBytes
            OR (totalBytes = :afterTotalBytes AND fileCount < :afterFileCount)
            OR (totalBytes = :afterTotalBytes AND fileCount = :afterFileCount AND signature > :afterSignature)
          )
        ORDER BY totalBytes DESC, fileCount DESC, signature ASC
        LIMIT :limit
        """
    )
    fun listClustersAfterTotalBytesDesc(
        experimentId: String,
        afterFileCount: Int,
        afterTotalBytes: Long,
        afterSignature: String,
        limit: Int
    ): List<SimilarityClusterEntity>

    @Query(
        """
        SELECT * FROM similarity_clusters
        WHERE experimentId = :experimentId
          AND (
            totalBytes > :afterTotalBytes
            OR (totalBytes = :afterTotalBytes AND fileCount > :afterFileCount)
            OR (totalBytes = :afterTotalBytes AND fileCount = :afterFileCount AND signature > :afterSignature)
          )
        ORDER BY totalBytes ASC, fileCount ASC, signature ASC
        LIMIT :limit
        """
    )
    fun listClustersAfterTotalBytesAsc(
        experimentId: String,
        afterFileCount: Int,
        afterTotalBytes: Long,
        afterSignature: String,
        limit: Int
    ): List<SimilarityClusterEntity>

    @Query("DELETE FROM similarity_experiment_runs WHERE experimentId = :experimentId")
    fun deleteRun(experimentId: String)

    @Query("DELETE FROM similarity_clusters WHERE experimentId = :experimentId")
    fun deleteClusters(experimentId: String)

    @Query("DELETE FROM similarity_duration_candidates WHERE experimentId = :experimentId")
    fun deleteDurationCandidates(experimentId: String)

    @Query(
        """
        SELECT * FROM similarity_duration_candidates
        WHERE experimentId = :experimentId
        ORDER BY durationMillis ASC, normalizedPath ASC
        """
    )
    fun listDurationCandidates(experimentId: String): List<SimilarityDurationCandidateEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertRun(run: SimilarityExperimentRunEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertClusters(clusters: List<SimilarityClusterEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertDurationCandidates(candidates: List<SimilarityDurationCandidateEntity>)
}
