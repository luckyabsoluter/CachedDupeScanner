package opensource.cached_dupe_scanner.cache

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "similarity_cluster_members",
    primaryKeys = ["clusterId", "normalizedPath"],
    indices = [
        Index(
            value = ["clusterId", "position"],
            name = "index_similarity_cluster_members_clusterId_position"
        ),
        Index(
            value = ["normalizedPath"],
            name = "index_similarity_cluster_members_normalizedPath"
        )
    ]
)
data class SimilarityClusterMemberEntity(
    val clusterId: Long,
    val normalizedPath: String,
    val position: Int
)

data class SimilarityClusterMemberFileRow(
    val normalizedPath: String,
    val path: String,
    val sizeBytes: Long,
    val lastModifiedMillis: Long,
    val hashHex: String?,
    val durationMillis: Long?,
    val widthPixels: Int?,
    val heightPixels: Int?
)
