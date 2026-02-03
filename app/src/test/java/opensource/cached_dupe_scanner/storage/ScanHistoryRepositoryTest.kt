package opensource.cached_dupe_scanner.storage

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import opensource.cached_dupe_scanner.cache.CacheDatabase
import opensource.cached_dupe_scanner.core.Hashing
import opensource.cached_dupe_scanner.core.FileMetadata
import opensource.cached_dupe_scanner.core.ScanResult
import opensource.cached_dupe_scanner.storage.AppSettingsStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class ScanHistoryRepositoryTest {
    @Test
    fun recordAndMergeHistory() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, CacheDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val settings = AppSettingsStore(context)
            val repo = ScanHistoryRepository(database.fileCacheDao(), settings)

            val r1 = ScanResult(
                scannedAtMillis = 1,
                files = listOf(
                    FileMetadata("/a", "/a", 1, 1, "h1")
                ),
                duplicateGroups = emptyList()
            )
            val r2 = ScanResult(
                scannedAtMillis = 2,
                files = listOf(
                    FileMetadata("/b", "/b", 1, 1, "h1")
                ),
                duplicateGroups = emptyList()
            )

            settings.setSkipZeroSizeInDb(true)
            val rZero = ScanResult(
                scannedAtMillis = 2,
                files = listOf(
                    FileMetadata("/zero", "/zero", 0, 1, "h3")
                ),
                duplicateGroups = emptyList()
            )

            repo.recordScan(r1)
            repo.recordScan(r2)
            repo.recordScan(rZero)

            // Record same path again with updated size/hash
            val r3 = ScanResult(
                scannedAtMillis = 3,
                files = listOf(
                    FileMetadata("/a", "/a", 5, 5, "h2")
                ),
                duplicateGroups = emptyList()
            )
            repo.recordScan(r3)

            val merged = repo.loadMergedHistory()
            assertNotNull(merged)
            assertEquals(2, merged?.files?.size)
            assertEquals(0, merged?.duplicateGroups?.size)
            assertEquals(2, database.fileCacheDao().getAll().size)

            val updated = merged?.files?.firstOrNull { it.normalizedPath == "/a" }
            assertNotNull(updated)
            assertEquals(5L, updated?.sizeBytes)
            assertEquals("h2", updated?.hashHex)
        } finally {
            database.close()
        }
    }

    @Test
    fun deleteByNormalizedPathRemovesEntry() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, CacheDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val settings = AppSettingsStore(context)
            val repo = ScanHistoryRepository(database.fileCacheDao(), settings)

            val r1 = ScanResult(
                scannedAtMillis = 1,
                files = listOf(
                    FileMetadata("/a", "/a", 1, 1, "h1"),
                    FileMetadata("/b", "/b", 1, 1, "h2")
                ),
                duplicateGroups = emptyList()
            )
            repo.recordScan(r1)

            repo.deleteByNormalizedPath("/a")

            val merged = repo.loadMergedHistory()
            assertNotNull(merged)
            assertEquals(1, merged?.files?.size)
            assertEquals("/b", merged?.files?.first()?.normalizedPath)
        } finally {
            database.close()
        }
    }

    @Test
    fun deleteMissingByNormalizedPathsRemovesOnlyMissing() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, CacheDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val existingFile = File.createTempFile("cached", ".txt")
        val missingFile = File.createTempFile("cached", ".missing")
        try {
            existingFile.writeText("hello")
            missingFile.writeText("goodbye")
            missingFile.delete()

            val settings = AppSettingsStore(context)
            val repo = ScanHistoryRepository(database.fileCacheDao(), settings)
            val result = ScanResult(
                scannedAtMillis = 1,
                files = listOf(
                    FileMetadata(
                        existingFile.absolutePath,
                        existingFile.absolutePath,
                        existingFile.length(),
                        existingFile.lastModified(),
                        "h1"
                    ),
                    FileMetadata(
                        missingFile.absolutePath,
                        missingFile.absolutePath,
                        1,
                        1,
                        "h2"
                    )
                ),
                duplicateGroups = emptyList()
            )
            repo.recordScan(result)

            val deleted = repo.deleteMissingByNormalizedPaths(
                listOf(existingFile.absolutePath, missingFile.absolutePath)
            )

            assertEquals(1, deleted)
            val merged = repo.loadMergedHistory()
            assertNotNull(merged)
            assertEquals(1, merged?.files?.size)
            assertEquals(existingFile.absolutePath, merged?.files?.first()?.normalizedPath)
        } finally {
            existingFile.delete()
            database.close()
        }
    }

    @Test
    fun deleteMissingAllRemovesMissingEntries() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, CacheDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val existingFile = File.createTempFile("cached", ".txt")
        val missingFile = File.createTempFile("cached", ".missing")
        try {
            existingFile.writeText("hello")
            missingFile.writeText("goodbye")
            missingFile.delete()

            val settings = AppSettingsStore(context)
            val repo = ScanHistoryRepository(database.fileCacheDao(), settings)
            val result = ScanResult(
                scannedAtMillis = 1,
                files = listOf(
                    FileMetadata(
                        existingFile.absolutePath,
                        existingFile.absolutePath,
                        existingFile.length(),
                        existingFile.lastModified(),
                        "h1"
                    ),
                    FileMetadata(
                        missingFile.absolutePath,
                        missingFile.absolutePath,
                        1,
                        1,
                        "h2"
                    )
                ),
                duplicateGroups = emptyList()
            )
            repo.recordScan(result)

            val deleted = repo.deleteMissingAll()

            assertEquals(1, deleted)
            val merged = repo.loadMergedHistory()
            assertNotNull(merged)
            assertEquals(1, merged?.files?.size)
            assertEquals(existingFile.absolutePath, merged?.files?.first()?.normalizedPath)
        } finally {
            existingFile.delete()
            database.close()
        }
    }

    @Test
    fun rehashIfChangedUpdatesHashAndMetadata() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, CacheDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val file = File.createTempFile("cached", ".data")
        try {
            file.writeText("alpha")
            val initialHash = Hashing.sha256Hex(file)

            val settings = AppSettingsStore(context)
            val repo = ScanHistoryRepository(database.fileCacheDao(), settings)
            val result = ScanResult(
                scannedAtMillis = 1,
                files = listOf(
                    FileMetadata(
                        file.absolutePath,
                        file.absolutePath,
                        file.length(),
                        file.lastModified(),
                        initialHash
                    )
                ),
                duplicateGroups = emptyList()
            )
            repo.recordScan(result)

            file.writeText("alpha beta")
            val updatedHash = Hashing.sha256Hex(file)

            val updated = repo.rehashIfChanged(listOf(file.absolutePath))

            assertEquals(1, updated)
            val entity = database.fileCacheDao().getByNormalizedPath(file.absolutePath)
            assertNotNull(entity)
            assertEquals(file.length(), entity?.sizeBytes)
            assertEquals(file.lastModified(), entity?.lastModifiedMillis)
            assertEquals(updatedHash, entity?.hashHex)
        } finally {
            file.delete()
            database.close()
        }
    }

    @Test
    fun rehashIfChangedAllUpdatesHashAndMetadata() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, CacheDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val file = File.createTempFile("cached", ".data")
        try {
            file.writeText("alpha")
            val initialHash = Hashing.sha256Hex(file)

            val settings = AppSettingsStore(context)
            val repo = ScanHistoryRepository(database.fileCacheDao(), settings)
            val result = ScanResult(
                scannedAtMillis = 1,
                files = listOf(
                    FileMetadata(
                        file.absolutePath,
                        file.absolutePath,
                        file.length(),
                        file.lastModified(),
                        initialHash
                    )
                ),
                duplicateGroups = emptyList()
            )
            repo.recordScan(result)

            file.writeText("alpha beta")
            val updatedHash = Hashing.sha256Hex(file)

            val updated = repo.rehashIfChangedAll()

            assertEquals(1, updated)
            val entity = database.fileCacheDao().getByNormalizedPath(file.absolutePath)
            assertNotNull(entity)
            assertEquals(file.length(), entity?.sizeBytes)
            assertEquals(file.lastModified(), entity?.lastModifiedMillis)
            assertEquals(updatedHash, entity?.hashHex)
        } finally {
            file.delete()
            database.close()
        }
    }

    @Test
    fun countAllReturnsEntryCount() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, CacheDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val settings = AppSettingsStore(context)
            val repo = ScanHistoryRepository(database.fileCacheDao(), settings)
            val result = ScanResult(
                scannedAtMillis = 1,
                files = listOf(
                    FileMetadata("/a", "/a", 1, 1, "h1"),
                    FileMetadata("/b", "/b", 2, 2, "h2")
                ),
                duplicateGroups = emptyList()
            )
            repo.recordScan(result)

            assertEquals(2, repo.countAll())
        } finally {
            database.close()
        }
    }

    @Test
    fun rehashMissingHashesAllUpdatesNullHashes() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, CacheDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val file = File.createTempFile("cached", ".data")
        try {
            file.writeText("alpha")
            val settings = AppSettingsStore(context)
            val repo = ScanHistoryRepository(database.fileCacheDao(), settings)
            val result = ScanResult(
                scannedAtMillis = 1,
                files = listOf(
                    FileMetadata(
                        file.absolutePath,
                        file.absolutePath,
                        file.length(),
                        file.lastModified(),
                        null
                    )
                ),
                duplicateGroups = emptyList()
            )
            repo.recordScan(result)

            val updated = repo.rehashMissingHashesAll()

            assertEquals(1, updated)
            val entity = database.fileCacheDao().getByNormalizedPath(file.absolutePath)
            assertNotNull(entity)
            assertEquals(Hashing.sha256Hex(file), entity?.hashHex)
        } finally {
            file.delete()
            database.close()
        }
    }
}
