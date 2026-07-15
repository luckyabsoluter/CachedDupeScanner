package opensource.cached_dupe_scanner.cache

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "similarity_exact_thumbnail_features",
    primaryKeys = ["settingId", "fileId"],
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
            value = ["settingId", "thumbnailSignature", "fileId"],
            name = "index_similarity_exact_thumbnail_features_signature"
        ),
        Index(value = ["fileId"], name = "index_similarity_exact_thumbnail_features_fileId")
    ]
)
data class SimilarityExactThumbnailFeatureEntity(
    val settingId: Long,
    val fileId: Long,
    val thumbnailSignature: String
)
