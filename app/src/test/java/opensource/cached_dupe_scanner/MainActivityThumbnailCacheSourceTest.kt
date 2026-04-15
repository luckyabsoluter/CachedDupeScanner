package opensource.cached_dupe_scanner

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MainActivityThumbnailCacheSourceTest {
    @Test
    fun mainActivitySeparatesThumbnailAndVideoPreviewCaches() {
        val projectDir = File(requireNotNull(System.getProperty("user.dir")))
        val sourceFile = sequenceOf(
            File(projectDir, "app/src/main/java/opensource/cached_dupe_scanner/MainActivity.kt"),
            File(projectDir.parentFile ?: projectDir, "app/src/main/java/opensource/cached_dupe_scanner/MainActivity.kt")
        ).firstOrNull { it.exists() }

        assertTrue("MainActivity.kt should exist", sourceFile != null)

        val content = sourceFile!!.readText()

        assertTrue(
            "MainActivity should keep a shared remembered thumbnail cache",
            content.contains("val rememberedThumbnailCache = remember { mutableStateMapOf<String, ImageBitmap>() }")
        )
        assertTrue(
            "MainActivity should keep a dedicated remembered video preview cache",
            content.contains("val rememberedVideoPreviewCache = remember { mutableStateMapOf<String, ImageBitmap>() }")
        )
        assertTrue(
            "MainActivity should clear the shared thumbnail cache when memory retention is disabled",
            content.contains("rememberedThumbnailCache.clear()")
        )
        assertTrue(
            "MainActivity should clear the video preview cache when video preview retention is disabled",
            content.contains("rememberedVideoPreviewCache.clear()")
        )
        assertTrue(
            "FilesScreenDb should receive the shared thumbnail cache and dedicated video preview cache",
            content.contains("rememberedThumbnailCache = rememberedThumbnailCache") &&
                content.contains("rememberedVideoPreviewCache = rememberedVideoPreviewCache")
        )
        assertTrue(
            "FilesScreenDb should receive separate thumbnail and video preview memory settings",
            content.contains("keepLoadedThumbnailsInMemory = settingsSnapshot.keepLoadedThumbnailsInMemory") &&
                content.contains("keepLoadedVideoPreviewsInMemory = settingsSnapshot.keepLoadedVideoPreviewsInMemory")
        )
        assertTrue(
            "ResultsScreenDb should receive the shared thumbnail cache",
            content.contains("Screen.Results -> ResultsScreenDb(") &&
                content.contains("rememberedPreviewCache = rememberedThumbnailCache")
        )
    }
}
