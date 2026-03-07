package opensource.cached_dupe_scanner.storage

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import opensource.cached_dupe_scanner.cache.CacheDatabase
import opensource.cached_dupe_scanner.cache.CachedFileEntity
import opensource.cached_dupe_scanner.cache.FileCacheDao

@RunWith(RobolectricTestRunner::class)
class ResultsDbRepositoryTest {
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

    private fun fetchAllAtSnapshot(
        repo: ResultsDbRepository,
        sortKey: DuplicateGroupSortKey,
        snapshotUpdatedAtMillis: Long,
        total: Int,
        pageSize: Int
    ): List<String> {
        val merged = mutableListOf<String>()
        var offset = 0
        while (offset < total) {
            val page = repo.loadPageAtSnapshot(
                sortKey = sortKey,
                snapshotUpdatedAtMillis = snapshotUpdatedAtMillis,
                offset = offset,
                limit = pageSize
            )
            if (page.isEmpty()) break
            merged.addAll(page.map { "${it.sizeBytes}:${it.hashHex}" })
            offset += page.size
        }
        return merged
    }

    @Test
    fun loadInitialSnapshotReturnsConsistentCountsAndFirstPage() {
        val db = newDb()
        val fileDao = db.fileCacheDao()
        val repo = ResultsDbRepository(fileDao, db.duplicateGroupDao())

        insertDuplicateGroup(fileDao, sizeBytes = 10L, hashHex = "h1", count = 2, pathPrefix = "a")
        insertDuplicateGroup(fileDao, sizeBytes = 5L, hashHex = "h2", count = 3, pathPrefix = "b")
        insertDuplicateGroup(fileDao, sizeBytes = 20L, hashHex = "h3", count = 2, pathPrefix = "c")

        val snapshot = repo.loadInitialSnapshot(
            sortKey = DuplicateGroupSortKey.CountDesc,
            limit = 2,
            rebuild = true
        )

        assertEquals(7, snapshot.fileCount)
        assertEquals(3, snapshot.groupCount)
        assertNotNull(snapshot.updatedAtMillis)
        assertEquals(2, snapshot.firstPage.size)
        assertFalse(snapshot.firstPage.isEmpty())

        val firstPageReloaded = repo.loadPageAtSnapshot(
            sortKey = DuplicateGroupSortKey.CountDesc,
            snapshotUpdatedAtMillis = snapshot.updatedAtMillis!!,
            offset = 0,
            limit = 2
        )
        assertEquals(
            snapshot.firstPage.map { "${it.sizeBytes}:${it.hashHex}" },
            firstPageReloaded.map { "${it.sizeBytes}:${it.hashHex}" }
        )

        db.close()
    }

    @Test
    fun loadPageAtSnapshotHasNoGapOrDuplicate() {
        val db = newDb()
        val fileDao = db.fileCacheDao()
        val repo = ResultsDbRepository(fileDao, db.duplicateGroupDao())

        repeat(61) { index ->
            val groupNumber = index + 1
            val hash = "g%03d".format(groupNumber)
            val size = (groupNumber % 11 + 1).toLong() * 7L
            val count = if (groupNumber % 4 == 0) 4 else 2
            insertDuplicateGroup(
                fileDao = fileDao,
                sizeBytes = size,
                hashHex = hash,
                count = count,
                pathPrefix = "p${groupNumber}_"
            )
        }

        val snapshot = repo.loadInitialSnapshot(
            sortKey = DuplicateGroupSortKey.CountDesc,
            limit = 9,
            rebuild = true
        )
        val snapshotUpdatedAtMillis = snapshot.updatedAtMillis ?: error("snapshot expected")

        val merged = fetchAllAtSnapshot(
            repo = repo,
            sortKey = DuplicateGroupSortKey.CountDesc,
            snapshotUpdatedAtMillis = snapshotUpdatedAtMillis,
            total = snapshot.groupCount,
            pageSize = 8
        )
        val full = repo.loadPageAtSnapshot(
            sortKey = DuplicateGroupSortKey.CountDesc,
            snapshotUpdatedAtMillis = snapshotUpdatedAtMillis,
            offset = 0,
            limit = snapshot.groupCount
        ).map { "${it.sizeBytes}:${it.hashHex}" }

        assertEquals(snapshot.groupCount, merged.size)
        assertEquals(snapshot.groupCount, merged.toSet().size)
        assertEquals(full, merged)

        db.close()
    }

