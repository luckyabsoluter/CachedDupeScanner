package opensource.cached_dupe_scanner.core

object ScanResultViewFilter {
    fun filterForDisplay(
        result: ScanResult,
        hideZeroSizeInResults: Boolean
    ): ScanResult {
        val files = if (hideZeroSizeInResults) {
            result.files.filter { it.sizeBytes > 0 }
        } else {
            result.files
        }

        return ScanResultMerger.fromFiles(
            scannedAtMillis = result.scannedAtMillis,
            files = files
        )
    }
}
