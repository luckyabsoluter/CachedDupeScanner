package opensource.cached_dupe_scanner.cache

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DuplicateGroupDaoTest {
    @Test
    fun rebuildFromCacheGroupsBySizeAndHash() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, CacheDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        val fileDao = db.fileCacheDao()
        val groupDao = db.duplicateGroupDao()

        // Two duplicates (same size+hash)
        fileDao.upsert(
            CachedFileEntity(
                normalizedPath = "/a",
                path = "/a",
                sizeBytes = 10,
                lastModifiedMillis = 1,
                hashHex = "h1"
            )
        )
        fileDao.upsert(
            CachedFileEntity(
                normalizedPath = "/b",
                path = "/b",
                sizeBytes = 10,
                lastModifiedMillis = 1,
                hashHex = "h1"
            )
        )
        // Not a duplicate
        fileDao.upsert(
            CachedFileEntity(
                normalizedPath = "/c",
                path = "/c",
                sizeBytes = 10,
                lastModifiedMillis = 1,
                hashHex = "h2"
            )
        )
        // Missing hash should be ignored
        fileDao.upsert(
            CachedFileEntity(
                normalizedPath = "/d",
                path = "/d",
                sizeBytes = 10,
                lastModifiedMillis = 1,
                hashHex = null
            )
        )

        groupDao.rebuildFromCache(updatedAtMillis = 123L)

        assertEquals(1, groupDao.countGroups())
        val group = groupDao.get(sizeBytes = 10, hashHex = "h1")
        assertNotNull(group)
        assertEquals(2, group?.fileCount)
        assertEquals(20L, group?.totalBytes)

        db.close()
    }
}
