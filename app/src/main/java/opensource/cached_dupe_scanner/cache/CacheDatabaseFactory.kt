package opensource.cached_dupe_scanner.cache

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration

const val CACHE_DATABASE_NAME = "scan-cache.db"

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
    CacheMigrations.MIGRATION_23_24
)

fun buildCacheDatabase(context: Context): CacheDatabase {
    return Room.databaseBuilder(context, CacheDatabase::class.java, CACHE_DATABASE_NAME)
        .addMigrations(*CACHE_DATABASE_MIGRATIONS)
        .build()
}
