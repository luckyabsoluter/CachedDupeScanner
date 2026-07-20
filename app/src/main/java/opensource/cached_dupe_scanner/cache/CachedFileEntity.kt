package opensource.cached_dupe_scanner.cache

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "cached_files",
    indices = [
        Index(value = ["normalizedPath"], unique = true, name = "index_cached_files_normalizedPath"),
        Index(value = ["sizeBytes"], name = "index_cached_files_sizeBytes"),
        Index(value = ["hashBytes"], name = "index_cached_files_hashBytes"),
        Index(value = ["sizeBytes", "hashBytes"], name = "index_cached_files_sizeBytes_hashBytes"),
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
    /** Stable normalized absolute path used for lookup and display. */
    val normalizedPath: String,
    /** Original display path shown to users. */
    val path: String,
    /** Last observed file size in bytes. */
    val sizeBytes: Long,
    /** Last observed file mtime in epoch millis. */
    val lastModifiedMillis: Long,
    /** Compact SHA-256 storage used by database grouping and comparisons. */
    val hashBytes: StoredHash?,
    /** Stable numeric identity used by derived tables and joins. */
    @PrimaryKey(autoGenerate = true)
    val fileId: Long = 0L
) {
    constructor(
        normalizedPath: String,
        path: String,
        sizeBytes: Long,
        lastModifiedMillis: Long,
        hashHex: String?,
        fileId: Long = 0L
    ) : this(
        normalizedPath = normalizedPath,
        path = path,
        sizeBytes = sizeBytes,
        lastModifiedMillis = lastModifiedMillis,
        hashBytes = storedHashOrNull(hashHex),
        fileId = fileId
    )

    val hashHex: String?
        get() = hashBytes?.toExternalString()
}
