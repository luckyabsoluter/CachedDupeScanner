package opensource.cached_dupe_scanner.export

import opensource.cached_dupe_scanner.core.DuplicateGroup
import opensource.cached_dupe_scanner.core.FileMetadata
import opensource.cached_dupe_scanner.core.ScanResult

enum class ExportFormat {
    JSON,
    CSV
}

object ScanExporter {
    fun export(result: ScanResult, format: ExportFormat): String {
        return when (format) {
            ExportFormat.JSON -> exportJson(result)
            ExportFormat.CSV -> exportCsv(result)
        }
    }

    private fun exportJson(result: ScanResult): String {
        val files = result.files.sortedBy { it.normalizedPath }
        val groups = result.duplicateGroups.sortedBy { it.hashHex }

        val filesJson = files.joinToString(separator = ",") { it.toJson() }
        val groupsJson = groups.joinToString(separator = ",") { it.toJson() }

        return "{" +
            "\"scannedAtMillis\":${result.scannedAtMillis}," +
            "\"files\":[${filesJson}]," +
            "\"duplicateGroups\":[${groupsJson}]" +
            "}"
    }

    private fun exportCsv(result: ScanResult): String {
        val header = "normalizedPath,path,sizeBytes,lastModifiedMillis,hashHex"
        val rows = result.files
            .sortedBy { it.normalizedPath }
            .joinToString(separator = "\n") { it.toCsvRow() }
        return if (rows.isBlank()) {
            header
        } else {
            "${header}\n${rows}"
        }
    }
}

private fun FileMetadata.toJson(): String {
    return "{" +
        "\"path\":\"${path.jsonEscape()}\"," +
        "\"normalizedPath\":\"${normalizedPath.jsonEscape()}\"," +
        "\"sizeBytes\":${sizeBytes}," +
        "\"lastModifiedMillis\":${lastModifiedMillis}," +
        "\"hashHex\":${hashHex?.let { "\"${it.jsonEscape()}\"" } ?: "null"}" +
        "}"
}

private fun DuplicateGroup.toJson(): String {
    val filesJson = files.sortedBy { it.normalizedPath }
        .joinToString(separator = ",") { it.toJson() }

    return "{" +
        "\"hashHex\":\"${hashHex.jsonEscape()}\"," +
        "\"files\":[${filesJson}]" +
        "}"
}

private fun FileMetadata.toCsvRow(): String {
    return listOf(
        normalizedPath.csvEscape(),
        path.csvEscape(),
        sizeBytes.toString(),
        lastModifiedMillis.toString(),
        (hashHex ?: "").csvEscape()
    ).joinToString(separator = ",")
}

private fun String.jsonEscape(): String {
    return buildString {
        for (char in this@jsonEscape) {
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(char)
            }
        }
    }
}

private fun String.csvEscape(): String {
    val needsQuote = contains(',') || contains('"') || contains('\n') || contains('\r')
    val escaped = replace("\"", "\"\"")
    return if (needsQuote) {
        "\"${escaped}\""
    } else {
        escaped
    }
}
