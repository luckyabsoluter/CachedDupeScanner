package opensource.cached_dupe_scanner.ui.home

import android.os.OperationCanceledException
import androidx.core.os.CancellationSignal
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
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

internal class UiListLoadCancellation internal constructor(
    private val parentJob: Job?
) : () -> Boolean {
    val queryCancellationSignal = CancellationSignal()

    val frameworkQueryCancellationSignal: android.os.CancellationSignal
        get() = checkNotNull(
            queryCancellationSignal.cancellationSignalObject as? android.os.CancellationSignal
        )

    override fun invoke(): Boolean {
        return parentJob?.isActive != false &&
            !queryCancellationSignal.isCanceled &&
            !Thread.currentThread().isInterrupted
    }

    internal fun cancel() {
        queryCancellationSignal.cancel()
    }
}

internal suspend fun <T> runInterruptibleUiListLoad(
    block: (cancellation: UiListLoadCancellation) -> T
): T = coroutineScope {
    val parentJob = currentCoroutineContext()[Job]
    val cancellation = UiListLoadCancellation(parentJob)
    val cancellationWatcher = launch(
        context = Dispatchers.Unconfined,
        start = CoroutineStart.UNDISPATCHED
    ) {
        try {
            awaitCancellation()
        } finally {
            cancellation.cancel()
        }
    }
    try {
        runInterruptible(Dispatchers.IO) {
            block(cancellation)
        }
    } catch (cancelledQuery: OperationCanceledException) {
        if (cancellation.queryCancellationSignal.isCanceled) {
            throw CancellationException("List database query was cancelled").also { cancellationException ->
                cancellationException.initCause(cancelledQuery)
            }
        }
        throw cancelledQuery
    } finally {
        cancellationWatcher.cancel()
    }
}

internal fun ensureUiListLoadContinues(shouldContinue: () -> Boolean) {
    if (!shouldContinue()) {
        throw CancellationException("List loading was cancelled")
    }
}
