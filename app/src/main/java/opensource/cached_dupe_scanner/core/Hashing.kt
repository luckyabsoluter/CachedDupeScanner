package opensource.cached_dupe_scanner.core

import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.Locale

object Hashing {
    const val DEFAULT_BUFFER_SIZE = 1024 * 1024

    fun sha256(bytes: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(bytes)
    }

    fun sha256Hex(bytes: ByteArray): String {
        return sha256(bytes).toHex()
    }

    fun sha256(inputStream: InputStream, bufferSize: Int = DEFAULT_BUFFER_SIZE): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(bufferSize)
        var read = inputStream.read(buffer)
        while (read > 0) {
            digest.update(buffer, 0, read)
            read = inputStream.read(buffer)
        }
        return digest.digest()
    }

    fun sha256Hex(inputStream: InputStream, bufferSize: Int = DEFAULT_BUFFER_SIZE): String {
        return sha256(inputStream, bufferSize).toHex()
    }

    fun sha256(file: File, bufferSize: Int = DEFAULT_BUFFER_SIZE): ByteArray {
        return file.inputStream().use { input ->
            sha256(input, bufferSize)
        }
    }

    fun sha256Hex(file: File, bufferSize: Int = DEFAULT_BUFFER_SIZE): String {
        return sha256(file, bufferSize).toHex()
    }
}

private fun ByteArray.toHex(): String {
    return joinToString(separator = "") { byte ->
        String.format(Locale.US, "%02x", byte)
    }
}
