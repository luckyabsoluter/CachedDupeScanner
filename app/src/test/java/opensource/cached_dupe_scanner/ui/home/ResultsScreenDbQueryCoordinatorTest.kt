package opensource.cached_dupe_scanner.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResultsScreenDbQueryCoordinatorTest {
    @Test
    fun laterRefreshInvalidatesEarlierRefreshToken() {
        val coordinator = ResultsScreenDbQueryCoordinator()

        val first = coordinator.beginRefresh(reset = true)
        val second = coordinator.beginRefresh(reset = true)

        assertFalse(coordinator.isRefreshTokenValid(first))
        assertTrue(coordinator.isRefreshTokenValid(second))
    }

    @Test
    fun resetRefreshInvalidatesInFlightPagingToken() {
        val coordinator = ResultsScreenDbQueryCoordinator()

        coordinator.beginRefresh(reset = true)
        val paging = coordinator.beginPaging()
        val refresh = coordinator.beginRefresh(reset = true)

        assertFalse(coordinator.isPagingTokenValid(paging))
        assertTrue(coordinator.isRefreshTokenValid(refresh))
    }

    @Test
    fun newerPagingInvalidatesOlderPagingToken() {
        val coordinator = ResultsScreenDbQueryCoordinator()

        coordinator.beginRefresh(reset = true)
        val firstPaging = coordinator.beginPaging()
        val secondPaging = coordinator.beginPaging()

        assertFalse(coordinator.isPagingTokenValid(firstPaging))
        assertTrue(coordinator.isPagingTokenValid(secondPaging))
    }

    @Test
    fun nonResetRefreshKeepsQueryVersion() {
        val coordinator = ResultsScreenDbQueryCoordinator()

        val resetRefresh = coordinator.beginRefresh(reset = true)
        val versionAfterReset = coordinator.currentQueryVersion()
        val nonResetRefresh = coordinator.beginRefresh(reset = false)

        assertEquals(versionAfterReset, coordinator.currentQueryVersion())
        assertEquals(versionAfterReset, resetRefresh.queryVersion)
        assertEquals(versionAfterReset, nonResetRefresh.queryVersion)
        assertTrue(coordinator.isRefreshTokenValid(nonResetRefresh))
    }
}
