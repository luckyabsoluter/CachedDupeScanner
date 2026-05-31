package opensource.cached_dupe_scanner.storage

interface CacheMutationObserver {
    fun onCachedFilesChanged(normalizedPaths: List<String>)
    fun onCacheCleared()
}

