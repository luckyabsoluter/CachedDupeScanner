package opensource.cached_dupe_scanner.ui.home

import android.os.Build
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionScreenTest {
    @Test
    fun hasAllFilesAccess_preR_returnsTrueWithoutCallingExternalManager() {
        var called = false

        val result = hasAllFilesAccess(
            sdkInt = Build.VERSION_CODES.Q,
            isExternalStorageManager = {
                called = true
                false
            }
        )

        assertTrue(result)
        assertFalse(called)
    }

    @Test
    fun hasAllFilesAccess_rOrAbove_delegatesToExternalManager() {
        val result = hasAllFilesAccess(
            sdkInt = Build.VERSION_CODES.R,
            isExternalStorageManager = { false }
        )

        assertFalse(result)
    }

    @Test
    fun allFilesAccessStatusText_preR_reportsNotRequired() {
        val text = allFilesAccessStatusText(
            sdkInt = Build.VERSION_CODES.Q,
            hasAccess = false
        )

        assertEquals("All-files access: not required", text)
    }
}
