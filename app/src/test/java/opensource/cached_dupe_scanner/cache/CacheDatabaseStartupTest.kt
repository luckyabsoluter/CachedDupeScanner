package opensource.cached_dupe_scanner.cache

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import java.io.RandomAccessFile
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import opensource.cached_dupe_scanner.storage.SimilaritySettingsRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CacheDatabaseStartupTest {
    @Test
    fun recoveryUpgradePreservesResultsWhenLegacyDerivedTablesAreCorrupt() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "r-${UUID.randomUUID().toString().take(8)}.db"
        val sourceFile = context.getDatabasePath(databaseName)
        sourceFile.parentFile?.mkdirs()
        val roots = createCorruptibleVersion21Database(sourceFile.absolutePath)
        roots.corrupt(sourceFile.absolutePath)

        val plan = inspectCacheDatabaseStartup(context, databaseName)

        assertEquals(
            CacheDatabaseStartupPlan.UpgradeRequired(
                fromVersion = 21,
                toVersion = CACHE_DATABASE_VERSION,
                recoveryRequired = true
            ),
            plan
        )
        assertEquals(21, readDatabaseVersion(sourceFile.absolutePath))

        val stages = mutableListOf<String>()
        val database = openCacheDatabaseForStartup(
            context = context,
            databaseName = databaseName,
            plan = plan,
            onProgress = stages::add
        )
        try {
            val db = database.openHelper.writableDatabase
            assertEquals(CACHE_DATABASE_VERSION, db.version)
            assertEquals(2, firstInt(db, "SELECT COUNT(*) FROM cached_files"))
            assertEquals(1, firstInt(db, "SELECT COUNT(*) FROM similarity_settings"))
            assertEquals(1, firstInt(db, "SELECT COUNT(*) FROM similarity_clusters"))
            assertEquals(2, firstInt(db, "SELECT COUNT(*) FROM similarity_cluster_members"))
            assertEquals(2, firstInt(db, "SELECT COUNT(*) FROM similarity_setting_files"))
            assertEquals(2, firstInt(db, "SELECT COUNT(*) FROM similarity_exact_thumbnail_features"))
            assertEquals(
                2,
                firstInt(
                    db,
                    "SELECT COUNT(*) FROM similarity_exact_thumbnail_features " +
                        "WHERE typeof(thumbnailHash) = 'blob' AND length(thumbnailHash) = 32"
                )
            )
            assertEquals(
                2,
                firstInt(
                    db,
                    "SELECT COUNT(*) FROM similarity_setting_files WHERE status = 'ready'"
                )
            )
            assertTrue(
                firstString(db, "SELECT clusterKey FROM similarity_clusters")
                    .startsWith("thumb-v2:video:color:1x1:raw:0:")
            )
            val repository = SimilaritySettingsRepository(
                database = database,
                fileDao = database.fileCacheDao(),
                similarityDao = database.similaritySettingsDao()
            )
            val recoveredMemberCount = runBlocking {
                withContext(Dispatchers.IO) {
                    val recoveredCluster = repository.listClusters(settingId = 1L).single()
                    repository.listClusterMembers(recoveredCluster.clusterId).size
                }
            }
            assertEquals(2, recoveredMemberCount)
            assertEquals("ok", firstString(db, "PRAGMA quick_check"))
        } finally {
            database.close()
            context.deleteDatabase(databaseName)
        }

        assertTrue(stages.contains("Recovering cached files"))
        assertTrue(stages.contains("Preserving similarity results"))
        assertFalse(context.getDatabasePath("$databaseName.upgrade-v24").exists())
        assertFalse(context.getDatabasePath("$databaseName.pre-v24").exists())
    }

    @Test
    fun recoveryFailureLeavesOriginalDatabaseInPlace() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "f-${UUID.randomUUID().toString().take(8)}.db"
        val sourceFile = context.getDatabasePath(databaseName)
        sourceFile.parentFile?.mkdirs()
        val roots = createCorruptibleVersion21Database(sourceFile.absolutePath)
        roots.corrupt(sourceFile.absolutePath, listOf(roots.cachedFilesRoot))
        val plan = inspectCacheDatabaseStartup(context, databaseName)

        assertThrows(Exception::class.java) {
            openCacheDatabaseForStartup(
                context = context,
                databaseName = databaseName,
                plan = plan
            )
        }

        assertTrue(sourceFile.exists())
        assertEquals(21, readDatabaseVersion(sourceFile.absolutePath))
        assertFalse(context.getDatabasePath("$databaseName.upgrade-v24").exists())
        assertFalse(context.getDatabasePath("$databaseName.pre-v24").exists())
        context.deleteDatabase(databaseName)
    }

    private fun createCorruptibleVersion21Database(path: String): CorruptibleRoots {
        val db = SQLiteDatabase.openOrCreateDatabase(path, null)
        try {
            db.execSQL(
                """
                CREATE TABLE cached_files (
                    normalizedPath TEXT NOT NULL PRIMARY KEY,
                    path TEXT NOT NULL,
                    sizeBytes INTEGER NOT NULL,
                    lastModifiedMillis INTEGER NOT NULL,
                    hashHex TEXT
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE similarity_settings (
                    settingId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    methodId TEXT NOT NULL,
                    mediaScope TEXT NOT NULL,
                    minSizeBytes INTEGER NOT NULL,
                    paramsJson TEXT NOT NULL,
                    paramsHash TEXT NOT NULL,
                    displayName TEXT NOT NULL,
                    enabled INTEGER NOT NULL,
                    createdAtMillis INTEGER NOT NULL,
                    updatedAtMillis INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE similarity_setting_files (
                    settingId INTEGER NOT NULL,
                    normalizedPath TEXT NOT NULL,
                    sizeBytes INTEGER NOT NULL,
                    lastModifiedMillis INTEGER NOT NULL,
                    status TEXT NOT NULL,
                    widthPixels INTEGER,
                    heightPixels INTEGER,
                    dimensionsChecked INTEGER NOT NULL DEFAULT 0,
                    durationChecked INTEGER NOT NULL DEFAULT 0,
                    updatedAtMillis INTEGER NOT NULL,
                    PRIMARY KEY(settingId, normalizedPath)
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE similarity_exact_thumbnail_features (
                    settingId INTEGER NOT NULL,
                    normalizedPath TEXT NOT NULL,
                    thumbnailSignature TEXT NOT NULL,
                    PRIMARY KEY(settingId, normalizedPath)
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE similarity_duration_features (
                    settingId INTEGER NOT NULL,
                    normalizedPath TEXT NOT NULL,
                    durationMillis INTEGER NOT NULL,
                    PRIMARY KEY(settingId, normalizedPath)
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE similarity_clusters (
                    clusterId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    settingId INTEGER NOT NULL,
                    clusterKey TEXT NOT NULL,
                    fileCount INTEGER NOT NULL,
                    totalBytes INTEGER NOT NULL,
                    updatedAtMillis INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE similarity_cluster_members (
                    clusterId INTEGER NOT NULL,
                    normalizedPath TEXT NOT NULL,
                    position INTEGER NOT NULL,
                    PRIMARY KEY(clusterId, normalizedPath)
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE similarity_maintenance_runs (
                    runId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    settingId INTEGER NOT NULL,
                    startedAtMillis INTEGER NOT NULL,
                    finishedAtMillis INTEGER NOT NULL,
                    candidateCount INTEGER NOT NULL,
                    processedCount INTEGER NOT NULL,
                    skippedCount INTEGER NOT NULL,
                    clusterCount INTEGER NOT NULL,
                    duplicateFileCount INTEGER NOT NULL,
                    cancelled INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO cached_files VALUES
                    ('/video/a.mp4', '/video/a.mp4', 10, 100, NULL),
                    ('/video/b.mp4', '/video/b.mp4', 10, 200, NULL)
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO similarity_settings VALUES (
                    1,
                    'exact-thumbnail',
                    'Video',
                    1,
                    '{"frameSeconds":[0],"resizeWidthPx":1,"resizeHeightPx":1,"quantizationLevels":null,"grayscale":false}',
                    'params',
                    'Exact thumbnail',
                    1,
                    1,
                    2
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO similarity_setting_files VALUES
                    (1, '/video/a.mp4', 10, 100, 'ready', NULL, NULL, 0, 0, 3),
                    (1, '/video/b.mp4', 10, 200, 'ready', NULL, NULL, 0, 0, 3)
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO similarity_exact_thumbnail_features VALUES
                    (1, '/video/a.mp4', 'thumb-v1:video:color:1x1:raw:0:ff0000'),
                    (1, '/video/b.mp4', 'thumb-v1:video:color:1x1:raw:0:ff0000')
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO similarity_clusters VALUES (
                    1,
                    1,
                    'thumb-v1:video:color:1x1:raw:0:ff0000',
                    2,
                    20,
                    4
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO similarity_cluster_members VALUES
                    (1, '/video/a.mp4', 0),
                    (1, '/video/b.mp4', 1)
                """.trimIndent()
            )
            db.version = 21
            return CorruptibleRoots(
                pageSize = firstInt(db, "PRAGMA page_size"),
                cachedFilesRoot = rootPage(db, "cached_files"),
                settingFilesRoot = rootPage(db, "similarity_setting_files"),
                exactFeaturesRoot = rootPage(db, "similarity_exact_thumbnail_features")
            )
        } finally {
            db.close()
        }
    }

    private fun CorruptibleRoots.corrupt(path: String) {
        corrupt(path, listOf(settingFilesRoot, exactFeaturesRoot))
    }

    private fun CorruptibleRoots.corrupt(path: String, rootPages: List<Int>) {
        RandomAccessFile(path, "rw").use { file ->
            rootPages.forEach { rootPage ->
                file.seek((rootPage - 1L) * pageSize)
                file.write(0xff)
            }
        }
    }

    private fun rootPage(db: SQLiteDatabase, tableName: String): Int {
        db.rawQuery(
            "SELECT rootpage FROM sqlite_master WHERE type = 'table' AND name = ?",
            arrayOf(tableName)
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            return cursor.getInt(0)
        }
    }

    private fun readDatabaseVersion(path: String): Int {
        val db = SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READONLY)
        return try {
            db.version
        } finally {
            db.close()
        }
    }

    private fun firstInt(db: SQLiteDatabase, sql: String): Int {
        db.rawQuery(sql, null).use { cursor ->
            assertTrue(cursor.moveToFirst())
            return cursor.getInt(0)
        }
    }

    private fun firstInt(db: androidx.sqlite.db.SupportSQLiteDatabase, sql: String): Int {
        db.query(sql).use { cursor ->
            assertTrue(cursor.moveToFirst())
            return cursor.getInt(0)
        }
    }

    private fun firstString(db: androidx.sqlite.db.SupportSQLiteDatabase, sql: String): String {
        db.query(sql).use { cursor ->
            assertTrue(cursor.moveToFirst())
            return cursor.getString(0)
        }
    }
}

private data class CorruptibleRoots(
    val pageSize: Int,
    val cachedFilesRoot: Int,
    val settingFilesRoot: Int,
    val exactFeaturesRoot: Int
)