    @Test
    fun hasSnapshotChangedDetectsRebuildAndAllowsReload() {
        val db = newDb()
        val fileDao = db.fileCacheDao()
        val repo = ResultsDbRepository(fileDao, db.duplicateGroupDao())

        insertDuplicateGroup(fileDao, sizeBytes = 10L, hashHex = "base", count = 2, pathPrefix = "base")
        val first = repo.loadInitialSnapshot(
            sortKey = DuplicateGroupSortKey.CountDesc,
            limit = 50,
            rebuild = true
        )

        Thread.sleep(2L)
        insertDuplicateGroup(fileDao, sizeBytes = 30L, hashHex = "added", count = 2, pathPrefix = "added")
        repo.rebuildGroups()

        assertTrue(repo.hasSnapshotChanged(first.updatedAtMillis))

        val second = repo.loadInitialSnapshot(
            sortKey = DuplicateGroupSortKey.CountDesc,
            limit = 50,
            rebuild = false
        )
        assertNotNull(second.updatedAtMillis)
        assertTrue(second.groupCount >= first.groupCount + 1)
        assertFalse(repo.hasSnapshotChanged(second.updatedAtMillis))

        db.close()
    }

    @Test
    fun seededRandomOrderingIsStableAcrossRepeatedSnapshotReads() {
        val db = newDb()
        val fileDao = db.fileCacheDao()
        val repo = ResultsDbRepository(fileDao, db.duplicateGroupDao())
        val random = Random(20260216)

        repeat(95) { index ->
            val groupNumber = index + 1
            val hash = "r%03d".format(groupNumber)
            val size = (random.nextInt(1, 20) * 13L)
            val count = random.nextInt(2, 6)
            insertDuplicateGroup(
                fileDao = fileDao,
                sizeBytes = size,
                hashHex = hash,
                count = count,
                pathPrefix = "rnd${groupNumber}_"
            )
        }

        val snapshot = repo.loadInitialSnapshot(
            sortKey = DuplicateGroupSortKey.TotalBytesDesc,
            limit = 20,
            rebuild = true
        )
        val snapshotUpdatedAtMillis = snapshot.updatedAtMillis ?: error("snapshot expected")
        val baseline = fetchAllAtSnapshot(
            repo = repo,
            sortKey = DuplicateGroupSortKey.TotalBytesDesc,
            snapshotUpdatedAtMillis = snapshotUpdatedAtMillis,
            total = snapshot.groupCount,
            pageSize = 9
        )

        repeat(20) { attempt ->
            val list = fetchAllAtSnapshot(
                repo = repo,
                sortKey = DuplicateGroupSortKey.TotalBytesDesc,
                snapshotUpdatedAtMillis = snapshotUpdatedAtMillis,
                total = snapshot.groupCount,
                pageSize = 5 + (attempt % 5)
            )
            assertEquals(baseline, list)
        }

        db.close()
    }

    @Test
    fun refreshSingleGroupKeepsSnapshotConsistentForAllGroups() {
        val db = newDb()
        val fileDao = db.fileCacheDao()
        val repo = ResultsDbRepository(fileDao, db.duplicateGroupDao())

        insertDuplicateGroup(fileDao, sizeBytes = 10L, hashHex = "ha", count = 3, pathPrefix = "a")
        insertDuplicateGroup(fileDao, sizeBytes = 20L, hashHex = "hb", count = 2, pathPrefix = "b")
        repo.rebuildGroups(updatedAtMillis = 100L)

        fileDao.deleteByNormalizedPath("/a1")
        repo.refreshSingleGroup(sizeBytes = 10L, hashHex = "ha", updatedAtMillis = 200L)

        val snapshot = repo.loadInitialSnapshot(
            sortKey = DuplicateGroupSortKey.CountDesc,
            limit = 10,
            rebuild = false
        )

        assertEquals(2, snapshot.groupCount)
        assertEquals(100L, snapshot.updatedAtMillis)
        assertEquals(
            setOf("ha", "hb"),
            snapshot.firstPage.map { it.hashHex }.toSet()
        )
        assertEquals(
            2,
            snapshot.firstPage.first { it.hashHex == "ha" }.fileCount
        )
        assertEquals(
            2,
            snapshot.firstPage.first { it.hashHex == "hb" }.fileCount
        )

        db.close()
    }

    @Test
    fun refreshSingleGroupRemovesCollapsedGroupWithoutRebuildingAll() {
        val db = newDb()
        val fileDao = db.fileCacheDao()
        val repo = ResultsDbRepository(fileDao, db.duplicateGroupDao())

        insertDuplicateGroup(fileDao, sizeBytes = 10L, hashHex = "ha", count = 2, pathPrefix = "a")
        insertDuplicateGroup(fileDao, sizeBytes = 20L, hashHex = "hb", count = 2, pathPrefix = "b")
        repo.rebuildGroups(updatedAtMillis = 100L)

        fileDao.deleteByNormalizedPath("/a1")
        repo.refreshSingleGroup(sizeBytes = 10L, hashHex = "ha", updatedAtMillis = 200L)

        val snapshot = repo.loadInitialSnapshot(
            sortKey = DuplicateGroupSortKey.CountDesc,
            limit = 10,
            rebuild = false
        )

        assertEquals(1, snapshot.groupCount)
        assertEquals(100L, snapshot.updatedAtMillis)
        assertEquals(listOf("hb"), snapshot.firstPage.map { it.hashHex })

        db.close()
    }
}
