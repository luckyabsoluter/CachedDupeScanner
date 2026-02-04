package opensource.cached_dupe_scanner.storage

import android.content.Context

interface StorageRootProvider {
    fun resolve(context: Context, absolutePath: String): StorageRootResolver.Root?
}

object DefaultStorageRootProvider : StorageRootProvider {
    override fun resolve(context: Context, absolutePath: String): StorageRootResolver.Root? {
        return StorageRootResolver.resolveRootForPath(context, absolutePath)
    }
}
