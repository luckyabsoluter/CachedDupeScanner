package opensource.cached_dupe_scanner.ui.home

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.os.OperationCanceledException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import opensource.cached_dupe_scanner.cache.CacheDatabase
import opensource.cached_dupe_scanner.cache.CachedFileEntity
import opensource.cached_dupe_scanner.cache.DuplicateGroupEntity
import opensource.cached_dupe_scanner.cache.SimilaritySettingEntity
import opensource.cached_dupe_scanner.cache.TrashEntryEntity
import opensource.cached_dupe_scanner.storage.DuplicateGroupSortKey
import opensource.cached_dupe_scanner.storage.PagedFileRepository
import opensource.cached_dupe_scanner.storage.ResultsDbRepository
import opensource.cached_dupe_scanner.storage.SimilaritySettingsRepository
import opensource.cached_dupe_scanner.storage.TrashRepository
import opensource.cached_dupe_scanner.storage.readRoomQueryWithCancellation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UiListRoomCancellationTest {
    @Test
    fun cancelledListSignalPreventsRoomQueryExecution() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, CacheDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val cancellation = UiListLoadCancellation(parentJob = null)
        val frameworkSignal = cancellation.frameworkQueryCancellationSignal

        try {
            cancellation.cancel()

            val failure = runCatching {
                database.readRoomQueryWithCancellation(
                    sql = "SELECT 1",
                    cancellationSignal = frameworkSignal
                ) { cursor -> cursor.moveToFirst() }
            }.exceptionOrNull()

            assertTrue(failure is OperationCanceledException)
        } finally {
            database.close()
        }
    }

    @Test
    @SQLiteMode(SQLiteMode.Mode.NATIVE)
    fun cancellingActiveListLoadInterruptsRunningRoomQueryAndStopsWorker() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, CacheDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val controller = UiListLoadController()
        val queryEntered = CountDownLatch(1)
        val releaseQuery = CountDownLatch(1)
        val workerStopped = CountDownLatch(1)
        val roomCancellationObserved = AtomicBoolean(false)
        val frameworkSignal = AtomicReference<android.os.CancellationSignal?>()

        frameworkSQLiteDatabase(database).setCustomScalarFunction(
            BLOCKING_QUERY_FUNCTION
        ) { value ->
            queryEntered.countDown()
            while (releaseQuery.count > 0L) {
                try {
                    releaseQuery.await(100, TimeUnit.MILLISECONDS)
                } catch (_: InterruptedException) {
                    Thread.interrupted()
                }
            }
            value
        }

        try {
            val job = controller.replace(scope) {
                try {
                    runInterruptibleUiListLoad { cancellation ->
                        val querySignal = cancellation.frameworkQueryCancellationSignal
                        frameworkSignal.set(querySignal)
                        database.readRoomQueryWithCancellation(
                            sql = "SELECT $BLOCKING_QUERY_FUNCTION('running')",
                            cancellationSignal = querySignal
                        ) { cursor ->
                            try {
                                cursor.moveToFirst()
                            } catch (cancelledQuery: OperationCanceledException) {
                                roomCancellationObserved.set(true)
                                throw cancelledQuery
                            }
                        }
                    }
                } catch (_: CancellationException) {
                    // Expected after the active Room cursor receives its cancellation signal.
                } finally {
                    workerStopped.countDown()
                }
            }

            assertTrue(queryEntered.await(5, TimeUnit.SECONDS))
            controller.cancel()
            assertTrue(awaitFrameworkCancellation(frameworkSignal.get()))
            releaseQuery.countDown()

            assertTrue(workerStopped.await(5, TimeUnit.SECONDS))
            job.join()
            assertTrue(job.isCancelled)
            assertTrue(roomCancellationObserved.get())
        } finally {
            releaseQuery.countDown()
            scope.cancel()
            database.close()
        }
    }

    @Test
    fun listRepositoriesAttachCancellationSignalToRoomReads() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, CacheDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val cancellation = UiListLoadCancellation(parentJob = null)
        val frameworkSignal = cancellation.frameworkQueryCancellationSignal
        val repositories = listOf<() -> Unit>(
            { PagedFileRepository(database).countAll(frameworkSignal) },
            { TrashRepository(database).countAll(frameworkSignal) },
            {
                ResultsDbRepository(database).loadInitialSnapshot(
                    sortKey = DuplicateGroupSortKey.CountDesc,
                    limit = 200,
                    rebuild = false,
                    cancellationSignal = frameworkSignal
                )
            },
            {
                SimilaritySettingsRepository(
                    database = database,
                    fileDao = database.fileCacheDao(),
                    similarityDao = database.similaritySettingsDao()
                ).listSettings(frameworkSignal)
            }
        )

        try {
            cancellation.cancel()

            repositories.forEach { read ->
                val failure = runCatching(read).exceptionOrNull()
                assertTrue(failure is OperationCanceledException)
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun activeListSignalsPreserveRepositoryPagingResults() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, CacheDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val signal = android.os.CancellationSignal()
        val filePath = "C:/cancellable/file.txt"
        val trashId = "trash-entry"
        val groupHash = "ab".repeat(32)

        try {
            database.fileCacheDao().upsert(
                CachedFileEntity(
                    normalizedPath = filePath,
                    path = filePath,
                    sizeBytes = 17L,
                    lastModifiedMillis = 123L,
                    hashHex = groupHash
                )
            )
            database.trashDao().upsert(
                TrashEntryEntity(
                    id = trashId,
                    originalPath = filePath,
                    trashedPath = "C:/trash/file.txt",
                    sizeBytes = 17L,
                    lastModifiedMillis = 123L,
                    hashHex = groupHash,
                    deletedAtMillis = 456L,
                    volumeRoot = "C:/"
                )
            )
            database.duplicateGroupDao().upsertAll(
                listOf(
                    DuplicateGroupEntity(
                        sizeBytes = 17L,
                        hashHex = groupHash,
                        fileCount = 2,
                        totalBytes = 34L,
                        updatedAtMillis = 789L
                    )
                )
            )
            database.similaritySettingsDao().insertSetting(
                SimilaritySettingEntity(
                    methodId = "test-method",
                    mediaScope = "Video",
                    minSizeBytes = 1L,
                    paramsJson = "{}",
                    paramsHash = "test-params",
                    displayName = "Cancellable setting",
                    enabled = false,
                    createdAtMillis = 1L,
                    updatedAtMillis = 2L
                )
            )

            val filePage = PagedFileRepository(database).loadPage(
                sortKey = PagedFileRepository.SortKey.Name,
                direction = PagedFileRepository.SortDirection.Asc,
                cursor = PagedFileRepository.Cursor.Start,
                limit = 10,
                cancellationSignal = signal
            )
            val trashPage = TrashRepository(database).getFirstPage(10, signal)
            val resultsSnapshot = ResultsDbRepository(database).loadInitialSnapshot(
                sortKey = DuplicateGroupSortKey.CountDesc,
                limit = 10,
                rebuild = false,
                cancellationSignal = signal
            )
            val settings = SimilaritySettingsRepository(
                database = database,
                fileDao = database.fileCacheDao(),
                similarityDao = database.similaritySettingsDao()
            ).listSettings(signal)

            assertEquals(filePath, filePage.items.single().normalizedPath)
            assertEquals(trashId, trashPage.single().id)
            assertEquals(groupHash, resultsSnapshot.firstPage.single().hashHex)
            assertEquals("Cancellable setting", settings.single().displayName)
        } finally {
            database.close()
        }
    }

    private fun frameworkSQLiteDatabase(database: CacheDatabase): SQLiteDatabase {
        val supportDatabase = database.openHelper.writableDatabase
        val delegateField = supportDatabase.javaClass.getDeclaredField("delegate")
        delegateField.isAccessible = true
        return delegateField.get(supportDatabase) as SQLiteDatabase
    }

    private fun awaitFrameworkCancellation(signal: android.os.CancellationSignal?): Boolean {
        val currentSignal = signal ?: return false
        val deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (!currentSignal.isCanceled && System.nanoTime() < deadlineNanos) {
            Thread.sleep(1)
        }
        return currentSignal.isCanceled
    }

    private companion object {
        const val BLOCKING_QUERY_FUNCTION = "wait_for_list_query_release"
    }
}
