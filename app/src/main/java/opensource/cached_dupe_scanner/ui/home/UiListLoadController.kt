package opensource.cached_dupe_scanner.ui.home

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.runInterruptible

internal class UiListLoadController {
    private data class ActiveLoad(
        val requestId: Long,
        val job: Job
    )

    private val lock = Any()
    private var requestSequence = 0L
    private var activeLoad: ActiveLoad? = null

    val hasActiveLoad: Boolean
        get() = synchronized(lock) { activeLoad?.job?.isActive == true }

    fun replace(
        scope: CoroutineScope,
        block: suspend UiListLoadRequest.() -> Unit
    ): Job {
        return launch(scope = scope, replace = true, block = block)!!
    }

    fun launchIfIdle(
        scope: CoroutineScope,
        block: suspend UiListLoadRequest.() -> Unit
    ): Job? {
        return launch(scope = scope, replace = false, block = block)
    }

    fun cancel() {
        val job = synchronized(lock) {
            val current = activeLoad?.job
            activeLoad = null
            current
        }
        job?.cancel()
    }

    private fun launch(
        scope: CoroutineScope,
        replace: Boolean,
        block: suspend UiListLoadRequest.() -> Unit
    ): Job? {
        lateinit var job: Job
        lateinit var request: UiListLoadRequest
        var previousJob: Job? = null
        val requestId: Long
        synchronized(lock) {
            val current = activeLoad
            if (!replace && current?.job?.isActive == true) return null
            previousJob = current?.job
            requestSequence += 1L
            requestId = requestSequence
            request = UiListLoadRequest {
                synchronized(lock) {
                    val active = activeLoad
                    active?.requestId == requestId && active.job === job && active.job.isActive
                }
            }
            job = scope.launch(start = CoroutineStart.LAZY) {
                request.block()
            }
            activeLoad = ActiveLoad(requestId = requestId, job = job)
            job.invokeOnCompletion {
                synchronized(lock) {
                    val active = activeLoad
                    if (active?.requestId == requestId && active.job === job) {
                        activeLoad = null
                    }
                }
            }
        }
        previousJob?.cancel()
        job.start()
        return job
    }
}

internal class UiListLoadRequest internal constructor(
    private val currentRequest: () -> Boolean
) {
    val isCurrent: Boolean
        get() = currentRequest()
}

internal suspend fun <T> runInterruptibleUiListLoad(
    block: (shouldContinue: () -> Boolean) -> T
): T {
    val parentJob = currentCoroutineContext()[Job]
    return runInterruptible(Dispatchers.IO) {
        block {
            parentJob?.isActive != false && !Thread.currentThread().isInterrupted
        }
    }
}

internal fun ensureUiListLoadContinues(shouldContinue: () -> Boolean) {
    if (!shouldContinue()) {
        throw CancellationException("List loading was cancelled")
    }
}
