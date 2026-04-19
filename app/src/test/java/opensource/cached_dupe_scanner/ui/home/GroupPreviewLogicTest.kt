package opensource.cached_dupe_scanner.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import androidx.compose.ui.unit.dp

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
            activePath = null,
            hasRememberedPreview = true,
            keepLoadedInMemory = false
        )

        assertEquals(true, useRemembered)
    }

    @Test
    fun shouldNotUseRememberedPreviewWhileAnotherCandidateCanStillLoad() {
        val useRemembered = shouldUseRememberedPreview(
            activePath = "/a/2.jpg",
            hasRememberedPreview = true,
            keepLoadedInMemory = false
        )

        assertEquals(false, useRemembered)
    }

    @Test
    fun shouldUseRememberedPreviewImmediatelyWhenMemoryRetentionIsEnabled() {
        val useRemembered = shouldUseRememberedPreview(
            activePath = "/a/1.jpg",
            hasRememberedPreview = true,
            keepLoadedInMemory = true
        )

        assertEquals(true, useRemembered)
    }

    @Test
    fun timelineFramesIncludeStartMiddleAndEndMarkers() {
        val frames = buildVideoTimelineFrames(frameCount = 7)

        assertEquals(7, frames.size)
        assertEquals("start", frames.first().keySuffix)
        assertEquals("middle", frames[3].keySuffix)
        assertEquals("end", frames.last().keySuffix)
    }

    @Test
    fun timelineFramesStayWithinSupportedPercentRange() {
        val frames = buildVideoTimelineFrames(frameCount = 9)

        assertEquals(0f, frames.first().percent)
        assertTrue(frames.last().percent <= 0.98f)
        assertTrue(frames.zipWithNext().all { (prev, next) -> next.percent >= prev.percent })
    }

    @Test
    fun dynamicTimelineFrameCountUsesAvailableWidth() {
        val count = dynamicTimelineFrameCount(
            containerWidth = 300.dp,
            frameHeight = 44.dp
        )

        assertEquals(4, count)
    }

    @Test
    fun dynamicTimelineFrameCountKeepsMinimumOneFrame() {
        val count = dynamicTimelineFrameCount(
            containerWidth = 1.dp,
            frameHeight = 44.dp
        )

        assertEquals(1, count)
    }

    @Test
    fun snappedTimelineFrameWidthFillsContainerWithSpacing() {
        val width = snappedTimelineFrameWidth(
            containerWidth = 300.dp,
            frameCount = 4,
            frameSpacing = 4.dp
        )

        assertEquals(72.dp, width)
    }
}
