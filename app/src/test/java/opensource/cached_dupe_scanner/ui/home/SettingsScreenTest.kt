package opensource.cached_dupe_scanner.ui.home

import opensource.cached_dupe_scanner.storage.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsScreenTest {
    @Test
    fun toggleSectionsBindToSettingsContracts() {
        val settings = appSettings(
            skipZeroSizeInDb = false,
            skipTrashBinContentsInScan = false,
            hideZeroSizeInResults = true,
            showMemoryOverlay = true,
            keepLoadedThumbnailsInMemory = true,
            keepLoadedVideoPreviewsInMemory = false,
            snapVideoPreviewFramesToWidth = true
        )

        assertSectionToggles(
            zeroSizeSettingsSection(settings),
            ToggleExpectation(ToggleSettingId.SkipZeroSizeInDb, checked = false),
            ToggleExpectation(ToggleSettingId.HideZeroSizeInResults, checked = true)
        )
        assertSectionToggles(
            trashScanSettingsSection(settings),
            ToggleExpectation(ToggleSettingId.SkipTrashBinContentsInScan, checked = false)
        )
        assertSectionToggles(
            memoryOverlaySection(settings),
            ToggleExpectation(ToggleSettingId.ShowMemoryOverlay, checked = true)
        )
        assertSectionToggles(
            thumbnailMemorySettingsSection(settings),
            ToggleExpectation(ToggleSettingId.KeepLoadedThumbnailsInMemory, checked = true)
        )
        assertSectionToggles(
            videoPreviewMemorySettingsSection(settings),
            ToggleExpectation(ToggleSettingId.KeepLoadedVideoPreviewsInMemory, checked = false)
        )
        assertSectionToggles(
            videoPreviewSnapSettingsSection(settings),
            ToggleExpectation(ToggleSettingId.SnapVideoPreviewFramesToWidth, checked = true)
        )
    }

    @Test
    fun dedicatedControlSectionsDoNotExposeToggleBindings() {
        val sections = listOf(
            workerSettingsSection(),
            thumbnailSizeSettingsSection(),
            videoPreviewSizeSettingsSection(),
            videoPreviewLineCountSettingsSection(),
            backupSettingsSection()
        )

        sections.forEach { section ->
            assertEquals(emptyList<ToggleSettingModel>(), section.toggles)
        }
    }

    @Test
    fun numberDraftInputKeepsOnlyDigits() {
        assertEquals("120", sanitizeNumberDraftInput(" 1a2%0 "))
    }

    @Test
    fun normalizedDraftValueUsesFallbackUntilDraftIsValid() {
        assertEquals(80, normalizedDraftValue(input = "", fallback = 80, minValue = 0))
        assertEquals(1, normalizedDraftValue(input = "0", fallback = 3, minValue = 1))
        assertEquals(125, normalizedDraftValue(input = "125", fallback = 80, minValue = 0))
        assertEquals(32, normalizedDraftValue(input = "99", fallback = 4, minValue = 1, maxValue = 32))
    }

    @Test
    fun adjustedDraftInputChangesDraftWithoutRequiringAppliedValueChange() {
        assertEquals("90", adjustedDraftInput(input = "100", fallback = 100, delta = -10, minValue = 0))
        assertEquals("1", adjustedDraftInput(input = "1", fallback = 1, delta = -1, minValue = 1))
        assertEquals("6", adjustedDraftInput(input = "", fallback = 5, delta = 1, minValue = 1))
        assertEquals(
            "32",
            adjustedDraftInput(input = "31", fallback = 4, delta = 4, minValue = 1, maxValue = 32)
        )
    }

    private fun assertSectionToggles(
        section: SettingsSectionModel,
        vararg expected: ToggleExpectation
    ) {
        assertEquals(expected.map { it.id }, section.toggles.map { it.id })
        assertEquals(expected.map { it.checked }, section.toggles.map { it.checked })
    }

    private data class ToggleExpectation(
        val id: ToggleSettingId,
        val checked: Boolean
    )

    private fun appSettings(
        skipZeroSizeInDb: Boolean = true,
        skipTrashBinContentsInScan: Boolean = true,
        hideZeroSizeInResults: Boolean = false,
        showMemoryOverlay: Boolean = false,
        keepLoadedThumbnailsInMemory: Boolean = false,
        keepLoadedVideoPreviewsInMemory: Boolean = true,
        snapVideoPreviewFramesToWidth: Boolean = false
    ): AppSettings {
        return AppSettings(
            skipZeroSizeInDb = skipZeroSizeInDb,
            skipTrashBinContentsInScan = skipTrashBinContentsInScan,
            hideZeroSizeInResults = hideZeroSizeInResults,
            showMemoryOverlay = showMemoryOverlay,
            keepLoadedThumbnailsInMemory = keepLoadedThumbnailsInMemory,
            keepLoadedVideoPreviewsInMemory = keepLoadedVideoPreviewsInMemory,
            snapVideoPreviewFramesToWidth = snapVideoPreviewFramesToWidth,
            scanWorkerCount = 4,
            similarityWorkerCount = 4,
            videoPreviewLineCount = 1,
            thumbnailSizePercent = 100,
            videoPreviewSizePercent = 100,
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
    }
}
