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
    private val database: CacheDatabase? = null
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
                val hash = Hashing.sha256Hex(file)
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
                val hash = Hashing.sha256Hex(file)
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
            val hash = Hashing.sha256Hex(file)
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
        onProgress: (DbMaintenanceProgress) -> Unit
    ): DbMaintenanceProgress {
        val total = dao.countAll()
        var processed = 0
        var deleted = 0
        var rehashed = 0
        var missingHashed = 0
        val batchSize = 200
        var lastPath = ""
        while (true) {
            val batch = dao.getPageAfter(lastPath, batchSize)
            if (batch.isEmpty()) break
            batch.forEach { entity ->
                val path = entity.path.ifBlank { entity.normalizedPath }
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
                    return@forEach
                }

                val size = file.length()
                val modified = file.lastModified()
                val isStale = size != entity.sizeBytes || modified != entity.lastModifiedMillis
                val isMissingHash = entity.hashHex.isNullOrBlank()
                var shouldHash = false
                if (rehashStale && isStale) {
                    rehashed += 1
                    shouldHash = true
                }
                if (rehashMissing && isMissingHash) {
                    missingHashed += 1
                    shouldHash = true
                }
                if (shouldHash) {
                    val hash = Hashing.sha256Hex(file)
                    val updatedEntity = entity.copy(
                        sizeBytes = size,
                        lastModifiedMillis = modified,
                        hashHex = hash
                    )
                    upsertEntityAndRefreshGroups(before = entity, after = updatedEntity)
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
        return DbMaintenanceProgress(
            total = total,
            processed = processed,
            deleted = deleted,
            rehashed = rehashed,
            missingHashed = missingHashed,
            currentPath = null
        )
    }

    fun clearAll() {
        runInConsistencyTransaction {
            dao.clear()
            groupDao?.clear()
        }
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

data class DbMaintenanceProgress(
    val total: Int,
    val processed: Int,
    val deleted: Int,
    val rehashed: Int,
    val missingHashed: Int,
    val currentPath: String?
)

private fun CachedFileEntity.toMetadata(): FileMetadata {
    return FileMetadata(
        path = path,
        normalizedPath = normalizedPath,
        sizeBytes = sizeBytes,
        lastModifiedMillis = lastModifiedMillis,
        hashHex = hashHex
    )
}
