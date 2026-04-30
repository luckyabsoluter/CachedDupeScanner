package opensource.cached_dupe_scanner.cache

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SimilarityExperimentDao {
    @Query("SELECT * FROM similarity_experiment_runs ORDER BY finishedAtMillis DESC")
    fun listRuns(): List<SimilarityExperimentRunEntity>

    @Query("SELECT * FROM similarity_experiment_runs WHERE experimentId = :experimentId LIMIT 1")
    fun getRun(experimentId: String): SimilarityExperimentRunEntity?

    @Query(
        """
        SELECT * FROM similarity_clusters
        WHERE experimentId = :experimentId
        ORDER BY fileCount DESC, totalBytes DESC, signature ASC
        """
    )
    fun listClusters(experimentId: String): List<SimilarityClusterEntity>

    @Query("DELETE FROM similarity_experiment_runs WHERE experimentId = :experimentId")
    fun deleteRun(experimentId: String)

    @Query("DELETE FROM similarity_clusters WHERE experimentId = :experimentId")
    fun deleteClusters(experimentId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertRun(run: SimilarityExperimentRunEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertClusters(clusters: List<SimilarityClusterEntity>)
}
