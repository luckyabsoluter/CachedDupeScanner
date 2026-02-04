package opensource.cached_dupe_scanner.cache

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface TrashDao {
    @Query("SELECT COUNT(*) FROM trash_entries")
    fun countAll(): Int

    @Query("SELECT * FROM trash_entries ORDER BY deletedAtMillis DESC")
    fun getAll(): List<TrashEntryEntity>

    @Query("SELECT * FROM trash_entries ORDER BY deletedAtMillis DESC, id DESC LIMIT :limit")
    fun getFirstPage(limit: Int): List<TrashEntryEntity>

    @Query(
        """
        SELECT * FROM trash_entries
        WHERE (deletedAtMillis < :beforeMillis)
           OR (deletedAtMillis = :beforeMillis AND id < :beforeId)
        ORDER BY deletedAtMillis DESC, id DESC
        LIMIT :limit
        """
    )
    fun getPageBefore(beforeMillis: Long, beforeId: String, limit: Int): List<TrashEntryEntity>

    @Query("SELECT * FROM trash_entries WHERE id = :id LIMIT 1")
    fun getById(id: String): TrashEntryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(entry: TrashEntryEntity)

    @Query("DELETE FROM trash_entries WHERE id = :id")
    fun deleteById(id: String)

    @Query("DELETE FROM trash_entries")
    fun clear()
}
