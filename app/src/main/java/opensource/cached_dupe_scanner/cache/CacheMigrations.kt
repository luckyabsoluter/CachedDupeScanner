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

    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS scan_reports (
                    id TEXT NOT NULL PRIMARY KEY,
                    startedAtMillis INTEGER NOT NULL,
                    finishedAtMillis INTEGER NOT NULL,
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
                CREATE TABLE IF NOT EXISTS scan_report_targets (
                    reportId TEXT NOT NULL,
                    target TEXT NOT NULL,
                    PRIMARY KEY(reportId, target)
                )
                """.trimIndent()
            )
        }
    }

    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // No-op: schema hash update only.
        }
    }
}
