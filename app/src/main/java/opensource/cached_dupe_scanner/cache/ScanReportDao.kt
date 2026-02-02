package opensource.cached_dupe_scanner.cache

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface ScanReportDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(report: ScanReportEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTargets(targets: List<ScanReportTargetEntity>)

    @Query("DELETE FROM scan_report_targets WHERE reportId = :reportId")
    suspend fun deleteTargets(reportId: String)

    @Transaction
    @Query("SELECT * FROM scan_reports ORDER BY startedAtMillis DESC")
    suspend fun getAll(): List<ScanReportWithTargets>

    @Transaction
    @Query("SELECT * FROM scan_reports WHERE id = :reportId LIMIT 1")
    suspend fun getById(reportId: String): ScanReportWithTargets?

    @Query("DELETE FROM scan_reports")
    suspend fun clearAll()
}