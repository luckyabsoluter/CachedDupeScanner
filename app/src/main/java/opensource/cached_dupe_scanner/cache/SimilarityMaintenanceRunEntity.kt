package opensource.cached_dupe_scanner.cache

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "similarity_maintenance_runs",
    indices = [
        Index(value = ["settingId", "startedAtMillis"], name = "index_similarity_maintenance_runs_setting_started")
    ]
)
data class SimilarityMaintenanceRunEntity(
    @PrimaryKey(autoGenerate = true)
    val runId: Long = 0L,
    val settingId: Long,
    val startedAtMillis: Long,
    val finishedAtMillis: Long,
    val candidateCount: Int,
    val processedCount: Int,
    val skippedCount: Int,
    val clusterCount: Int,
    val duplicateFileCount: Int,
    val cancelled: Boolean
)

