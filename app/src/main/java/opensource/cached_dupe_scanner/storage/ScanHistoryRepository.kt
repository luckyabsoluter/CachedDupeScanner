package opensource.cached_dupe_scanner.storage

import opensource.cached_dupe_scanner.cache.CachedFileEntity
import opensource.cached_dupe_scanner.cache.CacheDatabase
import opensource.cached_dupe_scanner.cache.DuplicateGroupDao
import opensource.cached_dupe_scanner.cache.FileCacheDao
import opensource.cached_dupe_scanner.cache.PathGroupKey
import opensource.cached_dupe_scanner.core.FileMetadata
import opensource.cached_dupe_scanner.core.Hashing
import opensource.cached_dupe_scanner.core.ScanResult
import opensource.cached_dupe_scanner.core.ScanResultMerger
import java.io.File

class ScanHistoryRepository(
    private val dao: FileCacheDao,
    private val settingsStore: AppSettingsStore,
    private val groupDao: DuplicateGroupDao? = null,
    private val database: CacheDatabase? = null,
    private val hashFile: (File, () -> Boolean) -> String? = { file, shouldContinue ->
        Hashing.sha256Hex(file, shouldContinue = shouldContinue)
    }
) {
    fun recordScan(result: ScanResult) {
        val settings = settingsStore.load()
        val files = result.files
            .filter { file -> !settings.skipZeroSizeInDb || file.sizeBytes > 0 }
            .map { file ->
                CachedFileEntity(
                    normalizedPath = file.normalizedPath,
                    path = file.path,
                    sizeBytes = file.sizeBytes,
                    lastModifiedMillis = file.lastModifiedMillis,
                    hashHex = file.hashHex
                )
            }

        files.chunked(RECORD_SCAN_CHUNK_SIZE).forEach { chunk ->
            runInConsistencyTransaction {
                val existingKeysByPath = loadExistingGroupKeysByPath(
                    paths = chunk.map { it.normalizedPath }
                )
                dao.upsertAll(chunk)
                val touched = linkedSetOf<GroupKey>()
                existingKeysByPath.values.forEach { touched.add(it) }
                chunk.forEach { entity ->
                    entity.toGroupKey()?.let(touched::add)
                }
                refreshGroupsLocked(touched)
            }
        }
    }

    fun loadMergedHistory(): ScanResult? {
        val files = dao.getAll().map { it.toMetadata() }
        if (files.isEmpty()) {
            return null
        }
        return ScanResultMerger.fromFiles(System.currentTimeMillis(), files)
    }

    fun loadAllFiles(): List<FileMetadata> {
        return dao.getAll().map { it.toMetadata() }
    }

    fun countAll(): Int {
        return dao.countAll()
    }

    fun deleteMissingByNormalizedPaths(normalizedPaths: List<String>): Int {
        var deleted = 0
        normalizedPaths.forEach { normalizedPath ->
            val entity = dao.getByNormalizedPath(normalizedPath) ?: return@forEach
            val path = entity.path.ifBlank { entity.normalizedPath }
            val file = File(path)
            if (!file.exists()) {
                deleteEntityAndRefreshGroup(entity)
                deleted += 1
            }
        }
        return deleted
    }

    fun deleteMissingAll(): Int {
        val entries = dao.getAll()
        var deleted = 0
        entries.forEach { entity ->
            val path = entity.path.ifBlank { entity.normalizedPath }
            val file = File(path)
            if (!file.exists()) {
                deleteEntityAndRefreshGroup(entity)
                deleted += 1
            }
        }
        return deleted
    }

    fun rehashIfChanged(normalizedPaths: List<String>): Int {
        var updated = 0
        normalizedPaths.forEach { normalizedPath ->
            val entity = dao.getByNormalizedPath(normalizedPath) ?: return@forEach
            val path = entity.path.ifBlank { entity.normalizedPath }
            val file = File(path)
            if (!file.exists()) return@forEach
            val size = file.length()
            val modified = file.lastModified()
            if (size != entity.sizeBytes || modified != entity.lastModifiedMillis) {
                val hash = requireNotNull(hashFile(file) { true })
                val updatedEntity = entity.copy(
                    sizeBytes = size,
                    lastModifiedMillis = modified,
                    hashHex = hash
                )
                upsertEntityAndRefreshGroups(before = entity, after = updatedEntity)
                updated += 1
            }
        }
        return updated
    }

    fun rehashIfChangedAll(): Int {
        val entries = dao.getAll()
        var updated = 0
        entries.forEach { entity ->
            val path = entity.path.ifBlank { entity.normalizedPath }
            val file = File(path)
            if (!file.exists()) return@forEach
            val size = file.length()
            val modified = file.lastModified()
            if (size != entity.sizeBytes || modified != entity.lastModifiedMillis) {
                val hash = requireNotNull(hashFile(file) { true })
                val updatedEntity = entity.copy(
                    sizeBytes = size,
                    lastModifiedMillis = modified,
                    hashHex = hash
                )
                upsertEntityAndRefreshGroups(before = entity, after = updatedEntity)
                updated += 1
            }
        }
        return updated
    }

    fun rehashMissingHashesAll(): Int {
        val entries = dao.getAll()
        var updated = 0
        entries.forEach { entity ->
            if (!entity.hashHex.isNullOrBlank()) return@forEach
            val path = entity.path.ifBlank { entity.normalizedPath }
            val file = File(path)
            if (!file.exists()) return@forEach
            val hash = requireNotNull(hashFile(file) { true })
            val updatedEntity = entity.copy(hashHex = hash)
            upsertEntityAndRefreshGroups(before = entity, after = updatedEntity)
            updated += 1
        }
        return updated
    }

    fun runMaintenance(
        deleteMissing: Boolean,
        rehashStale: Boolean,
        rehashMissing: Boolean,
        shouldContinue: () -> Boolean,
        onProgress: (DbMaintenanceProgress) -> Unit
    ): DbMaintenanceSummary {
        val total = dao.countAll()
        var processed = 0
        var deleted = 0
        var rehashed = 0
        var missingHashed = 0
        val batchSize = 200
        var lastPath = ""
        var currentPath: String? = null
        while (true) {
            if (!shouldContinue()) break
            val batch = dao.getPageAfter(lastPath, batchSize)
            if (batch.isEmpty()) break
            for (entity in batch) {
                if (!shouldContinue()) {
                    return DbMaintenanceSummary(
                        total = total,
                        processed = processed,
                        deleted = deleted,
                        rehashed = rehashed,
                        missingHashed = missingHashed,
                        cancelled = true,
                        currentPath = currentPath
                    )
                }
                val path = entity.path.ifBlank { entity.normalizedPath }
                currentPath = path
                val file = File(path)
                if (!file.exists()) {
                    if (deleteMissing) {
                        deleteEntityAndRefreshGroup(entity)
                        deleted += 1
                    }
                    processed += 1
                    onProgress(
                        DbMaintenanceProgress(
                            total = total,
                            processed = processed,
                            deleted = deleted,
                            rehashed = rehashed,
                            missingHashed = missingHashed,
                            currentPath = path
                        )
                    )
                    lastPath = entity.normalizedPath
                    continue
                }

                val size = file.length()
                val modified = file.lastModified()
                val isStale = size != entity.sizeBytes || modified != entity.lastModifiedMillis
                val isMissingHash = entity.hashHex.isNullOrBlank()
                val shouldRehashStale = rehashStale && isStale
                val shouldHashMissing = rehashMissing && isMissingHash
                val shouldHash = shouldRehashStale || shouldHashMissing
                if (shouldHash) {
                    val hash = hashFile(file, shouldContinue)
                    if (hash == null) {
                        return DbMaintenanceSummary(
                            total = total,
                            processed = processed,
                            deleted = deleted,
                            rehashed = rehashed,
                            missingHashed = missingHashed,
                            cancelled = true,
                            currentPath = currentPath
                        )
                    }
                    val updatedEntity = entity.copy(
                        sizeBytes = size,
                        lastModifiedMillis = modified,
                        hashHex = hash
                    )
                    upsertEntityAndRefreshGroups(before = entity, after = updatedEntity)
                    if (shouldRehashStale) {
                        rehashed += 1
                    }
                    if (shouldHashMissing) {
                        missingHashed += 1
                    }
                }

                processed += 1
                onProgress(
                    DbMaintenanceProgress(
                        total = total,
                        processed = processed,
                        deleted = deleted,
                        rehashed = rehashed,
                        missingHashed = missingHashed,
                        currentPath = path
                    )
                )
                lastPath = entity.normalizedPath
            }
        }
        return DbMaintenanceSummary(
            total = total,
            processed = processed,
            deleted = deleted,
            rehashed = rehashed,
            missingHashed = missingHashed,
            cancelled = processed < total,
            currentPath = currentPath
        )
    }

    fun clearAll() {
        clearAll(shouldContinue = { true }) { }
    }

    fun clearAll(
        shouldContinue: () -> Boolean,
        onProgress: (ClearCacheProgress) -> Unit
    ): ClearCacheSummary {
        val totalFiles = dao.countAll()
        val totalGroups = groupDao?.countGroups() ?: 0
        val total = totalFiles + totalGroups
        var processed = 0
        var clearedFiles = 0
        var clearedGroups = 0
        val batchSize = 200
        var lastPath = ""

        while (true) {
            if (!shouldContinue()) break
            val batch = dao.getPageAfter(lastPath, batchSize)
            if (batch.isEmpty()) break
            val paths = batch.map { it.normalizedPath }
            runInConsistencyTransaction {
                dao.deleteByNormalizedPaths(paths)
            }
            clearedFiles += batch.size
            processed += batch.size
            lastPath = batch.last().normalizedPath
            onProgress(
                ClearCacheProgress(
                    total = total,
                    processed = processed,
                    clearedFiles = clearedFiles,
                    clearedGroups = clearedGroups
                )
            )
        }

        val groups = groupDao
        if (groups != null) {
            while (true) {
                if (!shouldContinue()) break
                val batch = groups.listPageByKey(limit = batchSize, offset = 0)
                if (batch.isEmpty()) break
                runInConsistencyTransaction {
                    batch.forEach { group ->
                        groups.delete(group.sizeBytes, group.hashHex)
                    }
                }
                clearedGroups += batch.size
                processed += batch.size
                onProgress(
                    ClearCacheProgress(
                        total = total,
                        processed = processed,
                        clearedFiles = clearedFiles,
                        clearedGroups = clearedGroups
                    )
                )
            }
        }

        return ClearCacheSummary(
            total = total,
            processed = processed,
            clearedFiles = clearedFiles,
            clearedGroups = clearedGroups,
            cancelled = processed < total
        )
    }

    fun deleteByNormalizedPath(normalizedPath: String) {
        runInConsistencyTransaction {
            val before = dao.getByNormalizedPath(normalizedPath)
            dao.deleteByNormalizedPath(normalizedPath)
            refreshGroupsLocked(touchedGroupKeys(before = before, after = null))
        }
    }

    fun upsert(entity: CachedFileEntity) {
        runInConsistencyTransaction {
            val before = dao.getByNormalizedPath(entity.normalizedPath)
            dao.upsert(entity)
            refreshGroupsLocked(touchedGroupKeys(before = before, after = entity))
        }
    }

    private fun deleteEntityAndRefreshGroup(entity: CachedFileEntity) {
        runInConsistencyTransaction {
            dao.deleteByNormalizedPath(entity.normalizedPath)
            refreshGroupsLocked(touchedGroupKeys(before = entity, after = null))
        }
    }

    private fun upsertEntityAndRefreshGroups(before: CachedFileEntity, after: CachedFileEntity) {
        runInConsistencyTransaction {
            dao.upsert(after)
            refreshGroupsLocked(touchedGroupKeys(before = before, after = after))
        }
    }

    private fun refreshGroupsLocked(keys: Set<GroupKey>) {
        if (keys.isEmpty()) return
        val groups = groupDao ?: return
        val snapshotUpdatedAtMillis = groups.latestUpdatedAtMillis() ?: System.currentTimeMillis()
        keys.forEach { key ->
            groups.delete(
                sizeBytes = key.sizeBytes,
                hashHex = key.hashHex
            )
            groups.insertSingleGroupFromCache(
                sizeBytes = key.sizeBytes,
                hashHex = key.hashHex,
                updatedAtMillis = snapshotUpdatedAtMillis
            )
        }
    }

    private fun loadExistingGroupKeysByPath(paths: List<String>): Map<String, GroupKey> {
        if (paths.isEmpty()) return emptyMap()
        val keysByPath = LinkedHashMap<String, GroupKey>(paths.size)
        paths.distinct()
            .chunked(PATH_QUERY_CHUNK_SIZE)
            .forEach { chunk ->
                dao.findGroupKeysByPaths(chunk).forEach { row ->
                    row.toGroupKey()?.let { group ->
                        keysByPath[row.normalizedPath] = group
                    }
                }
            }
        return keysByPath
    }

    private fun touchedGroupKeys(before: CachedFileEntity?, after: CachedFileEntity?): Set<GroupKey> {
        val keys = linkedSetOf<GroupKey>()
        before.toGroupKey()?.let(keys::add)
        after.toGroupKey()?.let(keys::add)
        return keys
    }

    private inline fun runInConsistencyTransaction(crossinline block: () -> Unit) {
        val db = database
        if (db == null) {
            block()
            return
        }
        db.runInTransaction {
            block()
        }
    }
}

private data class GroupKey(
    val sizeBytes: Long,
    val hashHex: String
)

private fun CachedFileEntity?.toGroupKey(): GroupKey? {
    val entity = this ?: return null
    val hash = entity.hashHex?.takeIf { it.isNotBlank() } ?: return null
    return GroupKey(
        sizeBytes = entity.sizeBytes,
        hashHex = hash
    )
}

private fun PathGroupKey.toGroupKey(): GroupKey? {
    val hash = hashHex?.takeIf { it.isNotBlank() } ?: return null
    return GroupKey(
        sizeBytes = sizeBytes,
        hashHex = hash
    )
}

private const val RECORD_SCAN_CHUNK_SIZE = 500
private const val PATH_QUERY_CHUNK_SIZE = 800

private fun CachedFileEntity.toMetadata(): FileMetadata {
    return FileMetadata(
        path = path,
        normalizedPath = normalizedPath,
        sizeBytes = sizeBytes,
        lastModifiedMillis = lastModifiedMillis,
        hashHex = hashHex
    )
}
