package opensource.cached_dupe_scanner.cache

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class CacheMigrationsIndexTest {
    @Test
    fun migration9to10CreatesHotQueryIndexes() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "index-test-${UUID.randomUUID()}.db"

        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(name)
            .callback(
                object : SupportSQLiteOpenHelper.Callback(9) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        // Minimal schema required for MIGRATION_9_10
                        db.execSQL(
                            """
                            CREATE TABLE IF NOT EXISTS cached_files (
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
                            CREATE TABLE IF NOT EXISTS scan_reports (
                                id TEXT NOT NULL PRIMARY KEY,
                                startedAtMillis INTEGER NOT NULL,
                                finishedAtMillis INTEGER NOT NULL,
                                targetsText TEXT NOT NULL,
                                mode TEXT NOT NULL,
                                cancelled INTEGER NOT NULL,
                                collectedCount INTEGER NOT NULL,
                                detectedCount INTEGER NOT NULL,
                                hashCandidates INTEGER NOT NULL,
                                hashesComputed INTEGER NOT NULL,
                                collectingMillis INTEGER NOT NULL,
                                detectingMillis INTEGER NOT NULL,
                                hashingMillis INTEGER NOT NULL
                            )
                            """.trimIndent()
                        )
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                }
            )
            .build()

        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        val db = helper.writableDatabase
        try {
            CacheMigrations.MIGRATION_9_10.migrate(db)

            assertTrue(hasIndex(db, "cached_files", "index_cached_files_sizeBytes"))
            assertTrue(hasIndex(db, "scan_reports", "index_scan_reports_startedAtMillis"))
        } finally {
            helper.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun migration10to11CreatesDupeGroupCacheAndIndexes() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "dupe-groups-test-${UUID.randomUUID()}.db"

        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(name)
            .callback(
                object : SupportSQLiteOpenHelper.Callback(10) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        // Minimal schema required for MIGRATION_10_11
                        db.execSQL(
                            """
                            CREATE TABLE IF NOT EXISTS cached_files (
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
                            CREATE TABLE IF NOT EXISTS scan_reports (
                                id TEXT NOT NULL PRIMARY KEY,
                                startedAtMillis INTEGER NOT NULL,
                                finishedAtMillis INTEGER NOT NULL,
                                targetsText TEXT NOT NULL,
                                mode TEXT NOT NULL,
                                cancelled INTEGER NOT NULL,
                                collectedCount INTEGER NOT NULL,
                                detectedCount INTEGER NOT NULL,
                                hashCandidates INTEGER NOT NULL,
                                hashesComputed INTEGER NOT NULL,
                                collectingMillis INTEGER NOT NULL,
                                detectingMillis INTEGER NOT NULL,
                                hashingMillis INTEGER NOT NULL
                            )
                            """.trimIndent()
                        )
                        db.execSQL(
                            """
                            CREATE TABLE IF NOT EXISTS trash_entries (
                                id TEXT NOT NULL PRIMARY KEY,
                                originalPath TEXT NOT NULL,
                                trashedPath TEXT NOT NULL,
                                sizeBytes INTEGER NOT NULL,
                                lastModifiedMillis INTEGER NOT NULL,
                                hashHex TEXT,
                                deletedAtMillis INTEGER NOT NULL,
                                volumeRoot TEXT NOT NULL
                            )
                            """.trimIndent()
                        )
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                }
            )
            .build()

        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        val db = helper.writableDatabase
        try {
            CacheMigrations.MIGRATION_10_11.migrate(db)

            assertTrue(hasTable(db, "dupe_groups"))
            assertTrue(hasIndex(db, "dupe_groups", "index_dupe_groups_fileCount"))
            assertTrue(hasIndex(db, "dupe_groups", "index_dupe_groups_totalBytes"))

            assertTrue(hasIndex(db, "cached_files", "index_cached_files_hashHex"))
            assertTrue(hasIndex(db, "cached_files", "index_cached_files_sizeBytes_hashHex"))
            assertTrue(hasIndex(db, "cached_files", "index_cached_files_sizeBytes_normalizedPath"))
            assertTrue(hasIndex(db, "cached_files", "index_cached_files_lastModifiedMillis_normalizedPath"))
        } finally {
            helper.close()
            context.deleteDatabase(name)
        }
    }

    private fun hasIndex(db: SupportSQLiteDatabase, table: String, indexName: String): Boolean {
        db.query("PRAGMA index_list('$table')").use { cursor ->
            val nameIdx = cursor.getColumnIndex("name")
            while (cursor.moveToNext()) {
                if (nameIdx >= 0 && cursor.getString(nameIdx) == indexName) return true
            }
            return false
        }
    }

    private fun hasTable(db: SupportSQLiteDatabase, tableName: String): Boolean {
        db.query("SELECT name FROM sqlite_master WHERE type='table' AND name=?", arrayOf(tableName)).use { cursor ->
            return cursor.moveToFirst()
        }
    }
}
