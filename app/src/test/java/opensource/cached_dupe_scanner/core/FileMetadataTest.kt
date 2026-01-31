package opensource.cached_dupe_scanner.core

import org.junit.Assert.assertEquals
import org.junit.Test

class FileMetadataTest {
    @Test
    fun normalizedPathIsDeterministic() {
        val first = PathNormalizer.normalize("root/dir/../file.txt")
        val second = PathNormalizer.normalize("root\\file.txt")

        assertEquals("root/file.txt", first)
        assertEquals(first, second)
    }
}
