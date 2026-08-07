package opensource.cached_dupe_scanner.storage

import android.database.Cursor
import android.os.CancellationSignal
import androidx.room.RoomDatabase
import androidx.sqlite.db.SimpleSQLiteQuery
import opensource.cached_dupe_scanner.cache.CachedFileEntity
import opensource.cached_dupe_scanner.cache.DuplicateGroupEntity
import opensource.cached_dupe_scanner.cache.SimilarityClusterDurationStatsRow
import opensource.cached_dupe_scanner.cache.SimilarityClusterEntity
import opensource.cached_dupe_scanner.cache.SimilarityClusterFilterMemberRow
import opensource.cached_dupe_scanner.cache.SimilarityClusterMemberFileRow
import opensource.cached_dupe_scanner.cache.SimilarityFilterMetadataResolutionRow
import opensource.cached_dupe_scanner.cache.SimilaritySettingEntity
import opensource.cached_dupe_scanner.cache.StoredHash
import opensource.cached_dupe_scanner.cache.TrashEntryEntity

@Suppress("DEPRECATION")
internal inline fun <T> RoomDatabase.readRoomQueryWithCancellation(
    sql: String,
    bindArgs: Array<out Any?> = emptyArray(),
    cancellationSignal: CancellationSignal? = null,
    block: (Cursor) -> T
): T {
    return query(SimpleSQLiteQuery(sql, bindArgs), cancellationSignal).use(block)
}

internal fun RoomDatabase.readCachedFilesWithCancellation(
    sql: String,
    bindArgs: Array<out Any?>,
    cancellationSignal: CancellationSignal?
): List<CachedFileEntity> {
    return readRoomQueryWithCancellation(sql, bindArgs, cancellationSignal) { cursor ->
        val normalizedPathIndex = cursor.getColumnIndexOrThrow("normalizedPath")
        val pathIndex = cursor.getColumnIndexOrThrow("path")
        val sizeBytesIndex = cursor.getColumnIndexOrThrow("sizeBytes")
        val lastModifiedMillisIndex = cursor.getColumnIndexOrThrow("lastModifiedMillis")
        val hashBytesIndex = cursor.getColumnIndexOrThrow("hashBytes")
        val fileIdIndex = cursor.getColumnIndexOrThrow("fileId")
        buildList {
            while (cursor.moveToNext()) {
                add(
                    CachedFileEntity(
                        normalizedPath = cursor.getString(normalizedPathIndex),
                        path = cursor.getString(pathIndex),
                        sizeBytes = cursor.getLong(sizeBytesIndex),
                        lastModifiedMillis = cursor.getLong(lastModifiedMillisIndex),
                        hashBytes = cursor.storedHashOrNull(hashBytesIndex),
                        fileId = cursor.getLong(fileIdIndex)
                    )
                )
            }
        }
    }
}

internal fun RoomDatabase.readDuplicateGroupsWithCancellation(
    sql: String,
    bindArgs: Array<out Any?>,
    cancellationSignal: CancellationSignal?
): List<DuplicateGroupEntity> {
    return readRoomQueryWithCancellation(sql, bindArgs, cancellationSignal) { cursor ->
        val sizeBytesIndex = cursor.getColumnIndexOrThrow("sizeBytes")
        val hashBytesIndex = cursor.getColumnIndexOrThrow("hashBytes")
        val fileCountIndex = cursor.getColumnIndexOrThrow("fileCount")
        val totalBytesIndex = cursor.getColumnIndexOrThrow("totalBytes")
        val updatedAtMillisIndex = cursor.getColumnIndexOrThrow("updatedAtMillis")
        buildList {
            while (cursor.moveToNext()) {
                add(
                    DuplicateGroupEntity(
                        sizeBytes = cursor.getLong(sizeBytesIndex),
                        hashBytes = checkNotNull(cursor.storedHashOrNull(hashBytesIndex)),
                        fileCount = cursor.getInt(fileCountIndex),
                        totalBytes = cursor.getLong(totalBytesIndex),
                        updatedAtMillis = cursor.getLong(updatedAtMillisIndex)
                    )
                )
            }
        }
    }
}

