package opensource.cached_dupe_scanner.engine

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import opensource.cached_dupe_scanner.cache.CacheStatus
import opensource.cached_dupe_scanner.cache.CacheStore
import opensource.cached_dupe_scanner.core.DuplicateGroup
import opensource.cached_dupe_scanner.core.FileMetadata
import opensource.cached_dupe_scanner.core.Hashing
import opensource.cached_dupe_scanner.core.ScanResult

class DocumentScanner(
    private val cacheStore: CacheStore,
    private val context: Context,
    private val walker: DocumentFileWalker = DocumentFileWalker()
) {
    fun scan(treeUri: Uri, ignore: (DocumentFile) -> Boolean = { false }): ScanResult {
        val scannedAtMillis = System.currentTimeMillis()
        val root = DocumentFile.fromTreeUri(context, treeUri)
            ?: return ScanResult(scannedAtMillis, emptyList(), emptyList())

        val files = mutableListOf<FileMetadata>()

        walker.walk(root, ignore).forEach { doc ->
            val metadata = doc.toMetadata()
            if (metadata == null) {
                return@forEach
            }

            val cached = cacheStore.lookup(metadata)
            val hashHex = when (cached.status) {
                CacheStatus.FRESH -> cached.cached?.hashHex
                CacheStatus.STALE, CacheStatus.MISS -> hashDocument(doc)
            }

            if (hashHex == null) {
                return@forEach
            }

            val finalMetadata = metadata.copy(hashHex = hashHex)
            cacheStore.upsert(finalMetadata)
            files.add(finalMetadata)
        }

        val duplicateGroups = files
            .filter { it.hashHex != null }
            .groupBy { it.hashHex!! }
            .filterValues { it.size > 1 }
            .map { (hash, groupFiles) -> DuplicateGroup(hash, groupFiles) }

        return ScanResult(scannedAtMillis, files, duplicateGroups)
    }

    private fun hashDocument(doc: DocumentFile): String? {
        return context.contentResolver.openInputStream(doc.uri)?.use { input ->
            Hashing.sha256Hex(input)
        }
    }

    private fun DocumentFile.toMetadata(): FileMetadata? {
        val name = name ?: return null
        val path = uri.toString()
        return FileMetadata(
            path = path,
            normalizedPath = path,
            sizeBytes = length(),
            lastModifiedMillis = lastModified(),
            hashHex = null
        )
    }
}
