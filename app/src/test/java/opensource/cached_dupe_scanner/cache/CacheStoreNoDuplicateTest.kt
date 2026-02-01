package opensource.cached_dupe_scanner.cache

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import opensource.cached_dupe_scanner.core.FileMetadata
import opensource.cached_dupe_scanner.core.PathNormalizer
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CacheStoreNoDuplicateTest {
    @Test
    fun upsertDoesNotIncreaseRowCountForSamePath() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, CacheDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val dao = database.fileCacheDao()
            val store = CacheStore(dao)

            val base = metadata("root/file.txt", 10, 100, "h1")
            store.upsert(base)
            assertEquals(1, dao.getAll().size)

            val updated1 = metadata("root/file.txt", 10, 110, "h1")
            store.upsert(updated1)
            assertEquals(1, dao.getAll().size)

            val updated2 = metadata("root/file.txt", 20, 120, "h1")
            store.upsert(updated2)
            assertEquals(1, dao.getAll().size)

            val updated3 = metadata("root/file.txt", 10, 130, "h2")
            store.upsert(updated3)
            assertEquals(1, dao.getAll().size)

            val updated4 = metadata("root/file.txt", 30, 140, "h3")
            store.upsert(updated4)
            assertEquals(1, dao.getAll().size)
        } finally {
            database.close()
        }
    }

    private fun metadata(
        path: String,
        sizeBytes: Long,
        lastModifiedMillis: Long,
        hashHex: String?
    ): FileMetadata {
        return FileMetadata(
            path = path,
            normalizedPath = PathNormalizer.normalize(path),
            sizeBytes = sizeBytes,
            lastModifiedMillis = lastModifiedMillis,
            hashHex = hashHex
        )
    }
}
