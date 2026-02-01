package opensource.cached_dupe_scanner.cache

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface ScanHistoryDao {
    @Insert
    fun insertSession(session: ScanSessionEntity): Long

    @Insert
    fun insertFiles(files: List<ScanFileEntity>)

    @Query("SELECT * FROM scan_files")
    fun getAllFiles(): List<ScanFileEntity>

    @Query("SELECT COUNT(*) FROM scan_sessions")
    fun countSessions(): Int

    @Query("DELETE FROM scan_files")
    fun clearFiles()

    @Query("DELETE FROM scan_sessions")
    fun clearSessions()

    @Transaction
    fun insertScan(session: ScanSessionEntity, files: List<ScanFileEntity>) {
        val sessionId = insertSession(session)
        val withSession = files.map { it.copy(scanSessionId = sessionId) }
        insertFiles(withSession)
    }
}
