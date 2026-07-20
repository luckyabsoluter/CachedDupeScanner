package opensource.cached_dupe_scanner.cache

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "dupe_groups",
    primaryKeys = ["sizeBytes", "hashBytes"],
    indices = [
        Index(value = ["fileCount"], name = "index_dupe_groups_fileCount"),
        Index(value = ["totalBytes"], name = "index_dupe_groups_totalBytes")
    ]
)
/**
 * Materialized duplicate-group snapshot row keyed by (`sizeBytes`, `hashBytes`).
 *
 * This table is derived from `cached_files` and is optimized for Results list pagination/sorting.
 */
data class DuplicateGroupEntity(
    /** Per-file byte size shared by all members in this group. */
    val sizeBytes: Long,
    /** Shared file hash for this group key. */
    val hashBytes: StoredHash,
    /** Number of files currently in this group. */
    val fileCount: Int,
    /** Aggregate bytes (`fileCount * sizeBytes`). */
    val totalBytes: Long,
    /** Snapshot generation timestamp used for snapshot-consistent paging. */
    val updatedAtMillis: Long
) {
    constructor(
        sizeBytes: Long,
        hashHex: String,
        fileCount: Int,
        totalBytes: Long,
        updatedAtMillis: Long
    ) : this(
        sizeBytes = sizeBytes,
        hashBytes = StoredHash.fromExternalString(hashHex),
        fileCount = fileCount,
        totalBytes = totalBytes,
        updatedAtMillis = updatedAtMillis
    )

    val hashHex: String
        get() = hashBytes.toExternalString()
}
