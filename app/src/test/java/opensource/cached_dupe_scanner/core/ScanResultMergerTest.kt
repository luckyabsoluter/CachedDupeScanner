package opensource.cached_dupe_scanner.core

import org.junit.Assert.assertEquals
import org.junit.Test

class ScanResultMergerTest {
    @Test
    fun mergeCombinesFilesAndDuplicates() {
        val a = FileMetadata("/a", "/a", 1, 1, "h1")
        val b = FileMetadata("/b", "/b", 1, 1, "h1")
        val c = FileMetadata("/c", "/c", 2, 2, "h2")

        val r1 = ScanResult(1, listOf(a), emptyList())
        val r2 = ScanResult(2, listOf(b, c), emptyList())

        val merged = ScanResultMerger.merge(3, listOf(r1, r2))

        assertEquals(3, merged.files.size)
        assertEquals(1, merged.duplicateGroups.size)
        assertEquals("h1", merged.duplicateGroups.first().hashHex)
        assertEquals(2, merged.duplicateGroups.first().files.size)
    }

    @Test
    fun excludesZeroSizeDuplicatesWhenEnabled() {
        val a = FileMetadata("/a", "/a", 0, 1, "h1")
        val b = FileMetadata("/b", "/b", 0, 1, "h1")

        val merged = ScanResultMerger.fromFiles(1, listOf(a, b), excludeZeroSizeDuplicates = true)

        assertEquals(2, merged.files.size)
        assertEquals(0, merged.duplicateGroups.size)
    }
}