internal fun RoomDatabase.readTrashEntriesWithCancellation(
    sql: String,
    bindArgs: Array<out Any?>,
    cancellationSignal: CancellationSignal?
): List<TrashEntryEntity> {
    return readRoomQueryWithCancellation(sql, bindArgs, cancellationSignal) { cursor ->
        val idIndex = cursor.getColumnIndexOrThrow("id")
        val originalPathIndex = cursor.getColumnIndexOrThrow("originalPath")
        val trashedPathIndex = cursor.getColumnIndexOrThrow("trashedPath")
        val sizeBytesIndex = cursor.getColumnIndexOrThrow("sizeBytes")
        val lastModifiedMillisIndex = cursor.getColumnIndexOrThrow("lastModifiedMillis")
        val hashHexIndex = cursor.getColumnIndexOrThrow("hashHex")
        val deletedAtMillisIndex = cursor.getColumnIndexOrThrow("deletedAtMillis")
        val volumeRootIndex = cursor.getColumnIndexOrThrow("volumeRoot")
        buildList {
            while (cursor.moveToNext()) {
                add(
                    TrashEntryEntity(
                        id = cursor.getString(idIndex),
                        originalPath = cursor.getString(originalPathIndex),
                        trashedPath = cursor.getString(trashedPathIndex),
                        sizeBytes = cursor.getLong(sizeBytesIndex),
                        lastModifiedMillis = cursor.getLong(lastModifiedMillisIndex),
                        hashHex = cursor.stringOrNull(hashHexIndex),
                        deletedAtMillis = cursor.getLong(deletedAtMillisIndex),
                        volumeRoot = cursor.getString(volumeRootIndex)
                    )
                )
            }
        }
    }
}

internal fun RoomDatabase.readSimilaritySettingsWithCancellation(
    sql: String,
    bindArgs: Array<out Any?>,
    cancellationSignal: CancellationSignal?
): List<SimilaritySettingEntity> {
    return readRoomQueryWithCancellation(sql, bindArgs, cancellationSignal) { cursor ->
        val settingIdIndex = cursor.getColumnIndexOrThrow("settingId")
        val methodIdIndex = cursor.getColumnIndexOrThrow("methodId")
        val mediaScopeIndex = cursor.getColumnIndexOrThrow("mediaScope")
        val minSizeBytesIndex = cursor.getColumnIndexOrThrow("minSizeBytes")
        val paramsJsonIndex = cursor.getColumnIndexOrThrow("paramsJson")
        val paramsHashIndex = cursor.getColumnIndexOrThrow("paramsHash")
        val displayNameIndex = cursor.getColumnIndexOrThrow("displayName")
        val enabledIndex = cursor.getColumnIndexOrThrow("enabled")
        val createdAtMillisIndex = cursor.getColumnIndexOrThrow("createdAtMillis")
        val updatedAtMillisIndex = cursor.getColumnIndexOrThrow("updatedAtMillis")
        buildList {
            while (cursor.moveToNext()) {
                add(
                    SimilaritySettingEntity(
                        settingId = cursor.getLong(settingIdIndex),
                        methodId = cursor.getString(methodIdIndex),
                        mediaScope = cursor.getString(mediaScopeIndex),
                        minSizeBytes = cursor.getLong(minSizeBytesIndex),
                        paramsJson = cursor.getString(paramsJsonIndex),
                        paramsHash = cursor.getString(paramsHashIndex),
                        displayName = cursor.getString(displayNameIndex),
                        enabled = cursor.getInt(enabledIndex) != 0,
                        createdAtMillis = cursor.getLong(createdAtMillisIndex),
                        updatedAtMillis = cursor.getLong(updatedAtMillisIndex)
                    )
                )
            }
        }
    }
}

internal fun RoomDatabase.readSimilarityClustersWithCancellation(
    sql: String,
    bindArgs: Array<out Any?>,
    cancellationSignal: CancellationSignal?
): List<SimilarityClusterEntity> {
    return readRoomQueryWithCancellation(sql, bindArgs, cancellationSignal) { cursor ->
        val clusterIdIndex = cursor.getColumnIndexOrThrow("clusterId")
        val settingIdIndex = cursor.getColumnIndexOrThrow("settingId")
        val clusterKeyIndex = cursor.getColumnIndexOrThrow("clusterKey")
        val fileCountIndex = cursor.getColumnIndexOrThrow("fileCount")
        val totalBytesIndex = cursor.getColumnIndexOrThrow("totalBytes")
        val updatedAtMillisIndex = cursor.getColumnIndexOrThrow("updatedAtMillis")
        buildList {
            while (cursor.moveToNext()) {
                add(
                    SimilarityClusterEntity(
                        clusterId = cursor.getLong(clusterIdIndex),
                        settingId = cursor.getLong(settingIdIndex),
                        clusterKey = cursor.getString(clusterKeyIndex),
                        fileCount = cursor.getInt(fileCountIndex),
                        totalBytes = cursor.getLong(totalBytesIndex),
                        updatedAtMillis = cursor.getLong(updatedAtMillisIndex)
                    )
                )
            }
        }
    }
}

