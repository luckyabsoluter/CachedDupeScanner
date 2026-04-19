package opensource.cached_dupe_scanner.ui.home

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class GroupPreviewSourceTest {
    @Test
    fun groupPreviewThumbnailDoesNotForceCropRendering() {
        val projectDir = File(requireNotNull(System.getProperty("user.dir")))
        val previewFile = sequenceOf(
            File(projectDir, "app/src/main/java/opensource/cached_dupe_scanner/ui/home/GroupPreview.kt"),
            File(projectDir.parentFile ?: projectDir, "app/src/main/java/opensource/cached_dupe_scanner/ui/home/GroupPreview.kt")
        ).firstOrNull { it.exists() }

        assertTrue("GroupPreview.kt should exist", previewFile != null)

        val content = previewFile!!.readText()
        val groupPreviewBlock = content.substringAfter("internal fun GroupPreviewThumbnail(")
            .substringBefore("@Composable\nprivate fun MissingPreviewThumbnail(")

        assertFalse(
            "GroupPreviewThumbnail should not force crop rendering",
            groupPreviewBlock.contains("ContentScale.Crop")
        )
        assertFalse(
            "GroupPreviewThumbnail should not set contentScale explicitly",
            groupPreviewBlock.contains("contentScale =")
        )
        assertTrue(
            "GroupPreviewThumbnail should accept a shared preview memory key",
            groupPreviewBlock.contains("previewMemoryKey: String")
        )
        assertTrue(
            "GroupPreviewThumbnail should use a shared remembered preview cache",
            groupPreviewBlock.contains("rememberedPreviewCache: MutableMap<String, ImageBitmap>")
        )
        assertFalse(
            "GroupPreviewThumbnail should not keep remembered preview only in local remember state",
            groupPreviewBlock.contains("var rememberedPreview by remember")
        )
        assertTrue(
            "GroupPreview should provide a timeline preview strip with start/middle/end guidance",
            content.contains("VideoTimelinePreviewStrip(") &&
                content.contains("Start - ... - Middle - ... - End")
        )
        assertTrue(
            "GroupPreview timeline should use a fixed multi-frame default count",
            content.contains("DEFAULT_VIDEO_TIMELINE_FRAME_COUNT = 7")
        )
        assertTrue(
            "GroupPreview timeline should compute frame count dynamically from available width",
            content.contains("dynamicTimelineFrameCount(") &&
                content.contains("BoxWithConstraints(")
        )
        assertTrue(
            "GroupPreview timeline should support snap-to-width expansion",
            content.contains("snapToFillWidth") &&
                content.contains("snappedTimelineFrameWidth(")
        )
    }
}
