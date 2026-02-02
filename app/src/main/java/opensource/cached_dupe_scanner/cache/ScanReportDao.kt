package opensource.cached_dupe_scanner.cache

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ScanReportDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(report: ScanReportEntity)

    @Query("SELECT * FROM scan_reports ORDER BY startedAtMillis DESC")
    suspend fun getAll(): List<ScanReportEntity>

    @Query("SELECT * FROM scan_reports WHERE id = :reportId LIMIT 1")
    suspend fun getById(reportId: String): ScanReportEntity?

    @Query("DELETE FROM scan_reports")
    suspend fun clearAll()
}