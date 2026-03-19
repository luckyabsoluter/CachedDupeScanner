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
    }
}
