package opensource.cached_dupe_scanner.cache

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "cached_files",
    indices = [
        Index("sizeBytes")
    ]
)
data class CachedFileEntity(
    @PrimaryKey
    val normalizedPath: String,
    val path: String,
    val sizeBytes: Long,
    val lastModifiedMillis: Long,
    val hashHex: String?
)
