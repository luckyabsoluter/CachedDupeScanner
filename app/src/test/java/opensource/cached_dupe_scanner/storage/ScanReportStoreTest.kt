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
        val database = newDb()
        try {
            val repo = ScanReportRepository(database.scanReportDao())
            val report = report(id = "r1", startedAtMillis = 10)

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

    @Test
    fun countsReports() {
        val database = newDb()
        try {
            val repo = ScanReportRepository(database.scanReportDao())
            runBlocking {
                repo.add(report(id = "r1", startedAtMillis = 10))
                repo.add(report(id = "r2", startedAtMillis = 20))
                repo.add(report(id = "r3", startedAtMillis = 30))
            }

            assertEquals(3, runBlocking { repo.countAll() })
        } finally {
            database.close()
        }
    }

    @Test
    fun loadsFirstPageInStableDescendingOrder() {
        val database = newDb()
        try {
            val repo = ScanReportRepository(database.scanReportDao())
            runBlocking {
                repo.add(report(id = "a", startedAtMillis = 100))
                repo.add(report(id = "c", startedAtMillis = 100))
                repo.add(report(id = "b", startedAtMillis = 90))
                repo.add(report(id = "d", startedAtMillis = 80))
            }

            assertEquals(
                listOf("c", "a", "b"),
                runBlocking { repo.getFirstPage(limit = 3) }.map { it.id }
            )
        } finally {
            database.close()
        }
    }

    @Test
    fun loadsNextPagesWithoutGapsOrDuplicates() {
        val database = newDb()
        try {
            val repo = ScanReportRepository(database.scanReportDao())
            runBlocking {
                repo.add(report(id = "e", startedAtMillis = 300))
                repo.add(report(id = "d", startedAtMillis = 200))
                repo.add(report(id = "c", startedAtMillis = 200))
                repo.add(report(id = "b", startedAtMillis = 100))
                repo.add(report(id = "a", startedAtMillis = 100))
            }

            val page1 = runBlocking { repo.getFirstPage(limit = 2) }
            val page2 = runBlocking {
                repo.getPageBefore(
                    beforeMillis = page1.last().startedAtMillis,
                    beforeId = page1.last().id,
                    limit = 2
                )
            }
            val page3 = runBlocking {
                repo.getPageBefore(
                    beforeMillis = page2.last().startedAtMillis,
                    beforeId = page2.last().id,
                    limit = 2
                )
            }
            val allIds = (page1 + page2 + page3).map { it.id }

            assertEquals(listOf("e", "d", "c", "b", "a"), allIds)
            assertEquals(allIds.size, allIds.toSet().size)
        } finally {
            database.close()
        }
    }

    @Test
    fun pageAfterEndIsEmpty() {
        val database = newDb()
        try {
            val repo = ScanReportRepository(database.scanReportDao())
            runBlocking {
                repo.add(report(id = "b", startedAtMillis = 100))
                repo.add(report(id = "a", startedAtMillis = 100))
            }
            val first = runBlocking { repo.getFirstPage(limit = 2) }

            val afterEnd = runBlocking {
                repo.getPageBefore(
                    beforeMillis = first.last().startedAtMillis,
                    beforeId = first.last().id,
                    limit = 2
                )
            }

            assertEquals(emptyList<ScanReport>(), afterEnd)
        } finally {
            database.close()
        }
    }

    private fun newDb(): CacheDatabase {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return Room.inMemoryDatabaseBuilder(context, CacheDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    private fun report(id: String, startedAtMillis: Long): ScanReport {
        return ScanReport(
            id = id,
            startedAtMillis = startedAtMillis,
            finishedAtMillis = startedAtMillis + 10,
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
    }
}
