package opensource.cached_dupe_scanner.cache

import opensource.cached_dupe_scanner.core.FileMetadata

enum class CacheStatus {
    MISS,
    STALE,
    FRESH
}

data class CacheLookupResult(
    val status: CacheStatus,
    val cached: FileMetadata? = null
)
