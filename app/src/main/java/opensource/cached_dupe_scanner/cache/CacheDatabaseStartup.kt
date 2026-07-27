package opensource.cached_dupe_scanner.cache

import android.content.Context
import android.database.Cursor
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteStatement
import java.io.File
import opensource.cached_dupe_scanner.core.isSha256HashHex
import opensource.cached_dupe_scanner.core.thumbnailHashClusterKeyFromLegacyPayload

sealed interface CacheDatabaseStartupPlan {
    data class OpenCurrent(val existingVersion: Int?) : CacheDatabaseStartupPlan

    data class UpgradeRequired(
        val fromVersion: Int,
        val toVersion: Int,
        val recoveryRequired: Boolean
    ) : CacheDatabaseStartupPlan

    data class UnsupportedVersion(val version: Int) : CacheDatabaseStartupPlan
}

data class CacheDatabaseStartupProgress(
    val stage: String,
    val processed: Long? = null,
    val total: Long? = null
)

fun inspectCacheDatabaseStartup(
    context: Context,
    databaseName: String = CACHE_DATABASE_NAME
): CacheDatabaseStartupPlan {
    restoreInterruptedDatabaseReplacement(context, databaseName)
    val databaseFile = context.getDatabasePath(databaseName)
    if (!databaseFile.exists()) return CacheDatabaseStartupPlan.OpenCurrent(existingVersion = null)
    val version = readDatabaseVersion(databaseFile)
    return when {
        version == CACHE_DATABASE_VERSION -> {
            context.deleteDatabase("$databaseName$BACKUP_DATABASE_SUFFIX")
            CacheDatabaseStartupPlan.OpenCurrent(version)
        }
        version in 1 until CACHE_DATABASE_VERSION -> CacheDatabaseStartupPlan.UpgradeRequired(
            fromVersion = version,
            toVersion = CACHE_DATABASE_VERSION,
            recoveryRequired = version == LEGACY_RECOVERY_DATABASE_VERSION
        )
        else -> CacheDatabaseStartupPlan.UnsupportedVersion(version)
    }
}

fun openCacheDatabaseForStartup(
    context: Context,
    databaseName: String = CACHE_DATABASE_NAME,
    plan: CacheDatabaseStartupPlan,
    sqlitePageCacheMiB: Int = DEFAULT_SQLITE_PAGE_CACHE_MIB,
    onProgress: (CacheDatabaseStartupProgress) -> Unit = {}
): CacheDatabase {
    return when (plan) {
        is CacheDatabaseStartupPlan.OpenCurrent -> openRoomDatabase(
            context = context,
            databaseName = databaseName,
            sqlitePageCacheMiB = sqlitePageCacheMiB,
            onProgress = onProgress
        )
        is CacheDatabaseStartupPlan.UpgradeRequired -> {
            if (plan.recoveryRequired) {
                recoverVersion21Database(
                    context = context,
                    databaseName = databaseName,
                    sqlitePageCacheMiB = sqlitePageCacheMiB,
                    onProgress = onProgress
                )
            } else {
                openRoomDatabase(
                    context = context,
                    databaseName = databaseName,
                    sqlitePageCacheMiB = sqlitePageCacheMiB,
                    onProgress = onProgress
                )
            }
        }
        is CacheDatabaseStartupPlan.UnsupportedVersion -> {
            error("Unsupported cache database version ${plan.version}")
        }
    }
}

private fun openRoomDatabase(
    context: Context,
    databaseName: String,
    sqlitePageCacheMiB: Int,
    onProgress: (CacheDatabaseStartupProgress) -> Unit
): CacheDatabase {
    onProgress(CacheDatabaseStartupProgress(stage = "Opening database"))
    val database = buildCacheDatabase(
        context = context,
        databaseName = databaseName,
        sqlitePageCacheMiB = sqlitePageCacheMiB
    )
    return try {
        database.openHelper.writableDatabase
        database
    } catch (error: Exception) {
        database.close()
        throw error
    }
}

