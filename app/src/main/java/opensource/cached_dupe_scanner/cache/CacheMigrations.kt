package opensource.cached_dupe_scanner.cache

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import opensource.cached_dupe_scanner.core.isSha256HashHex
import opensource.cached_dupe_scanner.core.thumbnailHashClusterKeyFromLegacyPayload

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

    val MIGRATION_10_11 = object : Migration(10, 11) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS dupe_groups (
                    sizeBytes INTEGER NOT NULL,
                    hashHex TEXT NOT NULL,
                    fileCount INTEGER NOT NULL,
                    totalBytes INTEGER NOT NULL,
                    updatedAtMillis INTEGER NOT NULL,
                    PRIMARY KEY(sizeBytes, hashHex)
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_dupe_groups_fileCount ON dupe_groups(fileCount)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_dupe_groups_totalBytes ON dupe_groups(totalBytes)")

            db.execSQL("CREATE INDEX IF NOT EXISTS index_cached_files_hashHex ON cached_files(hashHex)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_cached_files_sizeBytes_hashHex ON cached_files(sizeBytes, hashHex)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_cached_files_sizeBytes_normalizedPath ON cached_files(sizeBytes, normalizedPath)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_cached_files_lastModifiedMillis_normalizedPath ON cached_files(lastModifiedMillis, normalizedPath)")
        }
    }

    val MIGRATION_11_12 = object : Migration(11, 12) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_trash_entries_deletedAtMillis_id ON trash_entries(deletedAtMillis, id)"
            )
        }
    }

    val MIGRATION_12_13 = object : Migration(12, 13) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS similarity_experiment_runs (
                    experimentId TEXT NOT NULL PRIMARY KEY,
                    experimentName TEXT NOT NULL,
                    startedAtMillis INTEGER NOT NULL,
                    finishedAtMillis INTEGER NOT NULL,
                    candidateCount INTEGER NOT NULL,
                    processedCount INTEGER NOT NULL,
                    skippedCount INTEGER NOT NULL,
                    clusterCount INTEGER NOT NULL,
                    duplicateFileCount INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS similarity_clusters (
                    experimentId TEXT NOT NULL,
                    signature TEXT NOT NULL,
                    fileCount INTEGER NOT NULL,
                    totalBytes INTEGER NOT NULL,
                    memberPathsText TEXT NOT NULL,
                    updatedAtMillis INTEGER NOT NULL,
                    PRIMARY KEY(experimentId, signature)
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_similarity_clusters_experimentId ON similarity_clusters(experimentId)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_similarity_clusters_fileCount ON similarity_clusters(fileCount)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_similarity_clusters_totalBytes ON similarity_clusters(totalBytes)"
            )
        }
    }

    val MIGRATION_13_14 = object : Migration(13, 14) {
        override fun migrate(db: SupportSQLiteDatabase) {
            val columns = tableColumns(db, "similarity_clusters")
            if (columns.contains("memberNormalizedPathsText")) {
                return
            }
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS similarity_clusters_new (
                    experimentId TEXT NOT NULL,
                    signature TEXT NOT NULL,
                    fileCount INTEGER NOT NULL,
                    totalBytes INTEGER NOT NULL,
                    memberNormalizedPathsText TEXT NOT NULL,
                    updatedAtMillis INTEGER NOT NULL,
                    PRIMARY KEY(experimentId, signature)
                )
                """.trimIndent()
            )
            if (columns.contains("memberPathsText")) {
                db.execSQL(
                    """
                    INSERT INTO similarity_clusters_new (
                        experimentId,
                        signature,
                        fileCount,
                        totalBytes,
                        memberNormalizedPathsText,
                        updatedAtMillis
                    )
                    SELECT
                        experimentId,
                        signature,
                        fileCount,
                        totalBytes,
                        memberPathsText,
                        updatedAtMillis
                    FROM similarity_clusters
                    """.trimIndent()
                )
            }
            db.execSQL("DROP TABLE IF EXISTS similarity_clusters")
            db.execSQL("ALTER TABLE similarity_clusters_new RENAME TO similarity_clusters")
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_similarity_clusters_experimentId ON similarity_clusters(experimentId)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_similarity_clusters_fileCount ON similarity_clusters(fileCount)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_similarity_clusters_totalBytes ON similarity_clusters(totalBytes)"
            )
        }
    }

    val MIGRATION_14_15 = object : Migration(14, 15) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS similarity_duration_candidates (
                    experimentId TEXT NOT NULL,
                    normalizedPath TEXT NOT NULL,
                    durationMillis INTEGER NOT NULL,
                    sizeBytes INTEGER NOT NULL,
                    updatedAtMillis INTEGER NOT NULL,
                    PRIMARY KEY(experimentId, normalizedPath)
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE INDEX IF NOT EXISTS index_similarity_duration_candidates_experimentId_durationMillis_normalizedPath
                ON similarity_duration_candidates(experimentId, durationMillis, normalizedPath)
                """.trimIndent()
            )
        }
    }

    val MIGRATION_15_16 = object : Migration(15, 16) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_scan_reports_startedAtMillis_id ON scan_reports(startedAtMillis, id)"
            )
        }
    }

    val MIGRATION_16_17 = object : Migration(16, 17) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS similarity_cluster_members (
                    experimentId TEXT NOT NULL,
                    signature TEXT NOT NULL,
                    normalizedPath TEXT NOT NULL,
                    position INTEGER NOT NULL,
                    durationMillis INTEGER,
                    PRIMARY KEY(experimentId, signature, normalizedPath)
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE INDEX IF NOT EXISTS index_similarity_cluster_members_cluster_position
                ON similarity_cluster_members(experimentId, signature, position)
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE INDEX IF NOT EXISTS index_similarity_cluster_members_normalizedPath
                ON similarity_cluster_members(normalizedPath)
                """.trimIndent()
            )

            val columns = tableColumns(db, "similarity_clusters")
            if (columns.contains("memberNormalizedPathsText")) {
                db.query(
                    """
                    SELECT experimentId, signature, memberNormalizedPathsText
                    FROM similarity_clusters
                    """.trimIndent()
                ).use { cursor ->
                    val experimentIdx = cursor.getColumnIndex("experimentId")
                    val signatureIdx = cursor.getColumnIndex("signature")
                    val membersIdx = cursor.getColumnIndex("memberNormalizedPathsText")
                    while (cursor.moveToNext()) {
                        val experimentId = cursor.getString(experimentIdx)
                        val signature = cursor.getString(signatureIdx)
                        val memberText = cursor.getString(membersIdx)
                        var position = 0
                        parseSimilarityClusterMemberText(memberText).forEach { member ->
                            db.execSQL(
                                """
                                INSERT OR IGNORE INTO similarity_cluster_members (
                                    experimentId,
                                    signature,
                                    normalizedPath,
                                    position,
                                    durationMillis
                                ) VALUES (?, ?, ?, ?, ?)
                                """.trimIndent(),
                                arrayOf<Any?>(
                                    experimentId,
                                    signature,
                                    member.normalizedPath,
                                    position,
                                    member.durationMillis
                                )
                            )
                            position += 1
                        }
                    }
                }

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS similarity_clusters_new (
                        experimentId TEXT NOT NULL,
                        signature TEXT NOT NULL,
                        fileCount INTEGER NOT NULL,
                        totalBytes INTEGER NOT NULL,
                        updatedAtMillis INTEGER NOT NULL,
                        PRIMARY KEY(experimentId, signature)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO similarity_clusters_new (
                        experimentId,
                        signature,
                        fileCount,
                        totalBytes,
                        updatedAtMillis
                    )
                    SELECT
                        experimentId,
                        signature,
                        fileCount,
                        totalBytes,
                        updatedAtMillis
                    FROM similarity_clusters
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE IF EXISTS similarity_clusters")
                db.execSQL("ALTER TABLE similarity_clusters_new RENAME TO similarity_clusters")
            }

            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_similarity_clusters_experimentId ON similarity_clusters(experimentId)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_similarity_clusters_fileCount ON similarity_clusters(fileCount)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_similarity_clusters_totalBytes ON similarity_clusters(totalBytes)"
            )
        }
    }

    val MIGRATION_17_18 = object : Migration(17, 18) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DROP TABLE IF EXISTS similarity_experiment_runs")
            db.execSQL("DROP TABLE IF EXISTS similarity_duration_candidates")
            db.execSQL("DROP TABLE IF EXISTS similarity_cluster_members")
            db.execSQL("DROP TABLE IF EXISTS similarity_clusters")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS similarity_settings (
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
                CREATE UNIQUE INDEX IF NOT EXISTS index_similarity_settings_identity
                ON similarity_settings(methodId, mediaScope, minSizeBytes, paramsHash)
                """.trimIndent()
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_similarity_settings_enabled ON similarity_settings(enabled)"
            )

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS similarity_setting_files (
                    settingId INTEGER NOT NULL,
                    normalizedPath TEXT NOT NULL,
                    sizeBytes INTEGER NOT NULL,
                    lastModifiedMillis INTEGER NOT NULL,
                    status TEXT NOT NULL,
                    updatedAtMillis INTEGER NOT NULL,
                    PRIMARY KEY(settingId, normalizedPath)
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE INDEX IF NOT EXISTS index_similarity_setting_files_normalizedPath
                ON similarity_setting_files(normalizedPath)
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE INDEX IF NOT EXISTS index_similarity_setting_files_settingId_status
                ON similarity_setting_files(settingId, status)
                """.trimIndent()
            )

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS similarity_exact_thumbnail_features (
                    settingId INTEGER NOT NULL,
                    normalizedPath TEXT NOT NULL,
                    thumbnailSignature TEXT NOT NULL,
                    PRIMARY KEY(settingId, normalizedPath)
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE INDEX IF NOT EXISTS index_similarity_exact_thumbnail_features_signature
                ON similarity_exact_thumbnail_features(settingId, thumbnailSignature, normalizedPath)
                """.trimIndent()
            )

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS similarity_duration_features (
                    settingId INTEGER NOT NULL,
                    normalizedPath TEXT NOT NULL,
                    durationMillis INTEGER NOT NULL,
                    PRIMARY KEY(settingId, normalizedPath)
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE INDEX IF NOT EXISTS index_similarity_duration_features_duration
                ON similarity_duration_features(settingId, durationMillis, normalizedPath)
                """.trimIndent()
            )

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS similarity_clusters (
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
                CREATE UNIQUE INDEX IF NOT EXISTS index_similarity_clusters_settingId_clusterKey
                ON similarity_clusters(settingId, clusterKey)
                """.trimIndent()
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_similarity_clusters_settingId ON similarity_clusters(settingId)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_similarity_clusters_fileCount ON similarity_clusters(fileCount)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_similarity_clusters_totalBytes ON similarity_clusters(totalBytes)"
            )

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS similarity_cluster_members (
                    clusterId INTEGER NOT NULL,
                    normalizedPath TEXT NOT NULL,
                    position INTEGER NOT NULL,
                    PRIMARY KEY(clusterId, normalizedPath)
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE INDEX IF NOT EXISTS index_similarity_cluster_members_clusterId_position
                ON similarity_cluster_members(clusterId, position)
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE INDEX IF NOT EXISTS index_similarity_cluster_members_normalizedPath
                ON similarity_cluster_members(normalizedPath)
                """.trimIndent()
            )

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS similarity_maintenance_runs (
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
                CREATE INDEX IF NOT EXISTS index_similarity_maintenance_runs_setting_started
                ON similarity_maintenance_runs(settingId, startedAtMillis)
                """.trimIndent()
            )
        }
    }

    val MIGRATION_18_19 = object : Migration(18, 19) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE INDEX IF NOT EXISTS index_similarity_clusters_setting_file_count_sort
                ON similarity_clusters(settingId, fileCount, totalBytes, clusterKey)
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE INDEX IF NOT EXISTS index_similarity_clusters_setting_total_size_sort
                ON similarity_clusters(settingId, totalBytes, fileCount, clusterKey)
                """.trimIndent()
            )
        }
    }

    val MIGRATION_19_20 = object : Migration(19, 20) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE similarity_setting_files ADD COLUMN widthPixels INTEGER")
            db.execSQL("ALTER TABLE similarity_setting_files ADD COLUMN heightPixels INTEGER")
            db.execSQL(
                "ALTER TABLE similarity_setting_files " +
                    "ADD COLUMN dimensionsChecked INTEGER NOT NULL DEFAULT 0"
            )
        }
    }

    val MIGRATION_20_21 = object : Migration(20, 21) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE similarity_setting_files " +
                    "ADD COLUMN durationChecked INTEGER NOT NULL DEFAULT 0"
            )
            db.execSQL(
                """
                UPDATE similarity_setting_files
                SET durationChecked = 1
                WHERE EXISTS (
                    SELECT 1
                    FROM similarity_duration_features AS duration
                    WHERE duration.settingId = similarity_setting_files.settingId
                      AND duration.normalizedPath = similarity_setting_files.normalizedPath
                )
                """.trimIndent()
            )
        }
    }

    val MIGRATION_21_22 = object : Migration(21, 22) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE cached_files RENAME TO cached_files_path_key")
            db.execSQL(
                """
                CREATE TABLE cached_files (
                    fileId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    normalizedPath TEXT NOT NULL,
                    path TEXT NOT NULL,
                    sizeBytes INTEGER NOT NULL,
                    lastModifiedMillis INTEGER NOT NULL,
                    hashHex TEXT
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO cached_files (
                    fileId,
                    normalizedPath,
                    path,
                    sizeBytes,
                    lastModifiedMillis,
                    hashHex
                )
                SELECT
                    rowid,
                    normalizedPath,
                    path,
                    sizeBytes,
                    lastModifiedMillis,
                    hashHex
                FROM cached_files_path_key
                ORDER BY rowid ASC
                """.trimIndent()
            )
            db.execSQL(
                "CREATE UNIQUE INDEX index_cached_files_normalizedPath " +
                    "ON cached_files(normalizedPath)"
            )

            db.execSQL(
                """
                CREATE TABLE similarity_setting_files_file_id (
                    settingId INTEGER NOT NULL,
                    fileId INTEGER NOT NULL,
                    sizeBytes INTEGER NOT NULL,
                    lastModifiedMillis INTEGER NOT NULL,
                    status TEXT NOT NULL,
                    widthPixels INTEGER,
                    heightPixels INTEGER,
                    dimensionsChecked INTEGER NOT NULL DEFAULT 0,
                    durationChecked INTEGER NOT NULL DEFAULT 0,
                    updatedAtMillis INTEGER NOT NULL,
                    PRIMARY KEY(settingId, fileId),
                    FOREIGN KEY(fileId) REFERENCES cached_files(fileId) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO similarity_setting_files_file_id (
                    settingId,
                    fileId,
                    sizeBytes,
                    lastModifiedMillis,
                    status,
                    widthPixels,
                    heightPixels,
                    dimensionsChecked,
                    durationChecked,
                    updatedAtMillis
                )
                SELECT
                    setting_file.settingId,
                    file.fileId,
                    setting_file.sizeBytes,
                    setting_file.lastModifiedMillis,
                    setting_file.status,
                    setting_file.widthPixels,
                    setting_file.heightPixels,
                    setting_file.dimensionsChecked,
                    setting_file.durationChecked,
                    setting_file.updatedAtMillis
                FROM similarity_setting_files AS setting_file
                INNER JOIN cached_files AS file
                    ON file.normalizedPath = setting_file.normalizedPath
                """.trimIndent()
            )

            db.execSQL(
                """
                CREATE TABLE similarity_exact_thumbnail_features_file_id (
                    settingId INTEGER NOT NULL,
                    fileId INTEGER NOT NULL,
                    thumbnailSignature TEXT NOT NULL,
                    PRIMARY KEY(settingId, fileId),
                    FOREIGN KEY(fileId) REFERENCES cached_files(fileId) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO similarity_exact_thumbnail_features_file_id (
                    settingId,
                    fileId,
                    thumbnailSignature
                )
                SELECT
                    feature.settingId,
                    file.fileId,
                    feature.thumbnailSignature
                FROM similarity_exact_thumbnail_features AS feature
                INNER JOIN cached_files AS file
                    ON file.normalizedPath = feature.normalizedPath
                """.trimIndent()
            )

            db.execSQL(
                """
                CREATE TABLE similarity_duration_features_file_id (
                    settingId INTEGER NOT NULL,
                    fileId INTEGER NOT NULL,
                    durationMillis INTEGER NOT NULL,
                    PRIMARY KEY(settingId, fileId),
                    FOREIGN KEY(fileId) REFERENCES cached_files(fileId) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO similarity_duration_features_file_id (
                    settingId,
                    fileId,
                    durationMillis
                )
                SELECT
                    feature.settingId,
                    file.fileId,
                    feature.durationMillis
                FROM similarity_duration_features AS feature
                INNER JOIN cached_files AS file
                    ON file.normalizedPath = feature.normalizedPath
                """.trimIndent()
            )

            db.execSQL(
                """
                CREATE TABLE similarity_cluster_members_file_id (
                    clusterId INTEGER NOT NULL,
                    fileId INTEGER NOT NULL,
                    position INTEGER NOT NULL,
                    PRIMARY KEY(clusterId, fileId),
                    FOREIGN KEY(fileId) REFERENCES cached_files(fileId) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO similarity_cluster_members_file_id (
                    clusterId,
                    fileId,
                    position
                )
                SELECT
                    member.clusterId,
                    file.fileId,
                    member.position
                FROM similarity_cluster_members AS member
                INNER JOIN cached_files AS file
                    ON file.normalizedPath = member.normalizedPath
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE INDEX index_similarity_cluster_members_file_id_migration_clusterId
                ON similarity_cluster_members_file_id(clusterId)
                """.trimIndent()
            )

            db.execSQL(
                """
                UPDATE similarity_clusters
                SET fileCount = (
                        SELECT COUNT(*)
                        FROM similarity_cluster_members_file_id AS member
                        WHERE member.clusterId = similarity_clusters.clusterId
                    ),
                    totalBytes = (
                        SELECT COALESCE(SUM(file.sizeBytes), 0)
                        FROM similarity_cluster_members_file_id AS member
                        INNER JOIN cached_files AS file
                            ON file.fileId = member.fileId
                        WHERE member.clusterId = similarity_clusters.clusterId
                    )
                """.trimIndent()
            )
            db.execSQL(
                """
                DELETE FROM similarity_cluster_members_file_id
                WHERE clusterId IN (
                    SELECT clusterId
                    FROM similarity_clusters
                    WHERE fileCount <= 1
                )
                """.trimIndent()
            )
            db.execSQL("DELETE FROM similarity_clusters WHERE fileCount <= 1")

            db.execSQL("DROP TABLE similarity_setting_files")
            db.execSQL("DROP TABLE similarity_exact_thumbnail_features")
            db.execSQL("DROP TABLE similarity_duration_features")
            db.execSQL("DROP TABLE similarity_cluster_members")
            db.execSQL("ALTER TABLE similarity_setting_files_file_id RENAME TO similarity_setting_files")
            db.execSQL(
                "ALTER TABLE similarity_exact_thumbnail_features_file_id " +
                    "RENAME TO similarity_exact_thumbnail_features"
            )
            db.execSQL(
                "ALTER TABLE similarity_duration_features_file_id " +
                    "RENAME TO similarity_duration_features"
            )
            db.execSQL("ALTER TABLE similarity_cluster_members_file_id RENAME TO similarity_cluster_members")
            db.execSQL("DROP TABLE cached_files_path_key")

            db.execSQL("CREATE INDEX index_cached_files_sizeBytes ON cached_files(sizeBytes)")
            db.execSQL("CREATE INDEX index_cached_files_hashHex ON cached_files(hashHex)")
            db.execSQL(
                "CREATE INDEX index_cached_files_sizeBytes_hashHex " +
                    "ON cached_files(sizeBytes, hashHex)"
            )
            db.execSQL(
                "CREATE INDEX index_cached_files_sizeBytes_normalizedPath " +
                    "ON cached_files(sizeBytes, normalizedPath)"
            )
            db.execSQL(
                "CREATE INDEX index_cached_files_lastModifiedMillis_normalizedPath " +
                    "ON cached_files(lastModifiedMillis, normalizedPath)"
            )
            db.execSQL(
                "CREATE INDEX index_similarity_setting_files_fileId " +
                    "ON similarity_setting_files(fileId)"
            )
            db.execSQL(
                "CREATE INDEX index_similarity_setting_files_settingId_status " +
                    "ON similarity_setting_files(settingId, status)"
            )
            db.execSQL(
                """
                CREATE INDEX index_similarity_exact_thumbnail_features_signature
                ON similarity_exact_thumbnail_features(settingId, thumbnailSignature, fileId)
                """.trimIndent()
            )
            db.execSQL(
                "CREATE INDEX index_similarity_exact_thumbnail_features_fileId " +
                    "ON similarity_exact_thumbnail_features(fileId)"
            )
            db.execSQL(
                """
                CREATE INDEX index_similarity_duration_features_duration
                ON similarity_duration_features(settingId, durationMillis, fileId)
                """.trimIndent()
            )
            db.execSQL(
                "CREATE INDEX index_similarity_duration_features_fileId " +
                    "ON similarity_duration_features(fileId)"
            )
            db.execSQL(
                """
                CREATE INDEX index_similarity_cluster_members_clusterId_position
                ON similarity_cluster_members(clusterId, position)
                """.trimIndent()
            )
            db.execSQL("DROP INDEX index_similarity_cluster_members_file_id_migration_clusterId")
            db.execSQL(
                "CREATE INDEX index_similarity_cluster_members_fileId " +
                    "ON similarity_cluster_members(fileId)"
            )
        }
    }

    val MIGRATION_22_23 = object : Migration(22, 23) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE cached_files RENAME TO cached_files_hash_text")
            db.execSQL(
                """
                CREATE TABLE cached_files (
                    fileId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    normalizedPath TEXT NOT NULL,
                    path TEXT NOT NULL,
                    sizeBytes INTEGER NOT NULL,
                    lastModifiedMillis INTEGER NOT NULL,
                    hashBytes BLOB
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO cached_files (
                    fileId,
                    normalizedPath,
                    path,
                    sizeBytes,
                    lastModifiedMillis,
                    hashBytes
                )
                SELECT
                    fileId,
                    normalizedPath,
                    path,
                    sizeBytes,
                    lastModifiedMillis,
                    NULL
                FROM cached_files_hash_text
                ORDER BY fileId ASC
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE UNIQUE INDEX index_cached_files_hash_blob_migration_normalizedPath
                ON cached_files(normalizedPath)
                """.trimIndent()
            )
            copyCachedFileHashesToBlob(db)
            db.execSQL(
                """
                CREATE INDEX index_cached_files_hash_blob_migration_size_hash
                ON cached_files(sizeBytes, hashBytes)
                """.trimIndent()
            )

            db.execSQL(
                """
                CREATE TABLE similarity_setting_files_hash_blob (
                    settingId INTEGER NOT NULL,
                    fileId INTEGER NOT NULL,
                    sizeBytes INTEGER NOT NULL,
                    lastModifiedMillis INTEGER NOT NULL,
                    status TEXT NOT NULL,
                    widthPixels INTEGER,
                    heightPixels INTEGER,
                    dimensionsChecked INTEGER NOT NULL DEFAULT 0,
                    durationChecked INTEGER NOT NULL DEFAULT 0,
                    updatedAtMillis INTEGER NOT NULL,
                    PRIMARY KEY(settingId, fileId),
                    FOREIGN KEY(fileId) REFERENCES cached_files(fileId) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO similarity_setting_files_hash_blob (
                    settingId,
                    fileId,
                    sizeBytes,
                    lastModifiedMillis,
                    status,
                    widthPixels,
                    heightPixels,
                    dimensionsChecked,
                    durationChecked,
                    updatedAtMillis
                )
                SELECT
                    settingId,
                    fileId,
                    sizeBytes,
                    lastModifiedMillis,
                    status,
                    widthPixels,
                    heightPixels,
                    dimensionsChecked,
                    durationChecked,
                    updatedAtMillis
                FROM similarity_setting_files
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE similarity_exact_thumbnail_features_hash_blob (
                    settingId INTEGER NOT NULL,
                    fileId INTEGER NOT NULL,
                    thumbnailSignature TEXT NOT NULL,
                    PRIMARY KEY(settingId, fileId),
                    FOREIGN KEY(fileId) REFERENCES cached_files(fileId) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO similarity_exact_thumbnail_features_hash_blob (
                    settingId,
                    fileId,
                    thumbnailSignature
                )
                SELECT settingId, fileId, thumbnailSignature
                FROM similarity_exact_thumbnail_features
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE similarity_duration_features_hash_blob (
                    settingId INTEGER NOT NULL,
                    fileId INTEGER NOT NULL,
                    durationMillis INTEGER NOT NULL,
                    PRIMARY KEY(settingId, fileId),
                    FOREIGN KEY(fileId) REFERENCES cached_files(fileId) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO similarity_duration_features_hash_blob (
                    settingId,
                    fileId,
                    durationMillis
                )
                SELECT settingId, fileId, durationMillis
                FROM similarity_duration_features
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE similarity_cluster_members_hash_blob (
                    clusterId INTEGER NOT NULL,
                    fileId INTEGER NOT NULL,
                    position INTEGER NOT NULL,
                    PRIMARY KEY(clusterId, fileId),
                    FOREIGN KEY(fileId) REFERENCES cached_files(fileId) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO similarity_cluster_members_hash_blob (
                    clusterId,
                    fileId,
                    position
                )
                SELECT clusterId, fileId, position
                FROM similarity_cluster_members
                """.trimIndent()
            )

            db.execSQL(
                """
                CREATE TABLE dupe_groups_hash_blob (
                    sizeBytes INTEGER NOT NULL,
                    hashBytes BLOB NOT NULL,
                    fileCount INTEGER NOT NULL,
                    totalBytes INTEGER NOT NULL,
                    updatedAtMillis INTEGER NOT NULL,
                    PRIMARY KEY(sizeBytes, hashBytes)
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO dupe_groups_hash_blob (
                    sizeBytes,
                    hashBytes,
                    fileCount,
                    totalBytes,
                    updatedAtMillis
                )
                SELECT
                    sizeBytes,
                    hashBytes,
                    COUNT(*),
                    COUNT(*) * sizeBytes,
                    (SELECT COALESCE(MAX(updatedAtMillis), 0) FROM dupe_groups)
                FROM cached_files
                WHERE hashBytes IS NOT NULL
                  AND EXISTS (SELECT 1 FROM dupe_groups)
                GROUP BY sizeBytes, hashBytes
                HAVING COUNT(*) > 1
                """.trimIndent()
            )

            db.execSQL("DROP TABLE similarity_setting_files")
            db.execSQL("DROP TABLE similarity_exact_thumbnail_features")
            db.execSQL("DROP TABLE similarity_duration_features")
            db.execSQL("DROP TABLE similarity_cluster_members")
            db.execSQL("DROP TABLE dupe_groups")
            db.execSQL("DROP TABLE cached_files_hash_text")
            db.execSQL("ALTER TABLE similarity_setting_files_hash_blob RENAME TO similarity_setting_files")
            db.execSQL(
                "ALTER TABLE similarity_exact_thumbnail_features_hash_blob " +
                    "RENAME TO similarity_exact_thumbnail_features"
            )
            db.execSQL(
                "ALTER TABLE similarity_duration_features_hash_blob " +
                    "RENAME TO similarity_duration_features"
            )
            db.execSQL("ALTER TABLE similarity_cluster_members_hash_blob RENAME TO similarity_cluster_members")
            db.execSQL("ALTER TABLE dupe_groups_hash_blob RENAME TO dupe_groups")

            db.execSQL("DROP INDEX index_cached_files_hash_blob_migration_normalizedPath")
            db.execSQL("DROP INDEX index_cached_files_hash_blob_migration_size_hash")
            db.execSQL(
                "CREATE UNIQUE INDEX index_cached_files_normalizedPath " +
                    "ON cached_files(normalizedPath)"
            )
            db.execSQL("CREATE INDEX index_cached_files_sizeBytes ON cached_files(sizeBytes)")
            db.execSQL("CREATE INDEX index_cached_files_hashBytes ON cached_files(hashBytes)")
            db.execSQL(
                "CREATE INDEX index_cached_files_sizeBytes_hashBytes " +
                    "ON cached_files(sizeBytes, hashBytes)"
            )
            db.execSQL(
                "CREATE INDEX index_cached_files_sizeBytes_normalizedPath " +
                    "ON cached_files(sizeBytes, normalizedPath)"
            )
            db.execSQL(
                "CREATE INDEX index_cached_files_lastModifiedMillis_normalizedPath " +
                    "ON cached_files(lastModifiedMillis, normalizedPath)"
            )
            db.execSQL("CREATE INDEX index_dupe_groups_fileCount ON dupe_groups(fileCount)")
            db.execSQL("CREATE INDEX index_dupe_groups_totalBytes ON dupe_groups(totalBytes)")
            createSimilarityFileIdIndexes(db)
        }
    }

    val MIGRATION_23_24 = object : Migration(23, 24) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE similarity_exact_thumbnail_features_hash_v24 (
                    settingId INTEGER NOT NULL,
                    fileId INTEGER NOT NULL,
                    thumbnailHash BLOB NOT NULL,
                    PRIMARY KEY(settingId, fileId),
                    FOREIGN KEY(fileId) REFERENCES cached_files(fileId) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            copyThumbnailFeatureHashesToBlob(db)
            db.execSQL("DROP TABLE similarity_exact_thumbnail_features")
            db.execSQL(
                "ALTER TABLE similarity_exact_thumbnail_features_hash_v24 " +
                    "RENAME TO similarity_exact_thumbnail_features"
            )
            db.execSQL(
                """
                CREATE INDEX index_similarity_exact_thumbnail_features_hash
                ON similarity_exact_thumbnail_features(settingId, thumbnailHash, fileId)
                """.trimIndent()
            )
            db.execSQL(
                "CREATE INDEX index_similarity_exact_thumbnail_features_fileId " +
                    "ON similarity_exact_thumbnail_features(fileId)"
            )
            migrateThumbnailClusterKeysToHash(db)
        }
    }

    val MIGRATION_24_25 = object : Migration(24, 25) {
        override fun migrate(db: SupportSQLiteDatabase) {
            createSimilarityClusterDurationStatsTable(db)
            rebuildSimilarityClusterDurationStats(db)
        }
    }

    val MIGRATION_25_26 = object : Migration(25, 26) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE INDEX index_similarity_cluster_members_clusterId_position_fileId
                ON similarity_cluster_members(clusterId, position, fileId)
                """.trimIndent()
            )
            db.execSQL(
                "DROP INDEX IF EXISTS index_similarity_cluster_members_clusterId_position"
            )
        }
    }

}

internal fun createSimilarityClusterDurationStatsTable(db: SupportSQLiteDatabase) {
    db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS similarity_cluster_duration_stats (
            clusterId INTEGER NOT NULL,
            clusterUpdatedAtMillis INTEGER NOT NULL,
            memberCount INTEGER NOT NULL,
            checkedCount INTEGER NOT NULL,
            durationCount INTEGER NOT NULL,
            durationSumMillis INTEGER,
            minimumDurationMillis INTEGER,
            maximumDurationMillis INTEGER,
            PRIMARY KEY(clusterId),
            FOREIGN KEY(clusterId) REFERENCES similarity_clusters(clusterId)
                ON UPDATE NO ACTION ON DELETE CASCADE
        )
        """.trimIndent()
    )
}

internal fun rebuildSimilarityClusterDurationStats(db: SupportSQLiteDatabase) {
    db.execSQL("DELETE FROM similarity_cluster_duration_stats")
    db.execSQL(
        """
        INSERT INTO similarity_cluster_duration_stats (
            clusterId,
            clusterUpdatedAtMillis,
            memberCount,
            checkedCount,
            durationCount,
            durationSumMillis,
            minimumDurationMillis,
            maximumDurationMillis
        )
        SELECT
            member.clusterId,
            cluster.updatedAtMillis,
            COUNT(*),
            SUM(
                CASE
                    WHEN setting_file.fileId IS NULL OR setting_file.durationChecked = 1
                    THEN 1
                    ELSE 0
                END
            ),
            COUNT(duration.durationMillis),
            SUM(duration.durationMillis),
            MIN(duration.durationMillis),
            MAX(duration.durationMillis)
        FROM similarity_cluster_members AS member
        INNER JOIN similarity_clusters AS cluster
            ON cluster.clusterId = member.clusterId
        LEFT JOIN similarity_setting_files AS setting_file
            ON setting_file.settingId = cluster.settingId
           AND setting_file.fileId = member.fileId
        LEFT JOIN similarity_duration_features AS duration
            ON duration.settingId = cluster.settingId
           AND duration.fileId = member.fileId
           AND setting_file.durationChecked = 1
        GROUP BY member.clusterId, cluster.updatedAtMillis
        """.trimIndent()
    )
}

private fun copyCachedFileHashesToBlob(db: SupportSQLiteDatabase) {
    val statement = db.compileStatement("UPDATE cached_files SET hashBytes = ? WHERE fileId = ?")
    try {
        db.query(
            """
            SELECT fileId, hashHex
            FROM cached_files_hash_text
            WHERE hashHex IS NOT NULL AND trim(hashHex) != ''
            """.trimIndent()
        ).use { cursor ->
            val fileIdIndex = cursor.getColumnIndexOrThrow("fileId")
            val hashIndex = cursor.getColumnIndexOrThrow("hashHex")
            while (cursor.moveToNext()) {
                val storedHash = StoredHash.fromExternalString(cursor.getString(hashIndex))
                statement.clearBindings()
                statement.bindBlob(1, storedHash.toStorageBytes())
                statement.bindLong(2, cursor.getLong(fileIdIndex))
                statement.executeUpdateDelete()
            }
        }
    } finally {
        statement.close()
    }
}

private fun copyThumbnailFeatureHashesToBlob(db: SupportSQLiteDatabase) {
    val insert = db.compileStatement(
        """
        INSERT INTO similarity_exact_thumbnail_features_hash_v24 (
            settingId,
            fileId,
            thumbnailHash
        ) VALUES (?, ?, ?)
        """.trimIndent()
    )
    val digest = MessageDigest.getInstance("SHA-256")
    try {
        db.query(
            """
            SELECT settingId, fileId, thumbnailSignature
            FROM similarity_exact_thumbnail_features
            ORDER BY settingId ASC, fileId ASC
            """.trimIndent()
        ).use { cursor ->
            val settingIdIndex = cursor.getColumnIndexOrThrow("settingId")
            val fileIdIndex = cursor.getColumnIndexOrThrow("fileId")
            val signatureIndex = cursor.getColumnIndexOrThrow("thumbnailSignature")
            while (cursor.moveToNext()) {
                val signature = cursor.getString(signatureIndex)
                val hashBytes = if (isSha256HashHex(signature)) {
                    StoredHash.fromExternalString(signature).toStorageBytes()
                } else {
                    digest.digest(signature.toByteArray(StandardCharsets.UTF_8))
                }
                insert.clearBindings()
                insert.bindLong(1, cursor.getLong(settingIdIndex))
                insert.bindLong(2, cursor.getLong(fileIdIndex))
                insert.bindBlob(3, hashBytes)
                insert.executeInsert()
            }
        }
    } finally {
        insert.close()
    }
}

private fun migrateThumbnailClusterKeysToHash(db: SupportSQLiteDatabase) {
    val update = db.compileStatement(
        "UPDATE similarity_clusters SET clusterKey = ? WHERE clusterId = ?"
    )
    try {
        db.query(
            """
            SELECT clusterId, clusterKey
            FROM similarity_clusters
            WHERE clusterKey LIKE 'thumb-v1:%'
            ORDER BY clusterId ASC
            """.trimIndent()
        ).use { cursor ->
            val clusterIdIndex = cursor.getColumnIndexOrThrow("clusterId")
            val clusterKeyIndex = cursor.getColumnIndexOrThrow("clusterKey")
            while (cursor.moveToNext()) {
                val clusterKey = thumbnailHashClusterKeyFromLegacyPayload(
                    cursor.getString(clusterKeyIndex)
                ) ?: continue
                update.clearBindings()
                update.bindString(1, clusterKey)
                update.bindLong(2, cursor.getLong(clusterIdIndex))
                update.executeUpdateDelete()
            }
        }
    } finally {
        update.close()
    }
}

private fun createSimilarityFileIdIndexes(db: SupportSQLiteDatabase) {
    db.execSQL(
        "CREATE INDEX index_similarity_setting_files_fileId " +
            "ON similarity_setting_files(fileId)"
    )
    db.execSQL(
        "CREATE INDEX index_similarity_setting_files_settingId_status " +
            "ON similarity_setting_files(settingId, status)"
    )
    db.execSQL(
        """
        CREATE INDEX index_similarity_exact_thumbnail_features_signature
        ON similarity_exact_thumbnail_features(settingId, thumbnailSignature, fileId)
        """.trimIndent()
    )
    db.execSQL(
        "CREATE INDEX index_similarity_exact_thumbnail_features_fileId " +
            "ON similarity_exact_thumbnail_features(fileId)"
    )
    db.execSQL(
        """
        CREATE INDEX index_similarity_duration_features_duration
        ON similarity_duration_features(settingId, durationMillis, fileId)
        """.trimIndent()
    )
    db.execSQL(
        "CREATE INDEX index_similarity_duration_features_fileId " +
            "ON similarity_duration_features(fileId)"
    )
    db.execSQL(
        """
        CREATE INDEX index_similarity_cluster_members_clusterId_position
        ON similarity_cluster_members(clusterId, position)
        """.trimIndent()
    )
    db.execSQL(
        "CREATE INDEX index_similarity_cluster_members_fileId " +
            "ON similarity_cluster_members(fileId)"
    )
}

private data class MigrationSimilarityClusterMember(
    val normalizedPath: String,
    val durationMillis: Long?
)

private fun parseSimilarityClusterMemberText(text: String): List<MigrationSimilarityClusterMember> {
    val membersByPath = linkedMapOf<String, MigrationSimilarityClusterMember>()
    text.lineSequence()
        .map { line -> line.trim() }
        .filter { line -> line.isNotEmpty() }
        .forEach { line ->
            val tabIndex = line.indexOf('\t')
            val durationMillis = if (tabIndex > 0) {
                line.substring(0, tabIndex)
                    .toLongOrNull()
                    ?.coerceAtLeast(0L)
            } else {
                null
            }
            val normalizedPath = if (durationMillis != null) {
                line.substring(tabIndex + 1).trim()
            } else {
                line
            }
            if (normalizedPath.isNotEmpty()) {
                membersByPath.putIfAbsent(
                    normalizedPath,
                    MigrationSimilarityClusterMember(
                        normalizedPath = normalizedPath,
                        durationMillis = durationMillis
                    )
                )
            }
        }
    return membersByPath.values.toList()
}

private fun tableColumns(
    db: SupportSQLiteDatabase,
    tableName: String
): Set<String> {
    db.query("PRAGMA table_info('$tableName')").use { cursor ->
        val nameIndex = cursor.getColumnIndex("name")
        val columns = linkedSetOf<String>()
        while (cursor.moveToNext()) {
            if (nameIndex >= 0) {
                columns.add(cursor.getString(nameIndex))
            }
        }
        return columns
    }
}
