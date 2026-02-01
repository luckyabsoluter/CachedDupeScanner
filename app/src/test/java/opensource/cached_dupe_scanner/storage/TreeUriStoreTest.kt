package opensource.cached_dupe_scanner.storage

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TreeUriStoreTest {
    @Test
    fun saveLoadClearRoundTrip() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = TreeUriStore(context)
        val uri = Uri.parse("content://com.example/tree/primary:Downloads")

        store.save(uri)
        assertEquals(uri, store.load())

        store.clear()
        assertNull(store.load())
    }
}
