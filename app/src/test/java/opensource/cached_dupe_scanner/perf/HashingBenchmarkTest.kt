package opensource.cached_dupe_scanner.perf

import opensource.cached_dupe_scanner.core.Hashing
import org.junit.Assert.assertTrue
import org.junit.Test

class HashingBenchmarkTest {
    @Test
    fun hashingBenchmarkSmokeTest() {
        val data = ByteArray(5 * 1024 * 1024) { index -> (index % 251).toByte() }

        val start = System.nanoTime()
        repeat(5) {
            Hashing.sha256Hex(data)
        }
        val elapsedMillis = (System.nanoTime() - start) / 1_000_000

        assertTrue("Hashing benchmark exceeded time limit: ${elapsedMillis}ms", elapsedMillis < 1500)
    }
}
