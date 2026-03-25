package opensource.cached_dupe_scanner.ui.home

import java.util.concurrent.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
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
}
