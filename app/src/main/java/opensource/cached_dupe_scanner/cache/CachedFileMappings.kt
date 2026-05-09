package opensource.cached_dupe_scanner.cache

import opensource.cached_dupe_scanner.core.FileMetadata

fun FileMetadata.toCachedFileEntity(): CachedFileEntity {
    return CachedFileEntity(
        normalizedPath = normalizedPath,
        path = path,
        sizeBytes = sizeBytes,
        lastModifiedMillis = lastModifiedMillis,
        hashHex = hashHex
    )
}

fun CachedFileEntity.toFileMetadata(): FileMetadata {
    return FileMetadata(
        path = path,
        normalizedPath = normalizedPath,
        sizeBytes = sizeBytes,
        lastModifiedMillis = lastModifiedMillis,
        hashHex = hashHex
    )
}
