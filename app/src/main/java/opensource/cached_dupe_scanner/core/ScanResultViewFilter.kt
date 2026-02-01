package opensource.cached_dupe_scanner.core

object ScanResultViewFilter {
    fun filterForDisplay(
        result: ScanResult,
        hideZeroSizeInResults: Boolean,
        sortKey: ResultSortKey,
        sortDirection: SortDirection
    ): ScanResult {
        val files = if (hideZeroSizeInResults) {
            result.files.filter { it.sizeBytes > 0 }
        } else {
            result.files
        }

        val sortedGroups = sortGroups(
            ScanResultMerger.fromFiles(
                scannedAtMillis = result.scannedAtMillis,
                files = files
            ).duplicateGroups,
            sortKey,
            sortDirection
        )

        return ScanResult(
            scannedAtMillis = result.scannedAtMillis,
            files = files,
            duplicateGroups = sortedGroups
        )
    }

    private fun sortGroups(
        groups: List<DuplicateGroup>,
        key: ResultSortKey,
        direction: SortDirection
    ): List<DuplicateGroup> {
        val sorted = when (key) {
            ResultSortKey.Count -> groups.sortedBy { it.files.size }
            ResultSortKey.TotalSize -> groups.sortedBy { it.files.sumOf { f -> f.sizeBytes } }
            ResultSortKey.PerFileSize -> groups.sortedBy { it.files.firstOrNull()?.sizeBytes ?: 0L }
            ResultSortKey.Name -> groups.sortedBy { it.files.firstOrNull()?.normalizedPath ?: "" }
        }
        return if (direction == SortDirection.Desc) sorted.reversed() else sorted
    }
}
