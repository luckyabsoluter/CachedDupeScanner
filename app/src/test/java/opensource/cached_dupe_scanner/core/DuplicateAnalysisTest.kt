package opensource.cached_dupe_scanner.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DuplicateAnalysisTest {
    @Test
    fun detectsCrossDuplicatesBetweenExistingAndNew() {
        val existing = listOf(file("/a", "h1"))
        val newFiles = listOf(file("/b", "h1"))

        val analysis = DuplicateAnalysis.analyze(existing, newFiles)

        assertEquals(1, analysis.crossDuplicates.size)
        assertEquals(0, analysis.existingOnly.size)
        assertEquals(0, analysis.newOnly.size)
        assertEquals(0, analysis.expandedExisting.size)
    }

    @Test
    fun detectsExistingOnlyDuplicates() {
        val existing = listOf(file("/a", "h1"), file("/b", "h1"))
        val newFiles = listOf(file("/c", "h2"))

        val analysis = DuplicateAnalysis.analyze(existing, newFiles)

        assertEquals(0, analysis.crossDuplicates.size)
        assertEquals(1, analysis.existingOnly.size)
        assertEquals(0, analysis.newOnly.size)
        assertEquals(0, analysis.expandedExisting.size)
    }

    @Test
    fun detectsNewOnlyDuplicates() {
        val existing = listOf(file("/a", "h1"))
        val newFiles = listOf(file("/b", "h2"), file("/c", "h2"))

        val analysis = DuplicateAnalysis.analyze(existing, newFiles)

        assertEquals(0, analysis.crossDuplicates.size)
        assertEquals(0, analysis.existingOnly.size)
        assertEquals(1, analysis.newOnly.size)
        assertEquals(0, analysis.expandedExisting.size)
    }

    @Test
    fun detectsExpandedExistingDuplicates() {
        val existing = listOf(file("/a", "h1"), file("/b", "h1"))
        val newFiles = listOf(file("/c", "h1"))

        val analysis = DuplicateAnalysis.analyze(existing, newFiles)

        assertEquals(1, analysis.crossDuplicates.size)
        assertEquals(0, analysis.existingOnly.size)
        assertEquals(0, analysis.newOnly.size)
        assertEquals(1, analysis.expandedExisting.size)
        assertTrue(analysis.expandedExisting.first().files.size >= 3)
    }

    private fun file(path: String, hash: String): FileMetadata {
        return FileMetadata(
            path = path,
            normalizedPath = path,
            sizeBytes = 1,
            lastModifiedMillis = 1,
            hashHex = hash
        )
    }
}
