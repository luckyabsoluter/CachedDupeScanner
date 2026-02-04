package opensource.cached_dupe_scanner.storage

import opensource.cached_dupe_scanner.cache.CachedFileEntity
import opensource.cached_dupe_scanner.cache.FileCacheDao
import opensource.cached_dupe_scanner.core.FileMetadata

class PagedFileRepository(
    private val dao: FileCacheDao
) {
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

    fun countAll(): Int = dao.countAll()

    fun loadPage(sortKey: SortKey, direction: SortDirection, cursor: Cursor, limit: Int): Page {
        val entities = when (sortKey) {
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

        val items = entities.map { it.toMetadata() }
        val last = entities.lastOrNull()
        val nextCursor = when {
            last == null -> null
            sortKey == SortKey.Name -> Cursor.Name(last.normalizedPath)
            sortKey == SortKey.Size -> Cursor.Size(last.sizeBytes, last.normalizedPath)
            else -> Cursor.Modified(last.lastModifiedMillis, last.normalizedPath)
        }
        return Page(items = items, nextCursor = nextCursor)
    }
}

private fun CachedFileEntity.toMetadata(): FileMetadata {
    return FileMetadata(
        path = path,
        normalizedPath = normalizedPath,
        sizeBytes = sizeBytes,
        lastModifiedMillis = lastModifiedMillis,
        hashHex = hashHex
    )
}
