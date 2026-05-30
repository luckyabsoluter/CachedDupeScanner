package opensource.cached_dupe_scanner.cache

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "similarity_cluster_members",
    primaryKeys = ["experimentId", "signature", "normalizedPath"],
    indices = [
        Index(
            value = ["experimentId", "signature", "position"],
            name = "index_similarity_cluster_members_cluster_position"
        ),
        Index(
            value = ["normalizedPath"],
            name = "index_similarity_cluster_members_normalizedPath"
        )
    ]
)
data class SimilarityClusterMemberEntity(
    val experimentId: String,
    val signature: String,
    val normalizedPath: String,
    val position: Int,
    val durationMillis: Long?
)

data class SimilarityClusterMemberFileRow(
    val normalizedPath: String,
    val path: String,
    val sizeBytes: Long,
    val lastModifiedMillis: Long,
    val hashHex: String?,
    val durationMillis: Long?
)
