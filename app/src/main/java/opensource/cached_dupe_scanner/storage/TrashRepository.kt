package opensource.cached_dupe_scanner.storage

import android.os.CancellationSignal
import opensource.cached_dupe_scanner.cache.CacheDatabase
import opensource.cached_dupe_scanner.cache.TrashDao
import opensource.cached_dupe_scanner.cache.TrashEntryEntity

class TrashRepository(
    private val dao: TrashDao,
    private val database: CacheDatabase? = null
) {
    constructor(database: CacheDatabase) : this(
        dao = database.trashDao(),
        database = database
    )

    fun listAll(): List<TrashEntryEntity> = dao.getAll()

    fun countAll(cancellationSignal: CancellationSignal? = null): Int {
        val currentDatabase = database
        if (currentDatabase == null || cancellationSignal == null) return dao.countAll()
        return currentDatabase.readRoomQueryWithCancellation(
            sql = "SELECT COUNT(*) FROM trash_entries",
            cancellationSignal = cancellationSignal
        ) { cursor ->
            check(cursor.moveToFirst())
            cursor.getInt(0)
        }
    }

    fun getFirstPage(
        limit: Int,
        cancellationSignal: CancellationSignal? = null
    ): List<TrashEntryEntity> {
        val currentDatabase = database
        if (currentDatabase == null || cancellationSignal == null) return dao.getFirstPage(limit)
        return currentDatabase.readTrashEntriesWithCancellation(
            sql = "SELECT * FROM trash_entries ORDER BY deletedAtMillis DESC, id DESC LIMIT ?",
            bindArgs = arrayOf(limit),
            cancellationSignal = cancellationSignal
        )
    }

    fun getPageBefore(
        beforeMillis: Long,
        beforeId: String,
        limit: Int,
        cancellationSignal: CancellationSignal? = null
    ): List<TrashEntryEntity> {
        val currentDatabase = database
        if (currentDatabase != null && cancellationSignal != null) {
            return currentDatabase.readTrashEntriesWithCancellation(
                sql = "SELECT * FROM trash_entries WHERE (deletedAtMillis < ?) OR (deletedAtMillis = ? AND id < ?) ORDER BY deletedAtMillis DESC, id DESC LIMIT ?",
                bindArgs = arrayOf<Any?>(beforeMillis, beforeMillis, beforeId, limit),
                cancellationSignal = cancellationSignal
            )
        }
        return dao.getPageBefore(beforeMillis, beforeId, limit)
    }

    fun getById(id: String): TrashEntryEntity? = dao.getById(id)

    fun upsert(entry: TrashEntryEntity) = dao.upsert(entry)

    fun deleteById(id: String) = dao.deleteById(id)

    fun clear() = dao.clear()
}
