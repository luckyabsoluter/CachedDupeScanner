package opensource.cached_dupe_scanner.cache

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "similarity_duration_features",
    primaryKeys = ["settingId", "normalizedPath"],
    indices = [
        Index(
            value = ["settingId", "durationMillis", "normalizedPath"],
            name = "index_similarity_duration_features_duration"
        )
    ]
)
data class SimilarityDurationFeatureEntity(
    val settingId: Long,
    val normalizedPath: String,
    val durationMillis: Long
)

