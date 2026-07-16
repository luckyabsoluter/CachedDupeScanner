package opensource.cached_dupe_scanner.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class ScrollbarLogicTest {
    @Test
    fun currentPointerYRemainsAtTrackEndWhenThumbGeometryChanges() {
        val initialOffset = thumbOffsetForPointerY(
            pointerY = 400f,
            thumbHeightPx = 160f,
            maxThumbOffsetPx = 240f
        )
        val resizedOffset = thumbOffsetForPointerY(
            pointerY = 400f,
            thumbHeightPx = 64f,
            maxThumbOffsetPx = 336f
        )

        assertEquals(240f, initialOffset, 0.001f)
        assertEquals(336f, resizedOffset, 0.001f)
    }

    @Test
    fun currentPointerYCentersThumbWithoutAccumulatedDragDelta() {
        val offset = thumbOffsetForPointerY(
            pointerY = 125f,
            thumbHeightPx = 50f,
            maxThumbOffsetPx = 300f
        )

        assertEquals(100f, offset, 0.001f)
    }

    @Test
    fun estimateLazyListScrollTargetMapsThumbOffsetToListPosition() {
        val target = estimateLazyListScrollTarget(
            targetThumbOffsetPx = 50f,
            maxThumbOffsetPx = 100f,
            maxScrollPx = 1_000f,
            typicalItemSizePx = 100f,
            totalItems = 20
        )

        assertEquals(5, target.index)
        assertEquals(0, target.scrollOffsetPx)
    }

    @Test
    fun estimateLazyListScrollTargetClampsBeyondTrackEndToLastItem() {
        val target = estimateLazyListScrollTarget(
            targetThumbOffsetPx = 120f,
            maxThumbOffsetPx = 100f,
            maxScrollPx = 1_950f,
            typicalItemSizePx = 100f,
            totalItems = 20
        )

        assertEquals(19, target.index)
        assertEquals(0, target.scrollOffsetPx)
    }

    @Test
    fun estimateLazyListScrollTargetMapsExactTrackEndToLastItem() {
        val target = estimateLazyListScrollTarget(
            targetThumbOffsetPx = 100f,
            maxThumbOffsetPx = 100f,
            maxScrollPx = 600f,
            typicalItemSizePx = 50f,
            totalItems = 20
        )

        assertEquals(19, target.index)
        assertEquals(0, target.scrollOffsetPx)
    }
}
