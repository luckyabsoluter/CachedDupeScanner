package opensource.cached_dupe_scanner.cache

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "dupe_groups",
    primaryKeys = ["sizeBytes", "hashHex"],
    indices = [
        Index(value = ["fileCount"], name = "index_dupe_groups_fileCount"),
        Index(value = ["totalBytes"], name = "index_dupe_groups_totalBytes")
    ]
)
data class DuplicateGroupEntity(
    val sizeBytes: Long,
    val hashHex: String,
    val fileCount: Int,
    val totalBytes: Long,
    val updatedAtMillis: Long
)
