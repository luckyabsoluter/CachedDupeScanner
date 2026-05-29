package opensource.cached_dupe_scanner.core

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import java.io.File

interface VideoFrameSignatureExtractor {
    fun signature(
        file: File,
        mediaScope: SimilarityMediaScope,
        step: ExactThumbnailHashStep,
        shouldContinue: () -> Boolean
    ): String?
}

class AndroidVideoFrameSignatureExtractor : VideoFrameSignatureExtractor {
    override fun signature(
        file: File,
        mediaScope: SimilarityMediaScope,
        step: ExactThumbnailHashStep,
        shouldContinue: () -> Boolean
    ): String? {
        return when (mediaScope) {
            SimilarityMediaScope.Video -> videoSignature(file, step, shouldContinue)
            SimilarityMediaScope.Image -> imageSignature(file, step, shouldContinue)
        }
    }

    private fun videoSignature(
        file: File,
        step: ExactThumbnailHashStep,
        shouldContinue: () -> Boolean
    ): String? {
        if (!shouldContinue()) return null
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val frameSignatures = step.frameSeconds.map { second ->
                if (!shouldContinue()) return null
                val bitmap = retriever.getFrameAtTime(
                    second * 1_000_000L,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                ) ?: return null
                thumbnailSignature(bitmap, step)
            }
            buildThumbnailSignature(SimilarityMediaScope.Video, step, frameSignatures)
        } catch (e: RuntimeException) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun imageSignature(
        file: File,
        step: ExactThumbnailHashStep,
        shouldContinue: () -> Boolean
    ): String? {
        if (!shouldContinue()) return null
        return try {
            val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return null
            buildThumbnailSignature(
                mediaScope = SimilarityMediaScope.Image,
                step = step,
                frameSignatures = listOf(thumbnailSignature(bitmap, step))
            )
        } catch (e: RuntimeException) {
            null
        }
    }
}

fun buildVideoFrameSignature(
    step: ExactThumbnailHashStep,
    frameSignatures: List<String>
): String {
    return buildThumbnailSignature(SimilarityMediaScope.Video, step, frameSignatures)
}

fun buildThumbnailSignature(
    mediaScope: SimilarityMediaScope,
    step: ExactThumbnailHashStep,
    frameSignatures: List<String>
): String {
    val mode = if (step.grayscale) "gray" else "color"
    val quantization = step.quantizationLevels?.let { "q$it" } ?: "raw"
    return listOf(
        "thumb-v1",
        mediaScope.name.lowercase(),
        mode,
        "${step.resizeWidthPx}x${step.resizeHeightPx}",
        quantization,
        step.frameSeconds.joinToString(","),
        frameSignatures.joinToString("|")
    ).joinToString(":")
}

fun quantizeChannel(value: Int, levels: Int): Int {
    val normalizedLevels = levels.coerceAtLeast(2)
    return ((value.coerceIn(0, 255) * normalizedLevels) / 256)
        .coerceIn(0, normalizedLevels - 1)
}

internal fun thumbnailSignature(bitmap: Bitmap, step: ExactThumbnailHashStep): String {
    val scaled = if (bitmap.width == step.resizeWidthPx && bitmap.height == step.resizeHeightPx) {
        bitmap
    } else {
        Bitmap.createScaledBitmap(bitmap, step.resizeWidthPx, step.resizeHeightPx, true)
    }
    val pixelSignatures = (0 until step.resizeHeightPx).flatMap { y ->
        (0 until step.resizeWidthPx).map { x ->
            pixelSignature(scaled.getPixel(x, y), step)
        }
    }
    return if (pixelSignatures.size == 1) {
        pixelSignatures.single()
    } else {
        pixelSignatures.joinToString(",")
    }
}

private fun pixelSignature(pixel: Int, step: ExactThumbnailHashStep): String {
    val red = android.graphics.Color.red(pixel)
    val green = android.graphics.Color.green(pixel)
    val blue = android.graphics.Color.blue(pixel)
    return if (step.grayscale) {
        val gray = ((red * 299) + (green * 587) + (blue * 114)) / 1000
        channelSignature(gray, step.quantizationLevels)
    } else {
        listOf(red, green, blue)
            .joinToString("") { channel -> channelSignature(channel, step.quantizationLevels) }
    }
}

private fun channelSignature(channel: Int, quantizationLevels: Int?): String {
    return quantizationLevels
        ?.let { levels ->
            quantizeChannel(channel, levels)
                .toString(16)
                .padStart(quantizedChannelHexWidth(levels), '0')
        }
        ?: channel.toString(16).padStart(2, '0')
}

private fun quantizedChannelHexWidth(levels: Int): Int {
    return (levels.coerceAtLeast(2) - 1).toString(16).length
}
