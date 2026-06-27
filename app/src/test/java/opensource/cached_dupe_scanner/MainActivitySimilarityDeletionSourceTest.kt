package opensource.cached_dupe_scanner

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MainActivitySimilarityDeletionSourceTest {
    @Test
    fun deleteHandlersDoNotRegenerateSimilarityResultsImmediately() {
        val content = mainActivitySource()
        val resultsRoute = content.substringAfter("Screen.Results -> ResultsScreenDb(")
            .substringBefore("taskScope = AppWorkScopes.taskScope")
        val similarityDetailRoute =
            content.substringAfter("is Screen.SimilarityClusterDetail -> SimilarityClusterDetailScreen(")
                .substringBefore("settingId = screen.settingId")

        assertFalse(
            "Duplicate result delete handlers should only mark the deleted path for the session",
            resultsRoute.contains("refreshSimilarityFromCache()")
        )
        assertFalse(
            "Similarity detail delete handlers should preserve generated clusters until update or rebuild",
            similarityDetailRoute.contains("refreshSimilarityFromCache()")
        )
    }

    private fun mainActivitySource(): String {
        val projectDir = File(requireNotNull(System.getProperty("user.dir")))
        val sourceFile = sequenceOf(
            File(projectDir, "app/src/main/java/opensource/cached_dupe_scanner/MainActivity.kt"),
            File(projectDir.parentFile ?: projectDir, "app/src/main/java/opensource/cached_dupe_scanner/MainActivity.kt")
        ).firstOrNull { file -> file.exists() }

        assertTrue("MainActivity.kt should exist", sourceFile != null)
        return sourceFile!!.readText()
    }
}
