package opensource.cached_dupe_scanner.core

import org.junit.Assert.assertEquals
import org.junit.Test

class PathMatchAnalyzerTest {
    @Test
    fun samePathSameSizeSameHash() {
        val existing = file("/a", 10, "h1")
        val incoming = file("/a", 10, "h1")

        val result = PathMatchAnalyzer.analyze(existing, incoming)

        assertEquals(PathMatchCase.SAME_SIZE_SAME_HASH, result.matchCase)
    }

    @Test
    fun samePathDifferentSizeSameHash() {
        val existing = file("/a", 10, "h1")
        val incoming = file("/a", 20, "h1")

        val result = PathMatchAnalyzer.analyze(existing, incoming)

        assertEquals(PathMatchCase.DIFF_SIZE_SAME_HASH, result.matchCase)
    }

    @Test
    fun samePathSameSizeDifferentHash() {
        val existing = file("/a", 10, "h1")
        val incoming = file("/a", 10, "h2")

        val result = PathMatchAnalyzer.analyze(existing, incoming)

        assertEquals(PathMatchCase.SAME_SIZE_DIFF_HASH, result.matchCase)
    }

    @Test
    fun samePathDifferentSizeDifferentHash() {
        val existing = file("/a", 10, "h1")
        val incoming = file("/a", 20, "h2")

        val result = PathMatchAnalyzer.analyze(existing, incoming)

        assertEquals(PathMatchCase.DIFF_SIZE_DIFF_HASH, result.matchCase)
    }

    private fun file(path: String, size: Long, hash: String): FileMetadata {
        return FileMetadata(
            path = path,
            normalizedPath = path,
            sizeBytes = size,
            lastModifiedMillis = 1,
            hashHex = hash
        )
    }
}
