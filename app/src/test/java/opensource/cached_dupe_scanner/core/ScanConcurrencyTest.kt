package opensource.cached_dupe_scanner.core

import org.junit.Assert.assertEquals
import org.junit.Test

class ScanConcurrencyTest {
    @Test
    fun defaultWorkerCountUsesAvailableProcessorsWithAConservativeCap() {
        assertEquals(1, defaultScanWorkerCount(availableProcessors = 0))
        assertEquals(2, defaultScanWorkerCount(availableProcessors = 2))
        assertEquals(4, defaultScanWorkerCount(availableProcessors = 64))
    }

    @Test
    fun workerCountSanitizationEnforcesSupportedBounds() {
        assertEquals(MIN_SCAN_WORKER_COUNT, sanitizeScanWorkerCount(Int.MIN_VALUE))
        assertEquals(7, sanitizeScanWorkerCount(7))
        assertEquals(MAX_SCAN_WORKER_COUNT, sanitizeScanWorkerCount(Int.MAX_VALUE))
    }
}
