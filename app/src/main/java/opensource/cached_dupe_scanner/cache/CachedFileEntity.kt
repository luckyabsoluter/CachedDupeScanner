package opensource.cached_dupe_scanner.cache

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "cached_files",
    indices = [
        Index(value = ["sizeBytes"], name = "index_cached_files_sizeBytes"),
        Index(value = ["hashHex"], name = "index_cached_files_hashHex"),
        Index(value = ["sizeBytes", "hashHex"], name = "index_cached_files_sizeBytes_hashHex"),
        Index(value = ["sizeBytes", "normalizedPath"], name = "index_cached_files_sizeBytes_normalizedPath"),
        Index(value = ["lastModifiedMillis", "normalizedPath"], name = "index_cached_files_lastModifiedMillis_normalizedPath")
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
