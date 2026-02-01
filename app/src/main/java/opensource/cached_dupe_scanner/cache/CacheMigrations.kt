package opensource.cached_dupe_scanner.cache

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object CacheMigrations {
    val MIGRATION_1_3 = object : Migration(1, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // No-op: cached_files already exists in v1
        }
    }

    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DROP TABLE IF EXISTS scan_files")
            db.execSQL("DROP TABLE IF EXISTS scan_sessions")
        }
    }
}
