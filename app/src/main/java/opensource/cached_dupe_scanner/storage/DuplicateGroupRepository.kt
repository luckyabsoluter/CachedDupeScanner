package opensource.cached_dupe_scanner.storage

import opensource.cached_dupe_scanner.cache.DuplicateGroupDao
import opensource.cached_dupe_scanner.cache.DuplicateGroupEntity

class DuplicateGroupRepository(
    private val dao: DuplicateGroupDao
) {
    fun rebuild() {
        dao.rebuildFromCache(System.currentTimeMillis())
    }

    fun clear() {
        dao.clear()
    }

    fun countGroups(): Int = dao.countGroups()

    fun listPage(sortKey: DuplicateGroupSortKey, offset: Int, limit: Int): List<DuplicateGroupEntity> {
        return when (sortKey) {
            DuplicateGroupSortKey.CountDesc -> dao.listByCountDesc(limit = limit, offset = offset)
            DuplicateGroupSortKey.CountAsc -> dao.listByCountAsc(limit = limit, offset = offset)
            DuplicateGroupSortKey.TotalBytesDesc -> dao.listByTotalBytesDesc(limit = limit, offset = offset)
            DuplicateGroupSortKey.TotalBytesAsc -> dao.listByTotalBytesAsc(limit = limit, offset = offset)
            DuplicateGroupSortKey.PerFileSizeDesc -> dao.listByPerFileSizeDesc(limit = limit, offset = offset)
            DuplicateGroupSortKey.PerFileSizeAsc -> dao.listByPerFileSizeAsc(limit = limit, offset = offset)
        }
    }

    /**
     * Updates a single group row after a targeted cached_files mutation.
     * If member count drops below 2, the group row is removed.
     */
    fun refreshGroup(sizeBytes: Long, hashHex: String) {
        val count = dao.countMembers(sizeBytes, hashHex)
        if (count <= 1) {
            dao.delete(sizeBytes, hashHex)
        } else {
            // For simplicity, do a cheap delete+insert for this key by rebuilding the whole table.
            // Callers should prefer rebuild() for bulk operations.
            rebuild()
        }
    }
}

enum class DuplicateGroupSortKey {
    CountDesc,
    CountAsc,
    TotalBytesDesc,
    TotalBytesAsc,
    PerFileSizeDesc,
    PerFileSizeAsc
}
