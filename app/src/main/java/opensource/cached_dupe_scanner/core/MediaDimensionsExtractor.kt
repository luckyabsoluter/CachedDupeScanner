package opensource.cached_dupe_scanner.core

import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import java.io.File

data class MediaDimensions(
    val widthPixels: Int,
    val heightPixels: Int
)

interface MediaDimensionsExtractor {
    fun dimensions(
        file: File,
        mediaScope: SimilarityMediaScope,
        shouldContinue: () -> Boolean
    ): MediaDimensions?
}

class AndroidMediaDimensionsExtractor : MediaDimensionsExtractor {
    override fun dimensions(
        file: File,
        mediaScope: SimilarityMediaScope,
        shouldContinue: () -> Boolean
    ): MediaDimensions? {
        if (!shouldContinue()) return null
        return when (mediaScope) {
            SimilarityMediaScope.Image -> imageDimensions(file, shouldContinue)
            SimilarityMediaScope.Video -> videoDimensions(file, shouldContinue)
        }
    }

    private fun imageDimensions(
        file: File,
        shouldContinue: () -> Boolean
    ): MediaDimensions? {
        if (!shouldContinue()) return null
        return try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(file.absolutePath, options)
            if (!shouldContinue()) return null
            normalizedMediaDimensions(
                widthPixels = options.outWidth,
                heightPixels = options.outHeight,
                rotationDegrees = 0
            )
        } catch (_: RuntimeException) {
            null
        }
    }

    private fun videoDimensions(
        file: File,
        shouldContinue: () -> Boolean
    ): MediaDimensions? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            if (!shouldContinue()) return null
            val widthPixels = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                .positiveIntOrNull()
                ?: return null
            val heightPixels = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                .positiveIntOrNull()
                ?: return null
            val rotationDegrees = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                ?.toIntOrNull()
                ?: 0
            normalizedMediaDimensions(widthPixels, heightPixels, rotationDegrees)
        } catch (_: RuntimeException) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }
}

internal fun normalizedMediaDimensions(
    widthPixels: Int,
    heightPixels: Int,
    rotationDegrees: Int
): MediaDimensions? {
    if (widthPixels <= 0 || heightPixels <= 0) return null
    val normalizedRotation = ((rotationDegrees % 360) + 360) % 360
    return if (normalizedRotation == 90 || normalizedRotation == 270) {
        MediaDimensions(widthPixels = heightPixels, heightPixels = widthPixels)
    } else {
        MediaDimensions(widthPixels = widthPixels, heightPixels = heightPixels)
    }
}

private fun String?.positiveIntOrNull(): Int? {
    return this?.toIntOrNull()?.takeIf { value -> value > 0 }
}
