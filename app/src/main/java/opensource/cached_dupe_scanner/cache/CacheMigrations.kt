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

    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            val hasPosition = db.query("PRAGMA table_info('scan_report_targets')").use { cursor ->
                var found = false
                val nameIndex = cursor.getColumnIndex("name")
                while (cursor.moveToNext()) {
                    if (nameIndex >= 0 && cursor.getString(nameIndex) == "position") {
                        found = true
                        break
                    }
                }
                found
            }

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS scan_report_targets_new (
                    reportId TEXT NOT NULL,
                    position INTEGER NOT NULL,
                    target TEXT NOT NULL,
                    PRIMARY KEY(reportId, position)
                )
                """.trimIndent()
            )

            if (hasPosition) {
                db.execSQL(
                    """
                    INSERT INTO scan_report_targets_new (reportId, position, target)
                    SELECT reportId, position, target FROM scan_report_targets
                    """.trimIndent()
                )
            } else {
                db.execSQL(
                    """
                    INSERT INTO scan_report_targets_new (reportId, position, target)
                    SELECT reportId, rowid, target FROM scan_report_targets
                    """.trimIndent()
                )
            }

            db.execSQL("DROP TABLE IF EXISTS scan_report_targets")
            db.execSQL("ALTER TABLE scan_report_targets_new RENAME TO scan_report_targets")
        }
    }

    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE scan_reports ADD COLUMN targetsText TEXT NOT NULL DEFAULT ''")
            db.execSQL("DROP TABLE IF EXISTS scan_report_targets")
        }
    }

    val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS trash_entries (
                    id TEXT NOT NULL PRIMARY KEY,
                    originalPath TEXT NOT NULL,
                    trashedPath TEXT NOT NULL,
                    sizeBytes INTEGER NOT NULL,
                    lastModifiedMillis INTEGER NOT NULL,
                    deletedAtMillis INTEGER NOT NULL,
                    volumeRoot TEXT NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_trash_entries_deletedAtMillis ON trash_entries(deletedAtMillis)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_trash_entries_originalPath ON trash_entries(originalPath)")
        }
    }

    val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE trash_entries ADD COLUMN hashHex TEXT")
        }
    }

    val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE INDEX IF NOT EXISTS index_cached_files_sizeBytes ON cached_files(sizeBytes)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_scan_reports_startedAtMillis ON scan_reports(startedAtMillis)")
        }
    }
}
