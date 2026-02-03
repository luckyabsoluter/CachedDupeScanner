package opensource.cached_dupe_scanner.storage

import java.io.File

object TrashPaths {
    private const val APP_DIR = ".CachedDupeScanner"

    fun appRoot(volumeRoot: File): File = File(volumeRoot, APP_DIR)

    fun trashBinDir(volumeRoot: File): File = File(appRoot(volumeRoot), "trashbin")

    /**
     * Ensures:
     * - <volumeRoot>/.CachedDupeScanner/
     * - <volumeRoot>/.CachedDupeScanner/.nomedia
     * - <volumeRoot>/.CachedDupeScanner/trashbin/
     */
    fun ensureTrashLayout(volumeRoot: File): Result<File> {
        return runCatching {
            val appRoot = appRoot(volumeRoot)
            if (!appRoot.exists() && !appRoot.mkdirs()) {
                error("Failed to create ${appRoot.absolutePath}")
            }

            val nomedia = File(appRoot, ".nomedia")
            if (!nomedia.exists()) {
                // createNewFile returns false if it already exists.
                nomedia.createNewFile()
            }

            val trashDir = trashBinDir(volumeRoot)
            if (!trashDir.exists() && !trashDir.mkdirs()) {
                error("Failed to create ${trashDir.absolutePath}")
            }

            trashDir
        }
    }
}
