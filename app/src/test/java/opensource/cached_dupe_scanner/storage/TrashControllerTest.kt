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
import java.nio.file.Files

@RunWith(RobolectricTestRunner::class)
class TrashControllerTest {
    private class FakeRootProvider(private val rootDir: File) : StorageRootProvider {
        override fun resolve(context: Context, absolutePath: String): StorageRootResolver.Root {
            return StorageRootResolver.Root(rootDir.absolutePath)
        }
    }

    @Test
    fun moveAndRestoreSynchronizeCacheDerivedContracts() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, CacheDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        val volumeRoot = createVolumeRoot()
        try {
            val fileA = File(volumeRoot, "docs/a.txt")
            val fileB = File(volumeRoot, "docs/b.txt")
            fileA.parentFile!!.mkdirs()
            fileA.writeText("same")
            fileB.writeText("same")

            val hash = Hashing.sha256Hex(fileA)
            val size = fileA.length()
            database.fileCacheDao().upsert(cachedEntity(fileA, hash))
            database.fileCacheDao().upsert(cachedEntity(fileB, hash))
            database.duplicateGroupDao().rebuildFromCache(System.currentTimeMillis())
            assertEquals(1, database.duplicateGroupDao().countGroups())

            val observer = RecordingCacheMutationObserver()
            val historyRepo = ScanHistoryRepository(
                dao = database.fileCacheDao(),
                settingsStore = AppSettingsStore(context),
                groupDao = database.duplicateGroupDao(),
                database = database,
                cacheMutationObserver = observer
            )
            val trashRepo = TrashRepository(database.trashDao())
            val controller = TrashController(
                context = context,
                database = database,
                historyRepo = historyRepo,
                trashRepo = trashRepo,
                storageRootProvider = FakeRootProvider(volumeRoot)
            )

            val move = controller.moveToTrash(fileA.absolutePath)
            assertTrue(move.success)
            val entry = requireNotNull(move.entry)
            assertFalse(fileA.exists())
            assertNull(database.fileCacheDao().getByNormalizedPath(fileA.absolutePath))
            assertEquals(0, database.duplicateGroupDao().countGroups())
            assertTrue(observer.changedPaths.isEmpty())

            val savedTrash = trashRepo.getById(entry.id)
            assertNotNull(savedTrash)
            assertEquals(hash, savedTrash?.hashHex)

            observer.changedPaths.clear()
            val restore = controller.restoreFromTrash(entry)
            assertEquals(TrashController.RestoreResult.Success, restore)
            assertTrue(fileA.exists())

            val restoredCache = database.fileCacheDao().getByNormalizedPath(fileA.absolutePath)
            assertNotNull(restoredCache)
            assertEquals(hash, restoredCache?.hashHex)
            assertEquals(1, database.duplicateGroupDao().countGroups())
            val restoredGroup = database.duplicateGroupDao().get(size, hash)
            assertNotNull(restoredGroup)
            assertEquals(2, restoredGroup?.fileCount)
            assertEquals(listOf(fileA.absolutePath), observer.changedPaths)
            assertNull(trashRepo.getById(entry.id))
        } finally {
            volumeRoot.deleteRecursively()
            database.close()
        }
    }

    @Test
    fun restoreDetectsConflictAndKeepsTrashEntry() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, CacheDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        val volumeRoot = createVolumeRoot()
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
    fun emptyTrashReportsProgressAndSupportsCancellation() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, CacheDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        val volumeRoot = createVolumeRoot()
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
    fun emptyTrashProcessesTrashEntriesAcrossPages() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, CacheDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        val volumeRoot = createVolumeRoot()
        val trashDir = File(volumeRoot, ".CachedDupeScanner/trashbin").apply { mkdirs() }
        try {
            val entryCount = EMPTY_TRASH_PAGE_SIZE + 3
            repeat(entryCount) { index ->
                insertTrashEntry(
                    database = database,
                    trashDir = trashDir,
                    volumeRoot = volumeRoot,
                    id = "id-${index.toString().padStart(3, '0')}",
                    deletedAtMillis = 1_000L + index
                )
            }
            val controller = trashController(context, database, volumeRoot)
            val progress = mutableListOf<TrashProgress>()

            val summary = controller.emptyTrash(
                shouldContinue = { true },
                onProgress = { progress += it }
            )

            assertFalse(summary.cancelled)
            assertEquals(entryCount, summary.total)
            assertEquals(entryCount, summary.processed)
            assertEquals(entryCount, summary.deleted)
            assertEquals(0, summary.failed)
            assertEquals(0, database.trashDao().countAll())
            assertEquals(entryCount, progress.size)
            assertEquals(entryCount, progress.last().total)
            assertEquals(entryCount, progress.last().processed)
        } finally {
            volumeRoot.deleteRecursively()
            database.close()
        }
    }

    @Test
    fun emptyTrashDoesNotLoopOnFailedDeletionWhilePaging() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, CacheDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        val volumeRoot = createVolumeRoot()
        val trashDir = File(volumeRoot, ".CachedDupeScanner/trashbin").apply { mkdirs() }
        try {
            val entryCount = EMPTY_TRASH_PAGE_SIZE + 3
            val failedDir = File(trashDir, "failed-dir").apply {
                mkdirs()
                File(this, "child.txt").writeText("x")
            }
            database.trashDao().upsert(
                TrashEntryEntity(
                    id = "zz-failed",
                    originalPath = "/origin/failed",
                    trashedPath = failedDir.absolutePath,
                    sizeBytes = 0L,
                    lastModifiedMillis = failedDir.lastModified(),
                    hashHex = null,
                    deletedAtMillis = 10_000L,
                    volumeRoot = volumeRoot.absolutePath
                )
            )
            repeat(entryCount - 1) { index ->
                insertTrashEntry(
                    database = database,
                    trashDir = trashDir,
                    volumeRoot = volumeRoot,
                    id = "id-${index.toString().padStart(3, '0')}",
                    deletedAtMillis = 1_000L + index
                )
            }
            val controller = trashController(context, database, volumeRoot)

            val summary = controller.emptyTrash(
                shouldContinue = { true },
                onProgress = { }
            )

            assertFalse(summary.cancelled)
            assertEquals(entryCount, summary.total)
            assertEquals(entryCount, summary.processed)
            assertEquals(entryCount - 1, summary.deleted)
            assertEquals(1, summary.failed)
            assertEquals(1, database.trashDao().countAll())
            assertEquals("zz-failed", database.trashDao().getAll().single().id)
            assertTrue(failedDir.exists())
        } finally {
            volumeRoot.deleteRecursively()
            database.close()
        }
    }

    @Test
    fun emptyTrashCancelsBeforeEntryAcrossPages() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, CacheDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        val volumeRoot = createVolumeRoot()
        val trashDir = File(volumeRoot, ".CachedDupeScanner/trashbin").apply { mkdirs() }
        try {
            val entryCount = EMPTY_TRASH_PAGE_SIZE + 3
            repeat(entryCount) { index ->
                insertTrashEntry(
                    database = database,
                    trashDir = trashDir,
                    volumeRoot = volumeRoot,
                    id = "id-${index.toString().padStart(3, '0')}",
                    deletedAtMillis = 1_000L + index
                )
            }
            val controller = trashController(context, database, volumeRoot)
            var continueChecks = 0
            val allowedAttempts = EMPTY_TRASH_PAGE_SIZE + 1

            val summary = controller.emptyTrash(
                shouldContinue = {
                    continueChecks += 1
                    continueChecks <= allowedAttempts
                },
                onProgress = { }
            )

            assertTrue(summary.cancelled)
            assertEquals(entryCount, summary.total)
            assertEquals(allowedAttempts, summary.processed)
            assertEquals(allowedAttempts, summary.deleted)
            assertEquals(entryCount - allowedAttempts, database.trashDao().countAll())
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

        val volumeRoot = createVolumeRoot()
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

            val controller = trashController(context, database, volumeRoot)

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

    private fun trashController(
        context: Context,
        database: CacheDatabase,
        volumeRoot: File
    ): TrashController {
        return TrashController(
            context = context,
            database = database,
            historyRepo = ScanHistoryRepository(database.fileCacheDao(), AppSettingsStore(context)),
            trashRepo = TrashRepository(database.trashDao()),
            storageRootProvider = FakeRootProvider(volumeRoot)
        )
    }

    private fun insertTrashEntry(
        database: CacheDatabase,
        trashDir: File,
        volumeRoot: File,
        id: String,
        deletedAtMillis: Long
    ): TrashEntryEntity {
        val file = File(trashDir, "$id.txt").apply { writeText(id) }
        val entry = TrashEntryEntity(
            id = id,
            originalPath = "/origin/$id",
            trashedPath = file.absolutePath,
            sizeBytes = file.length(),
            lastModifiedMillis = file.lastModified(),
            hashHex = null,
            deletedAtMillis = deletedAtMillis,
            volumeRoot = volumeRoot.absolutePath
        )
        database.trashDao().upsert(entry)
        return entry
    }

    private fun createVolumeRoot(): File {
        val projectDir = File(requireNotNull(System.getProperty("user.dir")))
        val baseDir = File(projectDir, "build/test-tmp/trash-controller").apply { mkdirs() }
        return Files.createTempDirectory(baseDir.toPath(), "volume_").toFile()
    }

    private fun cachedEntity(file: File, hash: String): CachedFileEntity {
        return CachedFileEntity(
            normalizedPath = file.absolutePath,
            path = file.absolutePath,
            sizeBytes = file.length(),
            lastModifiedMillis = file.lastModified(),
            hashHex = hash
        )
    }

    private class RecordingCacheMutationObserver : CacheMutationObserver {
        val changedPaths = mutableListOf<String>()

        override fun onCachedFilesChanged(normalizedPaths: List<String>) {
            changedPaths += normalizedPaths
        }

        override fun onCacheCleared() = Unit
    }
}
