package opensource.cached_dupe_scanner.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FilteredPagingTest {
    @Test
    fun loadFilteredSourcePageStopsAfterEnoughMatches() {
        val page = loadFilteredSourcePage(
            startCursor = 0,
            minMatches = 2,
            loadPage = { cursor ->
                SourcePage(
                    items = listOf(cursor, cursor + 1, cursor + 2),
                    nextCursor = cursor + 3,
                    exhausted = false
                )
            },
            transformMatch = { value ->
                value.takeIf { it % 2 == 0 }
            }
        )

        assertEquals(listOf(0, 2), page.items)
        assertEquals(3, page.nextCursor)
        assertEquals(3, page.sourceLoadedCount)
        assertFalse(page.exhausted)
    }

    @Test
    fun loadFilteredSourcePageTracksScannedRowsAcrossSkippedPages() {
        val page = loadFilteredSourcePage(
            startCursor = 0,
            minMatches = 1,
            loadPage = { cursor ->
                when (cursor) {
                    0 -> SourcePage(
                        items = listOf("skip-1", "skip-2"),
                        nextCursor = 2,
                        exhausted = false
                    )

                    else -> SourcePage(
                        items = listOf("match"),
                        nextCursor = 3,
                        exhausted = false
                    )
                }
            },
            transformMatch = { value ->
                value.takeIf { it == "match" }
            }
        )

        assertEquals(listOf("match"), page.items)
        assertEquals(3, page.nextCursor)
        assertEquals(3, page.sourceLoadedCount)
        assertFalse(page.exhausted)
    }

    @Test
    fun loadFilteredSourcePageCarriesNextCursorThroughSkippedPages() {
        val page = loadFilteredSourcePage(
            startCursor = "start",
            minMatches = 1,
            loadPage = { cursor ->
                when (cursor) {
                    "start" -> SourcePage(
                        items = listOf("skip"),
                        nextCursor = "after-skip",
                        exhausted = false
                    )

                    else -> SourcePage(
                        items = listOf("match"),
                        nextCursor = "after-match",
                        exhausted = false
                    )
                }
            },
            transformMatch = { value ->
                value.takeIf { it == "match" }
            }
        )

        assertEquals(listOf("match"), page.items)
        assertEquals("after-match", page.nextCursor)
    }

    @Test
    fun loadFilteredSourcePageCanKeepAllMatchesFromFinalLoadedPage() {
        val page = loadFilteredSourcePage(
            startCursor = 0,
            minMatches = 1,
            loadPage = {
                SourcePage(
                    items = listOf("first", "second", "third"),
                    nextCursor = 3,
                    exhausted = false
                )
            },
            transformMatch = { value -> value },
            trimToMinMatches = false
        )

        assertEquals(listOf("first", "second", "third"), page.items)
        assertEquals(3, page.nextCursor)
        assertEquals(3, page.sourceLoadedCount)
        assertFalse(page.exhausted)
    }

    @Test
    fun loadFilteredSourcePageMarksExhaustedWhenSourcePageExhausts() {
        val page = loadFilteredSourcePage(
            startCursor = 0,
            minMatches = 2,
            loadPage = {
                SourcePage(
                    items = listOf("match"),
                    nextCursor = 1,
                    exhausted = true
                )
            },
            transformMatch = { value -> value }
        )

        assertEquals(listOf("match"), page.items)
        assertEquals(1, page.nextCursor)
        assertEquals(1, page.sourceLoadedCount)
        assertTrue(page.exhausted)
    }
}
