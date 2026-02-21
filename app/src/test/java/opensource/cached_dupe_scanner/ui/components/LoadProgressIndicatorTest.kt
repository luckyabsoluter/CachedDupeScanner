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
}
