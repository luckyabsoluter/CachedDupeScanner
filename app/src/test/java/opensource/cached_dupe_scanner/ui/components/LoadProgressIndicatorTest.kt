package opensource.cached_dupe_scanner.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class LoadProgressIndicatorTest {
    @Test
    fun formatLoadProgressTextBuildsExpectedString() {
        val text = formatLoadProgressText(
            current = 3,
            loaded = 10,
            total = 20
        )

        assertEquals("3/10/20 (30%/50%)", text)
    }

    @Test
    fun formatLoadProgressTextReturnsNullForZeroTotal() {
        val text = formatLoadProgressText(
            current = 1,
            loaded = 0,
            total = 0
        )

        assertEquals(null, text)
    }

    @Test
    fun formatFilteredLoadProgressTextShowsSourceProgressAndMatchedCount() {
        val text = formatFilteredLoadProgressText(
            filteredCurrentIndex = 4,
            matchedCount = 37,
            sourceLoadedCount = 120,
            totalCount = 1000
        )

        assertEquals("5/37 - 120/1000", text)
    }

    @Test
    fun formatFilteredLoadProgressTextReturnsNullWhenNoSourceRowsExist() {
        val text = formatFilteredLoadProgressText(
            filteredCurrentIndex = 0,
            matchedCount = 0,
            sourceLoadedCount = 0,
            totalCount = 0
        )

        assertEquals(null, text)
    }

    @Test
    fun formatFilteredLoadProgressTextUsesZeroCurrentWhenNoMatchExists() {
        val text = formatFilteredLoadProgressText(
            filteredCurrentIndex = 7,
            matchedCount = 0,
            sourceLoadedCount = 120,
            totalCount = 1000
        )

        assertEquals("0/0 - 120/1000", text)
    }
}
