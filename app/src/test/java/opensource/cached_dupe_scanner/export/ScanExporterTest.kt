package opensource.cached_dupe_scanner.export

import opensource.cached_dupe_scanner.core.DuplicateGroup
import opensource.cached_dupe_scanner.core.FileMetadata
import opensource.cached_dupe_scanner.core.ScanResult
import org.junit.Assert.assertEquals
import org.junit.Test

class ScanExporterTest {
    @Test
    fun exportJsonIsStable() {
        val result = sampleResult()

        val json = ScanExporter.export(result, ExportFormat.JSON)

        val expected = "{" +
            "\"scannedAtMillis\":1700000000000," +
            "\"files\":[" +
            "{\"path\":\"root/a.txt\",\"normalizedPath\":\"root/a.txt\",\"sizeBytes\":1,\"lastModifiedMillis\":10,\"hashHex\":\"hash-1\"}," +
            "{\"path\":\"root/b.txt\",\"normalizedPath\":\"root/b.txt\",\"sizeBytes\":2,\"lastModifiedMillis\":20,\"hashHex\":\"hash-1\"}," +
            "{\"path\":\"root/c.txt\",\"normalizedPath\":\"root/c.txt\",\"sizeBytes\":3,\"lastModifiedMillis\":30,\"hashHex\":\"hash-2\"}" +
            "]," +
            "\"duplicateGroups\":[" +
            "{\"hashHex\":\"hash-1\",\"files\":[" +
            "{\"path\":\"root/a.txt\",\"normalizedPath\":\"root/a.txt\",\"sizeBytes\":1,\"lastModifiedMillis\":10,\"hashHex\":\"hash-1\"}," +
            "{\"path\":\"root/b.txt\",\"normalizedPath\":\"root/b.txt\",\"sizeBytes\":2,\"lastModifiedMillis\":20,\"hashHex\":\"hash-1\"}" +
            "]}" +
            "]" +
            "}"

        assertEquals(expected, json)
    }

    @Test
    fun exportCsvIsStable() {
        val result = sampleResult()

        val csv = ScanExporter.export(result, ExportFormat.CSV)

        val expected = "normalizedPath,path,sizeBytes,lastModifiedMillis,hashHex\n" +
            "root/a.txt,root/a.txt,1,10,hash-1\n" +
            "root/b.txt,root/b.txt,2,20,hash-1\n" +
            "root/c.txt,root/c.txt,3,30,hash-2"

        assertEquals(expected, csv)
    }

    private fun sampleResult(): ScanResult {
        val a = FileMetadata(
            path = "root/a.txt",
            normalizedPath = "root/a.txt",
            sizeBytes = 1,
            lastModifiedMillis = 10,
            hashHex = "hash-1"
        )
        val b = FileMetadata(
            path = "root/b.txt",
            normalizedPath = "root/b.txt",
            sizeBytes = 2,
            lastModifiedMillis = 20,
            hashHex = "hash-1"
        )
        val c = FileMetadata(
            path = "root/c.txt",
            normalizedPath = "root/c.txt",
            sizeBytes = 3,
            lastModifiedMillis = 30,
            hashHex = "hash-2"
        )

        return ScanResult(
            scannedAtMillis = 1700000000000,
            files = listOf(b, a, c),
            duplicateGroups = listOf(
                DuplicateGroup("hash-1", listOf(b, a))
            )
        )
    }
}
