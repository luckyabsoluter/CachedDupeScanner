package opensource.cached_dupe_scanner.cache

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class CacheDatabaseFactoryTest {
    @Test
    fun pageCacheSizeDefaultsToOneHundredMiBPerConnection() {
        assertEquals(100, DEFAULT_SQLITE_PAGE_CACHE_MIB)
        assertEquals(102_400L, sqlitePageCacheKiB(DEFAULT_SQLITE_PAGE_CACHE_MIB))
    }

    @Test
    fun pageCacheSizeKeepsRequestedNonnegativeValue() {
        assertEquals(0, sanitizeSqlitePageCacheMiB(Int.MIN_VALUE))
        assertEquals(
            Int.MAX_VALUE,
            sanitizeSqlitePageCacheMiB(Int.MAX_VALUE)
        )
        assertEquals(Int.MAX_VALUE.toLong() * 1024L, sqlitePageCacheKiB(Int.MAX_VALUE))
        assertEquals(0L, sqliteDefaultCachePageCount(0, 4096L))
        assertEquals(25_600L, sqliteDefaultCachePageCount(100, 4096L))
    }

    @Test
    @Config(sdk = [30])
    fun api30DatabaseAppliesConfiguredCacheToConnectionPool() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "cache-size-${UUID.randomUUID().toString().take(8)}.db"
        val database = buildCacheDatabase(
            context = context,
            databaseName = databaseName,
            sqlitePageCacheMiB = 64
        )

        try {
            val db = database.openHelper.writableDatabase

            assertEquals(-65_536L, firstLong(db, "PRAGMA cache_size"))
        } finally {
            database.close()
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    @Config(sdk = [28])
    fun preApi30DatabaseAppliesConfiguredCacheToCurrentConnection() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "cache-size-${UUID.randomUUID().toString().take(8)}.db"
        val database = buildCacheDatabase(
            context = context,
            databaseName = databaseName,
            sqlitePageCacheMiB = 100
        )

        try {
            val db = database.openHelper.writableDatabase
            val pageSize = firstLong(db, "PRAGMA page_size")

            assertEquals(-102_400L, firstLong(db, "PRAGMA cache_size"))
            firstLongOrNull(db, "PRAGMA default_cache_size")?.let { defaultCacheSize ->
                assertEquals(100L * 1024L * 1024L / pageSize, defaultCacheSize)
            }
        } finally {
            database.close()
            context.deleteDatabase(databaseName)
        }
    }

    private fun firstLong(
        db: androidx.sqlite.db.SupportSQLiteDatabase,
        sql: String
    ): Long {
        return checkNotNull(firstLongOrNull(db, sql))
    }

    private fun firstLongOrNull(
        db: androidx.sqlite.db.SupportSQLiteDatabase,
        sql: String
    ): Long? {
        db.query(sql).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getLong(0) else null
        }
    }
}
