package opensource.cached_dupe_scanner.cache

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "similarity_settings",
    indices = [
        Index(
            value = ["methodId", "mediaScope", "minSizeBytes", "paramsHash"],
            unique = true,
            name = "index_similarity_settings_identity"
        ),
        Index(value = ["enabled"], name = "index_similarity_settings_enabled")
    ]
)
data class SimilaritySettingEntity(
    @PrimaryKey(autoGenerate = true)
    val settingId: Long = 0L,
    val methodId: String,
    val mediaScope: String,
    val minSizeBytes: Long,
    val paramsJson: String,
    val paramsHash: String,
    val displayName: String,
    val enabled: Boolean,
    val createdAtMillis: Long,
    val updatedAtMillis: Long
)

