package opensource.cached_dupe_scanner.ui.home

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import opensource.cached_dupe_scanner.cache.CacheDatabase
import opensource.cached_dupe_scanner.cache.CachedFileEntity
import opensource.cached_dupe_scanner.storage.PagedFileRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FilesScreenDbFilterPagingTest {
    @Test
    fun loadFilteredFilesPageStopsAfterEnoughMatchesAreFound() {
        val db = newDb()
        val fileDao = db.fileCacheDao()
        val repo = PagedFileRepository(fileDao)

        insertFile(fileDao, "/files/a_skip.txt")
        insertFile(fileDao, "/files/b_keep.txt")
        insertFile(fileDao, "/files/c_later.txt")

        val definition = ResultsFilterDefinition(
            clusters = listOf(
                ResultsFilterCluster(
                    id = "cluster_1",
                    name = "Keep",
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

        val page = loadFilteredFilesPage(
            fileRepo = repo,
            sortKey = PagedFileRepository.SortKey.Name,
            direction = PagedFileRepository.SortDirection.Asc,
            cursor = PagedFileRepository.Cursor.Start,
            definition = definition,
            minMatches = 1,
            sourcePageSize = 1
        )

        assertEquals(listOf("/files/b_keep.txt"), page.items.map { it.normalizedPath })
        assertEquals(PagedFileRepository.Cursor.Name("/files/b_keep.txt"), page.nextCursor)
        assertEquals(2, page.sourceLoadedCount)
        assertFalse(page.exhausted)

        db.close()
    }

    @Test
    fun loadFilteredFilesPageTracksAllSourceRowsWhenNoMatches() {
        val db = newDb()
        val fileDao = db.fileCacheDao()
        val repo = PagedFileRepository(fileDao)

        insertFile(fileDao, "/files/a_skip.txt")
        insertFile(fileDao, "/files/b_skip.txt")
        insertFile(fileDao, "/files/c_skip.txt")

        val definition = ResultsFilterDefinition(
            clusters = listOf(
                ResultsFilterCluster(
                    id = "cluster_1",
                    name = "Keep",
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

        val page = loadFilteredFilesPage(
            fileRepo = repo,
            sortKey = PagedFileRepository.SortKey.Name,
            direction = PagedFileRepository.SortDirection.Asc,
            cursor = PagedFileRepository.Cursor.Start,
            definition = definition,
            minMatches = 1,
            sourcePageSize = 2
        )

        assertEquals(emptyList<String>(), page.items.map { it.normalizedPath })
        assertEquals(3, page.sourceLoadedCount)
        assertEquals(null, page.nextCursor)
        assertTrue(page.exhausted)

        db.close()
    }

    private fun newDb(): CacheDatabase {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return Room.inMemoryDatabaseBuilder(context, CacheDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    private fun insertFile(
        fileDao: opensource.cached_dupe_scanner.cache.FileCacheDao,
        path: String
    ) {
        fileDao.upsert(
            CachedFileEntity(
                normalizedPath = path,
                path = path,
                sizeBytes = 10L,
                lastModifiedMillis = 1L,
                hashHex = "hash"
            )
        )
    }
}
