package opensource.cached_dupe_scanner.sample

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class SampleDataTest {
    @Test
    fun createsSampleFiles() {
        val tempDir = Files.createTempDirectory("cached-dupe-sample").toFile()

        try {
            SampleData.createSampleFiles(tempDir)

            val files = tempDir.listFiles()?.map { it.name }?.toSet().orEmpty()
            assertTrue(files.contains("alpha.txt"))
            assertTrue(files.contains("beta.txt"))
            assertTrue(files.contains("gamma.txt"))
            assertTrue(files.contains("delta.txt"))
            assertTrue(files.contains("delta_copy.txt"))
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
