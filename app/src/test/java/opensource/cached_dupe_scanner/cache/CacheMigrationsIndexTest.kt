package opensource.cached_dupe_scanner.cache

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test
    fun migration11to12CreatesTrashPagingIndex() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "trash-paging-test-${UUID.randomUUID()}.db"

        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(name)
            .callback(
                object : SupportSQLiteOpenHelper.Callback(11) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
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
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_trash_entries_deletedAtMillis ON trash_entries(deletedAtMillis)")
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_trash_entries_originalPath ON trash_entries(originalPath)")
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                }
            )
            .build()

        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        val db = helper.writableDatabase
        try {
            CacheMigrations.MIGRATION_11_12.migrate(db)
            assertTrue(hasIndex(db, "trash_entries", "index_trash_entries_deletedAtMillis_id"))
        } finally {
            helper.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun migration12to13CreatesIndependentSimilarityExperimentTables() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "sim-${UUID.randomUUID()}.db"

        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(name)
            .callback(
                object : SupportSQLiteOpenHelper.Callback(12) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
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
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                }
            )
            .build()

        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        val db = helper.writableDatabase
        try {
            CacheMigrations.MIGRATION_12_13.migrate(db)

            assertTrue(hasTable(db, "cached_files"))
            assertTrue(hasTable(db, "similarity_experiment_runs"))
            assertTrue(hasTable(db, "similarity_clusters"))
            assertTrue(hasColumn(db, "similarity_clusters", "memberPathsText"))
            assertTrue(hasIndex(db, "similarity_clusters", "index_similarity_clusters_experimentId"))
            assertTrue(hasIndex(db, "similarity_clusters", "index_similarity_clusters_fileCount"))
            assertTrue(hasIndex(db, "similarity_clusters", "index_similarity_clusters_totalBytes"))
        } finally {
            helper.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun migration13to14PreservesSimilarityClustersWithNormalizedMemberColumn() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "sim-13-14-${UUID.randomUUID()}.db"

        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(name)
            .callback(
                object : SupportSQLiteOpenHelper.Callback(13) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        createVersion13SimilarityTables(db)
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                }
            )
            .build()

        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        val db = helper.writableDatabase
        try {
            CacheMigrations.MIGRATION_13_14.migrate(db)

            assertTrue(hasTable(db, "similarity_clusters"))
            assertTrue(hasColumn(db, "similarity_clusters", "memberNormalizedPathsText"))
            assertFalse(hasColumn(db, "similarity_clusters", "memberPathsText"))
            assertTrue(hasIndex(db, "similarity_clusters", "index_similarity_clusters_experimentId"))
            assertTrue(hasIndex(db, "similarity_clusters", "index_similarity_clusters_fileCount"))
            assertTrue(hasIndex(db, "similarity_clusters", "index_similarity_clusters_totalBytes"))
            assertEquals(
                "/storage/VIDEO/a.mp4\n/storage/VIDEO/b.mp4",
                firstString(db, "SELECT memberNormalizedPathsText FROM similarity_clusters WHERE experimentId = 'exact'")
            )
        } finally {
            helper.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun migration14to15CreatesSimilarityDurationCandidateCache() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "sim-14-15-${UUID.randomUUID()}.db"

        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(name)
            .callback(
                object : SupportSQLiteOpenHelper.Callback(14) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        createVersion14SimilarityTables(db)
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                }
            )
            .build()

        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        val db = helper.writableDatabase
        try {
            CacheMigrations.MIGRATION_14_15.migrate(db)

            assertTrue(hasTable(db, "similarity_experiment_runs"))
            assertTrue(hasTable(db, "similarity_clusters"))
            assertTrue(hasTable(db, "similarity_duration_candidates"))
            assertTrue(hasColumn(db, "similarity_duration_candidates", "experimentId"))
            assertTrue(hasColumn(db, "similarity_duration_candidates", "normalizedPath"))
            assertTrue(hasColumn(db, "similarity_duration_candidates", "durationMillis"))
            assertTrue(hasColumn(db, "similarity_duration_candidates", "sizeBytes"))
            assertTrue(hasColumn(db, "similarity_duration_candidates", "updatedAtMillis"))
            assertTrue(
                hasIndex(
                    db = db,
                    table = "similarity_duration_candidates",
                    indexName = "index_similarity_duration_candidates_experimentId_durationMillis_normalizedPath"
                )
            )
        } finally {
            helper.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun migration15to16CreatesScanReportPagingIndex() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "scan-report-15-16-${UUID.randomUUID()}.db"

        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(name)
            .callback(
                object : SupportSQLiteOpenHelper.Callback(15) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
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
                            "CREATE INDEX IF NOT EXISTS index_scan_reports_startedAtMillis ON scan_reports(startedAtMillis)"
                        )
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                }
            )
            .build()

        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        val db = helper.writableDatabase
        try {
            CacheMigrations.MIGRATION_15_16.migrate(db)

            assertTrue(hasIndex(db, "scan_reports", "index_scan_reports_startedAtMillis_id"))
            assertEquals(
                listOf("startedAtMillis", "id"),
                indexColumns(db, "index_scan_reports_startedAtMillis_id")
            )
        } finally {
            helper.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun migration16to17MovesSimilarityMembersToSidecarTable() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "sim-16-17-${UUID.randomUUID()}.db"

        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(name)
            .callback(
                object : SupportSQLiteOpenHelper.Callback(16) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        createVersion16SimilarityTables(db)
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                }
            )
            .build()

        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        val db = helper.writableDatabase
        try {
            CacheMigrations.MIGRATION_16_17.migrate(db)

            assertTrue(hasTable(db, "similarity_clusters"))
            assertTrue(hasTable(db, "similarity_cluster_members"))
            assertFalse(hasColumn(db, "similarity_clusters", "memberNormalizedPathsText"))
            assertTrue(hasColumn(db, "similarity_cluster_members", "normalizedPath"))
            assertTrue(hasColumn(db, "similarity_cluster_members", "position"))
            assertTrue(hasColumn(db, "similarity_cluster_members", "durationMillis"))
            assertTrue(
                hasIndex(
                    db = db,
                    table = "similarity_cluster_members",
                    indexName = "index_similarity_cluster_members_cluster_position"
                )
            )
            assertTrue(
                hasIndex(
                    db = db,
                    table = "similarity_cluster_members",
                    indexName = "index_similarity_cluster_members_normalizedPath"
                )
            )
            assertEquals(
                "/storage/video/a.mp4",
                firstString(
                    db,
                    """
                    SELECT normalizedPath
                    FROM similarity_cluster_members
                    WHERE experimentId = 'duration' AND signature = 'duration-v1:1000:range'
                    ORDER BY position ASC
                    LIMIT 1
                    """.trimIndent()
                )
            )
            assertEquals(
                "1000",
                firstString(
                    db,
                    """
                    SELECT CAST(durationMillis AS TEXT)
                    FROM similarity_cluster_members
                    WHERE normalizedPath = '/storage/video/a.mp4'
                    """.trimIndent()
                )
            )
            assertEquals(
                "2",
                firstString(db, "SELECT CAST(COUNT(*) AS TEXT) FROM similarity_cluster_members")
            )
        } finally {
            helper.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun migration17to18ReplacesSimilarityExperimentsWithSettingsSchema() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "sim-17-18-${UUID.randomUUID()}.db"

        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(name)
            .callback(
                object : SupportSQLiteOpenHelper.Callback(17) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        createVersion17SimilarityTables(db)
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
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                }
            )
            .build()

        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        val db = helper.writableDatabase
        try {
            CacheMigrations.MIGRATION_17_18.migrate(db)

            assertFalse(hasTable(db, "similarity_experiment_runs"))
            assertFalse(hasTable(db, "similarity_duration_candidates"))
            assertTrue(hasTable(db, "similarity_settings"))
            assertTrue(hasTable(db, "similarity_setting_files"))
            assertTrue(hasTable(db, "similarity_exact_thumbnail_features"))
            assertTrue(hasTable(db, "similarity_duration_features"))
            assertTrue(hasTable(db, "similarity_clusters"))
            assertTrue(hasTable(db, "similarity_cluster_members"))
            assertTrue(hasTable(db, "similarity_maintenance_runs"))
            assertTrue(hasColumn(db, "similarity_settings", "settingId"))
            assertTrue(hasColumn(db, "similarity_clusters", "clusterId"))
            assertTrue(hasColumn(db, "similarity_clusters", "clusterKey"))
            assertTrue(hasIndex(db, "similarity_settings", "index_similarity_settings_identity"))
            assertTrue(hasIndex(db, "similarity_clusters", "index_similarity_clusters_settingId_clusterKey"))
            assertTrue(hasTable(db, "cached_files"))
        } finally {
            helper.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun migration18to19AddsSimilarityClusterSortIndexes() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "sim-18-19-${UUID.randomUUID()}.db"

        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(name)
            .callback(
                object : SupportSQLiteOpenHelper.Callback(18) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        createVersion17SimilarityTables(db)
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
                        CacheMigrations.MIGRATION_17_18.migrate(db)
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                }
            )
            .build()

        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        val db = helper.writableDatabase
        try {
            CacheMigrations.MIGRATION_18_19.migrate(db)

            assertTrue(hasIndex(db, "similarity_clusters", "index_similarity_clusters_setting_file_count_sort"))
            assertTrue(hasIndex(db, "similarity_clusters", "index_similarity_clusters_setting_total_size_sort"))
            assertEquals(
                listOf("settingId", "fileCount", "totalBytes", "clusterKey"),
                indexColumns(db, "index_similarity_clusters_setting_file_count_sort")
            )
            assertEquals(
                listOf("settingId", "totalBytes", "fileCount", "clusterKey"),
                indexColumns(db, "index_similarity_clusters_setting_total_size_sort")
            )
        } finally {
            helper.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun migration19to20AddsUncheckedSimilarityDimensions() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "sim-19-20-${UUID.randomUUID()}.db"
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(name)
            .callback(
                object : SupportSQLiteOpenHelper.Callback(19) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
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
                            INSERT INTO similarity_setting_files (
                                settingId,
                                normalizedPath,
                                sizeBytes,
                                lastModifiedMillis,
                                status,
                                updatedAtMillis
                            ) VALUES (1, '/video/a.mp4', 10, 20, 'ready', 30)
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
            CacheMigrations.MIGRATION_19_20.migrate(db)

            assertTrue(hasColumn(db, "similarity_setting_files", "widthPixels"))
            assertTrue(hasColumn(db, "similarity_setting_files", "heightPixels"))
            assertTrue(hasColumn(db, "similarity_setting_files", "dimensionsChecked"))
            db.query(
                """
                SELECT widthPixels, heightPixels, dimensionsChecked
                FROM similarity_setting_files
                WHERE settingId = 1 AND normalizedPath = '/video/a.mp4'
                """.trimIndent()
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertTrue(cursor.isNull(cursor.getColumnIndexOrThrow("widthPixels")))
                assertTrue(cursor.isNull(cursor.getColumnIndexOrThrow("heightPixels")))
                assertEquals(0, cursor.getInt(cursor.getColumnIndexOrThrow("dimensionsChecked")))
            }
        } finally {
            helper.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun migration20to21AddsDurationStateAndKeepsStoredFeaturesChecked() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "sim-20-21-${UUID.randomUUID()}.db"
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(name)
            .callback(
                object : SupportSQLiteOpenHelper.Callback(20) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            """
                            CREATE TABLE IF NOT EXISTS similarity_setting_files (
                                settingId INTEGER NOT NULL,
                                normalizedPath TEXT NOT NULL,
                                sizeBytes INTEGER NOT NULL,
                                lastModifiedMillis INTEGER NOT NULL,
                                status TEXT NOT NULL,
                                widthPixels INTEGER,
                                heightPixels INTEGER,
                                dimensionsChecked INTEGER NOT NULL DEFAULT 0,
                                updatedAtMillis INTEGER NOT NULL,
                                PRIMARY KEY(settingId, normalizedPath)
                            )
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
                            INSERT INTO similarity_setting_files (
                                settingId,
                                normalizedPath,
                                sizeBytes,
                                lastModifiedMillis,
                                status,
                                widthPixels,
                                heightPixels,
                                dimensionsChecked,
                                updatedAtMillis
                            ) VALUES (1, '/video/a.mp4', 10, 20, 'ready', 1920, 1080, 1, 30)
                            """.trimIndent()
                        )
                        db.execSQL(
                            """
                            INSERT INTO similarity_setting_files (
                                settingId,
                                normalizedPath,
                                sizeBytes,
                                lastModifiedMillis,
                                status,
                                widthPixels,
                                heightPixels,
                                dimensionsChecked,
                                updatedAtMillis
                            ) VALUES (1, '/video/b.mp4', 10, 20, 'ready', 1920, 1080, 1, 30)
                            """.trimIndent()
                        )
                        db.execSQL(
                            """
                            INSERT INTO similarity_duration_features (
                                settingId,
                                normalizedPath,
                                durationMillis
                            ) VALUES (1, '/video/b.mp4', 1000)
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
            CacheMigrations.MIGRATION_20_21.migrate(db)

            assertTrue(hasColumn(db, "similarity_setting_files", "durationChecked"))
            db.query(
                """
                SELECT normalizedPath, durationChecked
                FROM similarity_setting_files
                WHERE settingId = 1
                ORDER BY normalizedPath ASC
                """.trimIndent()
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(cursor.getColumnIndexOrThrow("durationChecked")))
                assertTrue(cursor.moveToNext())
                assertEquals(1, cursor.getInt(cursor.getColumnIndexOrThrow("durationChecked")))
            }
        } finally {
            helper.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun migration21to22UsesStableFileIdsForSimilarityRelations() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "sim-21-22-${UUID.randomUUID()}.db"
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(name)
            .callback(
                object : SupportSQLiteOpenHelper.Callback(21) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        createVersion21FileIdMigrationTables(db)
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                }
            )
            .build()

        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        val db = helper.writableDatabase
        try {
            CacheMigrations.MIGRATION_21_22.migrate(db)

            assertEquals("INTEGER", columnType(db, "cached_files", "fileId"))
            assertEquals(1, columnPrimaryKeyPosition(db, "cached_files", "fileId"))
            assertTrue(hasIndex(db, "cached_files", "index_cached_files_normalizedPath"))
            assertTrue(indexIsUnique(db, "cached_files", "index_cached_files_normalizedPath"))
            assertTrue(hasColumn(db, "similarity_setting_files", "fileId"))
            assertFalse(hasColumn(db, "similarity_setting_files", "normalizedPath"))
            assertTrue(hasColumn(db, "similarity_exact_thumbnail_features", "fileId"))
            assertTrue(hasColumn(db, "similarity_duration_features", "fileId"))
            assertTrue(hasColumn(db, "similarity_cluster_members", "fileId"))
            listOf(
                "similarity_setting_files",
                "similarity_exact_thumbnail_features",
                "similarity_duration_features",
                "similarity_cluster_members"
            ).forEach { table ->
                assertTrue(hasCascadeFileIdForeignKey(db, table))
            }
            assertTrue(
                hasIndex(db, "similarity_setting_files", "index_similarity_setting_files_fileId")
            )
            assertTrue(
                hasIndex(
                    db,
                    "similarity_exact_thumbnail_features",
                    "index_similarity_exact_thumbnail_features_fileId"
                )
            )
            assertTrue(
                hasIndex(
                    db,
                    "similarity_duration_features",
                    "index_similarity_duration_features_fileId"
                )
            )
            assertTrue(
                hasIndex(db, "similarity_cluster_members", "index_similarity_cluster_members_fileId")
            )

            assertEquals(2, firstInt(db, "SELECT COUNT(*) FROM cached_files"))
            assertEquals(2, firstInt(db, "SELECT COUNT(*) FROM similarity_setting_files"))
            assertEquals(2, firstInt(db, "SELECT COUNT(*) FROM similarity_exact_thumbnail_features"))
            assertEquals(2, firstInt(db, "SELECT COUNT(*) FROM similarity_duration_features"))
            assertEquals(2, firstInt(db, "SELECT COUNT(*) FROM similarity_cluster_members"))
            assertEquals(1, firstInt(db, "SELECT COUNT(*) FROM similarity_clusters"))
            assertEquals(2, firstInt(db, "SELECT fileCount FROM similarity_clusters WHERE clusterId = 1"))
            assertEquals(30L, firstLong(db, "SELECT totalBytes FROM similarity_clusters WHERE clusterId = 1"))
            assertEquals(
                listOf("/video/a.mp4", "/video/b.mp4"),
                stringList(
                    db,
                    """
                    SELECT file.normalizedPath
                    FROM similarity_cluster_members AS member
                    INNER JOIN cached_files AS file ON file.fileId = member.fileId
                    WHERE member.clusterId = 1
                    ORDER BY member.position ASC
                    """.trimIndent()
                )
            )

            val queryPlan = stringList(
                db,
                """
                EXPLAIN QUERY PLAN
                SELECT file.normalizedPath
                FROM similarity_cluster_members AS member
                INNER JOIN cached_files AS file ON file.fileId = member.fileId
                WHERE member.clusterId = 1
                """.trimIndent(),
                columnIndex = 3
            )
            assertTrue(queryPlan.any { detail -> detail.contains("INTEGER PRIMARY KEY") })
        } finally {
            helper.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun migration21to22PassesRoomSchemaValidation() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "sim-21-22-room-${UUID.randomUUID()}.db"
        val current = Room.databaseBuilder(context, CacheDatabase::class.java, name)
            .allowMainThreadQueries()
            .build()
        try {
            downgradeFileRelationsToVersion21(current.openHelper.writableDatabase)
        } finally {
            current.close()
        }

        val migrated = Room.databaseBuilder(context, CacheDatabase::class.java, name)
            .addMigrations(*CACHE_DATABASE_MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        try {
            assertEquals(0, migrated.fileCacheDao().countAll())
            assertTrue(
                hasCascadeFileIdForeignKey(
                    migrated.openHelper.writableDatabase,
                    "similarity_setting_files"
                )
            )
        } finally {
            migrated.close()
            context.deleteDatabase(name)
        }
    }

    private fun downgradeFileRelationsToVersion21(db: SupportSQLiteDatabase) {
        db.execSQL("PRAGMA foreign_keys = OFF")
        db.execSQL("DROP TABLE similarity_setting_files")
        db.execSQL("DROP TABLE similarity_exact_thumbnail_features")
        db.execSQL("DROP TABLE similarity_duration_features")
        db.execSQL("DROP TABLE similarity_cluster_members")
        db.execSQL("DROP TABLE similarity_clusters")
        db.execSQL("DROP TABLE cached_files")
        createVersion21FileIdMigrationTables(db, seedData = false)
        db.execSQL("PRAGMA user_version = 21")
    }

    private fun createVersion21FileIdMigrationTables(
        db: SupportSQLiteDatabase,
        seedData: Boolean = true
    ) {
        db.execSQL(
            """
            CREATE TABLE cached_files (
                normalizedPath TEXT NOT NULL PRIMARY KEY,
                path TEXT NOT NULL,
                sizeBytes INTEGER NOT NULL,
                lastModifiedMillis INTEGER NOT NULL,
                hashHex TEXT
            )
            """.trimIndent()
        )
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
        createVersion17SimilarityTables(db)
        CacheMigrations.MIGRATION_17_18.migrate(db)
        CacheMigrations.MIGRATION_18_19.migrate(db)
        CacheMigrations.MIGRATION_19_20.migrate(db)
        CacheMigrations.MIGRATION_20_21.migrate(db)
        if (!seedData) return

        db.execSQL(
            """
            INSERT INTO cached_files VALUES
                ('/video/a.mp4', '/video/a.mp4', 10, 100, 'a'),
                ('/video/b.mp4', '/video/b.mp4', 20, 200, 'b')
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO similarity_setting_files VALUES
                (1, '/video/a.mp4', 10, 100, 'ready', 1920, 1080, 1, 1, 300),
                (1, '/video/b.mp4', 20, 200, 'ready', 1920, 1080, 1, 1, 300),
                (1, '/missing.mp4', 40, 400, 'ready', 1920, 1080, 1, 1, 300)
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO similarity_exact_thumbnail_features VALUES
                (1, '/video/a.mp4', 'same'),
                (1, '/video/b.mp4', 'same'),
                (1, '/missing.mp4', 'same')
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO similarity_duration_features VALUES
                (1, '/video/a.mp4', 1000),
                (1, '/video/b.mp4', 1000),
                (1, '/missing.mp4', 1000)
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO similarity_clusters VALUES
                (1, 1, 'kept', 3, 70, 500),
                (2, 1, 'orphaned', 2, 50, 500)
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO similarity_cluster_members VALUES
                (1, '/video/a.mp4', 0),
                (1, '/video/b.mp4', 1),
                (1, '/missing.mp4', 2),
                (2, '/video/a.mp4', 0),
                (2, '/missing.mp4', 1)
            """.trimIndent()
        )
    }

    private fun createVersion13SimilarityTables(db: SupportSQLiteDatabase) {
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
        db.execSQL("CREATE INDEX IF NOT EXISTS index_similarity_clusters_experimentId ON similarity_clusters(experimentId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_similarity_clusters_fileCount ON similarity_clusters(fileCount)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_similarity_clusters_totalBytes ON similarity_clusters(totalBytes)")
        db.execSQL(
            """
            INSERT INTO similarity_clusters (
                experimentId,
                signature,
                fileCount,
                totalBytes,
                memberPathsText,
                updatedAtMillis
            ) VALUES (
                'exact',
                'thumb-v1:video:color:1x1:raw:0:ff00aa',
                2,
                200,
                '/storage/VIDEO/a.mp4
/storage/VIDEO/b.mp4',
                1
            )
            """.trimIndent()
        )
    }

    private fun createVersion14SimilarityTables(db: SupportSQLiteDatabase) {
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
                memberNormalizedPathsText TEXT NOT NULL,
                updatedAtMillis INTEGER NOT NULL,
                PRIMARY KEY(experimentId, signature)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_similarity_clusters_experimentId ON similarity_clusters(experimentId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_similarity_clusters_fileCount ON similarity_clusters(fileCount)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_similarity_clusters_totalBytes ON similarity_clusters(totalBytes)")
    }

    private fun createVersion16SimilarityTables(db: SupportSQLiteDatabase) {
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
                memberNormalizedPathsText TEXT NOT NULL,
                updatedAtMillis INTEGER NOT NULL,
                PRIMARY KEY(experimentId, signature)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_similarity_clusters_experimentId ON similarity_clusters(experimentId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_similarity_clusters_fileCount ON similarity_clusters(fileCount)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_similarity_clusters_totalBytes ON similarity_clusters(totalBytes)")
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
            INSERT INTO similarity_clusters (
                experimentId,
                signature,
                fileCount,
                totalBytes,
                memberNormalizedPathsText,
                updatedAtMillis
            ) VALUES (
                'duration',
                'duration-v1:1000:range',
                2,
                200,
                '1000	/storage/video/a.mp4
/storage/video/b.mp4',
                1
            )
            """.trimIndent()
        )
    }

    private fun createVersion17SimilarityTables(db: SupportSQLiteDatabase) {
        createVersion16SimilarityTables(db)
        CacheMigrations.MIGRATION_16_17.migrate(db)
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

    private fun indexColumns(db: SupportSQLiteDatabase, indexName: String): List<String> {
        val columns = mutableListOf<String>()
        db.query("PRAGMA index_info('$indexName')").use { cursor ->
            val nameIdx = cursor.getColumnIndex("name")
            while (cursor.moveToNext()) {
                if (nameIdx >= 0) columns += cursor.getString(nameIdx)
            }
        }
        return columns
    }

    private fun hasTable(db: SupportSQLiteDatabase, tableName: String): Boolean {
        db.query("SELECT name FROM sqlite_master WHERE type='table' AND name=?", arrayOf(tableName)).use { cursor ->
            return cursor.moveToFirst()
        }
    }

    private fun hasColumn(db: SupportSQLiteDatabase, tableName: String, columnName: String): Boolean {
        db.query("PRAGMA table_info('$tableName')").use { cursor ->
            val nameIdx = cursor.getColumnIndex("name")
            while (cursor.moveToNext()) {
                if (nameIdx >= 0 && cursor.getString(nameIdx) == columnName) return true
            }
            return false
        }
    }

    private fun columnType(db: SupportSQLiteDatabase, tableName: String, columnName: String): String? {
        db.query("PRAGMA table_info('$tableName')").use { cursor ->
            val nameIdx = cursor.getColumnIndexOrThrow("name")
            val typeIdx = cursor.getColumnIndexOrThrow("type")
            while (cursor.moveToNext()) {
                if (cursor.getString(nameIdx) == columnName) return cursor.getString(typeIdx)
            }
            return null
        }
    }

    private fun columnPrimaryKeyPosition(
        db: SupportSQLiteDatabase,
        tableName: String,
        columnName: String
    ): Int? {
        db.query("PRAGMA table_info('$tableName')").use { cursor ->
            val nameIdx = cursor.getColumnIndexOrThrow("name")
            val primaryKeyIdx = cursor.getColumnIndexOrThrow("pk")
            while (cursor.moveToNext()) {
                if (cursor.getString(nameIdx) == columnName) return cursor.getInt(primaryKeyIdx)
            }
            return null
        }
    }

    private fun indexIsUnique(db: SupportSQLiteDatabase, tableName: String, indexName: String): Boolean {
        db.query("PRAGMA index_list('$tableName')").use { cursor ->
            val nameIdx = cursor.getColumnIndexOrThrow("name")
            val uniqueIdx = cursor.getColumnIndexOrThrow("unique")
            while (cursor.moveToNext()) {
                if (cursor.getString(nameIdx) == indexName) return cursor.getInt(uniqueIdx) == 1
            }
            return false
        }
    }

    private fun hasCascadeFileIdForeignKey(db: SupportSQLiteDatabase, tableName: String): Boolean {
        db.query("PRAGMA foreign_key_list('$tableName')").use { cursor ->
            val tableIdx = cursor.getColumnIndexOrThrow("table")
            val fromIdx = cursor.getColumnIndexOrThrow("from")
            val toIdx = cursor.getColumnIndexOrThrow("to")
            val onDeleteIdx = cursor.getColumnIndexOrThrow("on_delete")
            while (cursor.moveToNext()) {
                if (
                    cursor.getString(tableIdx) == "cached_files" &&
                    cursor.getString(fromIdx) == "fileId" &&
                    cursor.getString(toIdx) == "fileId" &&
                    cursor.getString(onDeleteIdx) == "CASCADE"
                ) {
                    return true
                }
            }
            return false
        }
    }

    private fun firstInt(db: SupportSQLiteDatabase, query: String): Int {
        db.query(query).use { cursor ->
            assertTrue(cursor.moveToFirst())
            return cursor.getInt(0)
        }
    }

    private fun firstLong(db: SupportSQLiteDatabase, query: String): Long {
        db.query(query).use { cursor ->
            assertTrue(cursor.moveToFirst())
            return cursor.getLong(0)
        }
    }

    private fun stringList(
        db: SupportSQLiteDatabase,
        query: String,
        columnIndex: Int = 0
    ): List<String> {
        db.query(query).use { cursor ->
            val values = mutableListOf<String>()
            while (cursor.moveToNext()) values += cursor.getString(columnIndex)
            return values
        }
    }

    private fun firstString(db: SupportSQLiteDatabase, query: String): String? {
        db.query(query).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }
}
