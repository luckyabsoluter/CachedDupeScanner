package opensource.cached_dupe_scanner.ui

import opensource.cached_dupe_scanner.core.ScanResult
import opensource.cached_dupe_scanner.ui.results.ScanAction
import opensource.cached_dupe_scanner.ui.results.ScanUiReducer
import opensource.cached_dupe_scanner.ui.results.ScanUiState
import org.junit.Assert.assertEquals
import org.junit.Test

class ScanUiReducerTest {
    @Test
    fun reducerHandlesProgressSuccessError() {
        var state: ScanUiState = ScanUiState.Idle

        state = ScanUiReducer.reduce(state, ScanAction.StartScan)
        assertEquals(ScanUiState.Scanning(0, null), state)

        state = ScanUiReducer.reduce(state, ScanAction.UpdateProgress(3, 10))
        assertEquals(ScanUiState.Scanning(3, 10), state)

        val result = ScanResult(scannedAtMillis = 1, files = emptyList(), duplicateGroups = emptyList())
        state = ScanUiReducer.reduce(state, ScanAction.ScanSuccess(result))
        assertEquals(ScanUiState.Success(result), state)

        state = ScanUiReducer.reduce(state, ScanAction.ScanError("boom"))
        assertEquals(ScanUiState.Error("boom"), state)
    }
}
