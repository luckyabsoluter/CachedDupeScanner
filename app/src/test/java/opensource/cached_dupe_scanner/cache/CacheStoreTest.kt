package opensource.cached_dupe_scanner.cache

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import opensource.cached_dupe_scanner.core.FileMetadata
import opensource.cached_dupe_scanner.core.PathNormalizer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CacheStoreTest {
    private lateinit var database: CacheDatabase
    private lateinit var dao: FileCacheDao
    private lateinit var store: CacheStore

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, CacheDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.fileCacheDao()
        store = CacheStore(dao)
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun upsertReplacesExistingEntry() {
        val first = metadata(
            path = "root/file.txt",
            sizeBytes = 10,
            lastModifiedMillis = 100,
            hashHex = "hash-a"
        )
        val updated = metadata(
            path = "root/file.txt",
            sizeBytes = 20,
            lastModifiedMillis = 200,
            hashHex = "hash-b"
        )

        store.upsert(first)
        store.upsert(updated)

        val cached = dao.getByNormalizedPath(updated.normalizedPath)
        assertNotNull(cached)
        assertEquals(20, cached?.sizeBytes)
        assertEquals(200, cached?.lastModifiedMillis)
        assertEquals("hash-b", cached?.hashHex)
    }

    @Test
    fun lookupReportsFreshEntry() {
        val metadata = metadata(
            path = "root/file.txt",
            sizeBytes = 10,
            lastModifiedMillis = 100,
            hashHex = "hash-a"
        )

        store.upsert(metadata)

        val result = store.lookup(metadata)

        assertEquals(CacheStatus.FRESH, result.status)
    }

    @Test
    fun lookupReportsStaleWhenMetadataChanges() {
        val cached = metadata(
            path = "root/file.txt",
            sizeBytes = 10,
            lastModifiedMillis = 100,
            hashHex = "hash-a"
        )
        val changed = metadata(
            path = "root/file.txt",
            sizeBytes = 11,
            lastModifiedMillis = 100,
            hashHex = "hash-a"
        )

        store.upsert(cached)

        val result = store.lookup(changed)

        assertEquals(CacheStatus.STALE, result.status)
    }

    @Test
    fun lookupReportsStaleWhenHashMissing() {
        val cached = metadata(
            path = "root/file.txt",
            sizeBytes = 10,
            lastModifiedMillis = 100,
            hashHex = null
        )

        store.upsert(cached)

        val result = store.lookup(cached)

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
