package opensource.cached_dupe_scanner.notifications

import java.io.File
import opensource.cached_dupe_scanner.engine.ScanPhase

data class ScanNotificationContent(
    val title: String,
    val text: String,
    val subText: String?
)

fun buildScanNotificationContent(
    phase: ScanPhase,
    scanned: Int,
    total: Int?,
    targetPath: String?,
    currentPath: String?
): ScanNotificationContent {
    val phaseText = when (phase) {
        ScanPhase.Collecting -> "Collecting files"
        ScanPhase.Detecting -> "Detecting hash candidates"
        ScanPhase.Hashing -> "Hashing"
        ScanPhase.Saving -> "Saving cache"
    }
    val totalText = total?.toString() ?: "?"
    val targetName = when {
        targetPath.isNullOrBlank() -> "All targets"
        else -> File(targetPath).name.ifBlank { targetPath }
    }
    val currentName = currentPath?.let { File(it).name.ifBlank { it } }
    val text = "$phaseText • $scanned/$totalText • $targetName"
    return ScanNotificationContent(
        title = "Scanning files",
        text = text,
        subText = currentName
    )
}
