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
        assertTrue(content.contains("private enum class SimilaritySettingsPane"))
        assertTrue(content.contains("SimilaritySettingsPane.Create"))
        assertTrue(content.contains("SimilaritySettingsPane.SettingDetail"))
        assertTrue(content.contains("SimilaritySettingsPane.ClusterDetail"))
        assertTrue(content.contains("startSimilarityMaintenanceTask("))
        assertTrue(content.contains("repository.setEnabled("))
        assertTrue(content.contains("repository.clearSettingResults("))
        assertTrue(content.contains("repository.clearAllResults()"))
        assertTrue(content.contains("parsedExactThumbnailStep("))
        assertTrue(content.contains("parsedDurationToleranceStep("))
        assertTrue(content.contains("parsedDurationNeighborListStep("))
        assertTrue(content.contains("repository.createExactThumbnailSetting("))
        assertTrue(content.contains("repository.createDurationToleranceSetting("))
        assertTrue(content.contains("repository.createDurationNeighborListSetting("))
        assertFalse(content.contains("Experiment"))
        assertFalse(content.contains("Template"))
    }

    @Test
    fun similaritySettingsScreenShowsClusterMembers() {
        val content = sourceText("SimilaritySettingsScreen.kt")

        assertTrue(content.contains("repository.listClusterMembers(cluster.clusterId)"))
        assertTrue(content.contains("SimilarityClusterDetailCard("))
        assertTrue(content.contains("SimilarityMemberRow("))
        assertTrue(content.contains("onOpenCluster"))
        assertFalse(content.contains("clusters.take("))
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
