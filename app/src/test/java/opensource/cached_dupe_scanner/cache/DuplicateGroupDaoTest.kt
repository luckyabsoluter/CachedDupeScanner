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
    private fun insertDuplicateGroup(
        fileDao: FileCacheDao,
        sizeBytes: Long,
        hashHex: String,
        count: Int,
        pathPrefix: String
    ) {
        repeat(count) { index ->
            val path = "/${pathPrefix}${index + 1}"
            fileDao.upsert(
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

    @Test
    fun insertFromCacheDoesNotFailWhenSameGroupAlreadyExists() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, CacheDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        val fileDao = db.fileCacheDao()
        val groupDao = db.duplicateGroupDao()

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

        groupDao.insertFromCache(updatedAtMillis = 100L)
        groupDao.insertFromCache(updatedAtMillis = 200L)

        assertEquals(1, groupDao.countGroups())
        val group = groupDao.get(sizeBytes = 10, hashHex = "h1")
        assertNotNull(group)
        assertEquals(200L, group?.updatedAtMillis)

        db.close()
    }

    @Test
    fun listSortQueriesSupportAscAndDescDirections() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, CacheDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        val fileDao = db.fileCacheDao()
        val groupDao = db.duplicateGroupDao()

        insertDuplicateGroup(fileDao, sizeBytes = 10L, hashHex = "h1", count = 2, pathPrefix = "a")
        insertDuplicateGroup(fileDao, sizeBytes = 5L, hashHex = "h2", count = 3, pathPrefix = "b")
        insertDuplicateGroup(fileDao, sizeBytes = 20L, hashHex = "h3", count = 2, pathPrefix = "c")

        groupDao.rebuildFromCache(updatedAtMillis = 1L)

        assertEquals(
            listOf("h2", "h3", "h1"),
            groupDao.listByCountDesc(limit = 10, offset = 0).map { it.hashHex }
        )
        assertEquals(
            listOf("h1", "h3", "h2"),
            groupDao.listByCountAsc(limit = 10, offset = 0).map { it.hashHex }
        )

        assertEquals(
            listOf("h3", "h1", "h2"),
            groupDao.listByTotalBytesDesc(limit = 10, offset = 0).map { it.hashHex }
        )
        assertEquals(
            listOf("h2", "h1", "h3"),
            groupDao.listByTotalBytesAsc(limit = 10, offset = 0).map { it.hashHex }
        )

        assertEquals(
            listOf("h3", "h1", "h2"),
            groupDao.listByPerFileSizeDesc(limit = 10, offset = 0).map { it.hashHex }
        )
        assertEquals(
            listOf("h2", "h1", "h3"),
            groupDao.listByPerFileSizeAsc(limit = 10, offset = 0).map { it.hashHex }
        )

        db.close()
    }
}
