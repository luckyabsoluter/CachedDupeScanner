package opensource.cached_dupe_scanner.core

object ScanResultMerger {
    fun merge(scannedAtMillis: Long, results: List<ScanResult>): ScanResult {
        val files = results.flatMap { it.files }
        return fromFiles(scannedAtMillis, files)
    }

    fun fromFiles(scannedAtMillis: Long, files: List<FileMetadata>): ScanResult {
        val duplicateGroups = files
            .filter { it.hashHex != null }
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
