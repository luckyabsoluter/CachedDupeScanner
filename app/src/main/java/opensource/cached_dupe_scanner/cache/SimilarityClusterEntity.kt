package opensource.cached_dupe_scanner.cache

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "similarity_clusters",
    primaryKeys = ["experimentId", "signature"],
    indices = [
        Index(value = ["experimentId"], name = "index_similarity_clusters_experimentId"),
        Index(value = ["fileCount"], name = "index_similarity_clusters_fileCount"),
        Index(value = ["totalBytes"], name = "index_similarity_clusters_totalBytes")
    ]
)
data class SimilarityClusterEntity(
    val experimentId: String,
    val signature: String,
    val fileCount: Int,
    val totalBytes: Long,
    val updatedAtMillis: Long
)
