package opensource.cached_dupe_scanner.cache

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FileCacheDaoTest {
    private fun newDb(): CacheDatabase {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return Room.inMemoryDatabaseBuilder(context, CacheDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @Test
    fun upsertPreservesGeneratedFileIdForExistingPath() {
        val db = newDb()
        try {
            val dao = db.fileCacheDao()
            val original = CachedFileEntity(
                normalizedPath = "/stable-id.mp4",
                path = "/stable-id.mp4",
                sizeBytes = 10L,
                lastModifiedMillis = 20L,
                hashHex = null
            )

            dao.upsert(original)
            val inserted = requireNotNull(dao.getByNormalizedPath(original.normalizedPath))
            dao.upsert(
                original.copy(
                    sizeBytes = 30L,
                    lastModifiedMillis = 40L,
                    hashHex = "updated"
                )
            )
            val updated = requireNotNull(dao.getByNormalizedPath(original.normalizedPath))
            dao.upsert(original.copy(normalizedPath = "/second.mp4", path = "/second.mp4"))
            val second = requireNotNull(dao.getByNormalizedPath("/second.mp4"))

            assertTrue(inserted.fileId > 0L)
            assertEquals(inserted.fileId, updated.fileId)
            assertEquals(30L, updated.sizeBytes)
            assertEquals("updated", updated.hashHex)
            assertNotEquals(inserted.fileId, second.fileId)
        } finally {
            db.close()
        }
    }

    @Test
    fun pagesDuplicateGroupKeysInStableOrderAndCountsDuplicateMembers() {
        val db = newDb()
        try {
            val dao = db.fileCacheDao()
            insertGroup(dao, sizeBytes = 20L, hashHex = "h3", count = 2)
            insertGroup(dao, sizeBytes = 10L, hashHex = "h2", count = 3)
            insertGroup(dao, sizeBytes = 10L, hashHex = "h1", count = 2)
            insertGroup(dao, sizeBytes = 30L, hashHex = "single", count = 1)
            dao.upsert(
                CachedFileEntity(
                    normalizedPath = "/null-hash",
                    path = "/null-hash",
                    sizeBytes = 40L,
                    lastModifiedMillis = 1L,
                    hashHex = null
                )
            )

            val firstPage = dao.listDuplicateGroupKeysFromCachePage(limit = 2)
            val secondPage = dao.listDuplicateGroupKeysFromCachePageAfter(
                afterSizeBytes = firstPage.last().sizeBytes,
                afterHashHex = firstPage.last().hashHex,
                limit = 2
            )

            val keys = firstPage + secondPage
            assertEquals(listOf(10L to "h1", 10L to "h2", 20L to "h3"), keys.map { it.sizeBytes to it.hashHex })
            assertEquals(7, dao.countDuplicateMembersFromCache())
        } finally {
            db.close()
        }
    }

    private fun insertGroup(
        dao: FileCacheDao,
        sizeBytes: Long,
        hashHex: String,
        count: Int
    ) {
        repeat(count) { index ->
            val path = "/$sizeBytes-$hashHex-$index"
            dao.upsert(
                CachedFileEntity(
                    normalizedPath = path,
                    path = path,
                    sizeBytes = sizeBytes,
                    lastModifiedMillis = 1L,
                    hashHex = hashHex
                )
            )
        }
    }
}
