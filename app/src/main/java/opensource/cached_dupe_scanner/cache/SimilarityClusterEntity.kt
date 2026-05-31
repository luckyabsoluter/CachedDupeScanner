package opensource.cached_dupe_scanner.cache

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "similarity_clusters",
    indices = [
        Index(
            value = ["settingId", "clusterKey"],
            unique = true,
            name = "index_similarity_clusters_settingId_clusterKey"
        ),
        Index(value = ["settingId"], name = "index_similarity_clusters_settingId"),
        Index(value = ["fileCount"], name = "index_similarity_clusters_fileCount"),
        Index(value = ["totalBytes"], name = "index_similarity_clusters_totalBytes")
    ]
)
data class SimilarityClusterEntity(
    @PrimaryKey(autoGenerate = true)
    val clusterId: Long = 0L,
    val settingId: Long,
    val clusterKey: String,
    val fileCount: Int,
    val totalBytes: Long,
    val updatedAtMillis: Long
)
