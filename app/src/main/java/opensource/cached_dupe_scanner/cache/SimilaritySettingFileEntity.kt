package opensource.cached_dupe_scanner.cache

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "similarity_setting_files",
    primaryKeys = ["settingId", "normalizedPath"],
    indices = [
        Index(value = ["normalizedPath"], name = "index_similarity_setting_files_normalizedPath"),
        Index(value = ["settingId", "status"], name = "index_similarity_setting_files_settingId_status")
    ]
)
data class SimilaritySettingFileEntity(
    val settingId: Long,
    val normalizedPath: String,
    val sizeBytes: Long,
    val lastModifiedMillis: Long,
    val status: String,
    val widthPixels: Int?,
    val heightPixels: Int?,
    @ColumnInfo(defaultValue = "0")
    val dimensionsChecked: Boolean,
    val updatedAtMillis: Long
)
