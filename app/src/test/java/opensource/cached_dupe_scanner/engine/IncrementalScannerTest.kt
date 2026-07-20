package opensource.cached_dupe_scanner.engine

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import opensource.cached_dupe_scanner.cache.CacheDatabase
import opensource.cached_dupe_scanner.cache.CacheStore
import opensource.cached_dupe_scanner.core.FileMetadata
import opensource.cached_dupe_scanner.core.PathNormalizer
import opensource.cached_dupe_scanner.core.ScanResult
import opensource.cached_dupe_scanner.storage.TrashPaths
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

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
    fun scanResultsAddHashesAfterSecondScanWithCollision() {
        val fileA = File(tempDir, "a.txt").apply {
            writeText("aa")
        }
        val scanner = IncrementalScanner(store, Sha256FileHasher(), FileWalker())

        val first = scanner.scan(tempDir)

        val firstA = first.files.find {
            it.normalizedPath == PathNormalizer.normalize(fileA.path)
        }
        assertNotNull(firstA)
        assertNull(firstA?.hashHex)

        val fileB = File(tempDir, "b.txt").apply {
            writeText("bb")
        }

        val second = scanner.scan(tempDir)

        val secondA = second.files.find {
            it.normalizedPath == PathNormalizer.normalize(fileA.path)
        }
        val secondB = second.files.find {
            it.normalizedPath == PathNormalizer.normalize(fileB.path)
        }
        assertNotNull(secondA?.hashHex)
        assertNotNull(secondB?.hashHex)
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

    @Test
    fun cancelledScanStopsDuringHashingWithoutPersistingPartialCache() {
        val fileA = File(tempDir, "a.txt").apply { writeText("aa") }
        val fileB = File(tempDir, "b.txt").apply { writeText("bb") }
        var allow = true
        val hasher = CancellingHasher { allow = false }
        val scanner = IncrementalScanner(store, hasher, FileWalker())

        val result = scanner.scan(
            tempDir,
            shouldContinue = { allow }
        )

        assertEquals(0, result.files.size)
        assertEquals(0, database.fileCacheDao().getAll().size)
    }

    @Test
    fun scanRepairsUnscannedSameSizeCachedEntryMissingHash() {
        val existingRoot = Files.createTempDirectory("cached-existing").toFile()
        try {
            val fileA = File(existingRoot, "a.txt").apply {
                writeText("aa")
            }
            val fileB = File(tempDir, "b.txt").apply {
                writeText("bb")
            }
            store.upsert(
                FileMetadata(
                    path = fileA.path,
                    normalizedPath = PathNormalizer.normalize(fileA.path),
                    sizeBytes = fileA.length(),
                    lastModifiedMillis = fileA.lastModified(),
                    hashHex = null
                )
            )
            val hasher = CountingHasher()
            val scanner = IncrementalScanner(store, hasher, FileWalker())

            scanner.scan(tempDir)

            assertEquals(1, hasher.callsFor(fileA))
            assertEquals(1, hasher.callsFor(fileB))
            val repaired = database.fileCacheDao()
                .getByNormalizedPath(PathNormalizer.normalize(fileA.path))
            assertNotNull(repaired?.hashHex)
        } finally {
            existingRoot.deleteRecursively()
        }
    }

    @Test
    fun cancelledScanStopsDuringRepairingCachedMissingHashWithoutPersistingPartialCache() {
        val existingRoot = Files.createTempDirectory("cached-existing").toFile()
        try {
            val fileA = File(existingRoot, "a.txt").apply {
                writeText("aa")
            }
            File(tempDir, "b.txt").apply {
                writeText("bb")
            }
            store.upsert(
                FileMetadata(
                    path = fileA.path,
                    normalizedPath = PathNormalizer.normalize(fileA.path),
                    sizeBytes = fileA.length(),
                    lastModifiedMillis = fileA.lastModified(),
                    hashHex = null
                )
            )
            var allow = true
            val hasher = CancellingHasher { allow = false }
            val scanner = IncrementalScanner(store, hasher, FileWalker())

            val result = scanner.scan(
                tempDir,
                shouldContinue = { allow }
            )

            assertEquals(0, result.files.size)
            val cachedAfter = database.fileCacheDao()
                .getByNormalizedPath(PathNormalizer.normalize(fileA.path))
            assertNull(cachedAfter?.hashHex)
            assertEquals(1, database.fileCacheDao().getAll().size)
        } finally {
            existingRoot.deleteRecursively()
        }
    }

    @Test
    fun scanSkipsMissingCachedEntryDuringHashRepair() {
        val missingFile = File(tempDir.parentFile, "missing-cached-entry.txt")
        val scannedFile = File(tempDir, "b.txt").apply {
            writeText("bb")
        }
        store.upsert(
            FileMetadata(
                path = missingFile.path,
                normalizedPath = PathNormalizer.normalize(missingFile.path),
                sizeBytes = scannedFile.length(),
                lastModifiedMillis = 1,
                hashHex = null
            )
        )
        val hasher = CountingHasher()
        val scanner = IncrementalScanner(store, hasher, FileWalker())

        val result = scanner.scan(tempDir)

        assertEquals(1, result.files.size)
        assertEquals(0, hasher.callsFor(missingFile))
        assertEquals(1, hasher.callsFor(scannedFile))
        val cachedAfter = database.fileCacheDao()
            .getByNormalizedPath(PathNormalizer.normalize(missingFile.path))
        assertNull(cachedAfter?.hashHex)
    }

    @Test
    fun scanIgnoreCanExcludeTrashBinContents() {
        val regularFile = File(tempDir, "regular.txt").apply {
            writeText("hello")
        }
        val trashDir = TrashPaths.trashBinDir(tempDir).apply { mkdirs() }
        File(trashDir, "trashed.txt").writeText("discard")
        val scanner = IncrementalScanner(store, Sha256FileHasher(), FileWalker())

        val result = scanner.scan(
            tempDir,
            ignore = TrashPaths::isInTrashBin
        )

        assertEquals(1, result.files.size)
        assertEquals(PathNormalizer.normalize(regularFile.path), result.files.first().normalizedPath)
        assertEquals(1, database.fileCacheDao().getAll().size)
        assertEquals(
            PathNormalizer.normalize(regularFile.path),
            database.fileCacheDao().getAll().first().normalizedPath
        )
    }

    @Test
    fun configuredWorkerCountBoundsConcurrentHashing() {
        repeat(6) { index ->
            File(tempDir, "parallel-$index.txt").writeText("aa")
        }
        val hasher = BlockingParallelHasher(expectedConcurrent = 3)
        val providerCalls = AtomicInteger(0)
        val scanner = IncrementalScanner(
            cacheStore = store,
            fileHasher = hasher,
            fileWalker = FileWalker(),
            workerCountProvider = {
                providerCalls.incrementAndGet()
                3
            }
        )
        val result = AtomicReference<ScanResult?>()
        val failure = AtomicReference<Throwable?>()
        val scanThread = Thread {
            runCatching { scanner.scan(tempDir) }
                .onSuccess(result::set)
                .onFailure(failure::set)
        }

        scanThread.start()
        val reachedConfiguredConcurrency = hasher.expectedWorkersEntered.await(5, TimeUnit.SECONDS)
        hasher.releaseWorkers.countDown()
        scanThread.join(10_000L)

        assertTrue(reachedConfiguredConcurrency)
        assertFalse(scanThread.isAlive)
        failure.get()?.let { error -> throw AssertionError(error) }
        assertEquals(1, providerCalls.get())
        assertEquals(3, hasher.maxConcurrent.get())
        assertEquals(6, hasher.hashCalls.get())
        assertEquals(6, result.get()?.files?.size)
        assertEquals(6, database.fileCacheDao().countAll())
    }

    @Test
    fun parallelHashCancellationDoesNotPersistCompletedWorkers() {
        repeat(4) { index ->
            File(tempDir, "cancel-parallel-$index.txt").writeText("aa")
        }
        val allow = AtomicBoolean(true)
        val hasher = BlockingCancellationHasher()
        val scanner = IncrementalScanner(
            cacheStore = store,
            fileHasher = hasher,
            fileWalker = FileWalker(),
            workerCountProvider = { 3 }
        )
        val result = AtomicReference<ScanResult?>()
        val scanThread = Thread {
            result.set(scanner.scan(tempDir, shouldContinue = allow::get))
        }

        scanThread.start()
        val workerEntered = hasher.workerEntered.await(5, TimeUnit.SECONDS)
        allow.set(false)
        hasher.releaseWorker.countDown()
        scanThread.join(10_000L)

        assertTrue(workerEntered)
        assertFalse(scanThread.isAlive)
        assertEquals(0, result.get()?.files?.size)
        assertEquals(0, database.fileCacheDao().countAll())
    }

    private class CountingHasher : FileHasher {
        private val counts = mutableMapOf<String, Int>()

        override fun hash(file: File, shouldContinue: () -> Boolean): String? {
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

    private class CancellingHasher(
        private val onHashStarted: () -> Unit
    ) : FileHasher {
        override fun hash(file: File, shouldContinue: () -> Boolean): String? {
            onHashStarted()
            return if (shouldContinue()) {
                "hash-${PathNormalizer.normalize(file.path)}"
            } else {
                null
            }
        }
    }

    private class BlockingParallelHasher(expectedConcurrent: Int) : FileHasher {
        val expectedWorkersEntered = CountDownLatch(expectedConcurrent)
        val releaseWorkers = CountDownLatch(1)
        val maxConcurrent = AtomicInteger(0)
        val hashCalls = AtomicInteger(0)
        private val active = AtomicInteger(0)

        override fun hash(file: File, shouldContinue: () -> Boolean): String? {
            hashCalls.incrementAndGet()
            val activeCount = active.incrementAndGet()
            maxConcurrent.updateAndGet { previous -> maxOf(previous, activeCount) }
            expectedWorkersEntered.countDown()
            return try {
                releaseWorkers.await(5, TimeUnit.SECONDS)
                if (shouldContinue()) "hash-${file.name}" else null
            } finally {
                active.decrementAndGet()
            }
        }
    }

    private class BlockingCancellationHasher : FileHasher {
        val workerEntered = CountDownLatch(1)
        val releaseWorker = CountDownLatch(1)

        override fun hash(file: File, shouldContinue: () -> Boolean): String? {
            workerEntered.countDown()
            releaseWorker.await(5, TimeUnit.SECONDS)
            return if (shouldContinue()) "hash-${file.name}" else null
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
