package opensource.cached_dupe_scanner.tasks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProgressMetricsTest {
    @Test
    fun calculatesAverageSpeedElapsedTimeAndRemainingTime() {
        val metrics = calculateProgressMetrics(
            processed = 20L,
            total = 100L,
            startedAtMillis = 1_000L,
            nowMillis = 11_000L
        )

        assertEquals(2.0, requireNotNull(metrics.itemsPerSecond), 0.001)
        assertEquals(10_000L, metrics.elapsedMillis)
        assertEquals(40_000L, metrics.remainingMillis)
        assertEquals(
            "Speed: 2.00 items/s | Elapsed: 10s | Remaining: 40s",
            formatProgressMetrics(metrics)
        )
    }

    @Test
    fun keepsAllMetricLabelsWhenRateAndEtaAreUnavailable() {
        val metrics = calculateProgressMetrics(
            processed = null,
            total = null,
            startedAtMillis = 1_000L,
            nowMillis = 66_000L
        )

        assertNull(metrics.itemsPerSecond)
        assertEquals(65_000L, metrics.elapsedMillis)
        assertNull(metrics.remainingMillis)
        assertEquals(
            "Speed: -- | Elapsed: 1m 05s | Remaining: --",
            formatProgressMetrics(metrics)
        )
    }

    @Test
    fun completedProgressReportsZeroRemainingTime() {
        val metrics = calculateProgressMetrics(
            processed = 100L,
            total = 100L,
            startedAtMillis = 1_000L,
            nowMillis = 11_000L
        )

        assertEquals(0L, metrics.remainingMillis)
        assertEquals("10.0 items/s", formatProcessingSpeed(metrics.itemsPerSecond))
        assertEquals("0s", formatProgressDuration(metrics.remainingMillis))
    }
}
