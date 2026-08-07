package opensource.cached_dupe_scanner.ui.home

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UiListLoadControllerTest {
    @Test
    fun replaceInterruptsPreviousLoadAndOnlyLatestRequestCanPublish() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val controller = UiListLoadController()
        val firstEntered = CountDownLatch(1)
        val firstInterrupted = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val staleErrorPublished = AtomicBoolean(false)
        val staleFinallyPublished = AtomicBoolean(false)
        val publishedValue = AtomicReference<String?>(null)

        try {
            val firstJob = controller.replace(scope) {
                try {
                    runInterruptibleUiListLoad {
                        firstEntered.countDown()
                        try {
                            releaseFirst.await(10, TimeUnit.SECONDS)
                        } catch (error: InterruptedException) {
                            firstInterrupted.countDown()
                            throw error
                        }
                    }
                } catch (error: CancellationException) {
                    if (isCurrent) {
                        staleErrorPublished.set(true)
                    }
                    throw error
                } finally {
                    if (isCurrent) {
                        staleFinallyPublished.set(true)
                    }
                }
            }
            assertTrue(firstEntered.await(5, TimeUnit.SECONDS))

            val secondJob = controller.replace(scope) {
                if (isCurrent) {
                    publishedValue.set("second")
                }
            }

            secondJob.join()
            firstJob.join()

            assertTrue(firstInterrupted.await(5, TimeUnit.SECONDS))
            assertEquals("second", publishedValue.get())
            assertFalse(staleErrorPublished.get())
            assertFalse(staleFinallyPublished.get())
            assertFalse(controller.hasActiveLoad)
        } finally {
            releaseFirst.countDown()
            scope.cancel()
        }
    }

    @Test
    fun launchIfIdleRejectsOverlapAndAllowsNextPageAfterCompletion() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val controller = UiListLoadController()
        val firstEntered = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)

        try {
            val firstJob = controller.launchIfIdle(scope) {
                firstEntered.countDown()
                releaseFirst.await(5, TimeUnit.SECONDS)
            }
            assertNotNull(firstJob)
            assertTrue(firstEntered.await(5, TimeUnit.SECONDS))

            val overlappingJob = controller.launchIfIdle(scope) {}
            assertNull(overlappingJob)

            releaseFirst.countDown()
            firstJob!!.join()

            val nextJob = controller.launchIfIdle(scope) {}
            assertNotNull(nextJob)
            nextJob!!.join()
            assertFalse(controller.hasActiveLoad)
        } finally {
            releaseFirst.countDown()
            scope.cancel()
        }
    }

    @Test
    fun cancelSignalsDatabaseQueryEvenWhenBlockingWorkIgnoresThreadInterrupt() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val controller = UiListLoadController()
        val queryEntered = CountDownLatch(1)
        val queryCancellationReceived = CountDownLatch(1)
        val releaseQuery = CountDownLatch(1)

        try {
            val job = controller.replace(scope) {
                runInterruptibleUiListLoad { cancellation ->
                    cancellation.queryCancellationSignal.setOnCancelListener {
                        queryCancellationReceived.countDown()
                    }
                    queryEntered.countDown()
                    while (releaseQuery.count > 0L) {
                        try {
                            releaseQuery.await(100, TimeUnit.MILLISECONDS)
                        } catch (_: InterruptedException) {
                            Thread.interrupted()
                        }
                    }
                }
            }

            assertTrue(queryEntered.await(5, TimeUnit.SECONDS))
            controller.cancel()

            assertTrue(queryCancellationReceived.await(5, TimeUnit.SECONDS))
            assertFalse(controller.hasActiveLoad)

            releaseQuery.countDown()
            job.join()
        } finally {
            releaseQuery.countDown()
            scope.cancel()
        }
    }
}
