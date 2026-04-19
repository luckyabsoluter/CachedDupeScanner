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

        assertTrue(settings.skipZeroSizeInDb)
        assertTrue(settings.skipTrashBinContentsInScan)
        assertFalse(settings.hideZeroSizeInResults)
        assertFalse(settings.showMemoryOverlay)
        assertFalse(settings.keepLoadedThumbnailsInMemory)
        assertTrue(settings.keepLoadedVideoPreviewsInMemory)
        assertFalse(settings.snapVideoPreviewFramesToWidth)
        assertEquals(100, settings.thumbnailSizePercent)
        assertEquals(100, settings.videoPreviewSizePercent)
        assertEquals("Count", settings.resultSortKey)
        assertEquals("Desc", settings.resultSortDirection)
        assertEquals("Path", settings.resultGroupSortKey)
        assertEquals("Asc", settings.resultGroupSortDirection)
        assertFalse(settings.showFullPaths)
        assertEquals("", settings.resultsFilterDefinitionJson)
        assertEquals("", settings.filesFilterDefinitionJson)
        assertEquals("Name", settings.filesSortKey)
        assertEquals("Asc", settings.filesSortDirection)
    }

    @Test
    fun canToggleExcludeZeroSizeDuplicates() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = AppSettingsStore(context)

        store.setSkipZeroSizeInDb(true)
        assertTrue(store.load().skipZeroSizeInDb)

        store.setSkipTrashBinContentsInScan(false)
        assertFalse(store.load().skipTrashBinContentsInScan)

        store.setHideZeroSizeInResults(true)
        assertTrue(store.load().hideZeroSizeInResults)

        store.setShowMemoryOverlay(true)
        assertTrue(store.load().showMemoryOverlay)

        store.setKeepLoadedThumbnailsInMemory(true)
        assertTrue(store.load().keepLoadedThumbnailsInMemory)

        store.setKeepLoadedVideoPreviewsInMemory(false)
        assertFalse(store.load().keepLoadedVideoPreviewsInMemory)

        store.setSnapVideoPreviewFramesToWidth(true)
        assertTrue(store.load().snapVideoPreviewFramesToWidth)

        store.setThumbnailSizePercent(125)
        assertEquals(125, store.load().thumbnailSizePercent)

        store.setVideoPreviewSizePercent(80)
        assertEquals(80, store.load().videoPreviewSizePercent)

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

        store.setResultsFilterDefinitionJson("{\"clusters\":[{\"id\":\"cluster_1\"}]}")
        assertEquals(
            "{\"clusters\":[{\"id\":\"cluster_1\"}]}",
            store.load().resultsFilterDefinitionJson
        )

        store.setFilesFilterDefinitionJson("{\"clusters\":[{\"id\":\"cluster_2\"}]}")
        assertEquals(
            "{\"clusters\":[{\"id\":\"cluster_2\"}]}",
            store.load().filesFilterDefinitionJson
        )

        store.setFilesSortKey("Size")
        store.setFilesSortDirection("Desc")
        val fileSortSettings = store.load()
        assertEquals("Size", fileSortSettings.filesSortKey)
        assertEquals("Desc", fileSortSettings.filesSortDirection)
    }

    @Test
    fun importUsesDefaultWhenSkipZeroSizeKeyIsMissing() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = AppSettingsStore(context)

        val imported = store.importFromJson("{}")

        assertTrue(imported.skipZeroSizeInDb)
        assertTrue(imported.skipTrashBinContentsInScan)
        assertFalse(imported.showMemoryOverlay)
        assertFalse(imported.keepLoadedThumbnailsInMemory)
        assertTrue(imported.keepLoadedVideoPreviewsInMemory)
        assertFalse(imported.snapVideoPreviewFramesToWidth)
        assertEquals(100, imported.thumbnailSizePercent)
        assertEquals(100, imported.videoPreviewSizePercent)
        assertEquals("", imported.resultsFilterDefinitionJson)
        assertEquals("", imported.filesFilterDefinitionJson)
        assertTrue(store.load().skipZeroSizeInDb)
        assertTrue(store.load().skipTrashBinContentsInScan)
    }

    @Test
    fun exportImportRoundTripPreservesNonDefaultValues() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = AppSettingsStore(context)

        store.setSkipZeroSizeInDb(false)
        store.setSkipTrashBinContentsInScan(false)
        store.setHideZeroSizeInResults(true)
        store.setShowMemoryOverlay(true)
        store.setKeepLoadedThumbnailsInMemory(true)
        store.setKeepLoadedVideoPreviewsInMemory(false)
        store.setSnapVideoPreviewFramesToWidth(true)
        store.setThumbnailSizePercent(125)
        store.setVideoPreviewSizePercent(80)
        store.setResultSortKey("Name")
        store.setResultSortDirection("Asc")
        store.setResultGroupSortKey("Modified")
        store.setResultGroupSortDirection("Desc")
        store.setShowFullPaths(true)
        store.setResultsFilterDefinitionJson("{\"clusters\":[{\"id\":\"cluster_1\",\"name\":\"Saved\"}]}")
        store.setFilesFilterDefinitionJson("{\"clusters\":[{\"id\":\"cluster_2\",\"name\":\"Files\"}]}")
        store.setFilesSortKey("Size")
        store.setFilesSortDirection("Desc")

        val exported = store.exportToJson()

        val importedStore = AppSettingsStore(context)
        val imported = importedStore.importFromJson(exported)

        assertFalse(imported.skipZeroSizeInDb)
        assertFalse(imported.skipTrashBinContentsInScan)
        assertTrue(imported.hideZeroSizeInResults)
        assertTrue(imported.showMemoryOverlay)
        assertTrue(imported.keepLoadedThumbnailsInMemory)
        assertFalse(imported.keepLoadedVideoPreviewsInMemory)
        assertTrue(imported.snapVideoPreviewFramesToWidth)
        assertEquals(125, imported.thumbnailSizePercent)
        assertEquals(80, imported.videoPreviewSizePercent)
        assertEquals("Name", imported.resultSortKey)
        assertEquals("Asc", imported.resultSortDirection)
        assertEquals("Modified", imported.resultGroupSortKey)
        assertEquals("Desc", imported.resultGroupSortDirection)
        assertTrue(imported.showFullPaths)
        assertEquals("{\"clusters\":[{\"id\":\"cluster_1\",\"name\":\"Saved\"}]}", imported.resultsFilterDefinitionJson)
        assertEquals("{\"clusters\":[{\"id\":\"cluster_2\",\"name\":\"Files\"}]}", imported.filesFilterDefinitionJson)
        assertEquals("Size", imported.filesSortKey)
        assertEquals("Desc", imported.filesSortDirection)
        assertEquals(imported, importedStore.load())
    }

    @Test
    fun previewSizeSupportsFineGrainedValuesWithoutUpperCap() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = AppSettingsStore(context)

        store.setThumbnailSizePercent(137)
        assertEquals(137, store.load().thumbnailSizePercent)

        store.setVideoPreviewSizePercent(163)
        assertEquals(163, store.load().videoPreviewSizePercent)

        store.setThumbnailSizePercent(10000)
        assertEquals(10000, store.load().thumbnailSizePercent)

        store.setVideoPreviewSizePercent(7500)
        assertEquals(7500, store.load().videoPreviewSizePercent)

        store.setThumbnailSizePercent(-1)
        assertEquals(0, store.load().thumbnailSizePercent)

        store.setVideoPreviewSizePercent(-25)
        assertEquals(0, store.load().videoPreviewSizePercent)
    }
}
