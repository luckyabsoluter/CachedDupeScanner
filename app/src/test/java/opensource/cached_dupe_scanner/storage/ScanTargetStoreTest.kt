package opensource.cached_dupe_scanner.storage

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ScanTargetStoreTest {
    @Test
    fun addUpdateRemoveTargets() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = ScanTargetStore(context)

        val added = store.addTarget("/storage/emulated/0/Download")
        assertNotNull(added.id)

        val loaded = store.loadTargets()
        assertEquals(1, loaded.size)

        store.updateTarget(added.id, "/storage/emulated/0/Documents")
        val updated = store.loadTargets().first()
        assertEquals("/storage/emulated/0/Documents", updated.path)

        store.saveSelectedTargetId(added.id)
        store.removeTarget(added.id)
        assertEquals(0, store.loadTargets().size)
        assertEquals(null, store.loadSelectedTargetId())
    }
}
