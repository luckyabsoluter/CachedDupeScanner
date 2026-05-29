package opensource.cached_dupe_scanner.cache

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "similarity_experiment_runs")
data class SimilarityExperimentRunEntity(
    @PrimaryKey
    val experimentId: String,
    val experimentName: String,
    val startedAtMillis: Long,
    val finishedAtMillis: Long,
    val candidateCount: Int,
    val processedCount: Int,
    val skippedCount: Int,
    val clusterCount: Int,
    val duplicateFileCount: Int
)
