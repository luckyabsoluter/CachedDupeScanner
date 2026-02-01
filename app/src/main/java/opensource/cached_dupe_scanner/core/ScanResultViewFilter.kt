package opensource.cached_dupe_scanner.core

object ScanResultViewFilter {
    fun filterForDisplay(
        result: ScanResult,
        hideZeroSizeInResults: Boolean,
        sortOption: ResultSortOption
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
            sortOption
        )

        return ScanResult(
            scannedAtMillis = result.scannedAtMillis,
            files = files,
            duplicateGroups = sortedGroups
        )
    }

    private fun sortGroups(
        groups: List<DuplicateGroup>,
        option: ResultSortOption
    ): List<DuplicateGroup> {
        return when (option) {
            ResultSortOption.CountDesc -> groups.sortedByDescending { it.files.size }
            ResultSortOption.TotalSizeDesc -> groups.sortedByDescending { it.files.sumOf { f -> f.sizeBytes } }
            ResultSortOption.NameAsc -> groups.sortedBy { it.files.firstOrNull()?.normalizedPath ?: "" }
        }
    }
}