private fun recoverVersion21Database(
    context: Context,
    databaseName: String,
    sqlitePageCacheMiB: Int,
    onProgress: (CacheDatabaseStartupProgress) -> Unit
): CacheDatabase {
    val sourceFile = context.getDatabasePath(databaseName)
    val upgradeName = "$databaseName$UPGRADE_DATABASE_SUFFIX"
    val backupName = "$databaseName$BACKUP_DATABASE_SUFFIX"
    context.deleteDatabase(upgradeName)
    val upgradeFile = context.getDatabasePath(upgradeName)
    upgradeFile.parentFile?.mkdirs()
    check(upgradeFile.createNewFile()) { "Upgrade database could not be created" }
    context.deleteDatabase(backupName)

    val source = SQLiteDatabase.openDatabase(
        sourceFile.absolutePath,
        null,
        SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS,
        PRESERVE_CORRUPT_DATABASE_HANDLER
    )
    val targetDatabase = buildCacheDatabase(
        context = context,
        databaseName = upgradeName,
        sqlitePageCacheMiB = sqlitePageCacheMiB
    )
    try {
        val target = targetDatabase.openHelper.writableDatabase
        onProgress(CacheDatabaseStartupProgress(stage = "Counting database rows"))
        val progress = RecoveryProgressTracker(
            total = source.recoveryCopyRowCount(),
            onProgress = onProgress
        )
        progress.changeStage("Recovering cached files")
        copyVersion21CoreTables(source, target, progress::rowsCopied)
        progress.changeStage("Preserving similarity results")
        copyVersion21SimilarityTables(source, target, progress::rowsCopied)
        progress.completeCopy()
        progress.changeStage("Checking upgraded database")
        checkRecoveredDatabase(source, target)
        target.query("PRAGMA wal_checkpoint(TRUNCATE)").use { cursor -> cursor.moveToFirst() }
    } catch (error: Exception) {
        targetDatabase.close()
        source.close()
        context.deleteDatabase(upgradeName)
        throw error
    }
    targetDatabase.close()
    source.close()

    onProgress(CacheDatabaseStartupProgress(stage = "Replacing database"))
    replaceDatabaseFiles(
        context = context,
        databaseName = databaseName,
        upgradeName = upgradeName,
        backupName = backupName
    )
    return try {
        val database = openRoomDatabase(
            context = context,
            databaseName = databaseName,
            sqlitePageCacheMiB = sqlitePageCacheMiB,
            onProgress = onProgress
        )
        context.deleteDatabase(backupName)
        database
    } catch (error: Exception) {
        context.deleteDatabase(databaseName)
        moveDatabaseFiles(context, backupName, databaseName)
        context.deleteDatabase(upgradeName)
        throw error
    }
}

