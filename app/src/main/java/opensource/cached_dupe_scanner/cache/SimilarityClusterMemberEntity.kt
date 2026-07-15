package opensource.cached_dupe_scanner.cache

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "similarity_cluster_members",
    primaryKeys = ["clusterId", "fileId"],
    foreignKeys = [
        ForeignKey(
            entity = CachedFileEntity::class,
            parentColumns = ["fileId"],
            childColumns = ["fileId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(
            value = ["clusterId", "position"],
            name = "index_similarity_cluster_members_clusterId_position"
        ),
        Index(value = ["fileId"], name = "index_similarity_cluster_members_fileId")
    ]
)
data class SimilarityClusterMemberEntity(
    val clusterId: Long,
    val fileId: Long,
    val position: Int
)

data class SimilarityClusterMemberFileRow(
    val settingId: Long,
    val fileId: Long,
    val normalizedPath: String,
    val path: String,
    val sizeBytes: Long,
    val lastModifiedMillis: Long,
    val hashHex: String?,
    val durationMillis: Long?,
    val widthPixels: Int?,
    val heightPixels: Int?,
    val dimensionsChecked: Boolean,
    val durationChecked: Boolean
)
