package opensource.cached_dupe_scanner.ui.home

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
