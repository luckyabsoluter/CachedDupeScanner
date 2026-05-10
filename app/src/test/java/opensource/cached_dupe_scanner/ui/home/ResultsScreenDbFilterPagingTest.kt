package opensource.cached_dupe_scanner.ui.home

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import opensource.cached_dupe_scanner.cache.CacheDatabase
import opensource.cached_dupe_scanner.cache.CachedFileEntity
import opensource.cached_dupe_scanner.storage.DuplicateGroupSortKey
import opensource.cached_dupe_scanner.storage.ResultsDbRepository

@RunWith(RobolectricTestRunner::class)
class ResultsScreenDbFilterPagingTest {
    @Test
    fun loadFilteredGroupsPageStopsAfterEnoughMatchesAreFound() {
        val db = newDb()
        val fileDao = db.fileCacheDao()
        val repo = ResultsDbRepository(fileDao, db.duplicateGroupDao())

        insertGroup(fileDao, sizeBytes = 30L, hashHex = "h1", pathPrefix = "skip")
        insertGroup(fileDao, sizeBytes = 20L, hashHex = "h2", pathPrefix = "keep")
        insertGroup(fileDao, sizeBytes = 10L, hashHex = "h3", pathPrefix = "later")

        val snapshot = repo.loadInitialSnapshot(
            sortKey = DuplicateGroupSortKey.CountDesc,
            limit = 1,
            rebuild = true
        )

        val definition = ResultsFilterDefinition(
            clusters = listOf(
                ResultsFilterCluster(
                    id = "cluster_1",
                    name = "Names",
                    rules = listOf(
                        ResultsFilterRule(
                            id = "rule_1",
                            target = ResultsFilterTarget.FileName,
                            textOperator = ResultsFilterTextOperator.Contains,
                            value = "keep"
                        )
                    )
                )
            )
        )

        val page = loadFilteredGroupsPage(
            resultsRepo = repo,
            sortKey = DuplicateGroupSortKey.CountDesc,
            snapshotUpdatedAtMillis = snapshot.updatedAtMillis ?: error("snapshot expected"),
            definition = definition,
            startOffset = 0,
            minMatches = 1,
            sourcePageSize = 1
        )

        assertEquals(listOf("h2"), page.matchedGroups.map { it.hashHex })
        assertEquals(2, page.nextSourceOffset)
        assertFalse(page.exhausted)

        db.close()
    }

    @Test
    fun loadFilteredGroupsPageScansAllSourceGroupsWhenNoMatches() {
        val db = newDb()
        val fileDao = db.fileCacheDao()
        val repo = ResultsDbRepository(fileDao, db.duplicateGroupDao())

        insertGroup(fileDao, sizeBytes = 30L, hashHex = "h1", pathPrefix = "skip")
        insertGroup(fileDao, sizeBytes = 20L, hashHex = "h2", pathPrefix = "also_skip")

        val snapshot = repo.loadInitialSnapshot(
            sortKey = DuplicateGroupSortKey.CountDesc,
            limit = 1,
            rebuild = true
        )
        val definition = ResultsFilterDefinition(
            clusters = listOf(
                ResultsFilterCluster(
                    id = "cluster_1",
                    name = "Names",
                    rules = listOf(
                        ResultsFilterRule(
                            id = "rule_1",
                            target = ResultsFilterTarget.FileName,
                            textOperator = ResultsFilterTextOperator.Contains,
                            value = "keep"
                        )
                    )
                )
            )
        )

        val page = loadFilteredGroupsPage(
            resultsRepo = repo,
            sortKey = DuplicateGroupSortKey.CountDesc,
            snapshotUpdatedAtMillis = snapshot.updatedAtMillis ?: error("snapshot expected"),
            definition = definition,
            startOffset = 0,
            minMatches = 1,
            sourcePageSize = 1
        )

        assertEquals(emptyList<String>(), page.matchedGroups.map { it.hashHex })
        assertEquals(2, page.nextSourceOffset)
        assertTrue(page.exhausted)
        db.close()
    }

