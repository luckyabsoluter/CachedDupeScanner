package opensource.cached_dupe_scanner.storage

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AppSettingsStoreTest {
    @Test
    fun defaultsToExcludeZeroSizeDuplicates() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = AppSettingsStore(context)

        val settings = store.load()

        assertFalse(settings.skipZeroSizeInDb)
        assertFalse(settings.hideZeroSizeInResults)
        assertEquals("Count", settings.resultSortKey)
        assertEquals("Desc", settings.resultSortDirection)
        assertEquals("Path", settings.resultGroupSortKey)
        assertEquals("Asc", settings.resultGroupSortDirection)
        assertFalse(settings.showFullPaths)
        assertEquals("Name", settings.filesSortKey)
        assertEquals("Asc", settings.filesSortDirection)
    }

    @Test
    fun canToggleExcludeZeroSizeDuplicates() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = AppSettingsStore(context)

        store.setSkipZeroSizeInDb(true)
        assertTrue(store.load().skipZeroSizeInDb)

        store.setHideZeroSizeInResults(true)
        assertTrue(store.load().hideZeroSizeInResults)

        store.setResultSortKey("Name")
        store.setResultSortDirection("Asc")
        val loaded = store.load()
        assertEquals("Name", loaded.resultSortKey)
        assertEquals("Asc", loaded.resultSortDirection)

        store.setResultGroupSortKey("Modified")
        store.setResultGroupSortDirection("Desc")
        val groupSortSettings = store.load()
        assertEquals("Modified", groupSortSettings.resultGroupSortKey)
        assertEquals("Desc", groupSortSettings.resultGroupSortDirection)

        store.setShowFullPaths(true)
        assertTrue(store.load().showFullPaths)

        store.setFilesSortKey("Size")
        store.setFilesSortDirection("Desc")
        val fileSortSettings = store.load()
        assertEquals("Size", fileSortSettings.filesSortKey)
        assertEquals("Desc", fileSortSettings.filesSortDirection)
    }
}
