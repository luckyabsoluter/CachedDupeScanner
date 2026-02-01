package opensource.cached_dupe_scanner.ui.results

import opensource.cached_dupe_scanner.core.ScanResult

sealed class ScanUiState {
    data object Idle : ScanUiState()
    data class Scanning(val scanned: Int, val total: Int?) : ScanUiState()
    data class Success(val result: ScanResult) : ScanUiState()
    data class Error(val message: String) : ScanUiState()
}

sealed class ScanAction {
    data object StartScan : ScanAction()
    data class UpdateProgress(val scanned: Int, val total: Int?) : ScanAction()
    data class ScanSuccess(val result: ScanResult) : ScanAction()
    data class ScanError(val message: String) : ScanAction()
}

object ScanUiReducer {
    fun reduce(state: ScanUiState, action: ScanAction): ScanUiState {
        return when (action) {
            ScanAction.StartScan -> ScanUiState.Scanning(scanned = 0, total = null)
            is ScanAction.UpdateProgress -> ScanUiState.Scanning(action.scanned, action.total)
            is ScanAction.ScanSuccess -> ScanUiState.Success(action.result)
            is ScanAction.ScanError -> ScanUiState.Error(action.message)
        }
    }
}
