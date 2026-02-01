package opensource.cached_dupe_scanner.core

import org.junit.Assert.assertEquals
import org.junit.Test

class ScanResultViewFilterTest {
    @Test
    fun hidesZeroSizeFilesWhenEnabled() {
        val a = FileMetadata("/a", "/a", 0, 1, "h1")
        val b = FileMetadata("/b", "/b", 2, 2, "h1")
        val result = ScanResult(1, listOf(a, b), emptyList())

        val filtered = ScanResultViewFilter.filterForDisplay(
            result,
            hideZeroSizeInResults = true,
            excludeZeroSizeDuplicates = false
        )

        assertEquals(1, filtered.files.size)
        assertEquals("/b", filtered.files.first().normalizedPath)
    }
}