    @Test
    fun loadFilteredGroupsPageReturnsExhaustedForInvalidSourcePageSize() {
        val db = newDb()
        val repo = ResultsDbRepository(db.fileCacheDao(), db.duplicateGroupDao())

        val page = loadFilteredGroupsPage(
            resultsRepo = repo,
            sortKey = DuplicateGroupSortKey.CountDesc,
            snapshotUpdatedAtMillis = 1L,
            definition = ResultsFilterDefinition(),
            startOffset = 5,
            minMatches = 1,
            sourcePageSize = 0
        )

        assertEquals(emptyList<String>(), page.matchedGroups.map { it.hashHex })
        assertEquals(5, page.nextSourceOffset)
        assertTrue(page.exhausted)
        db.close()
    }

    @Test
    fun loadFilteredGroupsPageKeepsCurrentBehaviorForInvalidMinMatches() {
        val db = newDb()
        val fileDao = db.fileCacheDao()
        val repo = ResultsDbRepository(fileDao, db.duplicateGroupDao())

        insertGroup(fileDao, sizeBytes = 30L, hashHex = "h1", pathPrefix = "keep")
        val snapshot = repo.loadInitialSnapshot(
            sortKey = DuplicateGroupSortKey.CountDesc,
            limit = 1,
            rebuild = true
        )

        val page = loadFilteredGroupsPage(
            resultsRepo = repo,
            sortKey = DuplicateGroupSortKey.CountDesc,
            snapshotUpdatedAtMillis = snapshot.updatedAtMillis ?: error("snapshot expected"),
            definition = ResultsFilterDefinition(),
            startOffset = 0,
            minMatches = 0,
            sourcePageSize = 1
        )

        assertEquals(emptyList<String>(), page.matchedGroups.map { it.hashHex })
        assertEquals(0, page.nextSourceOffset)
        assertFalse(page.exhausted)
        db.close()
    }

    @Test
    fun loadFilteredGroupsPageKeepsAllMatchesFromLoadedSourcePage() {
        val db = newDb()
        val fileDao = db.fileCacheDao()
        val repo = ResultsDbRepository(fileDao, db.duplicateGroupDao())

        insertGroup(fileDao, sizeBytes = 30L, hashHex = "h1", pathPrefix = "keep_a")
        insertGroup(fileDao, sizeBytes = 20L, hashHex = "h2", pathPrefix = "keep_b")
        insertGroup(fileDao, sizeBytes = 10L, hashHex = "h3", pathPrefix = "skip")

        val snapshot = repo.loadInitialSnapshot(
            sortKey = DuplicateGroupSortKey.CountDesc,
            limit = 3,
            rebuild = true
        )
        val definition = ResultsFilterDefinition(
            clusters = listOf(
                ResultsFilterCluster(
                    id = "cluster_1",
                    name = "Names",
                    rules = listOf(
                        ResultsFilterRule(
                            id = "rule_1",
                            target = ResultsFilterTarget.FileName,
                            textOperator = ResultsFilterTextOperator.Contains,
                            value = "keep"
                        )
                    )
                )
            )
        )

        val page = loadFilteredGroupsPage(
            resultsRepo = repo,
            sortKey = DuplicateGroupSortKey.CountDesc,
            snapshotUpdatedAtMillis = snapshot.updatedAtMillis ?: error("snapshot expected"),
            definition = definition,
            startOffset = 0,
            minMatches = 1,
            sourcePageSize = 3
        )

        assertEquals(listOf("h1", "h2"), page.matchedGroups.map { it.hashHex })
        assertEquals(3, page.nextSourceOffset)
        assertFalse(page.exhausted)
        db.close()
    }

    private fun newDb(): CacheDatabase {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return Room.inMemoryDatabaseBuilder(context, CacheDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    private fun insertGroup(
        fileDao: opensource.cached_dupe_scanner.cache.FileCacheDao,
        sizeBytes: Long,
        hashHex: String,
        pathPrefix: String
    ) {
        repeat(2) { index ->
            val path = "/$pathPrefix/${pathPrefix}_${index + 1}.jpg"
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
}
