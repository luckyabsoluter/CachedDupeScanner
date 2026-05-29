package opensource.cached_dupe_scanner.core

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class VideoFrameSignatureExtractorTest {
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
}
