package opensource.cached_dupe_scanner.ui.home

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanJobLauncherTest {
    @Test
    fun launchTrackedScanJobPublishesActiveJobBeforeRunningBlock() = runBlocking {
        val publishedJob = AtomicReference<Job?>()
        val blockRan = AtomicBoolean(false)
        val jobSeenByBlock = AtomicReference<Job?>()

        val launchedJob = launchTrackedScanJob(
            scope = this,
            onJobStarted = { job -> publishedJob.set(job) }
        ) {
            blockRan.set(true)
            jobSeenByBlock.set(publishedJob.get())
        }

        launchedJob.join()

        assertTrue(blockRan.get())
        assertSame(launchedJob, publishedJob.get())
        assertSame(launchedJob, jobSeenByBlock.get())
        assertFalse(publishedJob.get()?.isActive == true)
    }
}
