package opensource.cached_dupe_scanner.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test

class GroupPreviewLogicTest {
    @Test
    fun activePreviewPathSkipsFailedCandidates() {
        val activePath = activePreviewPath(
            candidatePaths = listOf("/a/1.jpg", "/a/2.jpg", "/a/3.jpg"),
            failedPaths = setOf("/a/1.jpg", "/a/2.jpg")
        )

        assertEquals("/a/3.jpg", activePath)
    }

    @Test
    fun shouldUseRememberedPreviewWhenNoLoadableCandidateRemains() {
        val useRemembered = shouldUseRememberedPreview(
            candidatePaths = listOf("/a/1.jpg", "/a/2.jpg"),
            failedPaths = setOf("/a/1.jpg", "/a/2.jpg"),
            hasRememberedPreview = true
        )

        assertEquals(true, useRemembered)
    }

    @Test
    fun shouldNotUseRememberedPreviewWhileAnotherCandidateCanStillLoad() {
        val useRemembered = shouldUseRememberedPreview(
            candidatePaths = listOf("/a/1.jpg", "/a/2.jpg"),
            failedPaths = setOf("/a/1.jpg"),
            hasRememberedPreview = true
        )

        assertEquals(false, useRemembered)
    }
}
