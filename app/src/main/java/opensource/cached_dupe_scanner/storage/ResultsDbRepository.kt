package opensource.cached_dupe_scanner.storage

import opensource.cached_dupe_scanner.cache.CachedFileEntity
import opensource.cached_dupe_scanner.cache.DuplicateGroupDao
import opensource.cached_dupe_scanner.cache.DuplicateGroupEntity
import opensource.cached_dupe_scanner.cache.FileCacheDao
import opensource.cached_dupe_scanner.core.FileMetadata
import opensource.cached_dupe_scanner.core.Hashing
import java.io.File

data class ResultsSnapshot(
    val fileCount: Int,
    val groupCount: Int,
    val updatedAtMillis: Long?,
    val firstPage: List<DuplicateGroupEntity>
)

class ResultsDbRepository(
    private val fileDao: FileCacheDao,
    private val groupDao: DuplicateGroupDao,
    private val hashFile: (File, () -> Boolean) -> String? = { file, shouldContinue ->
        Hashing.sha256Hex(file, shouldContinue = shouldContinue)
    }
) {
    fun countFiles(): Int = fileDao.countAll()

    fun countGroups(): Int = groupDao.countGroups()

    fun rebuildGroups(updatedAtMillis: Long = System.currentTimeMillis()) {
        rebuildGroups(updatedAtMillis = updatedAtMillis, shouldContinue = { true }) { }
    }

    fun rebuildGroups(
        updatedAtMillis: Long = System.currentTimeMillis(),
        shouldContinue: () -> Boolean,
        onProgress: (RebuildGroupsProgress) -> Unit
    ): RebuildGroupsSummary {
        val repairTotal = fileDao.countMissingHashSizeCollisionCandidates()
        if (repairTotal > 0) {
            onProgress(
                RebuildGroupsProgress(
                    total = repairTotal,
                    processed = 0,
                    phase = RebuildGroupsPhase.RepairingMissingHashes
                )
            )
        }
        val repairSummary = repairMissingHashesForSizeCollisions(
            total = repairTotal,
            shouldContinue = shouldContinue,
            onProgress = onProgress
        )
        if (repairSummary.cancelled) {
            return repairSummary
        }
        val total = groupDao.countGroupsFromCache()
        onProgress(
            RebuildGroupsProgress(
                total = total,
                processed = 0,
                phase = RebuildGroupsPhase.RebuildingGroups
            )
        )
        var processed = 0
        var offset = 0
        val batchSize = 200
        groupDao.clearInternal()
        while (processed < total) {
            if (!shouldContinue()) {
                break
            }
            val batch = groupDao.listGroupsFromCache(limit = batchSize, offset = offset)
            if (batch.isEmpty()) {
                break
            }
            groupDao.upsertAll(
                batch.map { row ->
                    DuplicateGroupEntity(
                        sizeBytes = row.sizeBytes,
                        hashHex = row.hashHex,
                        fileCount = row.fileCount,
                        totalBytes = row.totalBytes,
                        updatedAtMillis = updatedAtMillis
                    )
                }
            )
            processed += batch.size
            offset += batch.size
            onProgress(
                RebuildGroupsProgress(
                    total = total,
                    processed = processed
                )
            )
        }
        return RebuildGroupsSummary(
            total = total,
            processed = processed,
            cancelled = processed < total,
            phase = RebuildGroupsPhase.RebuildingGroups
        )
    }

    private fun repairMissingHashesForSizeCollisions(
        total: Int,
        shouldContinue: () -> Boolean,
        onProgress: (RebuildGroupsProgress) -> Unit
    ): RebuildGroupsSummary {
        var afterPath = ""
        var processed = 0
        val batchSize = 200
        while (true) {
            if (!shouldContinue()) {
                return RebuildGroupsSummary(
                    total = total,
                    processed = processed,
                    cancelled = true,
                    phase = RebuildGroupsPhase.RepairingMissingHashes
                )
            }
            val batch = fileDao.listMissingHashSizeCollisionCandidatesAfter(
                afterPath = afterPath,
                limit = batchSize
            )
            if (batch.isEmpty()) {
                return RebuildGroupsSummary(
                    total = total,
                    processed = processed,
                    cancelled = false,
                    phase = RebuildGroupsPhase.RepairingMissingHashes
                )
            }
            for (entity in batch) {
                afterPath = entity.normalizedPath
                if (!shouldContinue()) {
                    return RebuildGroupsSummary(
                        total = total,
                        processed = processed,
                        cancelled = true,
                        phase = RebuildGroupsPhase.RepairingMissingHashes
                    )
                }
                val path = entity.path.ifBlank { entity.normalizedPath }
                val file = File(path)
                if (!file.exists()) {
                    processed += 1
                    onProgress(
                        RebuildGroupsProgress(
                            total = total,
                            processed = processed,
                            phase = RebuildGroupsPhase.RepairingMissingHashes,
                            currentPath = path
                        )
                    )
                    continue
                }
                val hash = runCatching {
                    hashFile(file, shouldContinue)
                }.getOrNull()
                if (hash == null) {
                    if (!shouldContinue()) {
                        return RebuildGroupsSummary(
                            total = total,
                            processed = processed,
                            cancelled = true,
                            phase = RebuildGroupsPhase.RepairingMissingHashes
                        )
                    }
                    processed += 1
                    onProgress(
                        RebuildGroupsProgress(
                            total = total,
                            processed = processed,
                            phase = RebuildGroupsPhase.RepairingMissingHashes,
                            currentPath = path
                        )
                    )
                    continue
                }
                fileDao.upsert(
                    entity.copy(
                        sizeBytes = file.length(),
                        lastModifiedMillis = file.lastModified(),
                        hashHex = hash
                    )
                )
                processed += 1
                onProgress(
                    RebuildGroupsProgress(
                        total = total,
                        processed = processed,
                        phase = RebuildGroupsPhase.RepairingMissingHashes,
                        currentPath = path
                    )
                )
            }
        }
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

    fun listAllGroupMembers(sizeBytes: Long, hashHex: String, pageSize: Int = 200): List<FileMetadata> {
        if (pageSize <= 0) return emptyList()
        val allMembers = mutableListOf<FileMetadata>()
        var afterPath: String? = null
        while (true) {
            val page = listGroupMembers(
                sizeBytes = sizeBytes,
                hashHex = hashHex,
                afterPath = afterPath,
                limit = pageSize
            )
            if (page.isEmpty()) {
                break
            }
            allMembers.addAll(page)
            if (page.size < pageSize) {
                break
            }
            afterPath = page.last().normalizedPath
        }
        return allMembers
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
