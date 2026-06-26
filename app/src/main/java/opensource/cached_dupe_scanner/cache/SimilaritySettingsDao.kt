package opensource.cached_dupe_scanner.cache

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

data class SimilarityExactThumbnailFeatureRow(
    val normalizedPath: String,
    val thumbnailSignature: String,
    val sizeBytes: Long
)

data class SimilarityDurationFeatureRow(
    val normalizedPath: String,
    val durationMillis: Long,
    val sizeBytes: Long
)

data class SimilarityClusterSummaryRow(
    val clusterCount: Int,
    val fileCount: Int
)

data class SimilarityClusterRepairRow(
    val clusterId: Long,
    val fileCount: Int,
    val totalBytes: Long
)

@Dao
interface SimilaritySettingsDao {
    @Query("SELECT * FROM similarity_settings ORDER BY updatedAtMillis DESC, settingId DESC")
    fun listSettings(): List<SimilaritySettingEntity>

    @Query("SELECT * FROM similarity_settings WHERE enabled = 1 ORDER BY updatedAtMillis DESC, settingId DESC")
    fun listEnabledSettings(): List<SimilaritySettingEntity>

    @Query("SELECT * FROM similarity_settings WHERE settingId = :settingId LIMIT 1")
    fun getSetting(settingId: Long): SimilaritySettingEntity?

    @Query(
        """
        SELECT *
        FROM similarity_settings
        WHERE methodId = :methodId
          AND mediaScope = :mediaScope
          AND minSizeBytes = :minSizeBytes
          AND paramsHash = :paramsHash
        LIMIT 1
        """
    )
    fun getSettingByIdentity(
        methodId: String,
        mediaScope: String,
        minSizeBytes: Long,
        paramsHash: String
    ): SimilaritySettingEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertSetting(setting: SimilaritySettingEntity): Long

    @Update
    fun updateSetting(setting: SimilaritySettingEntity)

    @Query("UPDATE similarity_settings SET enabled = :enabled, updatedAtMillis = :updatedAtMillis WHERE settingId = :settingId")
    fun updateSettingEnabled(settingId: Long, enabled: Boolean, updatedAtMillis: Long)

    @Query("UPDATE similarity_settings SET displayName = :displayName, updatedAtMillis = :updatedAtMillis WHERE settingId = :settingId")
    fun updateSettingDisplayName(settingId: Long, displayName: String, updatedAtMillis: Long)

    @Query("DELETE FROM similarity_settings WHERE settingId = :settingId")
    fun deleteSetting(settingId: Long)

    @Query("SELECT COUNT(*) FROM similarity_settings")
    fun countSettings(): Int

