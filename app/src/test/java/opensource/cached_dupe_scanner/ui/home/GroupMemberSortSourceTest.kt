package opensource.cached_dupe_scanner.ui.home

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class GroupMemberSortSourceTest {
    @Test
    fun groupMemberSortingUsesSharedModelAndControl() {
        val sharedSortContent = sourceText("GroupMemberSort.kt")
        val detailContent = sourceText("DuplicateGroupDetailContent.kt")
        val resultsContent = sourceText("ResultsScreen.kt")
        val dbResultsContent = sourceText("ResultsScreenDb.kt")
        val similarityContent = sourceText("SimilarityExperimentsScreen.kt")

        assertTrue(sharedSortContent.contains("internal enum class ResultGroupMemberSortKey"))
        assertTrue(sharedSortContent.contains("internal fun sortGroupMembers("))
        assertTrue(sharedSortContent.contains("internal fun GroupMemberSortButton("))
        assertTrue(sharedSortContent.contains("title = { Text(\"Group sort options\") }"))

        assertTrue(detailContent.contains("sortKey: ResultGroupMemberSortKey"))
        assertTrue(detailContent.contains("sortDirection: SortDirection"))
        assertTrue(detailContent.contains("sortingEnabled: Boolean = true"))
        assertTrue(detailContent.contains("GroupMemberSortButton("))
        assertTrue(detailContent.contains("sortGroupMembers("))
        assertFalse(detailContent.contains("sortMembersByPath"))

        assertTrue(resultsContent.contains("resultGroupSortKey"))
        assertTrue(resultsContent.contains("settingsStore.setResultGroupSortKey"))
        assertTrue(resultsContent.contains("settingsStore.setResultGroupSortDirection"))
        assertTrue(resultsContent.contains("onApplySort = { key, direction ->"))

        assertTrue(dbResultsContent.contains("GroupMemberSortButton("))
        assertFalse(dbResultsContent.contains("internal enum class ResultGroupMemberSortKey"))
        assertFalse(dbResultsContent.contains("internal fun sortGroupMembers("))
        assertFalse(dbResultsContent.contains("title = { Text(\"Group sort options\") }"))

        assertTrue(similarityContent.contains("sortKey = similarityGroupMemberSortKey"))
        assertTrue(similarityContent.contains("sortingEnabled = durationNeighborExplanation == null"))
        assertFalse(similarityContent.contains("sortMembersByPath"))
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