internal fun RoomDatabase.readSimilarityClusterMembersWithCancellation(
    sql: String,
    bindArgs: Array<out Any?>,
    cancellationSignal: CancellationSignal?
): List<SimilarityClusterMemberFileRow> {
    return readRoomQueryWithCancellation(sql, bindArgs, cancellationSignal) { cursor ->
        val settingIdIndex = cursor.getColumnIndexOrThrow("settingId")
        val fileIdIndex = cursor.getColumnIndexOrThrow("fileId")
        val normalizedPathIndex = cursor.getColumnIndexOrThrow("normalizedPath")
        val pathIndex = cursor.getColumnIndexOrThrow("path")
        val sizeBytesIndex = cursor.getColumnIndexOrThrow("sizeBytes")
        val lastModifiedMillisIndex = cursor.getColumnIndexOrThrow("lastModifiedMillis")
        val hashBytesIndex = cursor.getColumnIndexOrThrow("hashBytes")
        val durationMillisIndex = cursor.getColumnIndexOrThrow("durationMillis")
        val widthPixelsIndex = cursor.getColumnIndexOrThrow("widthPixels")
        val heightPixelsIndex = cursor.getColumnIndexOrThrow("heightPixels")
        val dimensionsCheckedIndex = cursor.getColumnIndexOrThrow("dimensionsChecked")
        val durationCheckedIndex = cursor.getColumnIndexOrThrow("durationChecked")
        buildList {
            while (cursor.moveToNext()) {
                add(
                    SimilarityClusterMemberFileRow(
                        settingId = cursor.getLong(settingIdIndex),
                        fileId = cursor.getLong(fileIdIndex),
                        normalizedPath = cursor.getString(normalizedPathIndex),
                        path = cursor.getString(pathIndex),
                        sizeBytes = cursor.getLong(sizeBytesIndex),
                        lastModifiedMillis = cursor.getLong(lastModifiedMillisIndex),
                        hashBytes = cursor.storedHashOrNull(hashBytesIndex),
                        durationMillis = cursor.longOrNull(durationMillisIndex),
                        widthPixels = cursor.intOrNull(widthPixelsIndex),
                        heightPixels = cursor.intOrNull(heightPixelsIndex),
                        dimensionsChecked = cursor.getInt(dimensionsCheckedIndex) != 0,
                        durationChecked = cursor.getInt(durationCheckedIndex) != 0
                    )
                )
            }
        }
    }
}

internal fun RoomDatabase.readSimilarityFilterMembersWithCancellation(
    sql: String,
    bindArgs: Array<out Any?>,
    cancellationSignal: CancellationSignal?
): List<SimilarityClusterFilterMemberRow> {
    return readRoomQueryWithCancellation(sql, bindArgs, cancellationSignal) { cursor ->
        val clusterIdIndex = cursor.getColumnIndexOrThrow("clusterId")
        val positionIndex = cursor.getColumnIndexOrThrow("position")
        val fileIdIndex = cursor.getColumnIndexOrThrow("fileId")
        val normalizedPathIndex = cursor.getColumnIndexOrThrow("normalizedPath")
        val pathIndex = cursor.getColumnIndexOrThrow("path")
        val sizeBytesIndex = cursor.getColumnIndexOrThrow("sizeBytes")
        val lastModifiedMillisIndex = cursor.getColumnIndexOrThrow("lastModifiedMillis")
        val durationMillisIndex = cursor.getColumnIndexOrThrow("durationMillis")
        val widthPixelsIndex = cursor.getColumnIndexOrThrow("widthPixels")
        val heightPixelsIndex = cursor.getColumnIndexOrThrow("heightPixels")
        buildList {
            while (cursor.moveToNext()) {
                add(
                    SimilarityClusterFilterMemberRow(
                        clusterId = cursor.getLong(clusterIdIndex),
                        position = cursor.getInt(positionIndex),
                        fileId = cursor.getLong(fileIdIndex),
                        normalizedPath = cursor.getString(normalizedPathIndex),
                        path = cursor.getString(pathIndex),
                        sizeBytes = cursor.getLong(sizeBytesIndex),
                        lastModifiedMillis = cursor.getLong(lastModifiedMillisIndex),
                        durationMillis = cursor.longOrNull(durationMillisIndex),
                        widthPixels = cursor.intOrNull(widthPixelsIndex),
                        heightPixels = cursor.intOrNull(heightPixelsIndex)
                    )
                )
            }
        }
    }
}

