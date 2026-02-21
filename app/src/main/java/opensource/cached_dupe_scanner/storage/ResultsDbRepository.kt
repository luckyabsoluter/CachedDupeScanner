package opensource.cached_dupe_scanner.storage

import opensource.cached_dupe_scanner.cache.CachedFileEntity
import opensource.cached_dupe_scanner.cache.DuplicateGroupDao
import opensource.cached_dupe_scanner.cache.DuplicateGroupEntity
import opensource.cached_dupe_scanner.cache.FileCacheDao
import opensource.cached_dupe_scanner.core.FileMetadata

data class ResultsSnapshot(
    val fileCount: Int,
    val groupCount: Int,
    val updatedAtMillis: Long?,
    val firstPage: List<DuplicateGroupEntity>
)

class ResultsDbRepository(
    private val fileDao: FileCacheDao,
    private val groupDao: DuplicateGroupDao
) {
    fun countFiles(): Int = fileDao.countAll()

    fun countGroups(): Int = groupDao.countGroups()

    fun rebuildGroups(updatedAtMillis: Long = System.currentTimeMillis()) {
        groupDao.rebuildFromCache(updatedAtMillis)
    }

    fun refreshSingleGroup(sizeBytes: Long, hashHex: String, updatedAtMillis: Long = System.currentTimeMillis()) {
        groupDao.refreshSingleGroup(sizeBytes, hashHex, updatedAtMillis)
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

    fun latestSnapshotUpdatedAt(): Long? = groupDao.latestUpdatedAtMillis()

    fun hasSnapshotChanged(snapshotUpdatedAtMillis: Long?): Boolean {
        return groupDao.latestUpdatedAtMillis() != snapshotUpdatedAtMillis
    }

    fun loadInitialSnapshot(
        sortKey: DuplicateGroupSortKey,
        limit: Int,
        rebuild: Boolean
    ): ResultsSnapshot {
        if (rebuild) {
            rebuildGroups()
        }
        val fileCount = countFiles()
        val updatedAtMillis = groupDao.latestUpdatedAtMillis()
        if (updatedAtMillis == null) {
            return ResultsSnapshot(
                fileCount = fileCount,
                groupCount = 0,
                updatedAtMillis = null,
                firstPage = emptyList()
            )
        }

        val groupCount = groupDao.countGroupsAt(updatedAtMillis)
        val safeLimit = limit.coerceAtLeast(0).coerceAtMost(groupCount)
        val firstPage = if (safeLimit > 0) {
            loadPageAtSnapshot(
                sortKey = sortKey,
                snapshotUpdatedAtMillis = updatedAtMillis,
                offset = 0,
                limit = safeLimit
            )
        } else {
            emptyList()
        }
        return ResultsSnapshot(
            fileCount = fileCount,
            groupCount = groupCount,
            updatedAtMillis = updatedAtMillis,
            firstPage = firstPage
        )
    }

    fun loadPageAtSnapshot(
        sortKey: DuplicateGroupSortKey,
        snapshotUpdatedAtMillis: Long,
        offset: Int,
        limit: Int
    ): List<DuplicateGroupEntity> {
        if (offset < 0 || limit <= 0) return emptyList()
        return when (sortKey) {
            DuplicateGroupSortKey.CountDesc -> groupDao.listByCountDescAt(snapshotUpdatedAtMillis, limit, offset)
            DuplicateGroupSortKey.CountAsc -> groupDao.listByCountAscAt(snapshotUpdatedAtMillis, limit, offset)
            DuplicateGroupSortKey.TotalBytesDesc -> groupDao.listByTotalBytesDescAt(snapshotUpdatedAtMillis, limit, offset)
            DuplicateGroupSortKey.TotalBytesAsc -> groupDao.listByTotalBytesAscAt(snapshotUpdatedAtMillis, limit, offset)
            DuplicateGroupSortKey.PerFileSizeDesc -> groupDao.listByPerFileSizeDescAt(snapshotUpdatedAtMillis, limit, offset)
            DuplicateGroupSortKey.PerFileSizeAsc -> groupDao.listByPerFileSizeAscAt(snapshotUpdatedAtMillis, limit, offset)
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
