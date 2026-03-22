package opensource.cached_dupe_scanner

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MainActivityThumbnailCacheSourceTest {
    @Test
    fun mainActivitySharesThumbnailMemoryCacheAcrossScreens() {
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
            "MainActivity should clear the shared thumbnail cache when memory retention is disabled",
            content.contains("rememberedThumbnailCache.clear()")
        )
        assertTrue(
            "FilesScreenDb should receive the shared thumbnail cache",
            content.contains("rememberedPreviewCache = rememberedThumbnailCache")
        )
        assertTrue(
            "FilesScreenDb should receive the current thumbnail memory setting",
            content.contains("keepLoadedThumbnailsInMemory = settingsSnapshot.keepLoadedThumbnailsInMemory")
        )
        assertTrue(
            "ResultsScreenDb should receive the shared thumbnail cache",
            content.contains("Screen.Results -> ResultsScreenDb(") &&
                content.contains("rememberedPreviewCache = rememberedThumbnailCache")
        )
    }
}
