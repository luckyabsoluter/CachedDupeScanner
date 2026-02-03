package opensource.cached_dupe_scanner.storage

import android.content.Context
import java.io.File

/**
 * Resolves which external storage root a path belongs to, without hardcoding `/storage/...` parsing.
 *
 * Strategy:
 * - Use Context.getExternalFilesDirs(null) to discover mounted volume roots.
 * - Each dir looks like: <volumeRoot>/Android/data/<package>/files
 * - Walk up to the volumeRoot and match file paths by longest prefix.
 */
object StorageRootResolver {
    data class Root(val rootPath: String)

    fun discoverRoots(context: Context): List<Root> {
        val packageName = context.packageName
        val dirs = context.getExternalFilesDirs(null).filterNotNull()
        val roots = dirs.mapNotNull { externalFilesDir ->
            volumeRootFromExternalFilesDir(externalFilesDir, packageName)
        }
        return roots
            .distinctBy { it.rootPath }
            .sortedByDescending { it.rootPath.length }
    }

    fun resolveRootForPath(context: Context, absolutePath: String): Root? {
        val file = File(absolutePath)
        val canonicalPath = runCatching { file.canonicalPath }.getOrDefault(file.absolutePath)
        val normalizedPath = canonicalPath.replace('\\', '/')
        val roots = discoverRoots(context)
        return roots.firstOrNull { root ->
            normalizedPath == root.rootPath || normalizedPath.startsWith(root.rootPath.trimEnd('/') + "/")
        }
    }

    fun volumeRootFromExternalFilesDir(externalFilesDir: File, packageName: String): Root? {
        // Use the raw path string to avoid host-specific canonicalization (e.g., Windows drive letters)
        // and normalize separators to Android-style '/' for consistent matching on-device.
        val segments = externalFilesDir.path.split('/', '\\').filter { it.isNotBlank() }
        val androidIndex = segments.indexOf("Android")
        if (androidIndex < 0) return null
        if (androidIndex + 3 >= segments.size) return null
        if (segments[androidIndex + 1] != "data") return null
        if (segments[androidIndex + 2] != packageName) return null
        // volumeRoot is everything before "Android"
        val rootSegments = segments.take(androidIndex)
        if (rootSegments.isEmpty()) return null
        val rootPath = "/" + rootSegments.joinToString("/")
        return Root(rootPath)
    }
}
