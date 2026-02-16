package opensource.cached_dupe_scanner.cache

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DuplicateGroupDaoTest {
    private fun newDb(): CacheDatabase {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return Room.inMemoryDatabaseBuilder(context, CacheDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

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
        val db = newDb()

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
        val db = newDb()

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
        val db = newDb()

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

    @Test
    fun listSortQueriesUseDeterministicTieBreakers() {
        val db = newDb()
        val fileDao = db.fileCacheDao()
        val groupDao = db.duplicateGroupDao()

        insertDuplicateGroup(fileDao, sizeBytes = 10L, hashHex = "ha", count = 2, pathPrefix = "a")
        insertDuplicateGroup(fileDao, sizeBytes = 20L, hashHex = "aa", count = 2, pathPrefix = "b")
        insertDuplicateGroup(fileDao, sizeBytes = 20L, hashHex = "hb", count = 2, pathPrefix = "c")
        groupDao.rebuildFromCache(updatedAtMillis = 10L)

        assertEquals(
            listOf("hb", "aa", "ha"),
            groupDao.listByCountDesc(limit = 10, offset = 0).map { it.hashHex }
        )
        assertEquals(
            listOf("ha", "aa", "hb"),
            groupDao.listByCountAsc(limit = 10, offset = 0).map { it.hashHex }
        )
        assertEquals(
            listOf("hb", "aa", "ha"),
            groupDao.listByTotalBytesDesc(limit = 10, offset = 0).map { it.hashHex }
        )
        assertEquals(
            listOf("ha", "aa", "hb"),
            groupDao.listByTotalBytesAsc(limit = 10, offset = 0).map { it.hashHex }
        )
        assertEquals(
            listOf("hb", "aa", "ha"),
            groupDao.listByPerFileSizeDesc(limit = 10, offset = 0).map { it.hashHex }
        )
        assertEquals(
            listOf("ha", "aa", "hb"),
            groupDao.listByPerFileSizeAsc(limit = 10, offset = 0).map { it.hashHex }
        )

        db.close()
    }

    @Test
    fun listQueriesAcrossPagesHaveNoGapOrDuplicate() {
        val db = newDb()
        val fileDao = db.fileCacheDao()
        val groupDao = db.duplicateGroupDao()

        repeat(37) { index ->
            val groupNumber = index + 1
            val hash = "h%03d".format(groupNumber)
            val size = (groupNumber % 9 + 1).toLong() * 5L
            val count = if (groupNumber % 3 == 0) 3 else 2
            insertDuplicateGroup(
                fileDao = fileDao,
                sizeBytes = size,
                hashHex = hash,
                count = count,
                pathPrefix = "p${groupNumber}_"
            )
        }

        groupDao.rebuildFromCache(updatedAtMillis = 99L)
        val total = groupDao.countGroups()
        val full = groupDao.listByCountDesc(limit = total, offset = 0).map { "${it.sizeBytes}:${it.hashHex}" }

        val pageSize = 7
        val merged = mutableListOf<String>()
        var offset = 0
        while (offset < total) {
            val page = groupDao.listByCountDesc(limit = pageSize, offset = offset)
            if (page.isEmpty()) break
            merged.addAll(page.map { "${it.sizeBytes}:${it.hashHex}" })
            offset += page.size
        }

        assertEquals(total, merged.size)
        assertEquals(total, merged.toSet().size)
        assertEquals(full, merged)

        db.close()
    }

    @Test
    fun snapshotFilteredQueriesDoNotMixGenerations() {
        val db = newDb()
        val fileDao = db.fileCacheDao()
        val groupDao = db.duplicateGroupDao()

        insertDuplicateGroup(fileDao, sizeBytes = 10L, hashHex = "old", count = 2, pathPrefix = "old")
        insertDuplicateGroup(fileDao, sizeBytes = 20L, hashHex = "stable", count = 2, pathPrefix = "stable")
        groupDao.insertFromCache(updatedAtMillis = 100L)

        fileDao.deleteByNormalizedPath("/old1")
        fileDao.deleteByNormalizedPath("/old2")
        insertDuplicateGroup(fileDao, sizeBytes = 30L, hashHex = "new", count = 2, pathPrefix = "new")
        groupDao.insertFromCache(updatedAtMillis = 200L)

        assertEquals(200L, groupDao.latestUpdatedAtMillis())
        assertEquals(1, groupDao.countGroupsAt(100L))
        assertEquals(2, groupDao.countGroupsAt(200L))

        val gen100 = groupDao.listByCountDescAt(updatedAtMillis = 100L, limit = 10, offset = 0)
        val gen200 = groupDao.listByCountDescAt(updatedAtMillis = 200L, limit = 10, offset = 0)

        assertEquals(listOf("old"), gen100.map { it.hashHex })
        assertTrue(gen200.map { it.hashHex }.containsAll(listOf("stable", "new")))

        db.close()
    }
}
