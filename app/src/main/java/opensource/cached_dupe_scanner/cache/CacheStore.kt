package opensource.cached_dupe_scanner.cache

import opensource.cached_dupe_scanner.core.FileMetadata

class CacheStore(
    private val dao: FileCacheDao
) {
    fun upsert(metadata: FileMetadata) {
        dao.upsert(metadata.toEntity())
    }

    fun upsertAll(metadata: List<FileMetadata>) {
        if (metadata.isEmpty()) return
        dao.upsertAll(metadata.map { it.toEntity() })
    }

    fun deleteByNormalizedPath(normalizedPath: String) {
        dao.deleteByNormalizedPath(normalizedPath)
    }

    fun lookup(current: FileMetadata): CacheLookupResult {
        val cachedEntity = dao.getByNormalizedPath(current.normalizedPath)
            ?: return CacheLookupResult(CacheStatus.MISS)

        val cachedMetadata = cachedEntity.toMetadata()
        val isFresh = cachedEntity.sizeBytes == current.sizeBytes &&
            cachedEntity.lastModifiedMillis == current.lastModifiedMillis

        return if (isFresh) {
            CacheLookupResult(CacheStatus.FRESH, cachedMetadata)
        } else {
            CacheLookupResult(CacheStatus.STALE, cachedMetadata)
        }
    }

    fun countBySizes(sizes: Set<Long>): Map<Long, Int> {
        if (sizes.isEmpty()) return emptyMap()
        val result = mutableMapOf<Long, Int>()
        sizes.toList().chunked(SQLITE_VARIABLE_LIMIT).forEach { chunk ->
            dao.countBySizes(chunk).forEach { entry ->
                result[entry.sizeBytes] = (result[entry.sizeBytes] ?: 0) + entry.count
            }
        }
        return result
    }

    private companion object {
        const val SQLITE_VARIABLE_LIMIT = 900
    }
}

private fun FileMetadata.toEntity(): CachedFileEntity {
    return CachedFileEntity(
        normalizedPath = normalizedPath,
        path = path,
        sizeBytes = sizeBytes,
        lastModifiedMillis = lastModifiedMillis,
        hashHex = hashHex
    )
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
