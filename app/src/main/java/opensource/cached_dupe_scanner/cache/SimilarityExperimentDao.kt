package opensource.cached_dupe_scanner.cache

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SimilarityExperimentDao {
    @Query(
        """
        SELECT
            runs.experimentId AS experimentId,
            runs.experimentName AS experimentName,
            runs.startedAtMillis AS startedAtMillis,
            runs.finishedAtMillis AS finishedAtMillis,
            runs.candidateCount AS candidateCount,
            runs.processedCount AS processedCount,
            runs.skippedCount AS skippedCount,
            COALESCE(active.clusterCount, 0) AS clusterCount,
            COALESCE(active.duplicateFileCount, 0) AS duplicateFileCount
        FROM similarity_experiment_runs AS runs
        LEFT JOIN (
            SELECT
                grouped.experimentId AS experimentId,
                COUNT(*) AS clusterCount,
                COALESCE(SUM(grouped.fileCount), 0) AS duplicateFileCount
            FROM (
                SELECT
                    cluster.experimentId AS experimentId,
                    cluster.signature AS signature,
                    COUNT(file.normalizedPath) AS fileCount
                FROM similarity_clusters AS cluster
                INNER JOIN similarity_cluster_members AS member
                    ON member.experimentId = cluster.experimentId
                    AND member.signature = cluster.signature
                INNER JOIN cached_files AS file
                    ON file.normalizedPath = member.normalizedPath
                GROUP BY cluster.experimentId, cluster.signature
                HAVING COUNT(file.normalizedPath) > 1
            ) AS grouped
            GROUP BY grouped.experimentId
        ) AS active
            ON active.experimentId = runs.experimentId
        ORDER BY runs.finishedAtMillis DESC
        """
    )
    fun listRuns(): List<SimilarityExperimentRunEntity>

    @Query(
        """
        SELECT
            runs.experimentId AS experimentId,
            runs.experimentName AS experimentName,
            runs.startedAtMillis AS startedAtMillis,
            runs.finishedAtMillis AS finishedAtMillis,
            runs.candidateCount AS candidateCount,
            runs.processedCount AS processedCount,
            runs.skippedCount AS skippedCount,
            COALESCE(active.clusterCount, 0) AS clusterCount,
            COALESCE(active.duplicateFileCount, 0) AS duplicateFileCount
        FROM similarity_experiment_runs AS runs
        LEFT JOIN (
            SELECT
                grouped.experimentId AS experimentId,
                COUNT(*) AS clusterCount,
                COALESCE(SUM(grouped.fileCount), 0) AS duplicateFileCount
            FROM (
                SELECT
                    cluster.experimentId AS experimentId,
                    cluster.signature AS signature,
                    COUNT(file.normalizedPath) AS fileCount
                FROM similarity_clusters AS cluster
                INNER JOIN similarity_cluster_members AS member
                    ON member.experimentId = cluster.experimentId
                    AND member.signature = cluster.signature
                INNER JOIN cached_files AS file
                    ON file.normalizedPath = member.normalizedPath
                WHERE cluster.experimentId = :experimentId
                GROUP BY cluster.experimentId, cluster.signature
                HAVING COUNT(file.normalizedPath) > 1
            ) AS grouped
            GROUP BY grouped.experimentId
        ) AS active
            ON active.experimentId = runs.experimentId
        WHERE runs.experimentId = :experimentId
        LIMIT 1
        """
    )
    fun getRun(experimentId: String): SimilarityExperimentRunEntity?

    @Query("SELECT COUNT(*) FROM similarity_duration_candidates WHERE experimentId = :experimentId")
    fun countDurationCandidates(experimentId: String): Int

    @Query(
        """
        SELECT * FROM (
            SELECT
                cluster.experimentId AS experimentId,
                cluster.signature AS signature,
                COUNT(file.normalizedPath) AS fileCount,
                COALESCE(SUM(file.sizeBytes), 0) AS totalBytes,
                cluster.updatedAtMillis AS updatedAtMillis
            FROM similarity_clusters AS cluster
            INNER JOIN similarity_cluster_members AS member
                ON member.experimentId = cluster.experimentId
                AND member.signature = cluster.signature
            INNER JOIN cached_files AS file
                ON file.normalizedPath = member.normalizedPath
            WHERE cluster.experimentId = :experimentId
            GROUP BY cluster.experimentId, cluster.signature
            HAVING COUNT(file.normalizedPath) > 1
        ) AS active_cluster
        ORDER BY fileCount DESC, totalBytes DESC, signature ASC
        """
    )
    fun listClusters(experimentId: String): List<SimilarityClusterEntity>

    @Query(
        """
        SELECT * FROM (
            SELECT
                cluster.experimentId AS experimentId,
                cluster.signature AS signature,
                COUNT(file.normalizedPath) AS fileCount,
                COALESCE(SUM(file.sizeBytes), 0) AS totalBytes,
                cluster.updatedAtMillis AS updatedAtMillis
            FROM similarity_clusters AS cluster
            INNER JOIN similarity_cluster_members AS member
                ON member.experimentId = cluster.experimentId
                AND member.signature = cluster.signature
            INNER JOIN cached_files AS file
                ON file.normalizedPath = member.normalizedPath
            WHERE cluster.experimentId = :experimentId
            GROUP BY cluster.experimentId, cluster.signature
            HAVING COUNT(file.normalizedPath) > 1
        ) AS active_cluster
        ORDER BY fileCount DESC, totalBytes DESC, signature ASC
        LIMIT :limit
        """
    )
    fun listFirstClusters(experimentId: String, limit: Int): List<SimilarityClusterEntity>

    @Query(
        """
        SELECT * FROM (
            SELECT
                cluster.experimentId AS experimentId,
                cluster.signature AS signature,
                COUNT(file.normalizedPath) AS fileCount,
                COALESCE(SUM(file.sizeBytes), 0) AS totalBytes,
                cluster.updatedAtMillis AS updatedAtMillis
            FROM similarity_clusters AS cluster
            INNER JOIN similarity_cluster_members AS member
                ON member.experimentId = cluster.experimentId
                AND member.signature = cluster.signature
            INNER JOIN cached_files AS file
                ON file.normalizedPath = member.normalizedPath
            WHERE cluster.experimentId = :experimentId
            GROUP BY cluster.experimentId, cluster.signature
            HAVING COUNT(file.normalizedPath) > 1
        ) AS active_cluster
        ORDER BY fileCount ASC, totalBytes ASC, signature ASC
        LIMIT :limit
        """
    )
    fun listFirstClustersByCountAsc(experimentId: String, limit: Int): List<SimilarityClusterEntity>

    @Query(
        """
        SELECT * FROM (
            SELECT
                cluster.experimentId AS experimentId,
                cluster.signature AS signature,
                COUNT(file.normalizedPath) AS fileCount,
                COALESCE(SUM(file.sizeBytes), 0) AS totalBytes,
                cluster.updatedAtMillis AS updatedAtMillis
            FROM similarity_clusters AS cluster
            INNER JOIN similarity_cluster_members AS member
                ON member.experimentId = cluster.experimentId
                AND member.signature = cluster.signature
            INNER JOIN cached_files AS file
                ON file.normalizedPath = member.normalizedPath
            WHERE cluster.experimentId = :experimentId
            GROUP BY cluster.experimentId, cluster.signature
            HAVING COUNT(file.normalizedPath) > 1
        ) AS active_cluster
        ORDER BY totalBytes DESC, fileCount DESC, signature ASC
        LIMIT :limit
        """
    )
    fun listFirstClustersByTotalBytesDesc(experimentId: String, limit: Int): List<SimilarityClusterEntity>

    @Query(
        """
        SELECT * FROM (
            SELECT
                cluster.experimentId AS experimentId,
                cluster.signature AS signature,
                COUNT(file.normalizedPath) AS fileCount,
                COALESCE(SUM(file.sizeBytes), 0) AS totalBytes,
                cluster.updatedAtMillis AS updatedAtMillis
            FROM similarity_clusters AS cluster
            INNER JOIN similarity_cluster_members AS member
                ON member.experimentId = cluster.experimentId
                AND member.signature = cluster.signature
            INNER JOIN cached_files AS file
                ON file.normalizedPath = member.normalizedPath
            WHERE cluster.experimentId = :experimentId
            GROUP BY cluster.experimentId, cluster.signature
            HAVING COUNT(file.normalizedPath) > 1
        ) AS active_cluster
        ORDER BY totalBytes ASC, fileCount ASC, signature ASC
        LIMIT :limit
        """
    )
    fun listFirstClustersByTotalBytesAsc(experimentId: String, limit: Int): List<SimilarityClusterEntity>

    @Query(
        """
        SELECT * FROM (
            SELECT
                cluster.experimentId AS experimentId,
                cluster.signature AS signature,
                COUNT(file.normalizedPath) AS fileCount,
                COALESCE(SUM(file.sizeBytes), 0) AS totalBytes,
                cluster.updatedAtMillis AS updatedAtMillis
            FROM similarity_clusters AS cluster
            INNER JOIN similarity_cluster_members AS member
                ON member.experimentId = cluster.experimentId
                AND member.signature = cluster.signature
            INNER JOIN cached_files AS file
                ON file.normalizedPath = member.normalizedPath
            WHERE cluster.experimentId = :experimentId
            GROUP BY cluster.experimentId, cluster.signature
            HAVING COUNT(file.normalizedPath) > 1
        ) AS active_cluster
        WHERE (
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
        SELECT * FROM (
            SELECT
                cluster.experimentId AS experimentId,
                cluster.signature AS signature,
                COUNT(file.normalizedPath) AS fileCount,
                COALESCE(SUM(file.sizeBytes), 0) AS totalBytes,
                cluster.updatedAtMillis AS updatedAtMillis
            FROM similarity_clusters AS cluster
            INNER JOIN similarity_cluster_members AS member
                ON member.experimentId = cluster.experimentId
                AND member.signature = cluster.signature
            INNER JOIN cached_files AS file
                ON file.normalizedPath = member.normalizedPath
            WHERE cluster.experimentId = :experimentId
            GROUP BY cluster.experimentId, cluster.signature
            HAVING COUNT(file.normalizedPath) > 1
        ) AS active_cluster
        WHERE (
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
        SELECT * FROM (
            SELECT
                cluster.experimentId AS experimentId,
                cluster.signature AS signature,
                COUNT(file.normalizedPath) AS fileCount,
                COALESCE(SUM(file.sizeBytes), 0) AS totalBytes,
                cluster.updatedAtMillis AS updatedAtMillis
            FROM similarity_clusters AS cluster
            INNER JOIN similarity_cluster_members AS member
                ON member.experimentId = cluster.experimentId
                AND member.signature = cluster.signature
            INNER JOIN cached_files AS file
                ON file.normalizedPath = member.normalizedPath
            WHERE cluster.experimentId = :experimentId
            GROUP BY cluster.experimentId, cluster.signature
            HAVING COUNT(file.normalizedPath) > 1
        ) AS active_cluster
        WHERE (
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
        SELECT * FROM (
            SELECT
                cluster.experimentId AS experimentId,
                cluster.signature AS signature,
                COUNT(file.normalizedPath) AS fileCount,
                COALESCE(SUM(file.sizeBytes), 0) AS totalBytes,
                cluster.updatedAtMillis AS updatedAtMillis
            FROM similarity_clusters AS cluster
            INNER JOIN similarity_cluster_members AS member
                ON member.experimentId = cluster.experimentId
                AND member.signature = cluster.signature
            INNER JOIN cached_files AS file
                ON file.normalizedPath = member.normalizedPath
            WHERE cluster.experimentId = :experimentId
            GROUP BY cluster.experimentId, cluster.signature
            HAVING COUNT(file.normalizedPath) > 1
        ) AS active_cluster
        WHERE (
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

    @Query("DELETE FROM similarity_cluster_members WHERE experimentId = :experimentId")
    fun deleteClusterMembers(experimentId: String)

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

    @Query(
        """
        SELECT
            file.normalizedPath AS normalizedPath,
            file.path AS path,
            file.sizeBytes AS sizeBytes,
            file.lastModifiedMillis AS lastModifiedMillis,
            file.hashHex AS hashHex,
            member.durationMillis AS durationMillis
        FROM similarity_cluster_members AS member
        INNER JOIN cached_files AS file
            ON file.normalizedPath = member.normalizedPath
        WHERE member.experimentId = :experimentId
            AND member.signature = :signature
        ORDER BY member.position ASC, member.normalizedPath ASC
        LIMIT :limit OFFSET :offset
        """
    )
    fun listClusterMemberRowsAsc(
        experimentId: String,
        signature: String,
        offset: Int,
        limit: Int
    ): List<SimilarityClusterMemberFileRow>

    @Query(
        """
        SELECT
            file.normalizedPath AS normalizedPath,
            file.path AS path,
            file.sizeBytes AS sizeBytes,
            file.lastModifiedMillis AS lastModifiedMillis,
            file.hashHex AS hashHex,
            member.durationMillis AS durationMillis
        FROM similarity_cluster_members AS member
        INNER JOIN cached_files AS file
            ON file.normalizedPath = member.normalizedPath
        WHERE member.experimentId = :experimentId
            AND member.signature = :signature
        ORDER BY member.position DESC, member.normalizedPath DESC
        LIMIT :limit OFFSET :offset
        """
    )
    fun listClusterMemberRowsDesc(
        experimentId: String,
        signature: String,
        offset: Int,
        limit: Int
    ): List<SimilarityClusterMemberFileRow>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertRun(run: SimilarityExperimentRunEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertClusters(clusters: List<SimilarityClusterEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertClusterMembers(members: List<SimilarityClusterMemberEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertDurationCandidates(candidates: List<SimilarityDurationCandidateEntity>)
}
