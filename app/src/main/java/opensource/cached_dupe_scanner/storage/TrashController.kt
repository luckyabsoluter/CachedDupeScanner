package opensource.cached_dupe_scanner.storage

import android.content.Context
import android.os.Build
import opensource.cached_dupe_scanner.cache.CacheDatabase
import opensource.cached_dupe_scanner.cache.TrashEntryEntity
import java.io.File
import java.util.UUID

class TrashController(
    private val context: Context,
    private val database: CacheDatabase,
    private val historyRepo: ScanHistoryRepository,
    private val trashRepo: TrashRepository
) {
    data class MoveResult(
        val success: Boolean,
        val entry: TrashEntryEntity? = null,
        val message: String? = null
    )

    fun moveToTrash(normalizedPath: String): MoveResult {
        val file = File(normalizedPath)
        if (!file.exists()) {
            // Treat as success: if it's gone, remove cache and don't create trash entry.
            database.runInTransaction {
                historyRepo.deleteByNormalizedPath(normalizedPath)
            }
            return MoveResult(success = true, entry = null)
        }

        val root = StorageRootResolver.resolveRootForPath(context, file.absolutePath)
            ?: return MoveResult(false, message = "Unable to resolve storage root")

        val rootDir = File(root.rootPath)
        val trashDir = File(rootDir, ".CachedDupeScanner/trashbin")
        if (!trashDir.exists() && !trashDir.mkdirs()) {
            return MoveResult(false, message = "Failed to create trash directory")
        }

        val id = UUID.randomUUID().toString()
        val deletedAt = System.currentTimeMillis()
        val baseName = file.name.ifBlank { "file" }
        val destName = "${deletedAt}_${id.take(8)}_$baseName"
        val destFile = File(trashDir, destName)

        val moved = moveFile(file, destFile)
        if (!moved) {
            return MoveResult(false, message = "Failed to move file")
        }

        val entry = TrashEntryEntity(
            id = id,
            originalPath = normalizedPath,
            trashedPath = destFile.absolutePath,
            sizeBytes = destFile.length(),
            lastModifiedMillis = destFile.lastModified(),
            deletedAtMillis = deletedAt,
            volumeRoot = root.rootPath
        )

        runCatching {
            database.runInTransaction {
                historyRepo.deleteByNormalizedPath(normalizedPath)
                trashRepo.upsert(entry)
            }
        }.onFailure {
            // Best-effort rollback
            moveFile(destFile, file)
            return MoveResult(false, message = "DB update failed")
        }

        return MoveResult(true, entry = entry)
    }

    fun restoreFromTrash(entry: TrashEntryEntity): Boolean {
        val trashed = File(entry.trashedPath)
        if (!trashed.exists()) {
            database.runInTransaction {
                trashRepo.deleteById(entry.id)
            }
            return false
        }

        val target = File(entry.originalPath)
        target.parentFile?.let { parent ->
            if (!parent.exists()) {
                parent.mkdirs()
            }
        }

        val restored = moveFile(trashed, target)
        if (!restored) return false

        database.runInTransaction {
            trashRepo.deleteById(entry.id)
        }
        return true
    }

    fun deletePermanently(entry: TrashEntryEntity): Boolean {
        val trashed = File(entry.trashedPath)
        val deleted = if (trashed.exists()) trashed.delete() else true
        if (!deleted) return false
        database.runInTransaction {
            trashRepo.deleteById(entry.id)
        }
        return true
    }

    fun emptyTrash(): Int {
        val entries = trashRepo.listAll()
        var deletedCount = 0
        entries.forEach { entry ->
            if (deletePermanently(entry)) {
                deletedCount += 1
            }
        }
        return deletedCount
    }

    private fun moveFile(source: File, dest: File): Boolean {
        if (dest.exists()) {
            dest.delete()
        }
        // Fast path
        if (source.renameTo(dest)) return true

        // Fallback to copy+delete for older API or rename failures.
        return runCatching {
            if (Build.VERSION.SDK_INT >= 26) {
                // java.nio is available from API 26
                java.nio.file.Files.move(
                    source.toPath(),
                    dest.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING
                )
                true
            } else {
                source.inputStream().use { input ->
                    dest.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                val deleted = source.delete()
                deleted
            }
        }.getOrDefault(false)
    }
}
