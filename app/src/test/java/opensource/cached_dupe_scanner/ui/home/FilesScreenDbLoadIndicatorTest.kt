package opensource.cached_dupe_scanner.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test

class FilesScreenDbLoadIndicatorTest {
    @Test
    fun filteredFilesLoadIndicatorTextShowsSourceProgressAndMatchedCount() {
        val text = filteredFilesLoadIndicatorText(
            filteredCurrentIndex = 4,
            matchedCount = 37,
            sourceLoadedCount = 120,
            totalCount = 1000
        )

        assertEquals("5/37 - 120/1000", text)
    }

    @Test
    fun filteredFilesLoadIndicatorTextReturnsNullWhenNoSourceRowsExist() {
        val text = filteredFilesLoadIndicatorText(
            filteredCurrentIndex = 0,
            matchedCount = 0,
            sourceLoadedCount = 0,
            totalCount = 0
        )

        assertEquals(null, text)
    }

    @Test
    fun filteredFilesLoadIndicatorTextUsesZeroCurrentWhenNoMatchExists() {
        val text = filteredFilesLoadIndicatorText(
            filteredCurrentIndex = 7,
            matchedCount = 0,
            sourceLoadedCount = 120,
            totalCount = 1000
        )

        assertEquals("0/0 - 120/1000", text)
    }
}
