package opensource.cached_dupe_scanner.storage

import opensource.cached_dupe_scanner.cache.ScanFileEntity
import opensource.cached_dupe_scanner.cache.ScanHistoryDao
import opensource.cached_dupe_scanner.cache.ScanSessionEntity
import opensource.cached_dupe_scanner.core.FileMetadata
import opensource.cached_dupe_scanner.core.ScanResult
import opensource.cached_dupe_scanner.core.ScanResultMerger

class ScanHistoryRepository(private val dao: ScanHistoryDao) {
    fun recordScan(result: ScanResult) {
        val session = ScanSessionEntity(scannedAtMillis = result.scannedAtMillis)
        val files = result.files.map { file ->
            ScanFileEntity(
                scanSessionId = 0,
                path = file.path,
                normalizedPath = file.normalizedPath,
                sizeBytes = file.sizeBytes,
                lastModifiedMillis = file.lastModifiedMillis,
                hashHex = file.hashHex
            )
        }
        dao.insertScan(session, files)
    }

    fun loadMergedHistory(): ScanResult? {
        if (dao.countSessions() == 0) {
            return null
        }
        val files = dao.getAllFiles().map { it.toMetadata() }
        return ScanResultMerger.fromFiles(System.currentTimeMillis(), files)
    }

    fun clearAll() {
        dao.clearFiles()
        dao.clearSessions()
    }
}

private fun ScanFileEntity.toMetadata(): FileMetadata {
    return FileMetadata(
        path = path,
        normalizedPath = normalizedPath,
        sizeBytes = sizeBytes,
        lastModifiedMillis = lastModifiedMillis,
        hashHex = hashHex
    )
}
