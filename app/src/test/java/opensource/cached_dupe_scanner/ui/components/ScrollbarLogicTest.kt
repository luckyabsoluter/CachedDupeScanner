package opensource.cached_dupe_scanner.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class ScrollbarLogicTest {
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
    fun estimateLazyListScrollTargetClampsToLastItem() {
        val target = estimateLazyListScrollTarget(
            targetThumbOffsetPx = 120f,
            maxThumbOffsetPx = 100f,
            maxScrollPx = 1_950f,
            typicalItemSizePx = 100f,
            totalItems = 20
        )

        assertEquals(19, target.index)
        assertEquals(50, target.scrollOffsetPx)
    }
}
