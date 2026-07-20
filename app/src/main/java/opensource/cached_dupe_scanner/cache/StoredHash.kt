package opensource.cached_dupe_scanner.cache

import androidx.room.TypeConverter
import java.nio.charset.StandardCharsets

class StoredHash private constructor(
    private val storageBytes: ByteArray
) {
    fun toStorageBytes(): ByteArray = storageBytes.copyOf()

    fun toExternalString(): String {
        return when {
            storageBytes.size == SHA_256_BYTE_COUNT -> storageBytes.toLowerHex()
            storageBytes.startsWith(LEGACY_HASH_PREFIX) -> {
                storageBytes
                    .copyOfRange(LEGACY_HASH_PREFIX.size, storageBytes.size)
                    .toString(StandardCharsets.UTF_8)
            }
            else -> storageBytes.toLowerHex()
        }
    }

    override fun equals(other: Any?): Boolean {
        return other is StoredHash && storageBytes.contentEquals(other.storageBytes)
    }

    override fun hashCode(): Int = storageBytes.contentHashCode()

    override fun toString(): String = toExternalString()

    companion object {
        fun fromExternalString(value: String): StoredHash {
            val normalized = value.lowercase()
            return if (normalized.length == SHA_256_HEX_LENGTH && normalized.all(Char::isHexDigit)) {
                StoredHash(normalized.hexToBytes())
            } else {
                StoredHash(LEGACY_HASH_PREFIX + value.toByteArray(StandardCharsets.UTF_8))
            }
        }

        fun fromStorageBytes(bytes: ByteArray): StoredHash = StoredHash(bytes.copyOf())
    }
}

class StoredHashConverters {
    @TypeConverter
    fun fromBlob(bytes: ByteArray?): StoredHash? {
        return bytes?.let(StoredHash::fromStorageBytes)
    }

    @TypeConverter
    fun toBlob(hash: StoredHash?): ByteArray? {
        return hash?.toStorageBytes()
    }
}

internal fun storedHashOrNull(hashHex: String?): StoredHash? {
    return hashHex
        ?.takeIf { value -> value.isNotBlank() }
        ?.let(StoredHash::fromExternalString)
}

private fun Char.isHexDigit(): Boolean {
    return this in '0'..'9' || this in 'a'..'f'
}

private fun String.hexToBytes(): ByteArray {
    return ByteArray(length / 2) { index ->
        val offset = index * 2
        substring(offset, offset + 2).toInt(16).toByte()
    }
}

private fun ByteArray.toLowerHex(): String {
    val chars = CharArray(size * 2)
    forEachIndexed { index, byte ->
        val value = byte.toInt() and 0xff
        chars[index * 2] = HEX_CHARS[value ushr 4]
        chars[(index * 2) + 1] = HEX_CHARS[value and 0x0f]
    }
    return chars.concatToString()
}

private fun ByteArray.startsWith(prefix: ByteArray): Boolean {
    if (size < prefix.size) return false
    return prefix.indices.all { index -> this[index] == prefix[index] }
}

private const val SHA_256_BYTE_COUNT = 32
private const val SHA_256_HEX_LENGTH = SHA_256_BYTE_COUNT * 2
private val LEGACY_HASH_PREFIX = ByteArray(SHA_256_BYTE_COUNT + 1) { 0x7f }
private val HEX_CHARS = "0123456789abcdef".toCharArray()
