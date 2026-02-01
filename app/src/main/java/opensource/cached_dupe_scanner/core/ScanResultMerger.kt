package opensource.cached_dupe_scanner.core

object ScanResultMerger {
    fun merge(scannedAtMillis: Long, results: List<ScanResult>): ScanResult {
        val files = results.flatMap { it.files }
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
