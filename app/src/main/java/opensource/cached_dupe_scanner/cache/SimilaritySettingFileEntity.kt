package opensource.cached_dupe_scanner.cache

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "similarity_setting_files",
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
        Index(value = ["fileId"], name = "index_similarity_setting_files_fileId"),
        Index(value = ["settingId", "status"], name = "index_similarity_setting_files_settingId_status")
    ]
)
data class SimilaritySettingFileEntity(
    val settingId: Long,
    val fileId: Long,
    val sizeBytes: Long,
    val lastModifiedMillis: Long,
    val status: String,
    val widthPixels: Int?,
    val heightPixels: Int?,
    @ColumnInfo(defaultValue = "0")
    val dimensionsChecked: Boolean,
    @ColumnInfo(defaultValue = "0")
    val durationChecked: Boolean,
    val updatedAtMillis: Long
)
