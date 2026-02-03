package opensource.cached_dupe_scanner.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SystemBarUpdateTest {
    @Test
    fun mainActivityUpdatesSystemBarsOnConfigChange() {
        val projectDir = File(requireNotNull(System.getProperty("user.dir")))
        val activityFile = sequenceOf(
            File(projectDir, "app/src/main/java/opensource/cached_dupe_scanner/MainActivity.kt"),
            File(projectDir.parentFile ?: projectDir, "app/src/main/java/opensource/cached_dupe_scanner/MainActivity.kt")
        ).firstOrNull { it.exists() }

        assertTrue("MainActivity.kt should exist", activityFile != null)

        val content = activityFile!!.readText()
        assertTrue("MainActivity should override onConfigurationChanged", content.contains("onConfigurationChanged"))
        assertTrue("MainActivity should update system bars", content.contains("updateSystemBars()"))
    }
}
