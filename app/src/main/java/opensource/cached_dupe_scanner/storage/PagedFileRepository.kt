package opensource.cached_dupe_scanner.storage

import android.os.CancellationSignal
import opensource.cached_dupe_scanner.cache.CacheDatabase
import opensource.cached_dupe_scanner.cache.FileCacheDao
import opensource.cached_dupe_scanner.cache.toFileMetadata
import opensource.cached_dupe_scanner.core.FileMetadata

class PagedFileRepository(
    private val dao: FileCacheDao,
    private val database: CacheDatabase? = null
) {
    constructor(database: CacheDatabase) : this(
        dao = database.fileCacheDao(),
        database = database
    )

    enum class SortKey {
        Name,
        Size,
        Modified
    }

    enum class SortDirection {
        Asc,
        Desc
    }

    sealed class Cursor {
        data object Start : Cursor()
        data class Name(val path: String) : Cursor()
        data class Size(val sizeBytes: Long, val path: String) : Cursor()
        data class Modified(val lastModifiedMillis: Long, val path: String) : Cursor()
    }

    data class Page(
        val items: List<FileMetadata>,
        val nextCursor: Cursor?
    )

    fun countAll(cancellationSignal: CancellationSignal? = null): Int {
        val currentDatabase = database
        if (currentDatabase == null || cancellationSignal == null) return dao.countAll()
        return currentDatabase.readRoomQueryWithCancellation(
            sql = "SELECT COUNT(*) FROM cached_files",
            cancellationSignal = cancellationSignal
        ) { cursor ->
            check(cursor.moveToFirst())
            cursor.getInt(0)
        }
    }

    fun loadPage(
        sortKey: SortKey,
        direction: SortDirection,
        cursor: Cursor,
        limit: Int,
        cancellationSignal: CancellationSignal? = null
    ): Page {
        val currentDatabase = database
        val entities = if (currentDatabase != null && cancellationSignal != null) {
            val query = pageQuery(sortKey, direction, cursor, limit)
            currentDatabase.readCachedFilesWithCancellation(
                sql = query.sql,
                bindArgs = query.bindArgs,
                cancellationSignal = cancellationSignal
            )
        } else {
            loadPageFromDao(sortKey, direction, cursor, limit)
        }

        val items = entities.map { it.toFileMetadata() }
        val last = entities.lastOrNull()
        val nextCursor = when {
            last == null -> null
            sortKey == SortKey.Name -> Cursor.Name(last.normalizedPath)
            sortKey == SortKey.Size -> Cursor.Size(last.sizeBytes, last.normalizedPath)
            else -> Cursor.Modified(last.lastModifiedMillis, last.normalizedPath)
        }
        return Page(items = items, nextCursor = nextCursor)
    }

    private fun loadPageFromDao(
        sortKey: SortKey,
        direction: SortDirection,
        cursor: Cursor,
        limit: Int
    ) = when (sortKey) {
        SortKey.Name -> {
            when (direction) {
                SortDirection.Asc -> {
                    val after = (cursor as? Cursor.Name)?.path ?: ""
                    dao.getPageAfter(after, limit)
                }

                SortDirection.Desc -> {
                    when (cursor) {
                        Cursor.Start -> dao.getFirstPageByNameDesc(limit)
                        is Cursor.Name -> dao.getPageBefore(cursor.path, limit)
                        else -> dao.getFirstPageByNameDesc(limit)
                    }
                }
            }
        }

        SortKey.Size -> {
            when (direction) {
                SortDirection.Asc -> {
                    when (cursor) {
                        Cursor.Start -> dao.getFirstPageBySizeAsc(limit)
                        is Cursor.Size -> dao.getPageBySizeAsc(cursor.sizeBytes, cursor.path, limit)
                        else -> dao.getFirstPageBySizeAsc(limit)
                    }
                }

                SortDirection.Desc -> {
                    when (cursor) {
                        Cursor.Start -> dao.getFirstPageBySizeDesc(limit)
                        is Cursor.Size -> dao.getPageBySizeDesc(cursor.sizeBytes, cursor.path, limit)
                        else -> dao.getFirstPageBySizeDesc(limit)
                    }
                }
            }
        }

        SortKey.Modified -> {
            when (direction) {
                SortDirection.Asc -> {
                    when (cursor) {
                        Cursor.Start -> dao.getFirstPageByModifiedAsc(limit)
                        is Cursor.Modified -> dao.getPageByModifiedAsc(cursor.lastModifiedMillis, cursor.path, limit)
                        else -> dao.getFirstPageByModifiedAsc(limit)
                    }
                }

                SortDirection.Desc -> {
                    when (cursor) {
                        Cursor.Start -> dao.getFirstPageByModifiedDesc(limit)
                        is Cursor.Modified -> dao.getPageByModifiedDesc(cursor.lastModifiedMillis, cursor.path, limit)
                        else -> dao.getFirstPageByModifiedDesc(limit)
                    }
                }
            }
        }
    }

    private fun pageQuery(
        sortKey: SortKey,
        direction: SortDirection,
        cursor: Cursor,
        limit: Int
    ): PageQuery {
        return when (sortKey) {
            SortKey.Name -> when (direction) {
                SortDirection.Asc -> PageQuery(
                    sql = "SELECT * FROM cached_files WHERE normalizedPath > ? ORDER BY normalizedPath LIMIT ?",
                    bindArgs = arrayOf<Any?>((cursor as? Cursor.Name)?.path ?: "", limit)
                )
                SortDirection.Desc -> {
                    val nameCursor = cursor as? Cursor.Name
                    if (nameCursor == null) {
                        PageQuery(
                            sql = "SELECT * FROM cached_files ORDER BY normalizedPath DESC LIMIT ?",
                            bindArgs = arrayOf(limit)
                        )
                    } else {
                        PageQuery(
                            sql = "SELECT * FROM cached_files WHERE normalizedPath < ? ORDER BY normalizedPath DESC LIMIT ?",
                            bindArgs = arrayOf<Any?>(nameCursor.path, limit)
                        )
                    }
                }
            }
            SortKey.Size -> {
                val sizeCursor = cursor as? Cursor.Size
                val ascending = direction == SortDirection.Asc
                if (sizeCursor == null) {
                    PageQuery(
                        sql = "SELECT * FROM cached_files ORDER BY sizeBytes ${if (ascending) "ASC" else "DESC"}, normalizedPath ${if (ascending) "ASC" else "DESC"} LIMIT ?",
                        bindArgs = arrayOf(limit)
                    )
                } else {
                    val comparison = if (ascending) ">" else "<"
                    val order = if (ascending) "ASC" else "DESC"
                    PageQuery(
                        sql = "SELECT * FROM cached_files WHERE (sizeBytes $comparison ?) OR (sizeBytes = ? AND normalizedPath $comparison ?) ORDER BY sizeBytes $order, normalizedPath $order LIMIT ?",
                        bindArgs = arrayOf<Any?>(
                            sizeCursor.sizeBytes,
                            sizeCursor.sizeBytes,
                            sizeCursor.path,
                            limit
                        )
                    )
                }
            }
            SortKey.Modified -> {
                val modifiedCursor = cursor as? Cursor.Modified
                val ascending = direction == SortDirection.Asc
                if (modifiedCursor == null) {
                    PageQuery(
                        sql = "SELECT * FROM cached_files ORDER BY lastModifiedMillis ${if (ascending) "ASC" else "DESC"}, normalizedPath ${if (ascending) "ASC" else "DESC"} LIMIT ?",
                        bindArgs = arrayOf(limit)
                    )
                } else {
                    val comparison = if (ascending) ">" else "<"
                    val order = if (ascending) "ASC" else "DESC"
                    PageQuery(
                        sql = "SELECT * FROM cached_files WHERE (lastModifiedMillis $comparison ?) OR (lastModifiedMillis = ? AND normalizedPath $comparison ?) ORDER BY lastModifiedMillis $order, normalizedPath $order LIMIT ?",
                        bindArgs = arrayOf<Any?>(
                            modifiedCursor.lastModifiedMillis,
                            modifiedCursor.lastModifiedMillis,
                            modifiedCursor.path,
                            limit
                        )
                    )
                }
            }
        }
    }

    private data class PageQuery(
        val sql: String,
        val bindArgs: Array<out Any?>
    )
}
