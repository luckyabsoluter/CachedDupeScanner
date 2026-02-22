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
/**
 * Canonical persisted cache row for one file path.
 *
 * Rows in this table are the source of truth used by incremental scan logic and group derivation.
 */
data class CachedFileEntity(
    /** Stable normalized absolute path. Primary key. */
    @PrimaryKey
    val normalizedPath: String,
    /** Original display path shown to users. */
    val path: String,
    /** Last observed file size in bytes. */
    val sizeBytes: Long,
    /** Last observed file mtime in epoch millis. */
    val lastModifiedMillis: Long,
    /** SHA-256 hash when available; null when not computed yet. */
    val hashHex: String?
)
