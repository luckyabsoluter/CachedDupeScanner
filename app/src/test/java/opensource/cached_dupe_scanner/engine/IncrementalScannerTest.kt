package opensource.cached_dupe_scanner.engine

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import opensource.cached_dupe_scanner.cache.CacheDatabase
import opensource.cached_dupe_scanner.cache.CacheStore
import opensource.cached_dupe_scanner.core.PathNormalizer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.nio.file.Files

@RunWith(RobolectricTestRunner::class)
class IncrementalScannerTest {
    private lateinit var database: CacheDatabase
    private lateinit var store: CacheStore
    private lateinit var tempDir: File

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, CacheDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        store = CacheStore(database.fileCacheDao())
        tempDir = Files.createTempDirectory("cached-dupe-scanner").toFile()
    }

    @After
    fun teardown() {
        database.close()
        tempDir.deleteRecursively()
    }

    @Test
    fun incrementalScanSkipsUnchangedFiles() {
        val file = File(tempDir, "sample.txt").apply {
            writeText("hello")
        }
        val hasher = CountingHasher()
        val scanner = IncrementalScanner(store, hasher, FileWalker())

        scanner.scan(tempDir)
        scanner.scan(tempDir)

        assertEquals(1, hasher.callsFor(file))
    }

    @Test
    fun incrementalScanRehashesModifiedFiles() {
        val file = File(tempDir, "sample.txt").apply {
            writeText("hello")
        }
        val hasher = CountingHasher()
        val scanner = IncrementalScanner(store, hasher, FileWalker())

        scanner.scan(tempDir)

        file.writeText("hello-updated")
        file.setLastModified(System.currentTimeMillis() + 5000)
        scanner.scan(tempDir)

        assertEquals(2, hasher.callsFor(file))
    }

    @Test
    fun scanSkipsZeroSizeFilesInDbWhenConfigured() {
        val emptyFile = File(tempDir, "empty.txt").apply {
            writeText("")
        }
        val file = File(tempDir, "filled.txt").apply {
            writeText("data")
        }
        val scanner = IncrementalScanner(store, Sha256FileHasher(), FileWalker())

        scanner.scan(tempDir, skipZeroSizeInDb = true)

        val cached = database.fileCacheDao().getAll()
        assertEquals(1, cached.size)
        assertEquals(PathNormalizer.normalize(file.path), cached.first().normalizedPath)
    }

    private class CountingHasher : FileHasher {
        private val counts = mutableMapOf<String, Int>()

        override fun hash(file: File): String {
            val normalized = PathNormalizer.normalize(file.path)
            val next = (counts[normalized] ?: 0) + 1
            counts[normalized] = next
            return "hash-$normalized-$next"
        }

        fun callsFor(file: File): Int {
            val normalized = PathNormalizer.normalize(file.path)
            return counts[normalized] ?: 0
        }
    }
}
