package opensource.cached_dupe_scanner.core

data class DuplicateGroup(
    val hashHex: String,
    val files: List<FileMetadata>
)

data class ScanCacheSnapshot(
    val normalizedPath: String,
    val previous: FileMetadata?
)

data class ScanResult(
    val scannedAtMillis: Long,
    val files: List<FileMetadata>,
    val duplicateGroups: List<DuplicateGroup>,
    val cacheSnapshots: List<ScanCacheSnapshot> = emptyList()
)
