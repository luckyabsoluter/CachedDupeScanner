package opensource.cached_dupe_scanner.storage

import opensource.cached_dupe_scanner.cache.CachedFileEntity
import opensource.cached_dupe_scanner.cache.DuplicateGroupDao
import opensource.cached_dupe_scanner.cache.DuplicateGroupEntity
import opensource.cached_dupe_scanner.cache.FileCacheDao
import opensource.cached_dupe_scanner.core.FileMetadata

class ResultsDbRepository(
    private val fileDao: FileCacheDao,
    private val groupDao: DuplicateGroupDao
) {
    fun countFiles(): Int = fileDao.countAll()

    fun countGroups(): Int = groupDao.countGroups()

    fun rebuildGroups() {
        groupDao.rebuildFromCache(System.currentTimeMillis())
    }

    fun listGroups(sortKey: DuplicateGroupSortKey, offset: Int, limit: Int): List<DuplicateGroupEntity> {
        return when (sortKey) {
            DuplicateGroupSortKey.CountDesc -> groupDao.listByCountDesc(limit, offset)
            DuplicateGroupSortKey.CountAsc -> groupDao.listByCountAsc(limit, offset)
            DuplicateGroupSortKey.TotalBytesDesc -> groupDao.listByTotalBytesDesc(limit, offset)
            DuplicateGroupSortKey.TotalBytesAsc -> groupDao.listByTotalBytesAsc(limit, offset)
            DuplicateGroupSortKey.PerFileSizeDesc -> groupDao.listByPerFileSizeDesc(limit, offset)
            DuplicateGroupSortKey.PerFileSizeAsc -> groupDao.listByPerFileSizeAsc(limit, offset)
        }
    }

    fun listGroupMembers(sizeBytes: Long, hashHex: String, afterPath: String?, limit: Int): List<FileMetadata> {
        val entities = if (afterPath == null) {
            fileDao.listMembersBySizeAndHash(sizeBytes, hashHex, limit)
        } else {
            fileDao.listMembersBySizeAndHashAfter(sizeBytes, hashHex, afterPath, limit)
        }
        return entities.map { it.toMetadata() }
    }
}

private fun CachedFileEntity.toMetadata(): FileMetadata {
    return FileMetadata(
        path = path,
        normalizedPath = normalizedPath,
        sizeBytes = sizeBytes,
        lastModifiedMillis = lastModifiedMillis,
        hashHex = hashHex
    )
}
