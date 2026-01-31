package opensource.cached_dupe_scanner.core

import java.io.File

data class FileMetadata(
    val path: String,
    val normalizedPath: String,
    val sizeBytes: Long,
    val lastModifiedMillis: Long,
    val hashHex: String? = null
) {
    companion object {
        fun fromFile(file: File, hashHex: String? = null): FileMetadata {
            return FileMetadata(
                path = file.path,
                normalizedPath = PathNormalizer.normalize(file.path),
                sizeBytes = file.length(),
                lastModifiedMillis = file.lastModified(),
                hashHex = hashHex
            )
        }
    }
}