private fun copyVersion21CoreTables(
    source: SQLiteDatabase,
    target: SupportSQLiteDatabase,
    onRowsCopied: (Int) -> Unit
) {
    copyRowsIfTableExists(
        source = source,
        target = target,
        tableName = "cached_files",
        query = """
            SELECT rowid, normalizedPath, path, sizeBytes, lastModifiedMillis, hashHex
            FROM cached_files
            ORDER BY rowid ASC
        """.trimIndent(),
        insertSql = """
            INSERT INTO cached_files (
                fileId,
                normalizedPath,
                path,
                sizeBytes,
                lastModifiedMillis,
                hashBytes
            ) VALUES (?, ?, ?, ?, ?, ?)
        """.trimIndent(),
        onRowsCopied = onRowsCopied
    ) { cursor ->
        listOf(
            cursor.getLong(0),
            cursor.getString(1),
            cursor.getString(2),
            cursor.getLong(3),
            cursor.getLong(4),
            cursor.stringOrNull(5)
                ?.takeIf { hash -> hash.isNotBlank() }
                ?.let(StoredHash::fromExternalString)
                ?.toStorageBytes()
        )
    }
    copyRowsIfTableExists(
        source = source,
        target = target,
        tableName = "scan_reports",
        query = """
            SELECT
                id,
                startedAtMillis,
                finishedAtMillis,
                targetsText,
                mode,
                cancelled,
                collectedCount,
                detectedCount,
                hashCandidates,
                hashesComputed,
                collectingMillis,
                detectingMillis,
                hashingMillis
            FROM scan_reports
            ORDER BY startedAtMillis ASC, id ASC
        """.trimIndent(),
        insertSql = """
            INSERT INTO scan_reports VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent(),
        onRowsCopied = onRowsCopied
    ) { cursor -> cursor.values(13) }
    copyRowsIfTableExists(
        source = source,
        target = target,
        tableName = "trash_entries",
        query = """
            SELECT
                id,
                originalPath,
                trashedPath,
                sizeBytes,
                lastModifiedMillis,
                hashHex,
                deletedAtMillis,
                volumeRoot
            FROM trash_entries
            ORDER BY deletedAtMillis ASC, id ASC
        """.trimIndent(),
        insertSql = "INSERT INTO trash_entries VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
        onRowsCopied = onRowsCopied
    ) { cursor -> cursor.values(8) }

    val updatedAtMillis = if (source.hasTable("dupe_groups")) {
        source.rawQuery("SELECT COALESCE(MAX(updatedAtMillis), 0) FROM dupe_groups", null)
            .use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else 0L }
    } else {
        0L
    }
    target.execSQL(
        """
        INSERT INTO dupe_groups (
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
            ?
        FROM cached_files
        WHERE hashBytes IS NOT NULL
        GROUP BY sizeBytes, hashBytes
        HAVING COUNT(*) > 1
        """.trimIndent(),
        arrayOf(updatedAtMillis)
    )
}

private fun copyVersion21SimilarityTables(
    source: SQLiteDatabase,
    target: SupportSQLiteDatabase,
    onRowsCopied: (Int) -> Unit
) {
    // Legacy raw-feature tables can be unreadable; visible results are rebuilt from clusters.
    copyRowsIfTableExists(
        source = source,
        target = target,
        tableName = "similarity_settings",
        query = """
            SELECT
                settingId,
                methodId,
                mediaScope,
                minSizeBytes,
                paramsJson,
                paramsHash,
                displayName,
                enabled,
                createdAtMillis,
                updatedAtMillis
            FROM similarity_settings
            ORDER BY settingId ASC
        """.trimIndent(),
        insertSql = "INSERT INTO similarity_settings VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
        onRowsCopied = onRowsCopied
    ) { cursor -> cursor.values(10) }
    copyRowsIfTableExists(
        source = source,
        target = target,
        tableName = "similarity_clusters",
        query = """
            SELECT clusterId, settingId, clusterKey, fileCount, totalBytes, updatedAtMillis
            FROM similarity_clusters
            ORDER BY clusterId ASC
        """.trimIndent(),
        insertSql = "INSERT INTO similarity_clusters VALUES (?, ?, ?, ?, ?, ?)",
        onRowsCopied = onRowsCopied
    ) { cursor ->
        val legacyKey = cursor.getString(2)
        listOf(
            cursor.getLong(0),
            cursor.getLong(1),
            thumbnailHashClusterKeyFromLegacyPayload(legacyKey) ?: legacyKey,
            cursor.getLong(3),
            cursor.getLong(4),
            cursor.getLong(5)
        )
    }
    copyRowsIfTableExists(
        source = source,
        target = target,
        tableName = "similarity_cluster_members",
        query = """
            SELECT member.clusterId, file.rowid, member.position
            FROM similarity_cluster_members AS member
            INNER JOIN cached_files AS file
                ON file.normalizedPath = member.normalizedPath
            ORDER BY member.clusterId ASC, member.position ASC
        """.trimIndent(),
        insertSql = "INSERT INTO similarity_cluster_members VALUES (?, ?, ?)",
        onRowsCopied = onRowsCopied
    ) { cursor -> cursor.values(3) }
    copyRowsIfTableExists(
        source = source,
        target = target,
        tableName = "similarity_duration_features",
        query = """
            SELECT feature.settingId, file.rowid, feature.durationMillis
            FROM similarity_duration_features AS feature
            INNER JOIN cached_files AS file
                ON file.normalizedPath = feature.normalizedPath
            ORDER BY feature.settingId ASC, file.rowid ASC
        """.trimIndent(),
        insertSql = "INSERT INTO similarity_duration_features VALUES (?, ?, ?)",
        onRowsCopied = onRowsCopied
    ) { cursor -> cursor.values(3) }
    copyRowsIfTableExists(
        source = source,
        target = target,
        tableName = "similarity_maintenance_runs",
        query = """
            SELECT
                runId,
                settingId,
                startedAtMillis,
                finishedAtMillis,
                candidateCount,
                processedCount,
                skippedCount,
                clusterCount,
                duplicateFileCount,
                cancelled
            FROM similarity_maintenance_runs
            ORDER BY runId ASC
        """.trimIndent(),
        insertSql = "INSERT INTO similarity_maintenance_runs VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
        onRowsCopied = onRowsCopied
    ) { cursor -> cursor.values(10) }

    target.execSQL(
        """
        UPDATE similarity_clusters
        SET fileCount = (
                SELECT COUNT(*)
                FROM similarity_cluster_members AS member
                WHERE member.clusterId = similarity_clusters.clusterId
            ),
            totalBytes = (
                SELECT COALESCE(SUM(file.sizeBytes), 0)
                FROM similarity_cluster_members AS member
                INNER JOIN cached_files AS file ON file.fileId = member.fileId
                WHERE member.clusterId = similarity_clusters.clusterId
            )
        """.trimIndent()
    )
    target.execSQL(
        """
        DELETE FROM similarity_cluster_members
        WHERE clusterId IN (
            SELECT clusterId FROM similarity_clusters WHERE fileCount <= 1
        )
        """.trimIndent()
    )
    target.execSQL("DELETE FROM similarity_clusters WHERE fileCount <= 1")
    rebuildExactFeaturesFromClusters(target)
    target.execSQL(
        """
        INSERT OR IGNORE INTO similarity_setting_files (
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
            cluster.settingId,
            member.fileId,
            file.sizeBytes,
            file.lastModifiedMillis,
            CASE
                WHEN setting.methodId = 'exact-thumbnail' AND exact_feature.fileId IS NOT NULL
                    THEN 'ready'
                WHEN setting.methodId IN ('duration-tolerance', 'duration-neighbor-list')
                    AND duration.fileId IS NOT NULL
                    THEN 'ready'
                ELSE 'stale'
            END,
            NULL,
            NULL,
            0,
            CASE WHEN duration.fileId IS NULL THEN 0 ELSE 1 END,
            cluster.updatedAtMillis
        FROM similarity_cluster_members AS member
        INNER JOIN similarity_clusters AS cluster ON cluster.clusterId = member.clusterId
        INNER JOIN similarity_settings AS setting ON setting.settingId = cluster.settingId
        INNER JOIN cached_files AS file ON file.fileId = member.fileId
        LEFT JOIN similarity_exact_thumbnail_features AS exact_feature
            ON exact_feature.settingId = cluster.settingId
           AND exact_feature.fileId = member.fileId
        LEFT JOIN similarity_duration_features AS duration
            ON duration.settingId = cluster.settingId
           AND duration.fileId = member.fileId
        """.trimIndent()
    )
    rebuildSimilarityClusterDurationStats(target)
}

private fun rebuildExactFeaturesFromClusters(target: SupportSQLiteDatabase) {
    val statement = target.compileStatement(
        """
        INSERT OR IGNORE INTO similarity_exact_thumbnail_features (
            settingId,
            fileId,
            thumbnailHash
        ) VALUES (?, ?, ?)
        """.trimIndent()
    )
    try {
        target.query(
            """
            SELECT cluster.settingId, member.fileId, cluster.clusterKey
            FROM similarity_clusters AS cluster
            INNER JOIN similarity_settings AS setting ON setting.settingId = cluster.settingId
            INNER JOIN similarity_cluster_members AS member ON member.clusterId = cluster.clusterId
            WHERE setting.methodId = 'exact-thumbnail'
            ORDER BY cluster.clusterId ASC, member.position ASC
            """.trimIndent()
        ).use { cursor ->
            val batch = ArrayList<List<Any?>>(RECOVERY_COPY_BATCH_SIZE)
            while (cursor.moveToNext()) {
                val hashHex = cursor.getString(2).substringAfterLast(':')
                if (!isSha256HashHex(hashHex)) continue
                batch += listOf(
                    cursor.getLong(0),
                    cursor.getLong(1),
                    StoredHash.fromExternalString(hashHex).toStorageBytes()
                )
                if (batch.size == RECOVERY_COPY_BATCH_SIZE) {
                    insertBatch(target, statement, batch)
                    batch.clear()
                }
            }
            if (batch.isNotEmpty()) insertBatch(target, statement, batch)
        }
    } finally {
        statement.close()
    }
}

private fun checkRecoveredDatabase(
    source: SQLiteDatabase,
    target: SupportSQLiteDatabase
) {
    val sourceFileCount = source.rawQuery("SELECT COUNT(*) FROM cached_files", null)
        .use { cursor -> cursor.moveToFirst(); cursor.getLong(0) }
    val targetFileCount = target.query("SELECT COUNT(*) FROM cached_files")
        .use { cursor -> cursor.moveToFirst(); cursor.getLong(0) }
    check(sourceFileCount == targetFileCount) { "Cached file recovery count mismatch" }
    target.query("PRAGMA foreign_key_check").use { cursor ->
        check(!cursor.moveToFirst()) { "Recovered database contains broken relationships" }
    }
    target.query("PRAGMA quick_check").use { cursor ->
        check(cursor.moveToFirst() && cursor.getString(0) == "ok") {
            "Recovered database integrity check failed"
        }
    }
}

private fun copyRowsIfTableExists(
    source: SQLiteDatabase,
    target: SupportSQLiteDatabase,
    tableName: String,
    query: String,
    insertSql: String,
    onRowsCopied: (Int) -> Unit,
    rowValues: (Cursor) -> List<Any?>
): Int {
    if (!source.hasTable(tableName)) return 0
    val statement = target.compileStatement(insertSql)
    var copied = 0
    try {
        source.rawQuery(query, null).use { cursor ->
            val batch = ArrayList<List<Any?>>(RECOVERY_COPY_BATCH_SIZE)
            while (cursor.moveToNext()) {
                batch += rowValues(cursor)
                if (batch.size == RECOVERY_COPY_BATCH_SIZE) {
                    val batchCopied = insertBatch(target, statement, batch)
                    copied += batchCopied
                    onRowsCopied(batchCopied)
                    batch.clear()
                }
            }
            if (batch.isNotEmpty()) {
                val batchCopied = insertBatch(target, statement, batch)
                copied += batchCopied
                onRowsCopied(batchCopied)
            }
        }
    } finally {
        statement.close()
    }
    return copied
}

private fun insertBatch(
    database: SupportSQLiteDatabase,
    statement: SupportSQLiteStatement,
    rows: List<List<Any?>>
): Int {
    database.beginTransaction()
    return try {
        rows.forEach { values ->
            statement.clearBindings()
            values.forEachIndexed { index, value -> statement.bindValue(index + 1, value) }
            statement.executeInsert()
        }
        database.setTransactionSuccessful()
        rows.size
    } finally {
        database.endTransaction()
    }
}

private fun SupportSQLiteStatement.bindValue(index: Int, value: Any?) {
    when (value) {
        null -> bindNull(index)
        is ByteArray -> bindBlob(index, value)
        is String -> bindString(index, value)
        is Float -> bindDouble(index, value.toDouble())
        is Double -> bindDouble(index, value)
        is Boolean -> bindLong(index, if (value) 1L else 0L)
        is Number -> bindLong(index, value.toLong())
        else -> error("Unsupported database value type ${value::class.java.name}")
    }
}

private fun Cursor.values(count: Int): List<Any?> {
    return (0 until count).map { index ->
        when (getType(index)) {
            Cursor.FIELD_TYPE_NULL -> null
            Cursor.FIELD_TYPE_INTEGER -> getLong(index)
            Cursor.FIELD_TYPE_FLOAT -> getDouble(index)
            Cursor.FIELD_TYPE_BLOB -> getBlob(index)
            else -> getString(index)
        }
    }
}

private fun Cursor.stringOrNull(index: Int): String? {
    return if (isNull(index)) null else getString(index)
}

private fun SQLiteDatabase.hasTable(tableName: String): Boolean {
    return rawQuery(
        "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ? LIMIT 1",
        arrayOf(tableName)
    ).use(Cursor::moveToFirst)
}

private fun SQLiteDatabase.recoveryCopyRowCount(): Long {
    return RECOVERY_COPY_TABLES.sumOf { tableName ->
        if (!hasTable(tableName)) {
            0L
        } else {
            rawQuery("SELECT COUNT(*) FROM $tableName", null).use { cursor ->
                if (cursor.moveToFirst()) cursor.getLong(0) else 0L
            }
        }
    }
}

private class RecoveryProgressTracker(
    private val total: Long,
    private val onProgress: (CacheDatabaseStartupProgress) -> Unit
) {
    private var stage = "Preparing database recovery"
    private var processed = 0L

    fun changeStage(value: String) {
        stage = value
        publish()
    }

    fun rowsCopied(count: Int) {
        processed = (processed + count).coerceAtMost(total)
        publish()
    }

    fun completeCopy() {
        processed = total
        publish()
    }

    private fun publish() {
        onProgress(
            CacheDatabaseStartupProgress(
                stage = stage,
                processed = processed,
                total = total
            )
        )
    }
}

private fun readDatabaseVersion(file: File): Int {
    val database = SQLiteDatabase.openDatabase(
        file.absolutePath,
        null,
        SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS,
        PRESERVE_CORRUPT_DATABASE_HANDLER
    )
    return try {
        database.version
    } finally {
        database.close()
    }
}

private fun restoreInterruptedDatabaseReplacement(context: Context, databaseName: String) {
    val source = context.getDatabasePath(databaseName)
    val backupName = "$databaseName$BACKUP_DATABASE_SUFFIX"
    val backup = context.getDatabasePath(backupName)
    if (!source.exists() && backup.exists()) moveDatabaseFiles(context, backupName, databaseName)
    if (source.exists()) context.deleteDatabase("$databaseName$UPGRADE_DATABASE_SUFFIX")
}

private fun replaceDatabaseFiles(
    context: Context,
    databaseName: String,
    upgradeName: String,
    backupName: String
) {
    moveDatabaseFiles(context, databaseName, backupName)
    try {
        moveDatabaseFiles(context, upgradeName, databaseName)
    } catch (error: Exception) {
        moveDatabaseFiles(context, backupName, databaseName)
        throw error
    }
}

private fun moveDatabaseFiles(context: Context, fromName: String, toName: String) {
    val from = context.getDatabasePath(fromName)
    val to = context.getDatabasePath(toName)
    check(from.exists()) { "Database replacement source is missing" }
    val moved = mutableListOf<Pair<File, File>>()
    try {
        DATABASE_FILE_SUFFIXES.forEach { suffix ->
            val sourceFile = File(from.path + suffix)
            if (!sourceFile.exists()) return@forEach
            val targetFile = File(to.path + suffix)
            check(!targetFile.exists()) { "Database replacement target already exists" }
            check(sourceFile.renameTo(targetFile)) { "Database replacement failed" }
            moved += sourceFile to targetFile
        }
    } catch (error: Exception) {
        moved.asReversed().forEach { (sourceFile, targetFile) ->
            if (targetFile.exists()) targetFile.renameTo(sourceFile)
        }
        throw error
    }
}

private const val LEGACY_RECOVERY_DATABASE_VERSION = 21
private const val RECOVERY_COPY_BATCH_SIZE = 500
private const val UPGRADE_DATABASE_SUFFIX = ".upgrade-v24"
private const val BACKUP_DATABASE_SUFFIX = ".pre-v24"
private val RECOVERY_COPY_TABLES = listOf(
    "cached_files",
    "scan_reports",
    "trash_entries",
    "similarity_settings",
    "similarity_clusters",
    "similarity_cluster_members",
    "similarity_duration_features",
    "similarity_maintenance_runs"
)
private val DATABASE_FILE_SUFFIXES = listOf("", "-wal", "-shm", "-journal")
private val PRESERVE_CORRUPT_DATABASE_HANDLER = DatabaseErrorHandler { }
