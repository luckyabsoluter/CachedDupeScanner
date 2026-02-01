package opensource.cached_dupe_scanner.cache

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "scan_files",
    foreignKeys = [
        ForeignKey(
            entity = ScanSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["scanSessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("scanSessionId"), Index("hashHex")]
)
data class ScanFileEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val scanSessionId: Long,
    val path: String,
    val normalizedPath: String,
    val sizeBytes: Long,
    val lastModifiedMillis: Long,
    val hashHex: String?
)
