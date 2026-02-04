package opensource.cached_dupe_scanner.cache

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "trash_entries",
    indices = [
        Index("deletedAtMillis"),
        Index("originalPath")
    ]
)
data class TrashEntryEntity(
    @PrimaryKey
    val id: String,
    val originalPath: String,
    val trashedPath: String,
    val sizeBytes: Long,
    val lastModifiedMillis: Long,
    val hashHex: String?,
    val deletedAtMillis: Long,
    val volumeRoot: String
)