    @Query("SELECT * FROM similarity_setting_files WHERE settingId = :settingId AND normalizedPath = :normalizedPath LIMIT 1")
    fun getSettingFile(settingId: Long, normalizedPath: String): SimilaritySettingFileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertSettingFiles(files: List<SimilaritySettingFileEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertExactThumbnailFeatures(features: List<SimilarityExactThumbnailFeatureEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertDurationFeatures(features: List<SimilarityDurationFeatureEntity>)

    @Query("DELETE FROM similarity_setting_files WHERE settingId = :settingId")
    fun deleteSettingFiles(settingId: Long)

    @Query("DELETE FROM similarity_setting_files")
    fun deleteAllSettingFiles()

    @Query("DELETE FROM similarity_exact_thumbnail_features WHERE settingId = :settingId")
    fun deleteExactThumbnailFeatures(settingId: Long)

    @Query("DELETE FROM similarity_exact_thumbnail_features")
    fun deleteAllExactThumbnailFeatures()

    @Query("DELETE FROM similarity_duration_features WHERE settingId = :settingId")
    fun deleteDurationFeatures(settingId: Long)

    @Query("DELETE FROM similarity_duration_features")
    fun deleteAllDurationFeatures()

    @Query("DELETE FROM similarity_setting_files WHERE normalizedPath IN (:normalizedPaths)")
    fun deleteSettingFilesByPaths(normalizedPaths: List<String>)

    @Query("DELETE FROM similarity_exact_thumbnail_features WHERE normalizedPath IN (:normalizedPaths)")
    fun deleteExactThumbnailFeaturesByPaths(normalizedPaths: List<String>)

    @Query("DELETE FROM similarity_duration_features WHERE normalizedPath IN (:normalizedPaths)")
    fun deleteDurationFeaturesByPaths(normalizedPaths: List<String>)

    @Query("DELETE FROM similarity_cluster_members WHERE normalizedPath IN (:normalizedPaths)")
    fun deleteClusterMembersByPaths(normalizedPaths: List<String>)

    @Query("SELECT DISTINCT clusterId FROM similarity_cluster_members WHERE normalizedPath IN (:normalizedPaths)")
    fun listClusterIdsForMemberPaths(normalizedPaths: List<String>): List<Long>

    @Query("SELECT * FROM similarity_clusters WHERE settingId = :settingId AND clusterKey = :clusterKey LIMIT 1")
    fun getClusterByKey(settingId: Long, clusterKey: String): SimilarityClusterEntity?

    @Query("SELECT * FROM similarity_clusters WHERE settingId = :settingId AND clusterId = :clusterId AND fileCount > 1 LIMIT 1")
    fun getStoredCluster(settingId: Long, clusterId: Long): SimilarityClusterEntity?

    @Query("SELECT * FROM similarity_clusters WHERE settingId = :settingId")
    fun listStoredClusters(settingId: Long): List<SimilarityClusterEntity>

    @Query(
        """
        SELECT
            cluster.clusterId AS clusterId,
            COUNT(file.normalizedPath) AS fileCount,
            COALESCE(SUM(file.sizeBytes), 0) AS totalBytes
        FROM similarity_clusters AS cluster
        LEFT JOIN similarity_cluster_members AS member
            ON member.clusterId = cluster.clusterId
        LEFT JOIN cached_files AS file
            ON file.normalizedPath = member.normalizedPath
        WHERE cluster.clusterId IN (:clusterIds)
        GROUP BY cluster.clusterId
        """
    )
    fun listClusterRepairRows(clusterIds: List<Long>): List<SimilarityClusterRepairRow>

    @Query(
        """
        SELECT
            COUNT(*) AS clusterCount,
            COALESCE(SUM(fileCount), 0) AS fileCount
        FROM similarity_clusters
        WHERE settingId = :settingId
          AND fileCount > 1
        """
    )
    fun storedClusterSummary(settingId: Long): SimilarityClusterSummaryRow

    @Query(
        """
        SELECT *
        FROM similarity_clusters
        WHERE settingId = :settingId
          AND fileCount > 1
        ORDER BY fileCount ASC, totalBytes ASC, clusterKey ASC
        LIMIT :limit OFFSET :offset
        """
    )
    fun listStoredClustersByFileCountAsc(
        settingId: Long,
        offset: Int,
        limit: Int
    ): List<SimilarityClusterEntity>

    @Query(
        """
        SELECT *
        FROM similarity_clusters
        WHERE settingId = :settingId
          AND fileCount > 1
        ORDER BY fileCount DESC, totalBytes DESC, clusterKey ASC
        LIMIT :limit OFFSET :offset
        """
    )
    fun listStoredClustersByFileCountDesc(
        settingId: Long,
        offset: Int,
        limit: Int
    ): List<SimilarityClusterEntity>

    @Query(
        """
        SELECT *
        FROM similarity_clusters
        WHERE settingId = :settingId
          AND fileCount > 1
        ORDER BY totalBytes ASC, fileCount ASC, clusterKey ASC
        LIMIT :limit OFFSET :offset
        """
    )
    fun listStoredClustersByTotalSizeAsc(
        settingId: Long,
        offset: Int,
        limit: Int
    ): List<SimilarityClusterEntity>

    @Query(
        """
        SELECT *
        FROM similarity_clusters
        WHERE settingId = :settingId
          AND fileCount > 1
        ORDER BY totalBytes DESC, fileCount DESC, clusterKey ASC
        LIMIT :limit OFFSET :offset
        """
    )
    fun listStoredClustersByTotalSizeDesc(
        settingId: Long,
        offset: Int,
        limit: Int
    ): List<SimilarityClusterEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertCluster(cluster: SimilarityClusterEntity): Long

    @Query(
        """
        UPDATE similarity_clusters
        SET fileCount = :fileCount,
            totalBytes = :totalBytes,
            updatedAtMillis = :updatedAtMillis
        WHERE clusterId = :clusterId
        """
    )
    fun updateCluster(clusterId: Long, fileCount: Int, totalBytes: Long, updatedAtMillis: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertClusterMembers(members: List<SimilarityClusterMemberEntity>)

    @Query("DELETE FROM similarity_cluster_members WHERE clusterId IN (:clusterIds)")
    fun deleteClusterMembersByIds(clusterIds: List<Long>)

    @Query("DELETE FROM similarity_cluster_members")
    fun deleteAllClusterMembers()

    @Query("DELETE FROM similarity_clusters WHERE clusterId IN (:clusterIds)")
    fun deleteClustersByIds(clusterIds: List<Long>)

    @Query("DELETE FROM similarity_clusters")
    fun deleteAllClusters()

    @Query(
        """
        DELETE FROM similarity_cluster_members
        WHERE clusterId IN (
            SELECT clusterId FROM similarity_clusters WHERE settingId = :settingId
        )
        """
    )
    fun deleteClusterMembersForSetting(settingId: Long)

    @Query("DELETE FROM similarity_clusters WHERE settingId = :settingId")
    fun deleteClustersForSetting(settingId: Long)

    @Query("DELETE FROM similarity_maintenance_runs WHERE settingId = :settingId")
    fun deleteMaintenanceRunsForSetting(settingId: Long)

    @Query("DELETE FROM similarity_maintenance_runs")
    fun deleteAllMaintenanceRuns()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertMaintenanceRun(run: SimilarityMaintenanceRunEntity): Long

    @Query(
        """
        SELECT
            cluster.clusterId AS clusterId,
            cluster.settingId AS settingId,
            cluster.clusterKey AS clusterKey,
            COUNT(file.normalizedPath) AS fileCount,
            COALESCE(SUM(file.sizeBytes), 0) AS totalBytes,
            cluster.updatedAtMillis AS updatedAtMillis
        FROM similarity_clusters AS cluster
        INNER JOIN similarity_cluster_members AS member
            ON member.clusterId = cluster.clusterId
        INNER JOIN cached_files AS file
            ON file.normalizedPath = member.normalizedPath
        WHERE cluster.settingId = :settingId
        GROUP BY cluster.clusterId, cluster.settingId, cluster.clusterKey, cluster.updatedAtMillis
        HAVING COUNT(file.normalizedPath) > 1
        ORDER BY fileCount DESC, totalBytes DESC, cluster.clusterKey ASC
        """
    )
    fun listActiveClusters(settingId: Long): List<SimilarityClusterEntity>

    @Query(
        """
        SELECT
            file.normalizedPath AS normalizedPath,
            file.path AS path,
            file.sizeBytes AS sizeBytes,
            file.lastModifiedMillis AS lastModifiedMillis,
            file.hashHex AS hashHex,
            duration.durationMillis AS durationMillis
        FROM similarity_cluster_members AS member
        INNER JOIN similarity_clusters AS cluster
            ON cluster.clusterId = member.clusterId
        INNER JOIN cached_files AS file
            ON file.normalizedPath = member.normalizedPath
        LEFT JOIN similarity_duration_features AS duration
            ON duration.settingId = cluster.settingId
           AND duration.normalizedPath = member.normalizedPath
        WHERE member.clusterId = :clusterId
        ORDER BY member.position ASC, file.normalizedPath ASC
        """
    )
    fun listActiveClusterMembers(clusterId: Long): List<SimilarityClusterMemberFileRow>

    @Query(
        """
        SELECT
            file.normalizedPath AS normalizedPath,
            file.path AS path,
            file.sizeBytes AS sizeBytes,
            file.lastModifiedMillis AS lastModifiedMillis,
            file.hashHex AS hashHex,
            duration.durationMillis AS durationMillis
        FROM similarity_cluster_members AS member
        INNER JOIN similarity_clusters AS cluster
            ON cluster.clusterId = member.clusterId
        INNER JOIN cached_files AS file
            ON file.normalizedPath = member.normalizedPath
        LEFT JOIN similarity_duration_features AS duration
            ON duration.settingId = cluster.settingId
           AND duration.normalizedPath = member.normalizedPath
        WHERE member.clusterId = :clusterId
        ORDER BY member.position ASC, file.normalizedPath ASC
        LIMIT :limit OFFSET :offset
        """
    )
    fun listActiveClusterMembersPage(
        clusterId: Long,
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
            duration.durationMillis AS durationMillis
        FROM similarity_cluster_members AS member
        INNER JOIN similarity_clusters AS cluster
            ON cluster.clusterId = member.clusterId
        INNER JOIN cached_files AS file
            ON file.normalizedPath = member.normalizedPath
        LEFT JOIN similarity_duration_features AS duration
            ON duration.settingId = cluster.settingId
           AND duration.normalizedPath = member.normalizedPath
        WHERE member.clusterId = :clusterId
        ORDER BY member.position DESC, file.normalizedPath DESC
        LIMIT :limit OFFSET :offset
        """
    )
    fun listActiveClusterMembersPageDescending(
        clusterId: Long,
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
            duration.durationMillis AS durationMillis
        FROM similarity_cluster_members AS member
        INNER JOIN similarity_clusters AS cluster
            ON cluster.clusterId = member.clusterId
        INNER JOIN cached_files AS file
            ON file.normalizedPath = member.normalizedPath
        LEFT JOIN similarity_duration_features AS duration
            ON duration.settingId = cluster.settingId
           AND duration.normalizedPath = member.normalizedPath
        WHERE member.clusterId = :clusterId
        ORDER BY file.normalizedPath ASC
        LIMIT :limit OFFSET :offset
        """
    )
    fun listActiveClusterMembersPageByPathAsc(
        clusterId: Long,
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
            duration.durationMillis AS durationMillis
        FROM similarity_cluster_members AS member
        INNER JOIN similarity_clusters AS cluster
            ON cluster.clusterId = member.clusterId
        INNER JOIN cached_files AS file
            ON file.normalizedPath = member.normalizedPath
        LEFT JOIN similarity_duration_features AS duration
            ON duration.settingId = cluster.settingId
           AND duration.normalizedPath = member.normalizedPath
        WHERE member.clusterId = :clusterId
        ORDER BY file.normalizedPath DESC
        LIMIT :limit OFFSET :offset
        """
    )
    fun listActiveClusterMembersPageByPathDesc(
        clusterId: Long,
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
            duration.durationMillis AS durationMillis
        FROM similarity_cluster_members AS member
        INNER JOIN similarity_clusters AS cluster
            ON cluster.clusterId = member.clusterId
        INNER JOIN cached_files AS file
            ON file.normalizedPath = member.normalizedPath
        LEFT JOIN similarity_duration_features AS duration
            ON duration.settingId = cluster.settingId
           AND duration.normalizedPath = member.normalizedPath
        WHERE member.clusterId = :clusterId
        ORDER BY file.lastModifiedMillis ASC, file.normalizedPath ASC
        LIMIT :limit OFFSET :offset
        """
    )
    fun listActiveClusterMembersPageByModifiedAsc(
        clusterId: Long,
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
            duration.durationMillis AS durationMillis
        FROM similarity_cluster_members AS member
        INNER JOIN similarity_clusters AS cluster
            ON cluster.clusterId = member.clusterId
        INNER JOIN cached_files AS file
            ON file.normalizedPath = member.normalizedPath
        LEFT JOIN similarity_duration_features AS duration
            ON duration.settingId = cluster.settingId
           AND duration.normalizedPath = member.normalizedPath
        WHERE member.clusterId = :clusterId
        ORDER BY file.lastModifiedMillis DESC, file.normalizedPath DESC
        LIMIT :limit OFFSET :offset
        """
    )
    fun listActiveClusterMembersPageByModifiedDesc(
        clusterId: Long,
        offset: Int,
        limit: Int
    ): List<SimilarityClusterMemberFileRow>

    @Query(
        """
        SELECT
            feature.normalizedPath AS normalizedPath,
            feature.thumbnailSignature AS thumbnailSignature,
            file.sizeBytes AS sizeBytes
        FROM similarity_exact_thumbnail_features AS feature
        INNER JOIN cached_files AS file
            ON file.normalizedPath = feature.normalizedPath
        WHERE feature.settingId = :settingId
        ORDER BY feature.thumbnailSignature ASC, feature.normalizedPath ASC
        """
    )
    fun listActiveExactThumbnailFeatures(settingId: Long): List<SimilarityExactThumbnailFeatureRow>

    @Query(
        """
        SELECT
            feature.normalizedPath AS normalizedPath,
            feature.durationMillis AS durationMillis,
            file.sizeBytes AS sizeBytes
        FROM similarity_duration_features AS feature
        INNER JOIN cached_files AS file
            ON file.normalizedPath = feature.normalizedPath
        WHERE feature.settingId = :settingId
        ORDER BY feature.durationMillis ASC, feature.normalizedPath ASC
        """
    )
    fun listActiveDurationFeatures(settingId: Long): List<SimilarityDurationFeatureRow>
}
