package opensource.cached_dupe_scanner.cache

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object CacheMigrations {
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS scan_sessions (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    scannedAtMillis INTEGER NOT NULL
                )
                """.trimIndent()
            )
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS scan_files (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    scanSessionId INTEGER NOT NULL,
                    path TEXT NOT NULL,
                    normalizedPath TEXT NOT NULL,
                    sizeBytes INTEGER NOT NULL,
                    lastModifiedMillis INTEGER NOT NULL,
                    hashHex TEXT,
                    FOREIGN KEY(scanSessionId) REFERENCES scan_sessions(id) ON DELETE CASCADE
                )
                """.trimIndent()
            )
            database.execSQL("CREATE INDEX IF NOT EXISTS index_scan_files_scanSessionId ON scan_files(scanSessionId)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_scan_files_hashHex ON scan_files(hashHex)")
        }
    }
}
