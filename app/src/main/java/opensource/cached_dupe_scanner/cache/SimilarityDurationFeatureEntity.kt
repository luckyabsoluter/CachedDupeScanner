package opensource.cached_dupe_scanner.cache

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "similarity_duration_features",
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
            value = ["settingId", "durationMillis", "fileId"],
            name = "index_similarity_duration_features_duration"
        ),
        Index(value = ["fileId"], name = "index_similarity_duration_features_fileId")
    ]
)
data class SimilarityDurationFeatureEntity(
    val settingId: Long,
    val fileId: Long,
    val durationMillis: Long
)