internal fun RoomDatabase.readSimilarityFilterResolutionRowsWithCancellation(
    sql: String,
    bindArgs: Array<out Any?>,
    cancellationSignal: CancellationSignal?
): List<SimilarityFilterMetadataResolutionRow> {
    return readRoomQueryWithCancellation(sql, bindArgs, cancellationSignal) { cursor ->
        val clusterIdIndex = cursor.getColumnIndexOrThrow("clusterId")
        val positionIndex = cursor.getColumnIndexOrThrow("position")
        val settingIdIndex = cursor.getColumnIndexOrThrow("settingId")
        val fileIdIndex = cursor.getColumnIndexOrThrow("fileId")
        val normalizedPathIndex = cursor.getColumnIndexOrThrow("normalizedPath")
        val pathIndex = cursor.getColumnIndexOrThrow("path")
        val sizeBytesIndex = cursor.getColumnIndexOrThrow("sizeBytes")
        val lastModifiedMillisIndex = cursor.getColumnIndexOrThrow("lastModifiedMillis")
        val hashBytesIndex = cursor.getColumnIndexOrThrow("hashBytes")
        val durationMillisIndex = cursor.getColumnIndexOrThrow("durationMillis")
        val widthPixelsIndex = cursor.getColumnIndexOrThrow("widthPixels")
        val heightPixelsIndex = cursor.getColumnIndexOrThrow("heightPixels")
        val dimensionsCheckedIndex = cursor.getColumnIndexOrThrow("dimensionsChecked")
        val durationCheckedIndex = cursor.getColumnIndexOrThrow("durationChecked")
        buildList {
            while (cursor.moveToNext()) {
                add(
                    SimilarityFilterMetadataResolutionRow(
                        clusterId = cursor.getLong(clusterIdIndex),
                        position = cursor.getInt(positionIndex),
                        settingId = cursor.getLong(settingIdIndex),
                        fileId = cursor.getLong(fileIdIndex),
                        normalizedPath = cursor.getString(normalizedPathIndex),
                        path = cursor.getString(pathIndex),
                        sizeBytes = cursor.getLong(sizeBytesIndex),
                        lastModifiedMillis = cursor.getLong(lastModifiedMillisIndex),
                        hashBytes = cursor.storedHashOrNull(hashBytesIndex),
                        durationMillis = cursor.longOrNull(durationMillisIndex),
                        widthPixels = cursor.intOrNull(widthPixelsIndex),
                        heightPixels = cursor.intOrNull(heightPixelsIndex),
                        dimensionsChecked = cursor.getInt(dimensionsCheckedIndex) != 0,
                        durationChecked = cursor.getInt(durationCheckedIndex) != 0
                    )
                )
            }
        }
    }
}

internal fun RoomDatabase.readSimilarityDurationStatsWithCancellation(
    sql: String,
    bindArgs: Array<out Any?>,
    cancellationSignal: CancellationSignal?
): List<SimilarityClusterDurationStatsRow> {
    return readRoomQueryWithCancellation(sql, bindArgs, cancellationSignal) { cursor ->
        val clusterIdIndex = cursor.getColumnIndexOrThrow("clusterId")
        val clusterUpdatedAtMillisIndex = cursor.getColumnIndexOrThrow("clusterUpdatedAtMillis")
        val memberCountIndex = cursor.getColumnIndexOrThrow("memberCount")
        val checkedCountIndex = cursor.getColumnIndexOrThrow("checkedCount")
        val durationCountIndex = cursor.getColumnIndexOrThrow("durationCount")
        val durationSumMillisIndex = cursor.getColumnIndexOrThrow("durationSumMillis")
        val minimumDurationMillisIndex = cursor.getColumnIndexOrThrow("minimumDurationMillis")
        val maximumDurationMillisIndex = cursor.getColumnIndexOrThrow("maximumDurationMillis")
        buildList {
            while (cursor.moveToNext()) {
                add(
                    SimilarityClusterDurationStatsRow(
                        clusterId = cursor.getLong(clusterIdIndex),
                        clusterUpdatedAtMillis = cursor.getLong(clusterUpdatedAtMillisIndex),
                        memberCount = cursor.getLong(memberCountIndex),
                        checkedCount = cursor.getLong(checkedCountIndex),
                        durationCount = cursor.getLong(durationCountIndex),
                        durationSumMillis = cursor.longOrNull(durationSumMillisIndex),
                        minimumDurationMillis = cursor.longOrNull(minimumDurationMillisIndex),
                        maximumDurationMillis = cursor.longOrNull(maximumDurationMillisIndex)
                    )
                )
            }
        }
    }
}

private fun Cursor.storedHashOrNull(index: Int): StoredHash? {
    return if (isNull(index)) null else StoredHash.fromStorageBytes(getBlob(index))
}

private fun Cursor.stringOrNull(index: Int): String? {
    return if (isNull(index)) null else getString(index)
}

private fun Cursor.longOrNull(index: Int): Long? {
    return if (isNull(index)) null else getLong(index)
}

private fun Cursor.intOrNull(index: Int): Int? {
    return if (isNull(index)) null else getInt(index)
}
