package opensource.cached_dupe_scanner.core

enum class PathMatchCase {
    SAME_SIZE_SAME_HASH,
    DIFF_SIZE_SAME_HASH,
    SAME_SIZE_DIFF_HASH,
    DIFF_SIZE_DIFF_HASH,
    HASH_MISSING
}

data class PathMatchResult(
    val path: String,
    val matchCase: PathMatchCase,
    val existing: FileMetadata,
    val incoming: FileMetadata
)

object PathMatchAnalyzer {
    fun analyze(existing: FileMetadata, incoming: FileMetadata): PathMatchResult {
        val path = existing.normalizedPath
        val existingHash = existing.hashHex
        val incomingHash = incoming.hashHex

        if (existingHash.isNullOrBlank() || incomingHash.isNullOrBlank()) {
            return PathMatchResult(path, PathMatchCase.HASH_MISSING, existing, incoming)
        }

        val sameSize = existing.sizeBytes == incoming.sizeBytes
        val sameHash = existingHash == incomingHash

        val matchCase = when {
            sameSize && sameHash -> PathMatchCase.SAME_SIZE_SAME_HASH
            !sameSize && sameHash -> PathMatchCase.DIFF_SIZE_SAME_HASH
            sameSize && !sameHash -> PathMatchCase.SAME_SIZE_DIFF_HASH
            else -> PathMatchCase.DIFF_SIZE_DIFF_HASH
        }

        return PathMatchResult(path, matchCase, existing, incoming)
    }
}
