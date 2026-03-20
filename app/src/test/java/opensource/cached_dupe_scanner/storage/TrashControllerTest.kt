package opensource.cached_dupe_scanner.storage

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import opensource.cached_dupe_scanner.cache.CacheDatabase
import opensource.cached_dupe_scanner.cache.CachedFileEntity
import opensource.cached_dupe_scanner.cache.TrashEntryEntity
import opensource.cached_dupe_scanner.core.Hashing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class TrashControllerTest {
    private class FakeRootProvider(private val rootDir: File) : StorageRootProvider {
        override fun resolve(context: Context, absolutePath: String): StorageRootResolver.Root {
            return StorageRootResolver.Root(rootDir.absolutePath)
        }
    }

    @Test
    fun restoreReinsertsCachedRecordWithHash() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, CacheDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        val volumeRoot = createTempDir(prefix = "volume_")
        val original = File(volumeRoot, "docs/a.txt")
        original.parentFile!!.mkdirs()
        original.writeText("hello")

        val normalizedPath = original.absolutePath
        val hash = "deadbeef"
        database.fileCacheDao().upsert(
            CachedFileEntity(
                normalizedPath = normalizedPath,
                path = normalizedPath,
                sizeBytes = original.length(),
                lastModifiedMillis = original.lastModified(),
                hashHex = hash
            )
        )

        val settings = AppSettingsStore(context)
        val historyRepo = ScanHistoryRepository(database.fileCacheDao(), settings)
        val trashRepo = TrashRepository(database.trashDao())
        val controller = TrashController(
            context = context,
            database = database,
            historyRepo = historyRepo,
            trashRepo = trashRepo,
            storageRootProvider = FakeRootProvider(volumeRoot)
        )

        val move = controller.moveToTrash(normalizedPath)
        assertTrue(move.success)
        val entry = move.entry
        assertNotNull(entry)
        assertNull(database.fileCacheDao().getByNormalizedPath(normalizedPath))
        assertTrue(!File(normalizedPath).exists())

        val savedTrash = trashRepo.getById(entry!!.id)
        assertNotNull(savedTrash)
        assertEquals(hash, savedTrash?.hashHex)

        val restore = controller.restoreFromTrash(entry)
        assertEquals(TrashController.RestoreResult.Success, restore)
        assertTrue(File(normalizedPath).exists())

        val restoredCache = database.fileCacheDao().getByNormalizedPath(normalizedPath)
        assertNotNull(restoredCache)
        assertEquals(hash, restoredCache?.hashHex)
        assertNull(trashRepo.getById(entry.id))

        volumeRoot.deleteRecursively()
        database.close()
    }

    @Test
    fun restoreDetectsConflictAndKeepsTrashEntry() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, CacheDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        val volumeRoot = createTempDir(prefix = "volume_")
        val original = File(volumeRoot, "docs/b.txt")
        original.parentFile!!.mkdirs()
        original.writeText("hello")

        val normalizedPath = original.absolutePath
        database.fileCacheDao().upsert(
            CachedFileEntity(
                normalizedPath = normalizedPath,
                path = normalizedPath,
                sizeBytes = original.length(),
                lastModifiedMillis = original.lastModified(),
                hashHex = "h"
            )
        )

        val settings = AppSettingsStore(context)
        val historyRepo = ScanHistoryRepository(database.fileCacheDao(), settings)
        val trashRepo = TrashRepository(database.trashDao())
        val controller = TrashController(
            context = context,
            database = database,
            historyRepo = historyRepo,
            trashRepo = trashRepo,
            storageRootProvider = FakeRootProvider(volumeRoot)
        )

        val move = controller.moveToTrash(normalizedPath)
        val entry = move.entry!!

        // Create a conflicting file at the original path
        val conflictFile = File(normalizedPath)
        conflictFile.parentFile!!.mkdirs()
        conflictFile.writeText("new")
        assertTrue(conflictFile.exists())

        val result = controller.restoreFromTrash(entry)
        assertEquals(TrashController.RestoreResult.ConflictTargetExists, result)

        // Keep trash entry and trashed file
        assertNotNull(trashRepo.getById(entry.id))
        assertTrue(File(entry.trashedPath).exists())

        volumeRoot.deleteRecursively()
        database.close()
    }

    @Test
    fun moveToTrashSynchronizesDuplicateGroupsThroughHistoryRepository() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, CacheDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        val volumeRoot = createTempDir(prefix = "volume_")
        val fileA = File(volumeRoot, "docs/c_a.txt")
        val fileB = File(volumeRoot, "docs/c_b.txt")
        fileA.parentFile!!.mkdirs()
        fileA.writeText("same")
        fileB.writeText("same")

        val hash = Hashing.sha256Hex(fileA)
        val size = fileA.length()
        database.fileCacheDao().upsert(
            CachedFileEntity(
                normalizedPath = fileA.absolutePath,
                path = fileA.absolutePath,
                sizeBytes = size,
                lastModifiedMillis = fileA.lastModified(),
                hashHex = hash
            )
        )
        database.fileCacheDao().upsert(
            CachedFileEntity(
                normalizedPath = fileB.absolutePath,
                path = fileB.absolutePath,
                sizeBytes = size,
                lastModifiedMillis = fileB.lastModified(),
                hashHex = hash
            )
        )
        database.duplicateGroupDao().rebuildFromCache(System.currentTimeMillis())
        assertEquals(1, database.duplicateGroupDao().countGroups())

        val settings = AppSettingsStore(context)
        val historyRepo = ScanHistoryRepository(
            dao = database.fileCacheDao(),
            settingsStore = settings,
            groupDao = database.duplicateGroupDao()
        )
        val trashRepo = TrashRepository(database.trashDao())
        val controller = TrashController(
            context = context,
            database = database,
            historyRepo = historyRepo,
            trashRepo = trashRepo,
            storageRootProvider = FakeRootProvider(volumeRoot)
        )

        val moved = controller.moveToTrash(fileA.absolutePath)
        assertTrue(moved.success)
        assertEquals(0, database.duplicateGroupDao().countGroups())

        volumeRoot.deleteRecursively()
        database.close()
    }

    @Test
    fun emptyTrashReportsProgressAndSupportsCancellation() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, CacheDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        val volumeRoot = createTempDir(prefix = "volume_")
        val trashDir = File(volumeRoot, ".CachedDupeScanner/trashbin").apply { mkdirs() }
        try {
            repeat(3) { index ->
                val file = File(trashDir, "item$index.txt").apply { writeText("item-$index") }
                database.trashDao().upsert(
                    TrashEntryEntity(
                        id = "id-$index",
                        originalPath = "/origin/$index",
                        trashedPath = file.absolutePath,
                        sizeBytes = file.length(),
                        lastModifiedMillis = file.lastModified(),
                        hashHex = null,
                        deletedAtMillis = 100L + index,
                        volumeRoot = volumeRoot.absolutePath
                    )
                )
            }

            val controller = TrashController(
                context = context,
                database = database,
                historyRepo = ScanHistoryRepository(database.fileCacheDao(), AppSettingsStore(context)),
                trashRepo = TrashRepository(database.trashDao()),
                storageRootProvider = FakeRootProvider(volumeRoot)
            )

            var progressCalls = 0
            val summary = controller.emptyTrash(
                shouldContinue = { progressCalls == 0 }
            ) {
                progressCalls += 1
            }

            assertTrue(summary.cancelled)
            assertEquals(1, summary.processed)
            assertEquals(1, summary.deleted)
            assertEquals(2, database.trashDao().countAll())
            assertEquals(1, progressCalls)
        } finally {
            volumeRoot.deleteRecursively()
            database.close()
        }
    }

    @Test
    fun emptyTrashCountsDeletionFailuresAndKeepsEntry() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, CacheDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        val volumeRoot = createTempDir(prefix = "volume_")
        val trashDir = File(volumeRoot, ".CachedDupeScanner/trashbin").apply { mkdirs() }
        try {
            val failingDir = File(trashDir, "blocked").apply {
                mkdirs()
                File(this, "child.txt").writeText("x")
            }
            database.trashDao().upsert(
                TrashEntryEntity(
                    id = "blocked",
                    originalPath = "/origin/blocked",
                    trashedPath = failingDir.absolutePath,
                    sizeBytes = 0L,
                    lastModifiedMillis = failingDir.lastModified(),
                    hashHex = null,
                    deletedAtMillis = 100L,
                    volumeRoot = volumeRoot.absolutePath
                )
            )

            val controller = TrashController(
                context = context,
                database = database,
                historyRepo = ScanHistoryRepository(database.fileCacheDao(), AppSettingsStore(context)),
                trashRepo = TrashRepository(database.trashDao()),
                storageRootProvider = FakeRootProvider(volumeRoot)
            )

            val summary = controller.emptyTrash(
                shouldContinue = { true }
            ) { }

            assertFalse(summary.cancelled)
            assertEquals(1, summary.failed)
            assertEquals(0, summary.deleted)
            assertEquals(1, database.trashDao().countAll())
            assertTrue(failingDir.exists())
        } finally {
            volumeRoot.deleteRecursively()
            database.close()
        }
    }
}
