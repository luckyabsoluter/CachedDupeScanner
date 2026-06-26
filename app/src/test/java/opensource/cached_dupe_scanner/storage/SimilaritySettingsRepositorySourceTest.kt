package opensource.cached_dupe_scanner.storage

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class SimilaritySettingsRepositorySourceTest {
    @Test
    fun publicMaintenanceEntryPointsAreSerialized() {
        val projectDir = File(requireNotNull(System.getProperty("user.dir")))
        val repositorySource = sequenceOf(
            File(
                projectDir,
                "app/src/main/java/opensource/cached_dupe_scanner/storage/SimilaritySettingsRepository.kt"
            ),
            File(
                projectDir.parentFile ?: projectDir,
                "app/src/main/java/opensource/cached_dupe_scanner/storage/SimilaritySettingsRepository.kt"
            )
        ).firstOrNull { file -> file.exists() }?.readText()
            ?: error("SimilaritySettingsRepository.kt should exist")

        val generateEnabledResults = repositorySource.substringAfter("fun generateEnabledResults(")
            .substringBefore("fun runSettingMaintenance(")
        val runSettingMaintenance = repositorySource.substringAfter("fun runSettingMaintenance(")
            .substringBefore("override fun onCachedFilesChanged(")
        assertTrue(generateEnabledResults.contains("synchronized(maintenanceLock)"))
        assertTrue(runSettingMaintenance.contains("synchronized(maintenanceLock)"))
    }
}
