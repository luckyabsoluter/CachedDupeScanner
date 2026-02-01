package opensource.cached_dupe_scanner.storage

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ScanReportStoreTest {
    @Test
    fun savesAndLoadsReports() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = ScanReportStore(context)

        val report = ScanReport(
            id = "r1",
            startedAtMillis = 10,
            finishedAtMillis = 20,
            targets = listOf("/a"),
            mode = "single",
            cancelled = false,
            totals = ScanReportTotals(
                collectedCount = 2,
                detectedCount = 2,
                hashCandidates = 1,
                hashesComputed = 1
            ),
            durations = ScanReportDurations(
                collectingMillis = 5,
                detectingMillis = 3,
                hashingMillis = 2
            )
        )

        store.add(report)
        val loaded = store.loadAll()

        assertEquals(1, loaded.size)
        assertEquals("r1", loaded.first().id)
        assertEquals(2, loaded.first().totals.collectedCount)
    }
}
