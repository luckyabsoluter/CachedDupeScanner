package opensource.cached_dupe_scanner.storage

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PathStoreTest {
    @Test
    fun saveLoadClearRoundTrip() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = PathStore(context)

        store.save("/storage/emulated/0/Download")
        assertEquals("/storage/emulated/0/Download", store.load())

        store.clear()
        assertNull(store.load())
    }
}
