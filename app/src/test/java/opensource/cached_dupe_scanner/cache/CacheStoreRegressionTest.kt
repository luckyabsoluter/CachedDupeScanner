package opensource.cached_dupe_scanner.cache

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import opensource.cached_dupe_scanner.core.FileMetadata
import opensource.cached_dupe_scanner.core.PathNormalizer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CacheStoreRegressionTest {
    private lateinit var database: CacheDatabase
    private lateinit var store: CacheStore

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, CacheDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        store = CacheStore(database.fileCacheDao())
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun lookupReportsStaleWhenTimestampChanges() {
        val cached = metadata(
            path = "root/file.txt",
            sizeBytes = 10,
            lastModifiedMillis = 100,
            hashHex = "hash-a"
        )
        val updatedTimestamp = metadata(
            path = "root/file.txt",
            sizeBytes = 10,
            lastModifiedMillis = 200,
            hashHex = "hash-a"
        )

        store.upsert(cached)

        val result = store.lookup(updatedTimestamp)

        assertEquals(CacheStatus.STALE, result.status)
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
