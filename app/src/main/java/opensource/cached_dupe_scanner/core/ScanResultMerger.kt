package opensource.cached_dupe_scanner.core

object ScanResultMerger {
    fun merge(
        scannedAtMillis: Long,
        results: List<ScanResult>,
        excludeZeroSizeDuplicates: Boolean = false
    ): ScanResult {
        val files = results.flatMap { it.files }
        return fromFiles(scannedAtMillis, files, excludeZeroSizeDuplicates)
    }

    fun fromFiles(
        scannedAtMillis: Long,
        files: List<FileMetadata>,
        excludeZeroSizeDuplicates: Boolean = false
    ): ScanResult {
        val duplicateGroups = files
            .filter { it.hashHex != null }
            .filter { file -> !excludeZeroSizeDuplicates || file.sizeBytes > 0 }
            .groupBy { it.hashHex!! }
            .filterValues { it.size > 1 }
            .map { (hash, groupFiles) ->
                DuplicateGroup(hash, groupFiles)
            }
        return ScanResult(
            scannedAtMillis = scannedAtMillis,
            files = files,
            duplicateGroups = duplicateGroups
        )
    }
}
