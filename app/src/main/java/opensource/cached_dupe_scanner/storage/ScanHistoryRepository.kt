package opensource.cached_dupe_scanner.storage

import opensource.cached_dupe_scanner.cache.CachedFileEntity
import opensource.cached_dupe_scanner.cache.FileCacheDao
import opensource.cached_dupe_scanner.core.FileMetadata
import opensource.cached_dupe_scanner.core.ScanResult
import opensource.cached_dupe_scanner.core.ScanResultMerger

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

    fun clearAll() {
        dao.clear()
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
