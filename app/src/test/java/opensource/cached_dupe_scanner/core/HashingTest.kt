package opensource.cached_dupe_scanner.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.nio.file.Files

class HashingTest {
    @Test
    fun sha256MatchesKnownFixture() {
        val bytes = "hello world".toByteArray()
        val expected = "b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9"

        val actual = Hashing.sha256Hex(bytes)

        assertEquals(expected, actual)
    }

    @Test
    fun streamingHashMatchesSinglePass() {
        val bytes = "cached-dupe-scanner".toByteArray()

        val direct = Hashing.sha256Hex(bytes)
        val stream = ByteArrayInputStream(bytes).use { input ->
            Hashing.sha256Hex(input)
        }

        assertEquals(direct, stream)
    }

    @Test
    fun fileHashMatchesKnownFixture() {
        val bytes = "hello world".toByteArray()
        val expected = "b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9"
        val tempFile = Files.createTempFile("cached-dupe-scanner", ".txt").toFile()

        try {
            Files.write(tempFile.toPath(), bytes)
            val actual = Hashing.sha256Hex(tempFile)
            assertEquals(expected, actual)
        } finally {
            Files.deleteIfExists(tempFile.toPath())
        }
    }
}
