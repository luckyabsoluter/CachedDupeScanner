package opensource.cached_dupe_scanner.storage

import opensource.cached_dupe_scanner.cache.CachedFileEntity
import opensource.cached_dupe_scanner.cache.FileCacheDao
import opensource.cached_dupe_scanner.core.FileMetadata
import opensource.cached_dupe_scanner.core.ScanResult
import opensource.cached_dupe_scanner.core.ScanResultMerger
import opensource.cached_dupe_scanner.core.Hashing
import java.io.File

class ScanHistoryRepository(
    private val dao: FileCacheDao,
    private val settingsStore: AppSettingsStore
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
        dao.upsertAll(files)
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
        return dao.getAll().size
    }

    fun deleteMissingByNormalizedPaths(normalizedPaths: List<String>): Int {
        var deleted = 0
        normalizedPaths.forEach { normalizedPath ->
            val entity = dao.getByNormalizedPath(normalizedPath) ?: return@forEach
            val path = entity.path.ifBlank { entity.normalizedPath }
            val file = File(path)
            if (!file.exists()) {
                dao.deleteByNormalizedPath(normalizedPath)
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
                dao.deleteByNormalizedPath(entity.normalizedPath)
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
                dao.upsert(updatedEntity)
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
                dao.upsert(updatedEntity)
                updated += 1
            }
        }
        return updated
    }

    fun clearAll() {
        dao.clear()
    }

    fun deleteByNormalizedPath(normalizedPath: String) {
        dao.deleteByNormalizedPath(normalizedPath)
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
