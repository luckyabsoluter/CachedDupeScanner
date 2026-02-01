package opensource.cached_dupe_scanner.ui.home

import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun formatBytes(bytes: Long): String {
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unitIndex = 0
    while (value >= 1024 && unitIndex < units.lastIndex) {
        value /= 1024
        unitIndex++
    }
    return if (unitIndex == 0) {
        String.format(Locale.getDefault(), "%.0f %s", value, units[unitIndex])
    } else {
        String.format(Locale.getDefault(), "%.1f %s", value, units[unitIndex])
    }
}

fun formatDate(millis: Long): String {
    val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    return formatter.format(Date(millis))
}

fun formatPath(path: String, showFullPath: Boolean): String {
    return if (showFullPath) {
        path
    } else {
        File(path).name.ifBlank { path }
    }
}

fun isMediaFile(path: String): Boolean {
    val extension = File(path).extension.lowercase(Locale.getDefault())
    return extension in setOf(
        "jpg", "jpeg", "png", "webp", "gif", "bmp",
        "mp4", "mkv", "mov", "webm"
    )
}

fun openFile(context: android.content.Context, path: String) {
    val file = File(path)
    if (!file.exists()) {
        Toast.makeText(context, "File not found", Toast.LENGTH_SHORT).show()
        return
    }
    val uri = try {
        FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    } catch (e: IllegalArgumentException) {
        Toast.makeText(context, "Unable to open file location", Toast.LENGTH_SHORT).show()
        return
    }
    val mime = getMimeType(path)
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, mime)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    if (intent.resolveActivity(context.packageManager) != null) {
        context.startActivity(intent)
    } else {
        Toast.makeText(context, "No app can open this file", Toast.LENGTH_SHORT).show()
    }
}

private fun getMimeType(path: String): String {
    val ext = File(path).extension
    val mime = if (ext.isNotBlank()) {
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.lowercase(Locale.getDefault()))
    } else {
        null
    }
    return mime ?: "*/*"
}
