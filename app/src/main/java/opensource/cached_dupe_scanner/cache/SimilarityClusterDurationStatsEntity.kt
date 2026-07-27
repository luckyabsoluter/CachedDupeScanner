package opensource.cached_dupe_scanner.cache

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "similarity_cluster_duration_stats",
    foreignKeys = [
        ForeignKey(
            entity = SimilarityClusterEntity::class,
            parentColumns = ["clusterId"],
            childColumns = ["clusterId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class SimilarityClusterDurationStatsEntity(
    @PrimaryKey
    val clusterId: Long,
    val clusterUpdatedAtMillis: Long,
    val memberCount: Long,
    val checkedCount: Long,
    val durationCount: Long,
    val durationSumMillis: Long?,
    val minimumDurationMillis: Long?,
    val maximumDurationMillis: Long?
)
