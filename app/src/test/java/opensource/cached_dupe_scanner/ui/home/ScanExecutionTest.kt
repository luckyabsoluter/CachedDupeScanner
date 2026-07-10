package opensource.cached_dupe_scanner.ui.home

import java.util.concurrent.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import opensource.cached_dupe_scanner.core.ScanResult
import opensource.cached_dupe_scanner.tasks.TaskArea
import opensource.cached_dupe_scanner.tasks.TaskCoordinator
import opensource.cached_dupe_scanner.tasks.TaskKind
import opensource.cached_dupe_scanner.tasks.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ScanExecutionTest {
    @Test
    fun captureScanExecutionReturnsCompletedValue() = runBlocking {
        val execution = captureScanExecution { 42 }

        assertEquals(ScanExecution.Completed(42), execution)
    }

    @Test
    fun captureScanExecutionMapsCancellationToCancelled() = runBlocking {
        val execution = captureScanExecution<Int> {
            throw CancellationException("cancelled")
        }

        assertSame(ScanExecution.Cancelled, execution)
    }

    @Test
    fun captureScanExecutionRethrowsNonCancellationFailure() = runBlocking {
        val failure = IllegalStateException("boom")

        try {
            captureScanExecution<Int> { throw failure }
            fail("Expected failure to be rethrown")
        } catch (error: IllegalStateException) {
            assertSame(failure, error)
        }
    }

    @Test
    fun scanTaskCompletesOnlyAfterCompletionCallbackReturns() = runBlocking {
        val callbackEntered = CompletableDeferred<Unit>()
        val releaseCallback = CompletableDeferred<Unit>()
        val taskCoordinator = TaskCoordinator()
        taskCoordinator.tryStart(
            area = TaskArea.Scan,
            kind = TaskKind.ScanTarget,
            title = "Scanning files",
            detail = "Saving cache"
        )
        val result = ScanResult(
            scannedAtMillis = 1L,
            files = emptyList(),
            duplicateGroups = emptyList()
        )

        val completion = async {
            completeScanTaskAfterCallback(
                result = result,
                onScanComplete = {
                    callbackEntered.complete(Unit)
                    releaseCallback.await()
                },
                taskCoordinator = taskCoordinator
            )
        }

        callbackEntered.await()
        assertTrue(taskCoordinator.isAreaBusy(TaskArea.Scan))
        assertFalse(completion.isCompleted)

        releaseCallback.complete(Unit)
        val terminal = completion.await()

        assertFalse(taskCoordinator.isAreaBusy(TaskArea.Scan))
        assertEquals(TaskStatus.Completed, terminal?.status)
    }
}
