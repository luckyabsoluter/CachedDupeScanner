package opensource.cached_dupe_scanner.cache

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

const val CACHE_DATABASE_NAME = "scan-cache.db"
internal const val DEFAULT_SQLITE_PAGE_CACHE_MIB = 100

val CACHE_DATABASE_MIGRATIONS: Array<Migration> = arrayOf(
    CacheMigrations.MIGRATION_1_3,
    CacheMigrations.MIGRATION_2_3,
    CacheMigrations.MIGRATION_3_4,
    CacheMigrations.MIGRATION_4_5,
    CacheMigrations.MIGRATION_5_6,
    CacheMigrations.MIGRATION_6_7,
    CacheMigrations.MIGRATION_7_8,
    CacheMigrations.MIGRATION_8_9,
    CacheMigrations.MIGRATION_9_10,
    CacheMigrations.MIGRATION_10_11,
    CacheMigrations.MIGRATION_11_12,
    CacheMigrations.MIGRATION_12_13,
    CacheMigrations.MIGRATION_13_14,
    CacheMigrations.MIGRATION_14_15,
    CacheMigrations.MIGRATION_15_16,
    CacheMigrations.MIGRATION_16_17,
    CacheMigrations.MIGRATION_17_18,
    CacheMigrations.MIGRATION_18_19,
    CacheMigrations.MIGRATION_19_20,
    CacheMigrations.MIGRATION_20_21,
    CacheMigrations.MIGRATION_21_22,
    CacheMigrations.MIGRATION_22_23,
    CacheMigrations.MIGRATION_23_24,
    CacheMigrations.MIGRATION_24_25,
    CacheMigrations.MIGRATION_25_26
)

fun buildCacheDatabase(
    context: Context,
    databaseName: String = CACHE_DATABASE_NAME,
    sqlitePageCacheMiB: Int = DEFAULT_SQLITE_PAGE_CACHE_MIB
): CacheDatabase {
    return Room.databaseBuilder(context, CacheDatabase::class.java, databaseName)
        .addMigrations(*CACHE_DATABASE_MIGRATIONS)
        .addCallback(
            SQLitePageCacheCallback(
                cacheMiB = sanitizeSqlitePageCacheMiB(sqlitePageCacheMiB)
            )
        )
        .build()
}

internal fun sanitizeSqlitePageCacheMiB(value: Int): Int {
    return value.coerceAtLeast(0)
}

internal fun sqlitePageCacheKiB(value: Int): Long {
    return sanitizeSqlitePageCacheMiB(value).toLong() * KIB_PER_MIB
}

private class SQLitePageCacheCallback(
    private val cacheMiB: Int
) : RoomDatabase.Callback() {
    override fun onOpen(db: SupportSQLiteDatabase) {
        configureSqlitePageCache(db, cacheMiB)
    }
}

private fun configureSqlitePageCache(
    database: SupportSQLiteDatabase,
    cacheMiB: Int
) {
    val cacheKiB = sqlitePageCacheKiB(cacheMiB)
    val cachePragma = "PRAGMA cache_size = ${if (cacheKiB == 0L) 0L else -cacheKiB}"
    if (database.isExecPerConnectionSQLSupported) {
        database.execPerConnectionSQL(cachePragma, null)
        return
    }

    // Before API 30, persist the default for future pool connections and update the current one.
    val pageSize = database.firstPragmaLong("PRAGMA page_size")
    val defaultPageCount = sqliteDefaultCachePageCount(cacheMiB, pageSize)
    if (database.firstPragmaLongOrNull("PRAGMA default_cache_size") != defaultPageCount) {
        database.execSQL("PRAGMA default_cache_size = $defaultPageCount")
    }
    database.execSQL(cachePragma)
}

private fun SupportSQLiteDatabase.firstPragmaLong(sql: String): Long {
    return checkNotNull(firstPragmaLongOrNull(sql)) {
        "SQLite pragma returned no value: $sql"
    }
}

private fun SupportSQLiteDatabase.firstPragmaLongOrNull(sql: String): Long? {
    return query(sql).use { cursor ->
        if (cursor.moveToFirst()) cursor.getLong(0) else null
    }
}

internal fun sqliteDefaultCachePageCount(cacheMiB: Int, pageSizeBytes: Long): Long {
    require(pageSizeBytes > 0L)
    if (cacheMiB <= 0) return 0L
    return (sqlitePageCacheKiB(cacheMiB) * BYTES_PER_KIB / pageSizeBytes).coerceAtLeast(1L)
}

private const val KIB_PER_MIB = 1024L
private const val BYTES_PER_KIB = 1024L
