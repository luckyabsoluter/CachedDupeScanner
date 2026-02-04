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

    private fun hasIndex(db: SupportSQLiteDatabase, table: String, indexName: String): Boolean {
        db.query("PRAGMA index_list('$table')").use { cursor ->
            val nameIdx = cursor.getColumnIndex("name")
            while (cursor.moveToNext()) {
                if (nameIdx >= 0 && cursor.getString(nameIdx) == indexName) return true
            }
            return false
        }
    }
}
