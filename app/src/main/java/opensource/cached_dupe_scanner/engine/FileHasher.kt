package opensource.cached_dupe_scanner.engine

import opensource.cached_dupe_scanner.core.Hashing
import java.io.File

interface FileHasher {
    fun hash(file: File): String
}

class Sha256FileHasher : FileHasher {
    override fun hash(file: File): String {
        return Hashing.sha256Hex(file)
    }
}
