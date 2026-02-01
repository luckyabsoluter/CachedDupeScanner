package opensource.cached_dupe_scanner.storage

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import opensource.cached_dupe_scanner.core.DuplicateGroup
import opensource.cached_dupe_scanner.core.FileMetadata
import opensource.cached_dupe_scanner.core.ScanResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ScanResultStoreTest {
    @Test
    fun saveLoadClearRoundTrip() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = ScanResultStore(context)

        val result = ScanResult(
            scannedAtMillis = 1234L,
            files = listOf(
                FileMetadata("/a", "/a", 1, 10, "h1"),
                FileMetadata("/b", "/b", 2, 20, "h1")
            ),
            duplicateGroups = listOf(
                DuplicateGroup("h1", listOf(
                    FileMetadata("/a", "/a", 1, 10, "h1"),
                    FileMetadata("/b", "/b", 2, 20, "h1")
                ))
            )
        )

        store.save(result)
        val loaded = store.load()
        assertNotNull(loaded)
        assertEquals(result.scannedAtMillis, loaded?.scannedAtMillis)
        assertEquals(result.files.size, loaded?.files?.size)
        assertEquals(result.duplicateGroups.size, loaded?.duplicateGroups?.size)

        store.clear()
        assertNull(store.load())
    }
}
