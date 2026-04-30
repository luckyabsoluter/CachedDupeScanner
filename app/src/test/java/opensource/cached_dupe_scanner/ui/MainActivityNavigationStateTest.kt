package opensource.cached_dupe_scanner.ui

import opensource.cached_dupe_scanner.Screen
import opensource.cached_dupe_scanner.restoreScreenStack
import org.junit.Assert.assertEquals
import org.junit.Test

class MainActivityNavigationStateTest {
    @Test
    fun restoreScreenStackKeepsCurrentScreenAfterConfigurationRecreation() {
        val restored = restoreScreenStack(
            listOf(
                Screen.Dashboard.toSaveToken(),
                Screen.Settings.toSaveToken()
            )
        )

        assertEquals(listOf(Screen.Dashboard, Screen.Settings), restored)
    }

    @Test
    fun restoreScreenStackKeepsReportDetailParameters() {
        val restored = restoreScreenStack(
            listOf(
                Screen.Dashboard.toSaveToken(),
                Screen.Reports.toSaveToken(),
                Screen.ReportDetail("report-42").toSaveToken()
            )
        )

        assertEquals(
            listOf(Screen.Dashboard, Screen.Reports, Screen.ReportDetail("report-42")),
            restored
        )
    }

    @Test
    fun restoreScreenStackFallsBackToDashboardForEmptySavedState() {
        assertEquals(listOf(Screen.Dashboard), restoreScreenStack(emptyList()))
    }

    @Test
    fun restoreScreenStackKeepsDashboardAsStackRoot() {
        assertEquals(
            listOf(Screen.Dashboard, Screen.Trash),
            restoreScreenStack(listOf(Screen.Trash.toSaveToken()))
        )
    }
}
