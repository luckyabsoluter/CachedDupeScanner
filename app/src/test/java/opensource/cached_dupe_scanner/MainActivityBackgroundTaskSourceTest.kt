package opensource.cached_dupe_scanner

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MainActivityBackgroundTaskSourceTest {
    @Test
    fun mainActivityUsesAppOwnedTaskCoordinatorAndTaskScopeForLongRunningWork() {
        val content = source("app/src/main/java/opensource/cached_dupe_scanner/MainActivity.kt")

        assertTrue(
            "MainActivity should reuse the app-owned task coordinator so active task state survives UI recreation",
            content.contains("AppWorkScopes.taskCoordinator(context)")
        )
        assertFalse(
            "MainActivity should not create the task coordinator from remember because that resets active tasks",
            content.contains("remember { TaskCoordinator(context) }")
        )
        assertTrue(
            "DB management should run long tasks on the app-owned task scope",
            content.contains("appScope = AppWorkScopes.taskScope")
        )
        assertTrue(
            "Results bulk delete should receive the app-owned task scope",
            content.contains("taskScope = AppWorkScopes.taskScope")
        )
        assertTrue(
            "Trash cleanup should receive the app-owned task scope",
            content.contains("appScope = AppWorkScopes.taskScope")
        )
        assertTrue(
            "Similarity task banners should open the settings route because similarity results are scan-generated",
            content.contains("TaskArea.Similarity -> Screen.SimilaritySettings")
        )
        assertTrue(
            "Similarity update and rebuild should run on the app-owned task scope",
            content.contains("appScope = AppWorkScopes.taskScope")
        )
        assertFalse(
            "DB management should not receive the Compose coroutine scope for long tasks",
            content.contains("appScope = scope,")
        )
    }

    @Test
    fun appWorkScopesOwnsSharedTaskRuntime() {
        val content = source("app/src/main/java/opensource/cached_dupe_scanner/AppWorkScopes.kt")

        assertTrue(
            "AppWorkScopes should expose a task scope for non-scan background work",
            content.contains("val taskScope")
        )
        assertTrue(
            "AppWorkScopes should expose a shared task coordinator backed by application context",
            content.contains("fun taskCoordinator(context: Context): TaskCoordinator")
        )
        assertTrue(
            "The shared task coordinator should avoid leaking Activity context",
            content.contains("context.applicationContext")
        )
    }

    @Test
    fun longRunningScreensUseInjectedAppScopeForTrackedTasks() {
        val trashContent = source("app/src/main/java/opensource/cached_dupe_scanner/ui/home/TrashScreen.kt")
        val similarityContent = source(
            "app/src/main/java/opensource/cached_dupe_scanner/ui/home/SimilaritySettingsScreen.kt"
        )
        val bulkDeleteContent = source(
            "app/src/main/java/opensource/cached_dupe_scanner/ui/home/ResultsScreenDbBulkDelete.kt"
        )

        assertTrue(
            "TrashScreen should accept an app scope for empty-trash execution",
            trashContent.contains("appScope: CoroutineScope")
        )
        assertTrue(
            "Empty trash should launch on the app scope instead of the Compose scope",
            trashContent.contains("scope = appScope")
        )
        assertTrue(
            "Similarity should not expose a manual maintenance task runner",
            !similarityContent.contains("fun SimilarityMaintenanceScreen(") &&
                !similarityContent.contains("startSimilarityMaintenanceTask(")
        )
        assertTrue(
            "Similarity detail should accept an app scope for update and rebuild progress",
            similarityContent.contains("appScope: CoroutineScope")
        )
        assertTrue(
            "Similarity detail should publish shared task progress",
            similarityContent.contains("startSimilaritySettingGenerationTask(")
        )
        assertTrue(
            "Bulk delete command screens should accept a task scope for execution",
            bulkDeleteContent.contains("taskScope: CoroutineScope")
        )
        assertTrue(
            "Bulk delete execution should launch on the task scope instead of the Compose scope",
            bulkDeleteContent.contains("scope = taskScope")
        )
    }

    @Test
    fun mainActivityOwnsSimilarityDetailPreviewMenuSelections() {
        val content = source("app/src/main/java/opensource/cached_dupe_scanner/MainActivity.kt")

        assertTrue(
            "Similarity detail video preview selection should survive screen recreation",
            content.contains("val similarityShowVideoPreviews = rememberSaveable { mutableStateOf(false) }")
        )
        assertTrue(
            "Similarity detail duration label selection should survive screen recreation",
            content.contains("val similarityShowVideoPreviewDurations = rememberSaveable { mutableStateOf(false) }")
        )
        assertTrue(
            "Similarity detail resolution label selection should survive screen recreation",
            content.contains("val similarityShowVideoPreviewResolutions = rememberSaveable { mutableStateOf(false) }")
        )
        assertTrue(
            "Similarity detail screen should receive the hoisted video preview selection",
            content.contains("showVideoPreviews = similarityShowVideoPreviews.value")
        )
        assertTrue(
            "Similarity detail screen should update the hoisted video preview selection",
            content.contains("onShowVideoPreviewsChange = { similarityShowVideoPreviews.value = it }")
        )
        assertTrue(
            "Similarity group and detail screens should receive persistent settings",
            content.contains("settingsStore = settingsStore")
        )
    }

    private fun source(relativePath: String): String {
        val projectDir = File(requireNotNull(System.getProperty("user.dir")))
        val sourceFile = sequenceOf(
            File(projectDir, relativePath),
            File(projectDir.parentFile ?: projectDir, relativePath)
        ).firstOrNull { it.exists() }

        assertTrue("$relativePath should exist", sourceFile != null)
        return sourceFile!!.readText()
    }
}
