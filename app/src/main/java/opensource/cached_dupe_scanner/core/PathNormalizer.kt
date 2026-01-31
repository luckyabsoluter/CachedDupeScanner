package opensource.cached_dupe_scanner.core

import java.nio.file.Paths

object PathNormalizer {
    fun normalize(path: String): String {
        val trimmed = path.trim()
        if (trimmed.isEmpty()) {
            return trimmed
        }

        val cleaned = trimmed.replace('\\', '/')
        return try {
            Paths.get(cleaned)
                .normalize()
                .toString()
                .replace('\\', '/')
        } catch (exception: Exception) {
            cleaned
        }
    }
}
