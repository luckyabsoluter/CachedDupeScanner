package opensource.cached_dupe_scanner.ui.home

import opensource.cached_dupe_scanner.storage.DbMaintenanceProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DbManagementUiStateTest {
    @Test
    fun maintenanceProgressPersistsAcrossStateReads() {
        val state = DbManagementUiState()

        state.startMaintenance()
        state.applyMaintenanceProgress(
            DbMaintenanceProgress(
                total = 10,
                processed = 4,
                deleted = 1,
                rehashed = 2,
                missingHashed = 3,
                currentPath = "/storage/emulated/0/Download/sample.bin"
            )
        )

        assertTrue(state.isRunning)
        assertEquals(10, state.progressTotal)
        assertEquals(4, state.progressProcessed)
        assertEquals(1, state.progressDeleted)
        assertEquals(2, state.progressRehashed)
        assertEquals(3, state.progressMissingHashed)
        assertEquals("/storage/emulated/0/Download/sample.bin", state.progressCurrentPath)
    }

    @Test
    fun completionKeepsFinalMaintenanceSummaryVisible() {
        val state = DbManagementUiState()

        state.startMaintenance()
        state.completeMaintenance(
            DbMaintenanceProgress(
                total = 10,
                processed = 10,
                deleted = 2,
                rehashed = 5,
                missingHashed = 1,
                currentPath = null
            )
        )

        assertFalse(state.isRunning)
        assertEquals(
            "Maintenance complete. Deleted 2, rehashed 5, missing hashes 1.",
            state.maintenanceStatusMessage
        )
        assertEquals(10, state.progressProcessed)
    }

    @Test
    fun rebuildAndClearFlagsResetAfterCompletion() {
        val state = DbManagementUiState()

        state.startRebuild()
        assertTrue(state.isRebuilding)
        state.completeRebuild()
        assertFalse(state.isRebuilding)
        assertEquals("Duplicate groups rebuilt.", state.groupStatusMessage)

        state.startClearing()
        assertTrue(state.isClearing)
        state.completeClearing()
        assertFalse(state.isClearing)
        assertEquals("Cleared all cached results.", state.maintenanceStatusMessage)
    }
}
