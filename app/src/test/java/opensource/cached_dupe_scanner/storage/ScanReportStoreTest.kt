package opensource.cached_dupe_scanner.storage

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import opensource.cached_dupe_scanner.cache.CacheDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ScanReportRepositoryTest {
    @Test
    fun savesAndLoadsReports() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, CacheDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val repo = ScanReportRepository(database.scanReportDao())
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

            runBlocking {
                repo.add(report)
            }
            val loaded = runBlocking { repo.loadAll() }

            assertEquals(1, loaded.size)
            assertEquals("r1", loaded.first().id)
            assertEquals(2, loaded.first().totals.collectedCount)
            assertNotNull(runBlocking { repo.loadById("r1") })
        } finally {
            database.close()
        }
    }
}
