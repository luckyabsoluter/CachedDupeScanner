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
    fun currentDragPointerOverridesReestimatedLazyThumbPosition() {
        val offset = displayedLazyThumbOffsetPx(
            estimatedThumbOffsetPx = 80f,
            dragPointerY = 250f,
            thumbHeightPx = 60f,
            maxThumbOffsetPx = 340f
        )

        assertEquals(220f, offset, 0.001f)
    }

    @Test
    fun estimateLazyListScrollTargetUsesKnownVariableItemSizes() {
        val knownItemSizesPxByIndex = listOf(0 to 300)

        val target = estimateLazyListScrollTarget(
            targetThumbOffsetPx = 50f,
            maxThumbOffsetPx = 100f,
            maxScrollPx = 1_000f,
            typicalItemSizePx = 100f,
            totalItems = 10,
            knownItemSizesPxByIndex = knownItemSizesPxByIndex
        )

        assertEquals(3, target.index)
        assertEquals(0, target.scrollOffsetPx)
        assertEquals(
            500f,
            estimateLazyListScrollOffsetPx(
                firstVisibleItemIndex = target.index,
                firstVisibleItemScrollOffsetPx = target.scrollOffsetPx,
                typicalItemSizePx = 100f,
                knownItemSizesPxByIndex = knownItemSizesPxByIndex
            ),
            0.001f
        )
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
