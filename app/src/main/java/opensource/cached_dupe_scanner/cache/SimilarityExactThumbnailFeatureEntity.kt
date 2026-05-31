package opensource.cached_dupe_scanner.cache

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "similarity_exact_thumbnail_features",
    primaryKeys = ["settingId", "normalizedPath"],
    indices = [
        Index(
            value = ["settingId", "thumbnailSignature", "normalizedPath"],
            name = "index_similarity_exact_thumbnail_features_signature"
        )
    ]
)
data class SimilarityExactThumbnailFeatureEntity(
    val settingId: Long,
    val normalizedPath: String,
    val thumbnailSignature: String
)

