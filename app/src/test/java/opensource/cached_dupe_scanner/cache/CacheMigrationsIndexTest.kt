package opensource.cached_dupe_scanner.cache

import android.content.Context
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

    private fun firstString(db: SupportSQLiteDatabase, query: String): String? {
        db.query(query).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }
}
