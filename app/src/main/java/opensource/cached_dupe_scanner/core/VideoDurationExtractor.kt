package opensource.cached_dupe_scanner.core

import android.media.MediaMetadataRetriever
import java.io.File

interface VideoDurationExtractor {
    fun durationMillis(
        file: File,
        shouldContinue: () -> Boolean
    ): Long?
}

class AndroidVideoDurationExtractor : VideoDurationExtractor {
    override fun durationMillis(
        file: File,
        shouldContinue: () -> Boolean
    ): Long? {
        if (!shouldContinue()) return null
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?.coerceAtLeast(0L)
        } catch (e: RuntimeException) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }
}
