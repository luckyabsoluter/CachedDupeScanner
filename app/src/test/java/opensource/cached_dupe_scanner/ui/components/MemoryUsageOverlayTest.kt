package opensource.cached_dupe_scanner.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class MemoryUsageOverlayTest {
    @Test
    fun memoryOverlayTextFormatsAllocatedAndMaxHeapInMb() {
        val text = memoryOverlayText(
            HeapMemorySnapshot(
                allocatedBytes = 192L * 1024L * 1024L,
                maxBytes = 512L * 1024L * 1024L
            )
        )

        assertEquals("RAM 192/512 MB", text)
    }
}
