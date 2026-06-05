package opensource.cached_dupe_scanner.ui.home

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SimilaritySettingsScreenTest {
    @Test
    fun similarityScreensUseSeparateRouteLevelComposables() {
        val content = sourceText("SimilaritySettingsScreen.kt")

        assertTrue(content.contains("fun SimilaritySettingsScreen("))
        assertTrue(content.contains("fun SimilarityMaintenanceScreen("))
        assertTrue(content.contains("fun SimilaritySettingCreateScreen("))
        assertTrue(content.contains("fun SimilarityExactThumbnailSettingScreen("))
        assertTrue(content.contains("fun SimilarityDurationSettingScreen("))
        assertTrue(content.contains("fun SimilaritySettingDetailScreen("))
        assertTrue(content.contains("fun SimilarityClusterDetailScreen("))
        assertFalse(content.contains("private enum class SimilaritySettingsPane"))
        assertTrue(content.contains("startSimilarityMaintenanceTask("))
        assertTrue(content.contains("repository.setEnabled("))
        assertTrue(content.contains("repository.clearSettingResults("))
        assertTrue(content.contains("repository.clearAllResults()"))
        assertTrue(content.contains("ConfirmationDialog("))
        assertTrue(content.contains("parsedExactThumbnailStep("))
        assertTrue(content.contains("parsedDurationToleranceStep("))
        assertTrue(content.contains("parsedDurationNeighborListStep("))
        assertTrue(content.contains("repository.createExactThumbnailSetting("))
        assertTrue(content.contains("repository.createDurationToleranceSetting("))
        assertTrue(content.contains("repository.createDurationNeighborListSetting("))
        assertFalse(content.contains("title = \"Experiment"))
        assertFalse(content.contains("title = \"Template"))
    }

    @Test
    fun similaritySettingsScreenShowsClusterMembers() {
        val content = sourceText("SimilaritySettingsScreen.kt")

        assertTrue(content.contains("repository.listClusterMembersPage("))
        assertTrue(content.contains("SimilarityClusterSummaryCard("))
        assertTrue(content.contains("SimilarityMemberCard("))
        assertTrue(content.contains("GroupPreviewThumbnail("))
        assertTrue(content.contains("FileDetailsDialogWithDeleteConfirm("))
        assertTrue(content.contains("GroupMemberSortButton("))
        assertTrue(content.contains("sortGroupMembers("))
        assertTrue(content.contains("shouldTriggerSimilarityMemberAutoLoad("))
        assertTrue(content.contains("listState = memberListState"))
        assertTrue(content.contains("onOpenCluster"))
        assertFalse(content.contains("clusters.take("))
        assertFalse(content.contains("repository.listClusterMembers(clusterId)"))
    }

    @Test
    fun similaritySettingsScreenDoesNotExposeRawSimilarityInternals() {
        val content = sourceText("SimilaritySettingsScreen.kt")

        assertTrue(content.contains("settingParametersSummary(setting)"))
        assertFalse(content.contains("Parameters: \${setting.paramsJson}"))
        assertFalse(content.contains("Key \${cluster.clusterKey}"))
        assertFalse(content.contains("durationMillis"))
        assertFalse(content.contains("thumbnailSignature"))
    }

    @Test
    fun similarityMemberAutoLoadTriggersNearBottomOnlyWhenReady() {
        assertTrue(
            shouldTriggerSimilarityMemberAutoLoad(
                lastVisibleItemIndex = 8,
                totalItemsCount = 10,
                thresholdItems = 3,
                isLoading = false,
                isComplete = false
            )
        )
        assertFalse(
            shouldTriggerSimilarityMemberAutoLoad(
                lastVisibleItemIndex = 4,
                totalItemsCount = 10,
                thresholdItems = 3,
                isLoading = false,
                isComplete = false
            )
        )
        assertFalse(
            shouldTriggerSimilarityMemberAutoLoad(
                lastVisibleItemIndex = 8,
                totalItemsCount = 10,
                thresholdItems = 3,
                isLoading = true,
                isComplete = false
            )
        )
        assertFalse(
            shouldTriggerSimilarityMemberAutoLoad(
                lastVisibleItemIndex = 8,
                totalItemsCount = 10,
                thresholdItems = 3,
                isLoading = false,
                isComplete = true
            )
        )
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
