package opensource.cached_dupe_scanner.core

import android.graphics.Bitmap
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MediaDimensionsExtractorTest {
    @Test
    fun imageDimensionsAreReadWithoutDecodingFullPixels() {
        val directory = File(
            File(requireNotNull(System.getProperty("user.dir")), "build/test-temp"),
            "media-dimensions-${UUID.randomUUID()}"
        )
        directory.mkdirs()
        val file = File(directory, "sample.png")
        val bitmap = Bitmap.createBitmap(7, 5, Bitmap.Config.ARGB_8888)
        FileOutputStream(file).use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        }
        bitmap.recycle()

        try {
            assertEquals(
                MediaDimensions(widthPixels = 7, heightPixels = 5),
                AndroidMediaDimensionsExtractor().dimensions(
                    file = file,
                    mediaScope = SimilarityMediaScope.Image,
                    shouldContinue = { true }
                )
            )
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun videoDimensionsUseDisplayOrientationAndRespectCancellation() {
        assertEquals(
            MediaDimensions(widthPixels = 1080, heightPixels = 1920),
            normalizedMediaDimensions(widthPixels = 1920, heightPixels = 1080, rotationDegrees = 90)
        )
        assertEquals(
            MediaDimensions(widthPixels = 1920, heightPixels = 1080),
            normalizedMediaDimensions(widthPixels = 1920, heightPixels = 1080, rotationDegrees = 180)
        )
        assertNull(
            AndroidMediaDimensionsExtractor().dimensions(
                file = File("not-read-when-cancelled.mp4"),
                mediaScope = SimilarityMediaScope.Video,
                shouldContinue = { false }
            )
        )
    }
}
