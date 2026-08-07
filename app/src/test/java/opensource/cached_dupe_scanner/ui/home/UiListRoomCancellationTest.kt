package opensource.cached_dupe_scanner.ui.home

import android.content.Context
import android.os.OperationCanceledException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
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
}
