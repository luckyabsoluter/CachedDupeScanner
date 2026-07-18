package opensource.cached_dupe_scanner.core

import android.graphics.Bitmap
import android.graphics.Color
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class VideoFrameSignatureExtractorTest {
    @Test
    fun videoSignatureClampsOutOfRangeConfiguredFramesBeforeDecode() {
        val red = singlePixelBitmap(Color.RED)
        val green = singlePixelBitmap(Color.GREEN)
        val blue = singlePixelBitmap(Color.BLUE)
        val source = RecordingVideoFrameSource(
            durationMillis = 1_500L,
            frames = mapOf(
                0L to red,
                1_000_000L to green,
                1_499_000L to blue
            )
        )
        val step = ExactThumbnailHashStep(
            frameSeconds = listOf(0, 1, 10),
            resizeWidthPx = 1,
            resizeHeightPx = 1,
            quantizationLevels = null,
            grayscale = false
        )

        val result = AndroidVideoFrameSignatureExtractor { source }.signatureWithMetadata(
            file = File("short-video.mp4"),
            mediaScope = SimilarityMediaScope.Video,
            step = step,
            shouldContinue = { true }
        )

        assertEquals(
            buildVideoFrameSignature(
                step = step,
                frameSignatures = listOf("ff0000", "00ff00", "0000ff")
            ),
            result?.signature
        )
        assertEquals(1_500L, result?.durationMillis)
        assertEquals(listOf(0L, 1_000_000L, 1_499_000L), source.requestedTimesMicros)
        assertTrue(source.released)
    }

    @Test
    fun videoSignatureDoesNotHashAnUndecodableFrame() {
        val source = RecordingVideoFrameSource(
            durationMillis = 5_000L,
            frames = emptyMap()
        )
        val step = ExactThumbnailHashStep(
            frameSeconds = listOf(1),
            resizeWidthPx = 1,
            resizeHeightPx = 1,
            quantizationLevels = null,
            grayscale = false
        )

        val signature = AndroidVideoFrameSignatureExtractor { source }.signature(
            file = File("unsupported-video.mp4"),
            mediaScope = SimilarityMediaScope.Video,
            step = step,
            shouldContinue = { true }
        )

        assertNull(signature)
        assertEquals(listOf(1_000_000L), source.requestedTimesMicros)
        assertTrue(source.released)
    }

    @Test
    fun thumbnailSignatureIncludesEveryResizedPixelInRowMajorOrder() {
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888).apply {
            setPixel(0, 0, Color.rgb(255, 0, 0))
            setPixel(1, 0, Color.rgb(0, 255, 0))
            setPixel(0, 1, Color.rgb(0, 0, 255))
            setPixel(1, 1, Color.rgb(255, 255, 255))
        }
        val step = ExactThumbnailHashStep(
            frameSeconds = listOf(0),
            resizeWidthPx = 2,
            resizeHeightPx = 2,
            quantizationLevels = null,
            grayscale = false
        )

        val signature = thumbnailSignature(bitmap, step)

        assertEquals("ff0000,00ff00,0000ff,ffffff", signature)
    }

    @Test
    fun thumbnailSignatureKeepsSinglePixelLegacyShape() {
        val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).apply {
            setPixel(0, 0, Color.rgb(255, 128, 0))
        }
        val step = ExactThumbnailHashStep(
            frameSeconds = listOf(0),
            resizeWidthPx = 1,
            resizeHeightPx = 1,
            quantizationLevels = 16,
            grayscale = false
        )

        val signature = thumbnailSignature(bitmap, step)

        assertEquals("f80", signature)
    }

    private fun singlePixelBitmap(color: Int): Bitmap {
        return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).apply {
            setPixel(0, 0, color)
        }
    }
}

private class RecordingVideoFrameSource(
    private val durationMillis: Long?,
    private val frames: Map<Long, Bitmap>
) : VideoFrameSource {
    val requestedTimesMicros = mutableListOf<Long>()
    var released = false

    override fun setDataSource(path: String) = Unit

    override fun durationMillis(): Long? = durationMillis

    override fun frameAtTime(timeMicros: Long): Bitmap? {
        requestedTimesMicros += timeMicros
        return frames[timeMicros]
    }

    override fun release() {
        released = true
    }
}
