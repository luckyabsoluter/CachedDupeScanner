package opensource.cached_dupe_scanner.cache

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scan_sessions")
data class ScanSessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val scannedAtMillis: Long
)
