package opensource.cached_dupe_scanner.ui.home

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class FilesScreenDbVideoPreviewSourceTest {
    @Test
    fun filesScreenMenuCanToggleVideoPreviewMode() {
        val projectDir = File(requireNotNull(System.getProperty("user.dir")))
        val sourceFile = sequenceOf(
            File(projectDir, "app/src/main/java/opensource/cached_dupe_scanner/ui/home/FilesScreenDb.kt"),
            File(projectDir.parentFile ?: projectDir, "app/src/main/java/opensource/cached_dupe_scanner/ui/home/FilesScreenDb.kt")
        ).firstOrNull { it.exists() }

        assertTrue("FilesScreenDb.kt should exist", sourceFile != null)

        val content = sourceFile!!.readText()
        assertTrue(
            "Files menu should expose the video preview option",
            content.contains("Text(\"Video preview\")")
        )
        assertTrue(
            "Video preview option should toggle between compact and timeline modes",
            content.contains("FilesPreviewMode.VideoTimeline.name") &&
                content.contains("FilesPreviewMode.Compact.name")
        )
        assertTrue(
            "Turning video preview off should clear hidden metadata options",
            content.contains("showVideoPreviewDuration.value = false") &&
                content.contains("showVideoPreviewResolution.value = false")
        )
        assertTrue(
            "Files menu should expose the video duration option and enable the preview surface",
            content.contains("Text(\"Video duration\")") &&
                content.contains("showVideoPreviewDuration.value = !showVideoPreviewDuration.value") &&
                content.contains("if (showVideoPreviewDuration.value) {\n                                        previewMode.value = FilesPreviewMode.VideoTimeline.name\n                                    }")
        )
        assertTrue(
            "Files menu should expose the video resolution option and enable the preview surface",
            content.contains("Text(\"Video resolution\")") &&
                content.contains("showVideoPreviewResolution.value = !showVideoPreviewResolution.value") &&
                content.contains("if (showVideoPreviewResolution.value) {\n                                        previewMode.value = FilesPreviewMode.VideoTimeline.name\n                                    }")
        )
    }

    @Test
    fun filesCardRendersVideoTimelineStripOnlyForVideoFilesInPreviewMode() {
        val projectDir = File(requireNotNull(System.getProperty("user.dir")))
        val sourceFile = sequenceOf(
            File(projectDir, "app/src/main/java/opensource/cached_dupe_scanner/ui/home/FilesScreenDb.kt"),
            File(projectDir.parentFile ?: projectDir, "app/src/main/java/opensource/cached_dupe_scanner/ui/home/FilesScreenDb.kt")
        ).firstOrNull { it.exists() }

        assertTrue("FilesScreenDb.kt should exist", sourceFile != null)

        val content = sourceFile!!.readText()
        assertTrue(
            "Video timeline strip should only be shown in video preview mode for video files",
            content.contains("isVideoTimelinePreviewEnabled() && isVideo") &&
                content.contains("VideoTimelinePreviewStrip(")
        )
        assertTrue(
            "Video timeline strip should use dedicated video preview cache and setting",
            content.contains("rememberedPreviewCache = rememberedVideoPreviewCache") &&
                content.contains("keepLoadedInMemory = keepLoadedVideoPreviewsInMemory")
        )
        assertTrue(
            "Video timeline strip should receive width snap option",
            content.contains("snapToFillWidth = snapVideoPreviewFramesToWidth")
        )
        assertTrue(
            "Video timeline strip should receive configured line count",
            content.contains("lineCount = videoPreviewLineCount")
        )
        assertTrue(
            "Video timeline strip should use configurable frame height",
            content.contains("frameHeight = videoPreviewFrameHeightDp")
        )
        assertTrue(
            "Video timeline strip should receive the optional duration display setting",
            content.contains("showDuration = showVideoPreviewDuration.value")
        )
        assertTrue(
            "Video timeline strip should receive the optional resolution display setting",
            content.contains("showResolution = showVideoPreviewResolution.value")
        )
        assertTrue(
            "Primary thumbnail should keep using thumbnail cache and setting",
            content.contains("rememberedPreviewCache = rememberedThumbnailCache") &&
                content.contains("keepLoadedInMemory = keepLoadedThumbnailsInMemory")
        )
        assertTrue(
            "Primary thumbnail should use configurable thumbnail size",
            content.contains(".width(thumbnailSizeDp)") &&
                content.contains(".height(thumbnailSizeDp)")
        )
    }
}
