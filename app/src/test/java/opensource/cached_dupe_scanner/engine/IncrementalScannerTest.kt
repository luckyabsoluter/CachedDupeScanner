package opensource.cached_dupe_scanner.engine

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import opensource.cached_dupe_scanner.cache.CacheDatabase
import opensource.cached_dupe_scanner.cache.CacheStore
import opensource.cached_dupe_scanner.core.FileMetadata
import opensource.cached_dupe_scanner.core.PathNormalizer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
    fun scanDefersHashWhenNoSizeCollision() {
        val file = File(tempDir, "sample.txt").apply {
            writeText("hello")
        }
        val hasher = CountingHasher()
        val scanner = IncrementalScanner(store, hasher, FileWalker())

        scanner.scan(tempDir)
        scanner.scan(tempDir)

        assertEquals(0, hasher.callsFor(file))
    }

    @Test
    fun hashesOnlyOnSizeCollisionAndReusesWhenUnchanged() {
        val fileA = File(tempDir, "a.txt").apply {
            writeText("aa")
        }
        val fileB = File(tempDir, "b.txt").apply {
            writeText("bb")
        }
        val hasher = CountingHasher()
        val scanner = IncrementalScanner(store, hasher, FileWalker())

        scanner.scan(tempDir)
        scanner.scan(tempDir)

        assertEquals(1, hasher.callsFor(fileA))
        assertEquals(1, hasher.callsFor(fileB))
    }

    @Test
    fun rehashesModifiedFileWhenSizeStillCollides() {
        val fileA = File(tempDir, "a.txt").apply {
            writeText("aa")
        }
        val fileB = File(tempDir, "b.txt").apply {
            writeText("bb")
        }
        val hasher = CountingHasher()
        val scanner = IncrementalScanner(store, hasher, FileWalker())

        scanner.scan(tempDir)

        fileA.writeText("cc")
        fileA.setLastModified(System.currentTimeMillis() + 5000)
        scanner.scan(tempDir)

        assertEquals(2, hasher.callsFor(fileA))
        assertEquals(1, hasher.callsFor(fileB))
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

    @Test
    fun duplicatePathsDoNotTriggerHashingAcrossScans() {
        val file = File(tempDir, "dup.txt").apply {
            writeText("hello")
        }
        val hasher = CountingHasher()
        val walker = DuplicateFileWalker(file)
        val scanner = IncrementalScanner(store, hasher, walker)

        scanner.scan(tempDir)
        scanner.scan(tempDir)

        assertEquals(0, hasher.callsFor(file))
    }

    @Test
    fun scanHashesWhenDbHasSizeCollision() {
        val file = File(tempDir, "solo.txt").apply {
            writeText("aa")
        }
        val cached = FileMetadata(
            path = "other.txt",
            normalizedPath = PathNormalizer.normalize("other.txt"),
            sizeBytes = file.length(),
            lastModifiedMillis = 1234L,
            hashHex = null
        )
        store.upsert(cached)
        val hasher = CountingHasher()
        val scanner = IncrementalScanner(store, hasher, FileWalker())

        scanner.scan(tempDir)

        assertEquals(1, hasher.callsFor(file))
    }

    @Test
    fun scanHashesCachedEntryMissingHashOnSizeCollision() {
        val fileA = File(tempDir, "a.txt").apply {
            writeText("aa")
        }
        val fileB = File(tempDir, "b.txt").apply {
            writeText("bb")
        }
        val cached = FileMetadata(
            path = fileA.path,
            normalizedPath = PathNormalizer.normalize(fileA.path),
            sizeBytes = fileA.length(),
            lastModifiedMillis = fileA.lastModified(),
            hashHex = null
        )
        store.upsert(cached)
        val hasher = CountingHasher()
        val scanner = IncrementalScanner(store, hasher, FileWalker())

        scanner.scan(tempDir)

        assertEquals(1, hasher.callsFor(fileA))
        assertEquals(1, hasher.callsFor(fileB))
        val cachedAfter = database.fileCacheDao()
            .getByNormalizedPath(PathNormalizer.normalize(fileA.path))
        assertNotNull(cachedAfter?.hashHex)
    }

    @Test
    fun cancelledScanDoesNotPersistPartialCache() {
        val fileA = File(tempDir, "a.txt").apply { writeText("aa") }
        val walker = DuplicateFileWalker(fileA)
        val scanner = IncrementalScanner(store, Sha256FileHasher(), walker)
        var allow = true

        val result = scanner.scan(
            tempDir,
            onProgress = { _, _, _, _ -> allow = false },
            shouldContinue = { allow }
        )

        assertEquals(0, result.files.size)
        assertEquals(0, database.fileCacheDao().getAll().size)
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

    private class DuplicateFileWalker(private val file: File) : FileWalker() {
        override fun walk(
            root: File,
            ignore: (File) -> Boolean,
            onFile: (File) -> Unit,
            shouldContinue: () -> Boolean
        ): List<File> {
            onFile(file)
            onFile(file)
            return listOf(file, file)
        }
    }
}
