package opensource.cached_dupe_scanner.storage

import android.content.Context
import android.os.Build
import opensource.cached_dupe_scanner.cache.CacheDatabase
import opensource.cached_dupe_scanner.cache.CachedFileEntity
import opensource.cached_dupe_scanner.cache.TrashEntryEntity
import java.io.File
import java.util.UUID

class TrashController(
    private val context: Context,
    private val database: CacheDatabase,
    private val historyRepo: ScanHistoryRepository,
    private val trashRepo: TrashRepository,
    private val storageRootProvider: StorageRootProvider = DefaultStorageRootProvider
) {
    data class MoveResult(
        val success: Boolean,
        val entry: TrashEntryEntity? = null,
        val message: String? = null
    )

    sealed class RestoreResult {
        data object Success : RestoreResult()
        data object ConflictTargetExists : RestoreResult()
        data object TrashedFileMissing : RestoreResult()
        data object MoveFailed : RestoreResult()
        data object DbUpdateFailed : RestoreResult()
    }

    fun moveToTrash(normalizedPath: String): MoveResult {
        val file = File(normalizedPath)
        if (!file.exists()) {
            // Treat as success: if it's gone, remove cache and don't create trash entry.
            database.runInTransaction {
                database.fileCacheDao().deleteByNormalizedPath(normalizedPath)
            }
            return MoveResult(success = true, entry = null)
        }

        val root = storageRootProvider.resolve(context, file.absolutePath)
            ?: return MoveResult(false, message = "Unable to resolve storage root")

        val rootDir = File(root.rootPath)
        val trashDir = TrashPaths.ensureTrashLayout(rootDir)
            .getOrElse { return MoveResult(false, message = it.message ?: "Failed to initialize trash layout") }

        val id = UUID.randomUUID().toString()
        val deletedAt = System.currentTimeMillis()
        val baseName = file.name.ifBlank { "file" }
        val destName = "${deletedAt}_${id.take(8)}_$baseName"
        val destFile = File(trashDir, destName)

        val cached = database.fileCacheDao().getByNormalizedPath(normalizedPath)
        val snapshotSize = cached?.sizeBytes ?: file.length()
        val snapshotModified = cached?.lastModifiedMillis ?: file.lastModified()
        val snapshotHash = cached?.hashHex

        val moved = moveFile(file, destFile)
        if (!moved) {
            return MoveResult(false, message = "Failed to move file")
        }

        val entry = TrashEntryEntity(
            id = id,
            originalPath = normalizedPath,
            trashedPath = destFile.absolutePath,
            sizeBytes = snapshotSize,
            lastModifiedMillis = snapshotModified,
            hashHex = snapshotHash,
            deletedAtMillis = deletedAt,
            volumeRoot = root.rootPath
        )

        runCatching {
            database.runInTransaction {
                database.fileCacheDao().deleteByNormalizedPath(normalizedPath)
                trashRepo.upsert(entry)
            }
        }.onFailure {
            // Best-effort rollback
            moveFile(destFile, file)
            return MoveResult(false, message = "DB update failed")
        }

        return MoveResult(true, entry = entry)
    }

    fun restoreFromTrash(entry: TrashEntryEntity): RestoreResult {
        val trashed = File(entry.trashedPath)
        if (!trashed.exists()) {
            database.runInTransaction {
                trashRepo.deleteById(entry.id)
            }
            return RestoreResult.TrashedFileMissing
        }

        val target = File(entry.originalPath)
        if (target.exists()) {
            return RestoreResult.ConflictTargetExists
        }
        target.parentFile?.let { parent ->
            if (!parent.exists()) {
                parent.mkdirs()
            }
        }

        val restored = moveFile(trashed, target)
        if (!restored) return RestoreResult.MoveFailed

        val restoredSize = target.length()
        val restoredModified = target.lastModified()
        val restoredEntity = CachedFileEntity(
            normalizedPath = entry.originalPath,
            path = entry.originalPath,
            sizeBytes = restoredSize,
            lastModifiedMillis = restoredModified,
            hashHex = entry.hashHex
        )

        return runCatching {
            database.runInTransaction {
                database.fileCacheDao().upsert(restoredEntity)
                trashRepo.deleteById(entry.id)
            }
            RestoreResult.Success
        }.getOrElse {
            // Best-effort rollback: move back to trash location and keep DB entry
            moveFile(target, trashed)
            RestoreResult.DbUpdateFailed
        }
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
