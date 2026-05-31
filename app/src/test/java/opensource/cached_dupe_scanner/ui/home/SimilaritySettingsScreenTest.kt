package opensource.cached_dupe_scanner.ui.home

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SimilaritySettingsScreenTest {
    @Test
    fun similaritySettingsScreenUsesMaintenanceTaskAndManagementActions() {
        val content = sourceText("SimilaritySettingsScreen.kt")

        assertTrue(content.contains("fun SimilaritySettingsScreen("))
        assertTrue(content.contains("startSimilarityMaintenanceTask("))
        assertTrue(content.contains("repository.setEnabled("))
        assertTrue(content.contains("repository.clearSettingResults("))
        assertTrue(content.contains("repository.clearAllResults()"))
        assertTrue(content.contains("createExact(width = 2, height = 2)"))
        assertTrue(content.contains("createExact(width = 3, height = 3)"))
        assertFalse(content.contains("Experiment"))
    }

    @Test
    fun similaritySettingsScreenDoesNotUseMethodSpecificResultColumns() {
        val content = sourceText("SimilaritySettingsScreen.kt")

        assertTrue(content.contains("cluster.clusterKey"))
        assertFalse(content.contains("durationMillis"))
        assertFalse(content.contains("thumbnailSignature"))
    }

    private fun sourceText(fileName: String): String {
        val projectDir = File(requireNotNull(System.getProperty("user.dir")))
        val sourceFile = sequenceOf(
            File(projectDir, "app/src/main/java/opensource/cached_dupe_scanner/ui/home/$fileName"),
            File(projectDir.parentFile ?: projectDir, "app/src/main/java/opensource/cached_dupe_scanner/ui/home/$fileName")
        ).firstOrNull { it.exists() }

        assertTrue("$fileName should exist", sourceFile != null)
        return sourceFile!!.readText()
    }
}

