package opensource.cached_dupe_scanner.storage

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import opensource.cached_dupe_scanner.cache.CacheDatabase
import opensource.cached_dupe_scanner.cache.CachedFileEntity
import opensource.cached_dupe_scanner.cache.SimilarityClusterEntity
import opensource.cached_dupe_scanner.cache.SimilarityClusterMemberEntity
import opensource.cached_dupe_scanner.core.DurationNeighborListStep
import opensource.cached_dupe_scanner.core.DurationToleranceStep
import opensource.cached_dupe_scanner.core.ExactThumbnailHashStep
import opensource.cached_dupe_scanner.core.SimilarityExperimentSpec
import opensource.cached_dupe_scanner.core.SimilarityMediaScope
import opensource.cached_dupe_scanner.core.SortDirection
import opensource.cached_dupe_scanner.core.VideoDurationExtractor
import opensource.cached_dupe_scanner.core.VideoFrameSignatureExtractor
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class SimilarityExperimentRepositoryTest {
    private lateinit var database: CacheDatabase
    private lateinit var tempDir: File

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, CacheDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        tempDir = createTempDir(prefix = "similarity-experiment")
    }

    @After
    fun teardown() {
        database.close()
        tempDir.deleteRecursively()
    }

    @Test
    fun exactThumbnailRunPersistsOnlyDuplicateSignatureClusters() {
        val first = videoFile("a.mp4")
        val second = videoFile("b.mp4")
        val unique = videoFile("c.mp4")
        val tooSmall = videoFile("small.mp4")
        val minSizeBytes = 10L
        database.fileCacheDao().upsert(entity(first, sizeBytes = minSizeBytes))
        database.fileCacheDao().upsert(entity(second, sizeBytes = minSizeBytes + 1L))
        database.fileCacheDao().upsert(entity(unique, sizeBytes = minSizeBytes + 2L))
        database.fileCacheDao().upsert(entity(tooSmall, sizeBytes = minSizeBytes - 1L))

        val repository = SimilarityExperimentRepository(
            database = database,
            fileDao = database.fileCacheDao(),
            experimentDao = database.similarityExperimentDao(),
            frameSignatureExtractor = FakeSignatureExtractor(
                mapOf(
                    first.absolutePath to "same",
                    second.absolutePath to "same",
                    unique.absolutePath to "unique"
                )
            )
        )
        val experiment = SimilarityExperimentSpec(
            id = "custom",
            name = "Custom",
            description = "Custom test",
            defaultMinSizeBytes = minSizeBytes,
            mediaScope = SimilarityMediaScope.Video,
            steps = listOf(exactStep())
        )

        val summary = repository.runExactThumbnailHashExperiment(
            request = SimilarityExperimentRunRequest(
                experiment = experiment,
                mediaScope = SimilarityMediaScope.Video,
                minSizeBytes = minSizeBytes,
                exactThumbnailStep = exactStep()
            ),
            shouldContinue = { true },
            onProgress = {}
        )

        assertEquals(3, summary.candidateCount)
        assertEquals(3, summary.processedCount)
        assertEquals(1, summary.clusterCount)
        assertEquals(2, summary.duplicateFileCount)
        assertEquals(1, repository.listRuns().size)
        val clusters = repository.listClusters("custom")
        assertEquals(1, clusters.size)
        assertEquals(2, clusters.single().fileCount)
        assertEquals(
            listOf(first, second).map { it.absolutePath.replace('\\', '/').lowercase() },
            repository.listClusterMembers(clusters.single()).map { it.normalizedPath }
        )
    }

    @Test
    fun exactThumbnailRunCanUseImageCandidates() {
        val first = videoFile("a.jpg")
        val second = videoFile("b.png")
        val video = videoFile("c.mp4")
        val minSizeBytes = 10L
        database.fileCacheDao().upsert(entity(first, sizeBytes = minSizeBytes))
        database.fileCacheDao().upsert(entity(second, sizeBytes = minSizeBytes + 1L))
        database.fileCacheDao().upsert(entity(video, sizeBytes = minSizeBytes + 2L))

        val repository = SimilarityExperimentRepository(
            database = database,
            fileDao = database.fileCacheDao(),
            experimentDao = database.similarityExperimentDao(),
            frameSignatureExtractor = FakeSignatureExtractor(
                mapOf(
                    first.absolutePath to "same-image",
                    second.absolutePath to "same-image",
                    video.absolutePath to "video"
                )
            )
        )
        val experiment = SimilarityExperimentSpec(
            id = "custom-image",
            name = "Custom image",
            description = "Custom image test",
            defaultMinSizeBytes = minSizeBytes,
            mediaScope = SimilarityMediaScope.Image,
            steps = listOf(exactStep())
        )

        val summary = repository.runExactThumbnailHashExperiment(
            request = SimilarityExperimentRunRequest(
                experiment = experiment,
                mediaScope = SimilarityMediaScope.Image,
                minSizeBytes = minSizeBytes,
                exactThumbnailStep = exactStep()
            ),
            shouldContinue = { true },
            onProgress = {}
        )

        assertEquals(2, summary.candidateCount)
        assertEquals(1, summary.clusterCount)
        assertEquals(2, summary.duplicateFileCount)
    }

    @Test
    fun cachedFileDeletionHidesSimilarityMemberWithoutDroppingSidecarLink() {
        val first = videoFile("active-a.mp4")
        val second = videoFile("active-b.mp4")
        val minSizeBytes = 10L
        val firstEntity = entity(first, sizeBytes = minSizeBytes)
        val secondEntity = entity(second, sizeBytes = minSizeBytes + 1L)
        database.fileCacheDao().upsert(firstEntity)
        database.fileCacheDao().upsert(secondEntity)

        val repository = SimilarityExperimentRepository(
            database = database,
            fileDao = database.fileCacheDao(),
            experimentDao = database.similarityExperimentDao(),
            frameSignatureExtractor = FakeSignatureExtractor(
                mapOf(
                    first.absolutePath to "same",
                    second.absolutePath to "same"
                )
            )
        )
        val experiment = SimilarityExperimentSpec(
            id = "sidecar-active",
            name = "Sidecar active",
            description = "Sidecar active test",
            defaultMinSizeBytes = minSizeBytes,
            mediaScope = SimilarityMediaScope.Video,
            steps = listOf(exactStep())
        )

        repository.runExactThumbnailHashExperiment(
            request = SimilarityExperimentRunRequest(
                experiment = experiment,
                mediaScope = SimilarityMediaScope.Video,
                minSizeBytes = minSizeBytes,
                exactThumbnailStep = exactStep()
            ),
            shouldContinue = { true },
            onProgress = {}
        )

        assertEquals(1, repository.listClusters(experiment.id).size)

        database.fileCacheDao().deleteByNormalizedPath(secondEntity.normalizedPath)

        assertTrue(repository.listClusters(experiment.id).isEmpty())
        assertEquals(0, repository.listRuns().single { it.experimentId == experiment.id }.clusterCount)

        database.fileCacheDao().upsert(secondEntity)

        val restoredCluster = repository.listClusters(experiment.id).single()
        assertEquals(
            listOf(firstEntity.normalizedPath, secondEntity.normalizedPath),
            repository.listClusterMembers(restoredCluster).map { it.normalizedPath }
        )
    }

    @Test
    fun durationToleranceRunPersistsClustersWithoutFrameSignatures() {
        val first = videoFile("a.mp4")
        val second = videoFile("b.mp4")
        val unique = videoFile("c.mp4")
        val missingDuration = videoFile("missing-duration.mp4")
        val minSizeBytes = 10L
        database.fileCacheDao().upsert(entity(first, sizeBytes = minSizeBytes))
        database.fileCacheDao().upsert(entity(second, sizeBytes = minSizeBytes + 1L))
        database.fileCacheDao().upsert(entity(unique, sizeBytes = minSizeBytes + 2L))
        database.fileCacheDao().upsert(entity(missingDuration, sizeBytes = minSizeBytes + 3L))

        val repository = SimilarityExperimentRepository(
            database = database,
            fileDao = database.fileCacheDao(),
            experimentDao = database.similarityExperimentDao(),
            durationExtractor = FakeDurationExtractor(
                mapOf(
                    first.absolutePath to 10_000L,
                    second.absolutePath to 10_750L,
                    unique.absolutePath to 20_000L
                )
            )
        )
        val step = DurationToleranceStep(toleranceSeconds = 1)
        val experiment = SimilarityExperimentSpec(
            id = "duration-only",
            name = "Duration only",
            description = "Duration test",
            defaultMinSizeBytes = minSizeBytes,
            mediaScope = SimilarityMediaScope.Video,
            steps = listOf(step)
        )

        val summary = repository.runDurationToleranceExperiment(
            request = SimilarityExperimentRunRequest(
                experiment = experiment,
                mediaScope = SimilarityMediaScope.Video,
                minSizeBytes = minSizeBytes,
                durationToleranceStep = step
            ),
            shouldContinue = { true },
            onProgress = {}
        )

        assertEquals(4, summary.candidateCount)
        assertEquals(4, summary.processedCount)
        assertEquals(1, summary.skippedCount)
        assertEquals(1, summary.clusterCount)
        assertEquals(2, summary.duplicateFileCount)
        val clusters = repository.listClusters("duration-only")
        assertEquals(1, clusters.size)
        assertTrue(clusters.single().signature.startsWith("duration-v1:1000:"))
        assertEquals(
            listOf(first, second).map { it.absolutePath.replace('\\', '/').lowercase() },
            repository.listClusterMembers(clusters.single()).map { it.normalizedPath }
        )
        assertEquals(
            listOf(10_000L, 10_750L),
            repository.listClusterMemberRows(clusters.single()).map { member -> member.durationMillis }
        )
    }

    @Test
    fun durationNeighborListRunKeepsOnlyAdjacentDurationNeighbors() {
        val isolated = videoFile("a-isolated.mp4")
        val first = videoFile("b-first.mp4")
        val second = videoFile("c-second.mp4")
        val third = videoFile("d-third.mp4")
        val fourth = videoFile("e-fourth.mp4")
        val fifth = videoFile("f-fifth.mp4")
        val minSizeBytes = 10L
        listOf(isolated, first, second, third, fourth, fifth).forEachIndexed { index, file ->
            database.fileCacheDao().upsert(entity(file, sizeBytes = minSizeBytes + index))
        }

        val repository = SimilarityExperimentRepository(
            database = database,
            fileDao = database.fileCacheDao(),
            experimentDao = database.similarityExperimentDao(),
            durationExtractor = FakeDurationExtractor(
                mapOf(
                    isolated.absolutePath to 30_000L,
                    first.absolutePath to 10_000L,
                    second.absolutePath to 10_750L,
                    third.absolutePath to 12_100L,
                    fourth.absolutePath to 12_800L,
                    fifth.absolutePath to 13_500L
                )
            )
        )
        val step = DurationNeighborListStep(toleranceSeconds = 1)
        val experiment = SimilarityExperimentSpec(
            id = "video-duration-neighbor-test",
            name = "Duration neighbor",
            description = "Duration neighbor test",
            defaultMinSizeBytes = minSizeBytes,
            mediaScope = SimilarityMediaScope.Video,
            steps = listOf(step)
        )

        val summary = repository.runDurationNeighborListExperiment(
            request = SimilarityExperimentRunRequest(
                experiment = experiment,
                mediaScope = SimilarityMediaScope.Video,
                minSizeBytes = minSizeBytes,
                durationNeighborListStep = step
            ),
            shouldContinue = { true },
            onProgress = {}
        )

        assertEquals(6, summary.candidateCount)
        assertEquals(6, summary.processedCount)
        assertEquals(1, summary.clusterCount)
        assertEquals(5, summary.duplicateFileCount)
        val clusters = repository.listClusters("video-duration-neighbor-test")
        assertEquals(1, clusters.size)
        assertEquals(
            listOf(first, second, third, fourth, fifth).map { it.absolutePath.replace('\\', '/').lowercase() },
            repository.listClusterMembers(clusters.single()).map { it.normalizedPath }
        )
        assertEquals(
            listOf(10_000L, 10_750L, 12_100L, 12_800L, 13_500L),
            repository.listClusterMemberRows(clusters.single()).map { member -> member.durationMillis }
        )
        assertTrue(clusters.single().signature.startsWith("duration-neighbor-list-v1:1000:"))
    }

    @Test
    fun durationNeighborListRebuildUsesStoredDurationsWithoutExtractorIo() {
        val isolated = videoFile("a-isolated.mp4")
        val first = videoFile("b-first.mp4")
        val second = videoFile("c-second.mp4")
        val third = videoFile("d-third.mp4")
        val fourth = videoFile("e-fourth.mp4")
        val fifth = videoFile("f-fifth.mp4")
        val minSizeBytes = 10L
        listOf(isolated, first, second, third, fourth, fifth).forEachIndexed { index, file ->
            database.fileCacheDao().upsert(entity(file, sizeBytes = minSizeBytes + index))
        }
        val durationExtractor = FakeDurationExtractor(
            mapOf(
                isolated.absolutePath to 30_000L,
                first.absolutePath to 10_000L,
                second.absolutePath to 10_750L,
                third.absolutePath to 12_100L,
                fourth.absolutePath to 12_800L,
                fifth.absolutePath to 13_500L
            )
        )
        val repository = SimilarityExperimentRepository(
            database = database,
            fileDao = database.fileCacheDao(),
            experimentDao = database.similarityExperimentDao(),
            durationExtractor = durationExtractor
        )
        val experiment = SimilarityExperimentSpec(
            id = "video-duration-neighbor-rebuild-test",
            name = "Duration neighbor",
            description = "Duration neighbor test",
            defaultMinSizeBytes = minSizeBytes,
            mediaScope = SimilarityMediaScope.Video,
            steps = listOf(DurationNeighborListStep(toleranceSeconds = 1))
        )

        val initialSummary = repository.runDurationNeighborListExperiment(
            request = SimilarityExperimentRunRequest(
                experiment = experiment,
                mediaScope = SimilarityMediaScope.Video,
                minSizeBytes = minSizeBytes,
                durationNeighborListStep = DurationNeighborListStep(toleranceSeconds = 1)
            ),
            shouldContinue = { true },
            onProgress = {}
        )
        val initialExtractorCalls = durationExtractor.calls
        val rebuiltSummary = repository.rebuildDurationNeighborListFromStoredDurations(
            experimentId = experiment.id,
            neighborStep = DurationNeighborListStep(toleranceSeconds = 0)
        )

        assertEquals(5, initialSummary.duplicateFileCount)
        assertEquals(6, repository.countDurationCandidates(experiment.id))
        assertEquals(6, initialExtractorCalls)
        assertEquals(initialExtractorCalls, durationExtractor.calls)
        assertEquals(6, rebuiltSummary.candidateCount)
        assertEquals(6, rebuiltSummary.processedCount)
        assertEquals(0, rebuiltSummary.clusterCount)
        assertEquals(0, rebuiltSummary.duplicateFileCount)
        assertTrue(repository.listClusters(experiment.id).isEmpty())
    }

    @Test
    fun durationMemberRowsExtractLegacyPathOnlyDurations() {
        val first = videoFile("legacy-a.mp4")
        val second = videoFile("legacy-b.mp4")
        val minSizeBytes = 10L
        database.fileCacheDao().upsert(entity(first, sizeBytes = minSizeBytes))
        database.fileCacheDao().upsert(entity(second, sizeBytes = minSizeBytes + 1L))
        val firstPath = first.absolutePath.replace('\\', '/').lowercase()
        val secondPath = second.absolutePath.replace('\\', '/').lowercase()
        database.similarityExperimentDao().insertClusters(
            listOf(
                SimilarityClusterEntity(
                    experimentId = "video-duration-neighbor-legacy",
                    signature = "duration-neighbor-v1:1000:0000000010000-0000000010750",
                    fileCount = 2,
                    totalBytes = minSizeBytes + minSizeBytes + 1L,
                    updatedAtMillis = 1L
                )
            )
        )
        database.similarityExperimentDao().insertClusterMembers(
            listOf(
                SimilarityClusterMemberEntity(
                    experimentId = "video-duration-neighbor-legacy",
                    signature = "duration-neighbor-v1:1000:0000000010000-0000000010750",
                    normalizedPath = firstPath,
                    position = 0,
                    durationMillis = null
                ),
                SimilarityClusterMemberEntity(
                    experimentId = "video-duration-neighbor-legacy",
                    signature = "duration-neighbor-v1:1000:0000000010000-0000000010750",
                    normalizedPath = secondPath,
                    position = 1,
                    durationMillis = null
                )
            )
        )

        val repository = SimilarityExperimentRepository(
            database = database,
            fileDao = database.fileCacheDao(),
            experimentDao = database.similarityExperimentDao(),
            durationExtractor = FakeDurationExtractor(
                mapOf(
                    first.absolutePath to 10_000L,
                    second.absolutePath to 10_750L
                )
            )
        )

        val cluster = repository.listClusters("video-duration-neighbor-legacy").single()

        assertEquals(
            listOf(10_000L, 10_750L),
            repository.listClusterMemberRows(cluster).map { member -> member.durationMillis }
        )
    }

    @Test
    fun clusterMemberRowsSupportOffsetAndLimit() {
        val files = listOf(
            videoFile("member-a.mp4"),
            videoFile("member-b.mp4"),
            videoFile("member-c.mp4"),
            videoFile("member-d.mp4")
        )
        files.forEachIndexed { index, file ->
            database.fileCacheDao().upsert(entity(file, sizeBytes = 10L + index))
        }
        insertSimilarityCluster(
            experimentId = "paged-members",
            signature = "duration-neighbor-v1:1000:0000000010000-0000000013000",
            paths = files.map { file -> file.absolutePath.replace('\\', '/').lowercase() },
            sizes = files.indices.map { index -> 10L + index }
        )
        val repository = SimilarityExperimentRepository(
            database = database,
            fileDao = database.fileCacheDao(),
            experimentDao = database.similarityExperimentDao()
        )

        val cluster = repository.listClusters("paged-members").single()
        val page = repository.listClusterMemberRows(
            cluster = cluster,
            offset = 1,
            limit = 2
        )

        assertEquals(
            files.drop(1).take(2).map { file -> file.absolutePath },
            page.map { member -> member.metadata.path }
        )

        val descendingPage = repository.listClusterMemberRows(
            cluster = cluster,
            offset = 1,
            limit = 2,
            direction = SortDirection.Desc
        )

        assertEquals(
            files.asReversed().drop(1).take(2).map { file -> file.absolutePath },
            descendingPage.map { member -> member.metadata.path }
        )
    }

    @Test
    fun clusterMemberRowsStayUnderSqliteBindingLimit() {
        val files = (0 until 500).map { index ->
            videoFile("large-member-${index.toString().padStart(3, '0')}.mp4")
        }
        val entities = files.mapIndexed { index, file ->
            entity(file, sizeBytes = 10L + index)
        }
        database.fileCacheDao().upsertAll(entities)
        insertSimilarityCluster(
            experimentId = "large-paged-members",
            signature = "large",
            paths = files.map { file -> file.absolutePath.replace('\\', '/').lowercase() },
            sizes = entities.map { entity -> entity.sizeBytes }
        )
        val repository = SimilarityExperimentRepository(
            database = database,
            fileDao = database.fileCacheDao(),
            experimentDao = database.similarityExperimentDao()
        )

        val members = repository.listClusterMemberRows(
            repository.listClusters("large-paged-members").single()
        )

        assertEquals(files.size, members.size)
        assertEquals(files.first().absolutePath, members.first().metadata.path)
        assertEquals(files.last().absolutePath, members.last().metadata.path)
    }

    @Test
    fun clusterPagesLoadIncrementallyBySortOrder() {
        val experimentId = "paged-clusters"
        insertSyntheticCluster(experimentId, "c", listOf(10L, 10L))
        insertSyntheticCluster(experimentId, "a", listOf(2L, 2L, 2L, 2L, 2L))
        insertSyntheticCluster(experimentId, "b", listOf(1L, 1L, 1L, 1L, 1L))
        insertSyntheticCluster(experimentId, "d", listOf(2L, 3L))
        val repository = SimilarityExperimentRepository(
            database = database,
            fileDao = database.fileCacheDao(),
            experimentDao = database.similarityExperimentDao()
        )

        val firstPage = repository.loadFirstClusterPage(experimentId, limit = 2)
        val secondPage = repository.loadClusterPageAfter(
            experimentId = experimentId,
            cursor = requireNotNull(firstPage.nextCursor),
            limit = 2
        )

        assertEquals(listOf("a", "b"), firstPage.clusters.map { it.signature })
        assertEquals(listOf("c", "d"), secondPage.clusters.map { it.signature })
        assertEquals(false, firstPage.exhausted)
        assertEquals(false, secondPage.exhausted)
        assertEquals("b", firstPage.nextCursor?.signature)
        assertEquals("d", secondPage.nextCursor?.signature)
    }

    @Test
    fun clusterPagesCanSortByTotalSizeAsc() {
        val experimentId = "paged-clusters-total-size"
        insertSyntheticCluster(experimentId, "large", listOf(100L, 100L))
        insertSyntheticCluster(experimentId, "small", listOf(2L, 2L, 2L, 2L, 2L))
        insertSyntheticCluster(experimentId, "medium", listOf(50L, 50L))
        insertSyntheticCluster(experimentId, "tie", listOf(30L, 30L))
        val repository = SimilarityExperimentRepository(
            database = database,
            fileDao = database.fileCacheDao(),
            experimentDao = database.similarityExperimentDao()
        )

        val firstPage = repository.loadFirstClusterPage(
            experimentId = experimentId,
            limit = 2,
            sortKey = SimilarityClusterSortKey.TotalSize,
            direction = SortDirection.Asc
        )
        val secondPage = repository.loadClusterPageAfter(
            experimentId = experimentId,
            cursor = requireNotNull(firstPage.nextCursor),
            limit = 2,
            sortKey = SimilarityClusterSortKey.TotalSize,
            direction = SortDirection.Asc
        )

        assertEquals(listOf("small", "tie"), firstPage.clusters.map { it.signature })
        assertEquals(listOf("medium", "large"), secondPage.clusters.map { it.signature })
        assertEquals(false, firstPage.exhausted)
        assertEquals(false, secondPage.exhausted)
        assertEquals("tie", firstPage.nextCursor?.signature)
        assertEquals("large", secondPage.nextCursor?.signature)
    }

    @Test
    fun clusterPageReportsExhaustedWhenFinalPageIsShort() {
        val experimentId = "paged-clusters-short"
        insertSyntheticCluster(experimentId, "a", listOf(10L, 10L))
        insertSyntheticCluster(experimentId, "b", listOf(5L, 5L))
        val repository = SimilarityExperimentRepository(
            database = database,
            fileDao = database.fileCacheDao(),
            experimentDao = database.similarityExperimentDao()
        )

        val page = repository.loadFirstClusterPage(experimentId, limit = 3)

        assertEquals(listOf("a", "b"), page.clusters.map { it.signature })
        assertTrue(page.exhausted)
        assertEquals("b", page.nextCursor?.signature)
    }

    private fun videoFile(name: String): File {
        val file = File(tempDir, name)
        file.writeText("video")
        return file
    }

    private fun insertSyntheticCluster(
        experimentId: String,
        signature: String,
        sizes: List<Long>
    ) {
        val paths = sizes.indices.map { index -> "$experimentId/$signature/$index" }
        paths.zip(sizes).forEach { (path, size) ->
            database.fileCacheDao().upsert(
                CachedFileEntity(
                    normalizedPath = path,
                    path = path,
                    sizeBytes = size,
                    lastModifiedMillis = 1L,
                    hashHex = null
                )
            )
        }
        insertSimilarityCluster(
            experimentId = experimentId,
            signature = signature,
            paths = paths,
            sizes = sizes
        )
    }

    private fun insertSimilarityCluster(
        experimentId: String,
        signature: String,
        paths: List<String>,
        sizes: List<Long>,
        durationMillis: List<Long?> = List(paths.size) { null }
    ) {
        database.similarityExperimentDao().insertClusters(
            listOf(
                SimilarityClusterEntity(
                    experimentId = experimentId,
                    signature = signature,
                    fileCount = paths.size,
                    totalBytes = sizes.sum(),
                    updatedAtMillis = 1L
                )
            )
        )
        database.similarityExperimentDao().insertClusterMembers(
            paths.mapIndexed { index, path ->
                SimilarityClusterMemberEntity(
                    experimentId = experimentId,
                    signature = signature,
                    normalizedPath = path,
                    position = index,
                    durationMillis = durationMillis.getOrNull(index)
                )
            }
        )
    }

    private fun entity(file: File, sizeBytes: Long): CachedFileEntity {
        return CachedFileEntity(
            normalizedPath = file.absolutePath.replace('\\', '/').lowercase(),
            path = file.absolutePath,
            sizeBytes = sizeBytes,
            lastModifiedMillis = 1L,
            hashHex = null
        )
    }

    private fun exactStep(): ExactThumbnailHashStep {
        return ExactThumbnailHashStep(
            frameSeconds = listOf(0, 1, 10),
            resizeWidthPx = 1,
            resizeHeightPx = 1,
            quantizationLevels = 16,
            grayscale = false
        )
    }
}

private class FakeSignatureExtractor(
    private val signaturesByPath: Map<String, String>
) : VideoFrameSignatureExtractor {
    override fun signature(
        file: File,
        mediaScope: SimilarityMediaScope,
        step: ExactThumbnailHashStep,
        shouldContinue: () -> Boolean
    ): String? {
        return signaturesByPath[file.absolutePath]
    }
}

private class FakeDurationExtractor(
    private val durationsByPath: Map<String, Long>
) : VideoDurationExtractor {
    override fun durationMillis(
        file: File,
        shouldContinue: () -> Boolean
    ): Long? {
        calls += 1
        if (!shouldContinue()) return null
        return durationsByPath[file.absolutePath]
    }

    var calls = 0
        private set
}
