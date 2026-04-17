package opensource.cached_dupe_scanner.ui.home

import opensource.cached_dupe_scanner.storage.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsScreenTest {
    @Test
    fun zeroSizeSettingsSectionKeepsRelatedTogglesTogether() {
        val section = zeroSizeSettingsSection(
            settings = AppSettings(
                skipZeroSizeInDb = true,
                skipTrashBinContentsInScan = true,
                hideZeroSizeInResults = false,
                showMemoryOverlay = false,
                keepLoadedThumbnailsInMemory = false,
                keepLoadedVideoPreviewsInMemory = true,
                resultSortKey = "Count",
                resultSortDirection = "Desc",
                resultGroupSortKey = "Path",
                resultGroupSortDirection = "Asc",
                showFullPaths = false,
                resultsFilterDefinitionJson = "",
                filesFilterDefinitionJson = "",
                filesSortKey = "Name",
                filesSortDirection = "Asc"
            )
        )

        assertEquals("Zero-size handling", section.title)
        assertEquals(2, section.toggles.size)
        assertEquals(ToggleSettingId.SkipZeroSizeInDb, section.toggles[0].id)
        assertEquals(ToggleSettingId.HideZeroSizeInResults, section.toggles[1].id)
        assertTrue(section.toggles[0].checked)
        assertFalse(section.toggles[1].checked)
    }

    @Test
    fun trashScanSettingsSectionDefaultsToCheckedToggle() {
        val section = trashScanSettingsSection(
            settings = AppSettings(
                skipZeroSizeInDb = true,
                skipTrashBinContentsInScan = true,
                hideZeroSizeInResults = false,
                showMemoryOverlay = false,
                keepLoadedThumbnailsInMemory = false,
                keepLoadedVideoPreviewsInMemory = true,
                resultSortKey = "Count",
                resultSortDirection = "Desc",
                resultGroupSortKey = "Path",
                resultGroupSortDirection = "Asc",
                showFullPaths = false,
                resultsFilterDefinitionJson = "",
                filesFilterDefinitionJson = "",
                filesSortKey = "Name",
                filesSortDirection = "Asc"
            )
        )

        assertEquals("Trash scan exclusion", section.title)
        assertEquals(1, section.toggles.size)
        assertEquals(ToggleSettingId.SkipTrashBinContentsInScan, section.toggles[0].id)
        assertTrue(section.toggles[0].checked)
    }

    @Test
    fun memoryOverlaySectionExposesTopLeftOverlayToggle() {
        val section = memoryOverlaySection(
            settings = AppSettings(
                skipZeroSizeInDb = true,
                skipTrashBinContentsInScan = true,
                hideZeroSizeInResults = false,
                showMemoryOverlay = true,
                keepLoadedThumbnailsInMemory = false,
                keepLoadedVideoPreviewsInMemory = true,
                resultSortKey = "Count",
                resultSortDirection = "Desc",
                resultGroupSortKey = "Path",
                resultGroupSortDirection = "Asc",
                showFullPaths = false,
                resultsFilterDefinitionJson = "",
                filesFilterDefinitionJson = "",
                filesSortKey = "Name",
                filesSortDirection = "Asc"
            )
        )

        assertEquals("Memory overlay", section.title)
        assertEquals(1, section.toggles.size)
        assertEquals(ToggleSettingId.ShowMemoryOverlay, section.toggles[0].id)
        assertTrue(section.toggles[0].checked)
    }

    @Test
    fun thumbnailMemorySectionExposesRamToggle() {
        val section = thumbnailMemorySettingsSection(
            settings = AppSettings(
                skipZeroSizeInDb = true,
                skipTrashBinContentsInScan = true,
                hideZeroSizeInResults = false,
                showMemoryOverlay = false,
                keepLoadedThumbnailsInMemory = true,
                keepLoadedVideoPreviewsInMemory = true,
                resultSortKey = "Count",
                resultSortDirection = "Desc",
                resultGroupSortKey = "Path",
                resultGroupSortDirection = "Asc",
                showFullPaths = false,
                resultsFilterDefinitionJson = "",
                filesFilterDefinitionJson = "",
                filesSortKey = "Name",
                filesSortDirection = "Asc"
            )
        )

        assertEquals("Thumbnail memory", section.title)
        assertEquals(1, section.toggles.size)
        assertEquals(ToggleSettingId.KeepLoadedThumbnailsInMemory, section.toggles[0].id)
        assertTrue(section.toggles[0].checked)
    }

    @Test
    fun videoPreviewMemorySectionExposesDedicatedRamToggle() {
        val section = videoPreviewMemorySettingsSection(
            settings = AppSettings(
                skipZeroSizeInDb = true,
                skipTrashBinContentsInScan = true,
                hideZeroSizeInResults = false,
                showMemoryOverlay = false,
                keepLoadedThumbnailsInMemory = false,
                keepLoadedVideoPreviewsInMemory = true,
                resultSortKey = "Count",
                resultSortDirection = "Desc",
                resultGroupSortKey = "Path",
                resultGroupSortDirection = "Asc",
                showFullPaths = false,
                resultsFilterDefinitionJson = "",
                filesFilterDefinitionJson = "",
                filesSortKey = "Name",
                filesSortDirection = "Asc"
            )
        )

        assertEquals("Video preview memory", section.title)
        assertEquals(1, section.toggles.size)
        assertEquals(ToggleSettingId.KeepLoadedVideoPreviewsInMemory, section.toggles[0].id)
        assertTrue(section.toggles[0].checked)
    }

    @Test
    fun backupSettingsSectionDescribesExportAndImportTogether() {
        val section = backupSettingsSection()

        assertEquals("Settings backup", section.title)
        assertTrue(section.description.contains("Export current preferences"))
        assertTrue(section.description.contains("import a saved backup"))
        assertTrue(section.toggles.isEmpty())
    }
}
