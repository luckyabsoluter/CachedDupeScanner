package opensource.cached_dupe_scanner.core

object PathNormalizer {
    fun normalize(path: String): String {
        val trimmed = path.trim()
        if (trimmed.isEmpty()) {
            return trimmed
        }

        val cleaned = trimmed.replace('\\', '/')
        val (prefix, remainder) = splitPrefix(cleaned)
        val normalizedSegments = normalizeSegments(remainder)
        return joinPrefix(prefix, normalizedSegments)
    }

    private fun splitPrefix(path: String): Pair<String, String> {
        return when {
            path.length >= 2 && path[1] == ':' -> {
                val hasSlash = path.length >= 3 && path[2] == '/'
                val prefix = if (hasSlash) path.substring(0, 3) else path.substring(0, 2)
                val remainder = if (hasSlash) path.substring(3) else path.substring(2)
                prefix to remainder
            }
            path.startsWith("/") -> {
                "/" to path.substring(1)
            }
            else -> "" to path
        }
    }

    private fun normalizeSegments(path: String): List<String> {
        if (path.isEmpty()) {
            return emptyList()
        }

        val output = mutableListOf<String>()
        val segments = path.split('/').filter { it.isNotEmpty() }
        for (segment in segments) {
            when (segment) {
                "." -> Unit
                ".." -> {
                    if (output.isNotEmpty() && output.last() != "..") {
                        output.removeAt(output.size - 1)
                    } else {
                        output.add("..")
                    }
                }
                else -> output.add(segment)
            }
        }
        return output
    }

    private fun joinPrefix(prefix: String, segments: List<String>): String {
        val joined = segments.joinToString("/")
        if (prefix.isEmpty()) {
            return joined
        }
        if (joined.isEmpty()) {
            return prefix
        }
        return if (prefix.endsWith("/")) {
            prefix + joined
        } else {
            "$prefix/$joined"
        }
    }
}
