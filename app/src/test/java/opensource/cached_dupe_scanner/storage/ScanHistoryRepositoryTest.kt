package opensource.cached_dupe_scanner.storage

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import opensource.cached_dupe_scanner.cache.CacheDatabase
import opensource.cached_dupe_scanner.core.FileMetadata
import opensource.cached_dupe_scanner.core.ScanResult
import opensource.cached_dupe_scanner.storage.AppSettingsStore
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
            val settings = AppSettingsStore(context)
            val repo = ScanHistoryRepository(database.fileCacheDao(), settings)

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

            settings.setSkipZeroSizeInDb(true)
            val rZero = ScanResult(
                scannedAtMillis = 2,
                files = listOf(
                    FileMetadata("/zero", "/zero", 0, 1, "h3")
                ),
                duplicateGroups = emptyList()
            )

            repo.recordScan(r1)
            repo.recordScan(r2)
            repo.recordScan(rZero)

            // Record same path again with updated size/hash
            val r3 = ScanResult(
                scannedAtMillis = 3,
                files = listOf(
                    FileMetadata("/a", "/a", 5, 5, "h2")
                ),
                duplicateGroups = emptyList()
            )
            repo.recordScan(r3)

            val merged = repo.loadMergedHistory()
            assertNotNull(merged)
            assertEquals(2, merged?.files?.size)
            assertEquals(0, merged?.duplicateGroups?.size)
            assertEquals(2, database.fileCacheDao().getAll().size)

            val updated = merged?.files?.firstOrNull { it.normalizedPath == "/a" }
            assertNotNull(updated)
            assertEquals(5L, updated?.sizeBytes)
            assertEquals("h2", updated?.hashHex)
        } finally {
            database.close()
        }
    }

    @Test
    fun deleteByNormalizedPathRemovesEntry() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, CacheDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val settings = AppSettingsStore(context)
            val repo = ScanHistoryRepository(database.fileCacheDao(), settings)

            val r1 = ScanResult(
                scannedAtMillis = 1,
                files = listOf(
                    FileMetadata("/a", "/a", 1, 1, "h1"),
                    FileMetadata("/b", "/b", 1, 1, "h2")
                ),
                duplicateGroups = emptyList()
            )
            repo.recordScan(r1)

            repo.deleteByNormalizedPath("/a")

            val merged = repo.loadMergedHistory()
            assertNotNull(merged)
            assertEquals(1, merged?.files?.size)
            assertEquals("/b", merged?.files?.first()?.normalizedPath)
        } finally {
            database.close()
        }
    }
}
