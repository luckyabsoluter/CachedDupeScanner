package opensource.cached_dupe_scanner.cache

import opensource.cached_dupe_scanner.core.FileMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CachedFileMappingsTest {
    @Test
    fun fileMetadataToCachedFileEntityPreservesAllFields() {
        val metadata = FileMetadata(
            path = "root/file.txt",
            normalizedPath = "root/file.txt",
            sizeBytes = 123,
            lastModifiedMillis = 456,
            hashHex = "hash"
        )

        val entity = metadata.toCachedFileEntity()

        assertEquals(metadata.path, entity.path)
        assertEquals(metadata.normalizedPath, entity.normalizedPath)
        assertEquals(metadata.sizeBytes, entity.sizeBytes)
        assertEquals(metadata.lastModifiedMillis, entity.lastModifiedMillis)
        assertEquals(metadata.hashHex, entity.hashHex)
    }

    @Test
    fun cachedFileEntityToFileMetadataPreservesAllFields() {
        val entity = CachedFileEntity(
            normalizedPath = "root/file.txt",
            path = "root/file.txt",
            sizeBytes = 123,
            lastModifiedMillis = 456,
            hashHex = "hash"
        )

        val metadata = entity.toFileMetadata()

        assertEquals(entity.path, metadata.path)
        assertEquals(entity.normalizedPath, metadata.normalizedPath)
        assertEquals(entity.sizeBytes, metadata.sizeBytes)
        assertEquals(entity.lastModifiedMillis, metadata.lastModifiedMillis)
        assertEquals(entity.hashHex, metadata.hashHex)
    }

    @Test
    fun mappingsPreserveNullHash() {
        val metadata = FileMetadata(
            path = "root/file.txt",
            normalizedPath = "root/file.txt",
            sizeBytes = 123,
            lastModifiedMillis = 456,
            hashHex = null
        )
        val entity = CachedFileEntity(
            normalizedPath = "root/other.txt",
            path = "root/other.txt",
            sizeBytes = 789,
            lastModifiedMillis = 101,
            hashHex = null
        )

        assertNull(metadata.toCachedFileEntity().hashHex)
        assertNull(entity.toFileMetadata().hashHex)
    }

    @Test
    fun roundTripPreservesMetadata() {
        val metadata = FileMetadata(
            path = "root/file.txt",
            normalizedPath = "root/file.txt",
            sizeBytes = 123,
            lastModifiedMillis = 456,
            hashHex = "hash"
        )

        assertEquals(metadata, metadata.toCachedFileEntity().toFileMetadata())
    }
}
