package opensource.cached_dupe_scanner.storage

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import opensource.cached_dupe_scanner.cache.CacheDatabase
import opensource.cached_dupe_scanner.core.FileMetadata
import opensource.cached_dupe_scanner.core.ScanResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ScanHistoryRepositoryTest {
    @Test
    fun recordAndMergeHistory() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, CacheDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val repo = ScanHistoryRepository(database.scanHistoryDao())

            val r1 = ScanResult(
                scannedAtMillis = 1,
                files = listOf(
                    FileMetadata("/a", "/a", 1, 1, "h1")
                ),
                duplicateGroups = emptyList()
            )
            val r2 = ScanResult(
                scannedAtMillis = 2,
                files = listOf(
                    FileMetadata("/b", "/b", 1, 1, "h1")
                ),
                duplicateGroups = emptyList()
            )

            repo.recordScan(r1)
            repo.recordScan(r2)

            val merged = repo.loadMergedHistory()
            assertNotNull(merged)
            assertEquals(2, merged?.files?.size)
            assertEquals(1, merged?.duplicateGroups?.size)
        } finally {
            database.close()
        }
    }
}
