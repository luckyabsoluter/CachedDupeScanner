package opensource.cached_dupe_scanner.ui.home

import java.util.concurrent.CancellationException

internal sealed interface ScanExecution<out T> {
    data class Completed<T>(val value: T) : ScanExecution<T>

    data object Cancelled : ScanExecution<Nothing>
}

internal suspend fun <T> captureScanExecution(block: suspend () -> T): ScanExecution<T> {
    return try {
        ScanExecution.Completed(block())
    } catch (_: CancellationException) {
        ScanExecution.Cancelled
    }
}
