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
                onePixelSignature(bitmap, step)
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
                frameSignatures = listOf(onePixelSignature(bitmap, step))
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

private fun onePixelSignature(bitmap: Bitmap, step: ExactThumbnailHashStep): String {
    val scaled = if (bitmap.width == 1 && bitmap.height == 1) {
        bitmap
    } else {
        Bitmap.createScaledBitmap(bitmap, step.resizeWidthPx, step.resizeHeightPx, true)
    }
    val pixel = scaled.getPixel(0, 0)
    val red = android.graphics.Color.red(pixel)
    val green = android.graphics.Color.green(pixel)
    val blue = android.graphics.Color.blue(pixel)
    return if (step.grayscale) {
        val gray = ((red * 299) + (green * 587) + (blue * 114)) / 1000
        step.quantizationLevels
            ?.let { levels -> quantizeChannel(gray, levels).toString(16) }
            ?: gray.toString(16).padStart(2, '0')
    } else {
        listOf(red, green, blue)
            .joinToString("") { channel ->
                step.quantizationLevels
                    ?.let { levels -> quantizeChannel(channel, levels).toString(16) }
                    ?: channel.toString(16).padStart(2, '0')
            }
    }
}
