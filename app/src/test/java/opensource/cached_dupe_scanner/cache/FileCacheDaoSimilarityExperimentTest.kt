package opensource.cached_dupe_scanner.cache

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FileCacheDaoSimilarityExperimentTest {
    private lateinit var database: CacheDatabase
    private lateinit var dao: FileCacheDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, CacheDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.fileCacheDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun countVideoCandidatesUsesProvidedSizeFloor() {
        val minSizeBytes = 100L * 1024L * 1024L
        dao.upsert(entity("/video/a.mp4", minSizeBytes))
        dao.upsert(entity("/video/b.MKV", minSizeBytes + 1L))
        dao.upsert(entity("/video/small.mp4", minSizeBytes - 1L))
        dao.upsert(entity("/image/large.jpg", minSizeBytes + 1L))
        dao.upsert(entity("/backup/not-video.mp4.backup", minSizeBytes + 1L))

        assertEquals(
            2,
            dao.countVideoCandidates(minSizeBytes)
        )
    }

    @Test
    fun listVideoCandidatesUsesProvidedSizeFloorAndStablePaging() {
        val minSizeBytes = 10L
        dao.upsert(entity("/video/a.mp4", minSizeBytes))
        dao.upsert(entity("/video/b.mp4", minSizeBytes + 1L))
        dao.upsert(entity("/video/c.mp4", minSizeBytes + 2L))
        dao.upsert(entity("/video/small.mp4", minSizeBytes - 1L))

        assertEquals(
            listOf("/video/a.mp4", "/video/b.mp4"),
            dao.listVideoCandidatesAfter(minSizeBytes = minSizeBytes, afterPath = "", limit = 2)
                .map { it.normalizedPath }
        )
        assertEquals(
            listOf("/video/c.mp4"),
            dao.listVideoCandidatesAfter(minSizeBytes = minSizeBytes, afterPath = "/video/b.mp4", limit = 2)
                .map { it.normalizedPath }
        )
    }

    @Test
    fun countImageCandidatesUsesProvidedSizeFloor() {
        val minSizeBytes = 100L
        dao.upsert(entity("/image/a.jpg", minSizeBytes))
        dao.upsert(entity("/image/b.PNG", minSizeBytes + 1L))
        dao.upsert(entity("/image/small.webp", minSizeBytes - 1L))
        dao.upsert(entity("/video/large.mp4", minSizeBytes + 1L))
        dao.upsert(entity("/backup/not-image.jpg.backup", minSizeBytes + 1L))

        assertEquals(
            2,
            dao.countImageCandidates(minSizeBytes)
        )
    }

    @Test
    fun listImageCandidatesUsesProvidedSizeFloorAndStablePaging() {
        val minSizeBytes = 10L
        dao.upsert(entity("/image/a.jpg", minSizeBytes))
        dao.upsert(entity("/image/b.png", minSizeBytes + 1L))
        dao.upsert(entity("/image/c.webp", minSizeBytes + 2L))
        dao.upsert(entity("/image/small.jpg", minSizeBytes - 1L))

        assertEquals(
            listOf("/image/a.jpg", "/image/b.png"),
            dao.listImageCandidatesAfter(minSizeBytes = minSizeBytes, afterPath = "", limit = 2)
                .map { it.normalizedPath }
        )
        assertEquals(
            listOf("/image/c.webp"),
            dao.listImageCandidatesAfter(minSizeBytes = minSizeBytes, afterPath = "/image/b.png", limit = 2)
                .map { it.normalizedPath }
        )
    }

    @Test
    fun findByNormalizedOrDisplayPathsResolvesSimilarityClusterMembers() {
        dao.upsert(entity("/video/a.mp4", 10L))
        dao.upsert(entity("/video/b.mp4", 11L))
        dao.upsert(entity("/video/c.mp4", 12L))

        assertEquals(
            setOf("/video/a.mp4", "/video/c.mp4"),
            dao.findByNormalizedOrDisplayPaths(listOf("/video/c.mp4", "/video/a.mp4"))
                .mapTo(hashSetOf()) { it.normalizedPath }
        )
    }

    @Test
    fun findByNormalizedOrDisplayPathsCanResolveOldClusterDisplayPaths() {
        dao.upsert(
            CachedFileEntity(
                normalizedPath = "/storage/video/a.mp4",
                path = "/storage/VIDEO/a.mp4",
                sizeBytes = 10L,
                lastModifiedMillis = 1L,
                hashHex = null
            )
        )

        assertEquals(
            listOf("/storage/video/a.mp4"),
            dao.findByNormalizedOrDisplayPaths(listOf("/storage/VIDEO/a.mp4"))
                .map { it.normalizedPath }
        )
    }

    private fun entity(path: String, sizeBytes: Long): CachedFileEntity {
        return CachedFileEntity(
            normalizedPath = path.lowercase(),
            path = path,
            sizeBytes = sizeBytes,
            lastModifiedMillis = 1L,
            hashHex = null
        )
    }
}
