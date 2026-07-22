package opensource.cached_dupe_scanner.cache

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

data class SimilarityExactThumbnailFeatureRow(
    val fileId: Long,
    val normalizedPath: String,
    val thumbnailHash: StoredHash,
    val sizeBytes: Long
)

data class SimilarityDurationFeatureRow(
    val fileId: Long,
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

data class SimilarityReusableDurationRow(
    val fileId: Long,
    val durationMillis: Long?
)

data class SimilarityReusableDimensionsRow(
    val fileId: Long,
    val widthPixels: Int?,
    val heightPixels: Int?
)

data class SimilarityFilterResolutionWorkCountRow(
    val dimensionCount: Int,
    val durationCount: Int
)

@Dao
interface SimilaritySettingsDao {
    @Query("SELECT * FROM similarity_settings ORDER BY updatedAtMillis DESC, settingId DESC")
    fun listSettings(): List<SimilaritySettingEntity>

    @Query("SELECT * FROM similarity_settings WHERE enabled = 1 ORDER BY updatedAtMillis DESC, settingId DESC")
    fun listEnabledSettings(): List<SimilaritySettingEntity>

    @Query(
        """
        SELECT setting.*
        FROM similarity_settings AS setting
        WHERE setting.enabled = 1
           OR EXISTS (
               SELECT 1
               FROM similarity_maintenance_runs AS maintenance
               WHERE maintenance.settingId = setting.settingId
           )
        ORDER BY setting.updatedAtMillis DESC, setting.settingId DESC
        """
    )
    fun listRestoreEligibleSettings(): List<SimilaritySettingEntity>

    @Query("SELECT COUNT(*) FROM similarity_settings WHERE enabled = 1")
    fun countEnabledSettings(): Int

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

    @Query("SELECT * FROM similarity_setting_files WHERE settingId = :settingId AND fileId = :fileId LIMIT 1")
    fun getSettingFile(settingId: Long, fileId: Long): SimilaritySettingFileEntity?

    @Query(
        """
        SELECT *
        FROM similarity_setting_files
        WHERE settingId = :settingId
          AND fileId IN (:fileIds)
        """
    )
    fun listSettingFilesByIds(
        settingId: Long,
        fileIds: List<Long>
    ): List<SimilaritySettingFileEntity>

    @Query(
        "SELECT fileId FROM similarity_setting_files " +
            "WHERE settingId = :settingId AND fileId IN (:fileIds)"
    )
    fun listExistingSettingFileIds(settingId: Long, fileIds: List<Long>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertSettingFiles(files: List<SimilaritySettingFileEntity>)

    @Query(
        """
        UPDATE similarity_setting_files
        SET widthPixels = :widthPixels,
            heightPixels = :heightPixels,
            dimensionsChecked = 1,
            updatedAtMillis = :updatedAtMillis
        WHERE settingId = :settingId
          AND fileId = :fileId
          AND sizeBytes = :sizeBytes
          AND lastModifiedMillis = :lastModifiedMillis
          AND dimensionsChecked = 0
        """
    )
    fun updateSettingFileDimensionsIfCurrent(
        settingId: Long,
        fileId: Long,
        sizeBytes: Long,
        lastModifiedMillis: Long,
        widthPixels: Int?,
        heightPixels: Int?,
        updatedAtMillis: Long
    ): Int

    @Query(
        """
        UPDATE similarity_setting_files
        SET durationChecked = 1,
            updatedAtMillis = :updatedAtMillis
        WHERE settingId = :settingId
          AND fileId = :fileId
          AND sizeBytes = :sizeBytes
          AND lastModifiedMillis = :lastModifiedMillis
          AND durationChecked = 0
        """
    )
    fun updateSettingFileDurationIfCurrent(
        settingId: Long,
        fileId: Long,
        sizeBytes: Long,
        lastModifiedMillis: Long,
        updatedAtMillis: Long
    ): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertExactThumbnailFeatures(features: List<SimilarityExactThumbnailFeatureEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertDurationFeatures(features: List<SimilarityDurationFeatureEntity>)

    @Query(
        """
        SELECT *
        FROM similarity_duration_features
        WHERE settingId = :settingId AND fileId = :fileId
        LIMIT 1
        """
    )
    fun getDurationFeature(settingId: Long, fileId: Long): SimilarityDurationFeatureEntity?

    @Query(
        """
        SELECT
            target.fileId AS fileId,
            source_duration.durationMillis AS durationMillis
        FROM similarity_setting_files AS target
        INNER JOIN similarity_settings AS target_setting
            ON target_setting.settingId = target.settingId
        INNER JOIN similarity_setting_files AS source
            ON source.fileId = target.fileId
           AND source.settingId = (
                SELECT candidate.settingId
                FROM similarity_setting_files AS candidate
                INNER JOIN similarity_settings AS candidate_setting
                    ON candidate_setting.settingId = candidate.settingId
                LEFT JOIN similarity_duration_features AS candidate_duration
                    ON candidate_duration.settingId = candidate.settingId
                   AND candidate_duration.fileId = candidate.fileId
                WHERE candidate.fileId = target.fileId
                  AND candidate.settingId != target.settingId
                  AND candidate.sizeBytes = target.sizeBytes
                  AND candidate.lastModifiedMillis = target.lastModifiedMillis
                  AND candidate.durationChecked = 1
                  AND candidate_setting.mediaScope = target_setting.mediaScope
                ORDER BY
                    CASE WHEN candidate_duration.durationMillis IS NULL THEN 1 ELSE 0 END ASC,
                    candidate.settingId ASC
                LIMIT 1
            )
        LEFT JOIN similarity_duration_features AS source_duration
            ON source_duration.settingId = source.settingId
           AND source_duration.fileId = source.fileId
        WHERE target.settingId = :settingId
          AND target.fileId IN (:fileIds)
          AND target.durationChecked = 0
        ORDER BY target.fileId ASC
        """
    )
    fun listReusableDurations(
        settingId: Long,
        fileIds: List<Long>
    ): List<SimilarityReusableDurationRow>

    @Query(
        """
        SELECT
            file.fileId AS fileId,
            source_duration.durationMillis AS durationMillis
        FROM cached_files AS file
        INNER JOIN similarity_setting_files AS source
            ON source.fileId = file.fileId
           AND source.settingId = (
                SELECT candidate.settingId
                FROM similarity_setting_files AS candidate
                INNER JOIN similarity_settings AS candidate_setting
                    ON candidate_setting.settingId = candidate.settingId
                LEFT JOIN similarity_duration_features AS candidate_duration
                    ON candidate_duration.settingId = candidate.settingId
                   AND candidate_duration.fileId = candidate.fileId
                WHERE candidate.fileId = file.fileId
                  AND candidate.settingId != :settingId
                  AND candidate.sizeBytes = file.sizeBytes
                  AND candidate.lastModifiedMillis = file.lastModifiedMillis
                  AND candidate.durationChecked = 1
                  AND candidate_setting.mediaScope = :mediaScope
                ORDER BY
                    CASE WHEN candidate_duration.durationMillis IS NULL THEN 1 ELSE 0 END ASC,
                    candidate.settingId ASC
                LIMIT 1
            )
        LEFT JOIN similarity_duration_features AS source_duration
            ON source_duration.settingId = source.settingId
           AND source_duration.fileId = source.fileId
        WHERE file.fileId IN (:fileIds)
        ORDER BY file.fileId ASC
        """
    )
    fun listReusableDurationsForFiles(
        settingId: Long,
        mediaScope: String,
        fileIds: List<Long>
    ): List<SimilarityReusableDurationRow>

    @Query(
        """
        SELECT
            target.fileId AS fileId,
            source.widthPixels AS widthPixels,
            source.heightPixels AS heightPixels
        FROM similarity_setting_files AS target
        INNER JOIN similarity_settings AS target_setting
            ON target_setting.settingId = target.settingId
        INNER JOIN similarity_setting_files AS source
            ON source.fileId = target.fileId
           AND source.settingId = (
                SELECT candidate.settingId
                FROM similarity_setting_files AS candidate
                INNER JOIN similarity_settings AS candidate_setting
                    ON candidate_setting.settingId = candidate.settingId
                WHERE candidate.fileId = target.fileId
                  AND candidate.settingId != target.settingId
                  AND candidate.sizeBytes = target.sizeBytes
                  AND candidate.lastModifiedMillis = target.lastModifiedMillis
                  AND candidate.dimensionsChecked = 1
                  AND candidate_setting.mediaScope = target_setting.mediaScope
                ORDER BY
                    CASE
                        WHEN candidate.widthPixels IS NULL OR candidate.heightPixels IS NULL THEN 1
                        ELSE 0
                    END ASC,
                    candidate.settingId ASC
                LIMIT 1
            )
        WHERE target.settingId = :settingId
          AND target.fileId IN (:fileIds)
          AND target.dimensionsChecked = 0
        ORDER BY target.fileId ASC
        """
    )
    fun listReusableDimensions(
        settingId: Long,
        fileIds: List<Long>
    ): List<SimilarityReusableDimensionsRow>

    @Query("SELECT COUNT(*) FROM similarity_setting_files WHERE settingId = :settingId")
    fun countSettingFiles(settingId: Long): Int

    @Query(
        """
        SELECT
            COUNT(
                DISTINCT CASE
                    WHEN :resolveDimensions = 1 AND setting_file.dimensionsChecked = 0
                    THEN member.fileId
                END
            ) AS dimensionCount,
            COUNT(
                DISTINCT CASE
                    WHEN :resolveDurations = 1 AND setting_file.durationChecked = 0
                    THEN member.fileId
                END
            ) AS durationCount
        FROM similarity_cluster_members AS member
        INNER JOIN similarity_clusters AS cluster
            ON cluster.clusterId = member.clusterId
        INNER JOIN similarity_setting_files AS setting_file
            ON setting_file.settingId = cluster.settingId
           AND setting_file.fileId = member.fileId
        WHERE member.clusterId IN (:clusterIds)
          AND cluster.settingId = :settingId
        """
    )
    fun countFilterResolutionWorkForClusters(
        settingId: Long,
        clusterIds: List<Long>,
        resolveDimensions: Boolean,
        resolveDurations: Boolean
    ): SimilarityFilterResolutionWorkCountRow

    @Query(
        """
        SELECT
            member.clusterId AS clusterId,
            member.position AS position,
            cluster.settingId AS settingId,
            member.fileId AS fileId,
            file.normalizedPath AS normalizedPath,
            file.path AS path,
            setting_file.sizeBytes AS sizeBytes,
            setting_file.lastModifiedMillis AS lastModifiedMillis,
            file.hashBytes AS hashBytes,
            CASE WHEN setting_file.durationChecked = 1 THEN duration.durationMillis END AS durationMillis,
            setting_file.widthPixels AS widthPixels,
            setting_file.heightPixels AS heightPixels,
            setting_file.dimensionsChecked AS dimensionsChecked,
            setting_file.durationChecked AS durationChecked
        FROM similarity_cluster_members AS member
        INNER JOIN similarity_clusters AS cluster
            ON cluster.clusterId = member.clusterId
        INNER JOIN similarity_setting_files AS setting_file
            ON setting_file.settingId = cluster.settingId
           AND setting_file.fileId = member.fileId
        INNER JOIN cached_files AS file
            ON file.fileId = member.fileId
        LEFT JOIN similarity_duration_features AS duration
            ON duration.settingId = cluster.settingId
           AND duration.fileId = member.fileId
        WHERE cluster.settingId = :settingId
          AND member.clusterId IN (:clusterIds)
          AND (
              (:resolveDimensions = 1 AND setting_file.dimensionsChecked = 0)
               OR (:resolveDurations = 1 AND setting_file.durationChecked = 0)
          )
          AND (
              member.clusterId > :afterClusterId
               OR (
                   member.clusterId = :afterClusterId
                   AND member.position > :afterPosition
               )
               OR (
                   member.clusterId = :afterClusterId
                   AND member.position = :afterPosition
                   AND member.fileId > :afterFileId
               )
          )
        ORDER BY member.clusterId ASC, member.position ASC, member.fileId ASC
        LIMIT :limit
        """
    )
    fun listUncheckedFilterMetadataMembersForClusters(
        settingId: Long,
        clusterIds: List<Long>,
        resolveDimensions: Boolean,
        resolveDurations: Boolean,
        afterClusterId: Long,
        afterPosition: Int,
        afterFileId: Long,
        limit: Int
    ): List<SimilarityFilterMetadataResolutionRow>

    @Query(
        """
        SELECT
            member.clusterId AS clusterId,
            member.position AS position,
            member.fileId AS fileId,
            file.normalizedPath AS normalizedPath,
            file.path AS path,
            setting_file.sizeBytes AS sizeBytes,
            setting_file.lastModifiedMillis AS lastModifiedMillis,
            CASE WHEN setting_file.durationChecked = 1 THEN duration.durationMillis END AS durationMillis,
            CASE WHEN setting_file.dimensionsChecked = 1 THEN setting_file.widthPixels END AS widthPixels,
            CASE WHEN setting_file.dimensionsChecked = 1 THEN setting_file.heightPixels END AS heightPixels
        FROM similarity_cluster_members AS member
        INNER JOIN similarity_clusters AS cluster
            ON cluster.clusterId = member.clusterId
        INNER JOIN similarity_setting_files AS setting_file
            ON setting_file.settingId = cluster.settingId
           AND setting_file.fileId = member.fileId
        INNER JOIN cached_files AS file
            ON file.fileId = member.fileId
        LEFT JOIN similarity_duration_features AS duration
            ON duration.settingId = cluster.settingId
           AND duration.fileId = member.fileId
        WHERE cluster.settingId = :settingId
          AND member.clusterId IN (:clusterIds)
          AND (
              member.clusterId > :afterClusterId
               OR (
                   member.clusterId = :afterClusterId
                   AND member.position > :afterPosition
               )
               OR (
                   member.clusterId = :afterClusterId
                   AND member.position = :afterPosition
                   AND member.fileId > :afterFileId
               )
          )
        ORDER BY member.clusterId ASC, member.position ASC, member.fileId ASC
        LIMIT :limit
        """
    )
    fun listFilterMembersForClustersPage(
        settingId: Long,
        clusterIds: List<Long>,
        afterClusterId: Long,
        afterPosition: Int,
        afterFileId: Long,
        limit: Int
    ): List<SimilarityClusterFilterMemberRow>

    @Query(
        """
        SELECT
            member.clusterId AS clusterId,
            COUNT(*) AS memberCount,
            SUM(
                CASE WHEN setting_file.durationChecked = 1 THEN 1 ELSE 0 END
            ) AS checkedCount,
            COUNT(duration.durationMillis) AS durationCount,
            SUM(duration.durationMillis) AS durationSumMillis,
            MIN(duration.durationMillis) AS minimumDurationMillis,
            MAX(duration.durationMillis) AS maximumDurationMillis
        FROM similarity_cluster_members AS member
        INNER JOIN similarity_clusters AS cluster
            ON cluster.clusterId = member.clusterId
        INNER JOIN similarity_setting_files AS setting_file
            ON setting_file.settingId = cluster.settingId
           AND setting_file.fileId = member.fileId
        LEFT JOIN similarity_duration_features AS duration
            ON duration.settingId = cluster.settingId
           AND duration.fileId = member.fileId
        WHERE cluster.settingId = :settingId
          AND member.clusterId IN (:clusterIds)
        GROUP BY member.clusterId
        ORDER BY member.clusterId ASC
        """
    )
    fun listDurationStatsForClusters(
        settingId: Long,
        clusterIds: List<Long>
    ): List<SimilarityClusterDurationStatsRow>

    @Query("SELECT COUNT(*) FROM similarity_exact_thumbnail_features WHERE settingId = :settingId")
    fun countExactThumbnailFeatures(settingId: Long): Int

    @Query("SELECT COUNT(*) FROM similarity_duration_features WHERE settingId = :settingId")
    fun countDurationFeatures(settingId: Long): Int

    @Query("DELETE FROM similarity_setting_files WHERE settingId = :settingId")
    fun deleteSettingFiles(settingId: Long): Int

    @Query("DELETE FROM similarity_setting_files")
    fun deleteAllSettingFiles()

    @Query("DELETE FROM similarity_exact_thumbnail_features WHERE settingId = :settingId")
    fun deleteExactThumbnailFeatures(settingId: Long): Int

    @Query("DELETE FROM similarity_exact_thumbnail_features")
    fun deleteAllExactThumbnailFeatures()

    @Query("DELETE FROM similarity_duration_features WHERE settingId = :settingId")
    fun deleteDurationFeatures(settingId: Long): Int

    @Query("DELETE FROM similarity_duration_features")
    fun deleteAllDurationFeatures()

    @Query(
        """
        SELECT fileId
        FROM similarity_setting_files
        WHERE settingId = :settingId
        ORDER BY fileId ASC
        LIMIT :limit
        """
    )
    fun listSettingFileIdsForClear(settingId: Long, limit: Int): List<Long>

    @Query(
        """
        DELETE FROM similarity_setting_files
        WHERE settingId = :settingId
          AND fileId IN (:fileIds)
        """
    )
    fun deleteSettingFilesForSettingByIds(settingId: Long, fileIds: List<Long>): Int

    @Query(
        """
        SELECT fileId
        FROM similarity_exact_thumbnail_features
        WHERE settingId = :settingId
        ORDER BY fileId ASC
        LIMIT :limit
        """
    )
    fun listExactThumbnailFeatureIdsForClear(settingId: Long, limit: Int): List<Long>

    @Query(
        """
        DELETE FROM similarity_exact_thumbnail_features
        WHERE settingId = :settingId
          AND fileId IN (:fileIds)
        """
    )
    fun deleteExactThumbnailFeaturesForSettingByIds(settingId: Long, fileIds: List<Long>): Int

    @Query(
        """
        SELECT fileId
        FROM similarity_duration_features
        WHERE settingId = :settingId
        ORDER BY fileId ASC
        LIMIT :limit
        """
    )
    fun listDurationFeatureIdsForClear(settingId: Long, limit: Int): List<Long>

    @Query(
        """
        DELETE FROM similarity_duration_features
        WHERE settingId = :settingId
          AND fileId IN (:fileIds)
        """
    )
    fun deleteDurationFeaturesForSettingByIds(settingId: Long, fileIds: List<Long>): Int

    @Query(
        """
        DELETE FROM similarity_setting_files
        WHERE fileId IN (
            SELECT fileId FROM cached_files WHERE normalizedPath IN (:normalizedPaths)
        )
        """
    )
    fun deleteSettingFilesByPaths(normalizedPaths: List<String>)

    @Query(
        """
        DELETE FROM similarity_exact_thumbnail_features
        WHERE fileId IN (
            SELECT fileId FROM cached_files WHERE normalizedPath IN (:normalizedPaths)
        )
        """
    )
    fun deleteExactThumbnailFeaturesByPaths(normalizedPaths: List<String>)

    @Query(
        """
        DELETE FROM similarity_duration_features
        WHERE fileId IN (
            SELECT fileId FROM cached_files WHERE normalizedPath IN (:normalizedPaths)
        )
        """
    )
    fun deleteDurationFeaturesByPaths(normalizedPaths: List<String>)

    @Query(
        """
        DELETE FROM similarity_cluster_members
        WHERE fileId IN (
            SELECT fileId FROM cached_files WHERE normalizedPath IN (:normalizedPaths)
        )
        """
    )
    fun deleteClusterMembersByPaths(normalizedPaths: List<String>)

    @Query(
        """
        SELECT DISTINCT clusterId
        FROM similarity_cluster_members
        WHERE fileId IN (
            SELECT fileId FROM cached_files WHERE normalizedPath IN (:normalizedPaths)
        )
        """
    )
    fun listClusterIdsForMemberPaths(normalizedPaths: List<String>): List<Long>

    @Query("SELECT * FROM similarity_clusters WHERE settingId = :settingId AND clusterKey = :clusterKey LIMIT 1")
    fun getClusterByKey(settingId: Long, clusterKey: String): SimilarityClusterEntity?

    @Query("SELECT * FROM similarity_clusters WHERE settingId = :settingId AND clusterId = :clusterId AND fileCount > 1 LIMIT 1")
    fun getStoredCluster(settingId: Long, clusterId: Long): SimilarityClusterEntity?

    @Query("SELECT * FROM similarity_clusters WHERE settingId = :settingId")
    fun listStoredClusters(settingId: Long): List<SimilarityClusterEntity>

    @Query(
        """
        SELECT *
        FROM similarity_clusters
        WHERE settingId = :settingId
          AND fileCount > 1
          AND clusterId > :afterClusterId
        ORDER BY clusterId ASC
        LIMIT :limit
        """
    )
    fun listStoredClustersAfterId(
        settingId: Long,
        afterClusterId: Long,
        limit: Int
    ): List<SimilarityClusterEntity>

    @Query(
        """
        SELECT
            cluster.clusterId AS clusterId,
            COUNT(file.fileId) AS fileCount,
            COALESCE(SUM(file.sizeBytes), 0) AS totalBytes
        FROM similarity_clusters AS cluster
        LEFT JOIN similarity_cluster_members AS member
            ON member.clusterId = cluster.clusterId
        LEFT JOIN cached_files AS file
            ON file.fileId = member.fileId
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

    @Query(
        """
        SELECT *
        FROM similarity_clusters
        WHERE settingId = :settingId
          AND fileCount > 1
          AND (
              fileCount > :afterFileCount
               OR (fileCount = :afterFileCount AND totalBytes > :afterTotalBytes)
               OR (
                   fileCount = :afterFileCount
                   AND totalBytes = :afterTotalBytes
                   AND clusterKey > :afterClusterKey
               )
          )
        ORDER BY fileCount ASC, totalBytes ASC, clusterKey ASC
        LIMIT :limit
        """
    )
    fun listStoredClustersByFileCountAscAfter(
        settingId: Long,
        afterFileCount: Int,
        afterTotalBytes: Long,
        afterClusterKey: String,
        limit: Int
    ): List<SimilarityClusterEntity>

    @Query(
        """
        SELECT *
        FROM similarity_clusters
        WHERE settingId = :settingId
          AND fileCount > 1
          AND (
              fileCount < :afterFileCount
               OR (fileCount = :afterFileCount AND totalBytes < :afterTotalBytes)
               OR (
                   fileCount = :afterFileCount
                   AND totalBytes = :afterTotalBytes
                   AND clusterKey > :afterClusterKey
               )
          )
        ORDER BY fileCount DESC, totalBytes DESC, clusterKey ASC
        LIMIT :limit
        """
    )
    fun listStoredClustersByFileCountDescAfter(
        settingId: Long,
        afterFileCount: Int,
        afterTotalBytes: Long,
        afterClusterKey: String,
        limit: Int
    ): List<SimilarityClusterEntity>

    @Query(
        """
        SELECT *
        FROM similarity_clusters
        WHERE settingId = :settingId
          AND fileCount > 1
          AND (
              totalBytes > :afterTotalBytes
               OR (totalBytes = :afterTotalBytes AND fileCount > :afterFileCount)
               OR (
                   totalBytes = :afterTotalBytes
                   AND fileCount = :afterFileCount
                   AND clusterKey > :afterClusterKey
               )
          )
        ORDER BY totalBytes ASC, fileCount ASC, clusterKey ASC
        LIMIT :limit
        """
    )
    fun listStoredClustersByTotalSizeAscAfter(
        settingId: Long,
        afterFileCount: Int,
        afterTotalBytes: Long,
        afterClusterKey: String,
        limit: Int
    ): List<SimilarityClusterEntity>

    @Query(
        """
        SELECT *
        FROM similarity_clusters
        WHERE settingId = :settingId
          AND fileCount > 1
          AND (
              totalBytes < :afterTotalBytes
               OR (totalBytes = :afterTotalBytes AND fileCount < :afterFileCount)
               OR (
                   totalBytes = :afterTotalBytes
                   AND fileCount = :afterFileCount
                   AND clusterKey > :afterClusterKey
               )
          )
        ORDER BY totalBytes DESC, fileCount DESC, clusterKey ASC
        LIMIT :limit
        """
    )
    fun listStoredClustersByTotalSizeDescAfter(
        settingId: Long,
        afterFileCount: Int,
        afterTotalBytes: Long,
        afterClusterKey: String,
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
    fun deleteClusterMembersByIds(clusterIds: List<Long>): Int

    @Query("DELETE FROM similarity_cluster_members")
    fun deleteAllClusterMembers()

    @Query("DELETE FROM similarity_clusters WHERE clusterId IN (:clusterIds)")
    fun deleteClustersByIds(clusterIds: List<Long>): Int

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
    fun deleteClusterMembersForSetting(settingId: Long): Int

    @Query("DELETE FROM similarity_clusters WHERE settingId = :settingId")
    fun deleteClustersForSetting(settingId: Long): Int

    @Query("SELECT COUNT(*) FROM similarity_clusters WHERE settingId = :settingId")
    fun countClustersForSetting(settingId: Long): Int

    @Query(
        """
        SELECT COUNT(*)
        FROM similarity_cluster_members
        WHERE clusterId IN (
            SELECT clusterId FROM similarity_clusters WHERE settingId = :settingId
        )
        """
    )
    fun countClusterMembersForSetting(settingId: Long): Int

    @Query(
        """
        SELECT cluster.clusterId
        FROM similarity_clusters AS cluster
        WHERE cluster.settingId = :settingId
          AND EXISTS (
              SELECT 1
              FROM similarity_cluster_members AS member
              WHERE member.clusterId = cluster.clusterId
          )
        ORDER BY cluster.clusterId ASC
        LIMIT 1
        """
    )
    fun firstClusterIdWithMembersForClear(settingId: Long): Long?

    @Query(
        """
        SELECT fileId
        FROM similarity_cluster_members
        WHERE clusterId = :clusterId
        ORDER BY position ASC, fileId ASC
        LIMIT :limit
        """
    )
    fun listClusterMemberIdsForClear(clusterId: Long, limit: Int): List<Long>

    @Query(
        """
        DELETE FROM similarity_cluster_members
        WHERE clusterId = :clusterId
          AND fileId IN (:fileIds)
        """
    )
    fun deleteClusterMemberIdsForClear(clusterId: Long, fileIds: List<Long>): Int

    @Query("SELECT * FROM similarity_clusters WHERE clusterId = :clusterId LIMIT 1")
    fun getClusterForClear(clusterId: Long): SimilarityClusterEntity?

    @Query(
        """
        SELECT COALESCE(SUM(setting_file.sizeBytes), 0)
        FROM similarity_cluster_members AS member
        INNER JOIN similarity_clusters AS cluster
            ON cluster.clusterId = member.clusterId
        INNER JOIN similarity_setting_files AS setting_file
            ON setting_file.settingId = cluster.settingId
           AND setting_file.fileId = member.fileId
        WHERE member.clusterId = :clusterId
          AND member.fileId IN (:fileIds)
        """
    )
    fun sumClusterMemberBytesForClear(clusterId: Long, fileIds: List<Long>): Long

    @Query(
        """
        SELECT clusterId
        FROM similarity_clusters
        WHERE settingId = :settingId
        ORDER BY clusterId ASC
        LIMIT :limit
        """
    )
    fun listClusterIdsForClear(settingId: Long, limit: Int): List<Long>

    @Query("DELETE FROM similarity_maintenance_runs WHERE settingId = :settingId")
    fun deleteMaintenanceRunsForSetting(settingId: Long): Int

    @Query("SELECT COUNT(*) FROM similarity_maintenance_runs WHERE settingId = :settingId")
    fun countMaintenanceRunsForSetting(settingId: Long): Int

    @Query(
        """
        SELECT runId
        FROM similarity_maintenance_runs
        WHERE settingId = :settingId
        ORDER BY runId ASC
        LIMIT :limit
        """
    )
    fun listMaintenanceRunIdsForClear(settingId: Long, limit: Int): List<Long>

    @Query("DELETE FROM similarity_maintenance_runs WHERE runId IN (:runIds)")
    fun deleteMaintenanceRunsByIds(runIds: List<Long>): Int

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
            COUNT(file.fileId) AS fileCount,
            COALESCE(SUM(file.sizeBytes), 0) AS totalBytes,
            cluster.updatedAtMillis AS updatedAtMillis
        FROM similarity_clusters AS cluster
        INNER JOIN similarity_cluster_members AS member
            ON member.clusterId = cluster.clusterId
        INNER JOIN cached_files AS file
            ON file.fileId = member.fileId
        WHERE cluster.settingId = :settingId
        GROUP BY cluster.clusterId, cluster.settingId, cluster.clusterKey, cluster.updatedAtMillis
        HAVING COUNT(file.fileId) > 1
        ORDER BY fileCount DESC, totalBytes DESC, cluster.clusterKey ASC
        """
    )
    fun listActiveClusters(settingId: Long): List<SimilarityClusterEntity>

    @Query(
        """
        SELECT
            cluster.settingId AS settingId,
            member.fileId AS fileId,
            file.normalizedPath AS normalizedPath,
            file.path AS path,
            setting_file.sizeBytes AS sizeBytes,
            setting_file.lastModifiedMillis AS lastModifiedMillis,
            file.hashBytes AS hashBytes,
            CASE WHEN setting_file.durationChecked = 1 THEN duration.durationMillis END AS durationMillis,
            setting_file.widthPixels AS widthPixels,
            setting_file.heightPixels AS heightPixels,
            setting_file.dimensionsChecked AS dimensionsChecked,
            setting_file.durationChecked AS durationChecked
        FROM similarity_cluster_members AS member
        INNER JOIN similarity_clusters AS cluster
            ON cluster.clusterId = member.clusterId
        INNER JOIN similarity_setting_files AS setting_file
            ON setting_file.settingId = cluster.settingId
           AND setting_file.fileId = member.fileId
        INNER JOIN cached_files AS file
            ON file.fileId = member.fileId
        LEFT JOIN similarity_duration_features AS duration
            ON duration.settingId = cluster.settingId
           AND duration.fileId = member.fileId
        WHERE member.clusterId = :clusterId
        ORDER BY member.position ASC, member.fileId ASC
        """
    )
    fun listStoredClusterMembers(clusterId: Long): List<SimilarityClusterMemberFileRow>

    @Query(
        """
        SELECT
            cluster.settingId AS settingId,
            member.fileId AS fileId,
            file.normalizedPath AS normalizedPath,
            file.path AS path,
            setting_file.sizeBytes AS sizeBytes,
            setting_file.lastModifiedMillis AS lastModifiedMillis,
            file.hashBytes AS hashBytes,
            CASE WHEN setting_file.durationChecked = 1 THEN duration.durationMillis END AS durationMillis,
            setting_file.widthPixels AS widthPixels,
            setting_file.heightPixels AS heightPixels,
            setting_file.dimensionsChecked AS dimensionsChecked,
            setting_file.durationChecked AS durationChecked
        FROM similarity_cluster_members AS member
        INNER JOIN similarity_clusters AS cluster
            ON cluster.clusterId = member.clusterId
        INNER JOIN similarity_setting_files AS setting_file
            ON setting_file.settingId = cluster.settingId
           AND setting_file.fileId = member.fileId
        INNER JOIN cached_files AS file
            ON file.fileId = member.fileId
        LEFT JOIN similarity_duration_features AS duration
            ON duration.settingId = cluster.settingId
           AND duration.fileId = member.fileId
        WHERE member.clusterId = :clusterId
        ORDER BY member.position ASC, member.fileId ASC
        LIMIT :limit OFFSET :offset
        """
    )
    fun listStoredClusterMembersPage(
        clusterId: Long,
        offset: Int,
        limit: Int
    ): List<SimilarityClusterMemberFileRow>

    @Query(
        """
        SELECT
            cluster.settingId AS settingId,
            member.fileId AS fileId,
            file.normalizedPath AS normalizedPath,
            file.path AS path,
            setting_file.sizeBytes AS sizeBytes,
            setting_file.lastModifiedMillis AS lastModifiedMillis,
            file.hashBytes AS hashBytes,
            CASE WHEN setting_file.durationChecked = 1 THEN duration.durationMillis END AS durationMillis,
            setting_file.widthPixels AS widthPixels,
            setting_file.heightPixels AS heightPixels,
            setting_file.dimensionsChecked AS dimensionsChecked,
            setting_file.durationChecked AS durationChecked
        FROM similarity_cluster_members AS member
        INNER JOIN similarity_clusters AS cluster
            ON cluster.clusterId = member.clusterId
        INNER JOIN similarity_setting_files AS setting_file
            ON setting_file.settingId = cluster.settingId
           AND setting_file.fileId = member.fileId
        INNER JOIN cached_files AS file
            ON file.fileId = member.fileId
        LEFT JOIN similarity_duration_features AS duration
            ON duration.settingId = cluster.settingId
           AND duration.fileId = member.fileId
        WHERE member.clusterId = :clusterId
        ORDER BY member.position DESC, member.fileId DESC
        LIMIT :limit OFFSET :offset
        """
    )
    fun listStoredClusterMembersPageDescending(
        clusterId: Long,
        offset: Int,
        limit: Int
    ): List<SimilarityClusterMemberFileRow>

    @Query(
        """
        SELECT
            cluster.settingId AS settingId,
            member.fileId AS fileId,
            file.normalizedPath AS normalizedPath,
            file.path AS path,
            setting_file.sizeBytes AS sizeBytes,
            setting_file.lastModifiedMillis AS lastModifiedMillis,
            file.hashBytes AS hashBytes,
            CASE WHEN setting_file.durationChecked = 1 THEN duration.durationMillis END AS durationMillis,
            setting_file.widthPixels AS widthPixels,
            setting_file.heightPixels AS heightPixels,
            setting_file.dimensionsChecked AS dimensionsChecked,
            setting_file.durationChecked AS durationChecked
        FROM similarity_cluster_members AS member
        INNER JOIN similarity_clusters AS cluster
            ON cluster.clusterId = member.clusterId
        INNER JOIN similarity_setting_files AS setting_file
            ON setting_file.settingId = cluster.settingId
           AND setting_file.fileId = member.fileId
        INNER JOIN cached_files AS file
            ON file.fileId = member.fileId
        LEFT JOIN similarity_duration_features AS duration
            ON duration.settingId = cluster.settingId
           AND duration.fileId = member.fileId
        WHERE member.clusterId = :clusterId
        ORDER BY file.normalizedPath ASC
        LIMIT :limit OFFSET :offset
        """
    )
    fun listStoredClusterMembersPageByPathAsc(
        clusterId: Long,
        offset: Int,
        limit: Int
    ): List<SimilarityClusterMemberFileRow>

    @Query(
        """
        SELECT
            cluster.settingId AS settingId,
            member.fileId AS fileId,
            file.normalizedPath AS normalizedPath,
            file.path AS path,
            setting_file.sizeBytes AS sizeBytes,
            setting_file.lastModifiedMillis AS lastModifiedMillis,
            file.hashBytes AS hashBytes,
            CASE WHEN setting_file.durationChecked = 1 THEN duration.durationMillis END AS durationMillis,
            setting_file.widthPixels AS widthPixels,
            setting_file.heightPixels AS heightPixels,
            setting_file.dimensionsChecked AS dimensionsChecked,
            setting_file.durationChecked AS durationChecked
        FROM similarity_cluster_members AS member
        INNER JOIN similarity_clusters AS cluster
            ON cluster.clusterId = member.clusterId
        INNER JOIN similarity_setting_files AS setting_file
            ON setting_file.settingId = cluster.settingId
           AND setting_file.fileId = member.fileId
        INNER JOIN cached_files AS file
            ON file.fileId = member.fileId
        LEFT JOIN similarity_duration_features AS duration
            ON duration.settingId = cluster.settingId
           AND duration.fileId = member.fileId
        WHERE member.clusterId = :clusterId
        ORDER BY file.normalizedPath DESC
        LIMIT :limit OFFSET :offset
        """
    )
    fun listStoredClusterMembersPageByPathDesc(
        clusterId: Long,
        offset: Int,
        limit: Int
    ): List<SimilarityClusterMemberFileRow>

    @Query(
        """
        SELECT
            cluster.settingId AS settingId,
            member.fileId AS fileId,
            file.normalizedPath AS normalizedPath,
            file.path AS path,
            setting_file.sizeBytes AS sizeBytes,
            setting_file.lastModifiedMillis AS lastModifiedMillis,
            file.hashBytes AS hashBytes,
            CASE WHEN setting_file.durationChecked = 1 THEN duration.durationMillis END AS durationMillis,
            setting_file.widthPixels AS widthPixels,
            setting_file.heightPixels AS heightPixels,
            setting_file.dimensionsChecked AS dimensionsChecked,
            setting_file.durationChecked AS durationChecked
        FROM similarity_cluster_members AS member
        INNER JOIN similarity_clusters AS cluster
            ON cluster.clusterId = member.clusterId
        INNER JOIN similarity_setting_files AS setting_file
            ON setting_file.settingId = cluster.settingId
           AND setting_file.fileId = member.fileId
        INNER JOIN cached_files AS file
            ON file.fileId = member.fileId
        LEFT JOIN similarity_duration_features AS duration
            ON duration.settingId = cluster.settingId
           AND duration.fileId = member.fileId
        WHERE member.clusterId = :clusterId
        ORDER BY setting_file.lastModifiedMillis ASC, member.fileId ASC
        LIMIT :limit OFFSET :offset
        """
    )
    fun listStoredClusterMembersPageByModifiedAsc(
        clusterId: Long,
        offset: Int,
        limit: Int
    ): List<SimilarityClusterMemberFileRow>

    @Query(
        """
        SELECT
            cluster.settingId AS settingId,
            member.fileId AS fileId,
            file.normalizedPath AS normalizedPath,
            file.path AS path,
            setting_file.sizeBytes AS sizeBytes,
            setting_file.lastModifiedMillis AS lastModifiedMillis,
            file.hashBytes AS hashBytes,
            CASE WHEN setting_file.durationChecked = 1 THEN duration.durationMillis END AS durationMillis,
            setting_file.widthPixels AS widthPixels,
            setting_file.heightPixels AS heightPixels,
            setting_file.dimensionsChecked AS dimensionsChecked,
            setting_file.durationChecked AS durationChecked
        FROM similarity_cluster_members AS member
        INNER JOIN similarity_clusters AS cluster
            ON cluster.clusterId = member.clusterId
        INNER JOIN similarity_setting_files AS setting_file
            ON setting_file.settingId = cluster.settingId
           AND setting_file.fileId = member.fileId
        INNER JOIN cached_files AS file
            ON file.fileId = member.fileId
        LEFT JOIN similarity_duration_features AS duration
            ON duration.settingId = cluster.settingId
           AND duration.fileId = member.fileId
        WHERE member.clusterId = :clusterId
        ORDER BY setting_file.lastModifiedMillis DESC, member.fileId DESC
        LIMIT :limit OFFSET :offset
        """
    )
    fun listStoredClusterMembersPageByModifiedDesc(
        clusterId: Long,
        offset: Int,
        limit: Int
    ): List<SimilarityClusterMemberFileRow>

    @Query(
        """
        SELECT
            feature.fileId AS fileId,
            file.normalizedPath AS normalizedPath,
            feature.thumbnailHash AS thumbnailHash,
            file.sizeBytes AS sizeBytes
        FROM similarity_exact_thumbnail_features AS feature
        INNER JOIN cached_files AS file
            ON file.fileId = feature.fileId
        WHERE feature.settingId = :settingId
        ORDER BY feature.thumbnailHash ASC, feature.fileId ASC
        """
    )
    fun listActiveExactThumbnailFeatures(settingId: Long): List<SimilarityExactThumbnailFeatureRow>

    @Query(
        """
        SELECT
            feature.fileId AS fileId,
            file.normalizedPath AS normalizedPath,
            feature.durationMillis AS durationMillis,
            file.sizeBytes AS sizeBytes
        FROM similarity_duration_features AS feature
        INNER JOIN cached_files AS file
            ON file.fileId = feature.fileId
        WHERE feature.settingId = :settingId
        ORDER BY feature.durationMillis ASC, file.normalizedPath ASC
        """
    )
    fun listActiveDurationFeatures(settingId: Long): List<SimilarityDurationFeatureRow>
}
