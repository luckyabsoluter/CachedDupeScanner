package opensource.cached_dupe_scanner.ui.home

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal fun launchTrackedScanJob(
    scope: CoroutineScope,
    onJobStarted: (Job) -> Unit,
    block: suspend CoroutineScope.() -> Unit
): Job {
    val job = scope.launch(start = CoroutineStart.LAZY, block = block)
    onJobStarted(job)
    job.start()
    return job
}
