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

internal interface VideoFrameSource {
    fun setDataSource(path: String)
    fun durationMillis(): Long?
    fun frameAtTime(timeMicros: Long): Bitmap?
    fun release()
}

private class MediaMetadataVideoFrameSource : VideoFrameSource {
    private val retriever = MediaMetadataRetriever()

    override fun setDataSource(path: String) {
        retriever.setDataSource(path)
    }

    override fun durationMillis(): Long? {
        return retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            ?.toLongOrNull()
            ?.coerceAtLeast(0L)
    }

    override fun frameAtTime(timeMicros: Long): Bitmap? {
        return retriever.getFrameAtTime(
            timeMicros,
            MediaMetadataRetriever.OPTION_CLOSEST_SYNC
        )
    }

    override fun release() {
        retriever.release()
    }
}

class AndroidVideoFrameSignatureExtractor : VideoFrameSignatureExtractor {
    private val sourceFactory: () -> VideoFrameSource

    constructor() {
        sourceFactory = { MediaMetadataVideoFrameSource() }
    }

    internal constructor(sourceFactory: () -> VideoFrameSource) {
        this.sourceFactory = sourceFactory
    }

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
        val source = sourceFactory()
        return try {
            source.setDataSource(file.absolutePath)
            val durationMillis = runCatching { source.durationMillis() }.getOrNull()
            val frameSignatures = step.frameSeconds.map { second ->
                if (!shouldContinue()) return null
                val timeMicros = boundedVideoFrameTimeMicros(second, durationMillis)
                val bitmap = source.frameAtTime(timeMicros) ?: return null
                thumbnailSignature(bitmap, step)
            }
            buildThumbnailHash(SimilarityMediaScope.Video, step, frameSignatures)
        } catch (_: RuntimeException) {
            null
        } finally {
            runCatching { source.release() }
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
            buildThumbnailHash(
                mediaScope = SimilarityMediaScope.Image,
                step = step,
                frameSignatures = listOf(thumbnailSignature(bitmap, step))
            )
        } catch (e: RuntimeException) {
            null
        }
    }
}

internal fun boundedVideoFrameTimeMicros(
    frameSecond: Int,
    durationMillis: Long?
): Long {
    val requestedMicros = frameSecond.coerceAtLeast(0).toLong() * MICROS_PER_SECOND
    val maximumDurationMillis = Long.MAX_VALUE / MICROS_PER_MILLISECOND
    val lastValidMicros = durationMillis
        ?.coerceIn(0L, maximumDurationMillis)
        ?.let { safeDurationMillis ->
            (safeDurationMillis - 1L).coerceAtLeast(0L) * MICROS_PER_MILLISECOND
        }
    return lastValidMicros?.let { maximum -> requestedMicros.coerceAtMost(maximum) }
        ?: requestedMicros
}

fun buildVideoFrameSignature(
    step: ExactThumbnailHashStep,
    frameSignatures: List<String>
): String {
    return buildThumbnailHash(SimilarityMediaScope.Video, step, frameSignatures)
}

fun buildThumbnailSignature(
    mediaScope: SimilarityMediaScope,
    step: ExactThumbnailHashStep,
    frameSignatures: List<String>
): String {
    return buildThumbnailHash(mediaScope, step, frameSignatures)
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

private const val MICROS_PER_MILLISECOND = 1_000L
private const val MICROS_PER_SECOND = 1_000_000L
