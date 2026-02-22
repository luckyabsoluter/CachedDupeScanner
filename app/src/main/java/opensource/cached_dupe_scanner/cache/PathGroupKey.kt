package opensource.cached_dupe_scanner.cache

/** Lightweight projection for loading current group key by normalized path. */
data class PathGroupKey(
    val normalizedPath: String,
    val sizeBytes: Long,
    val hashHex: String?
)
