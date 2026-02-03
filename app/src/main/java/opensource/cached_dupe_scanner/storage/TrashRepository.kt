package opensource.cached_dupe_scanner.storage

import opensource.cached_dupe_scanner.cache.TrashDao
import opensource.cached_dupe_scanner.cache.TrashEntryEntity

class TrashRepository(
    private val dao: TrashDao
) {
    fun listAll(): List<TrashEntryEntity> = dao.getAll()

    fun getById(id: String): TrashEntryEntity? = dao.getById(id)

    fun upsert(entry: TrashEntryEntity) = dao.upsert(entry)

    fun deleteById(id: String) = dao.deleteById(id)

    fun clear() = dao.clear()
}
