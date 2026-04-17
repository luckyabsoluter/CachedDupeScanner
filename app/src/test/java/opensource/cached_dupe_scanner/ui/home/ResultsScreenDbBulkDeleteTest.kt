package opensource.cached_dupe_scanner.ui.home

import opensource.cached_dupe_scanner.cache.DuplicateGroupEntity
import opensource.cached_dupe_scanner.core.FileMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ResultsScreenDbBulkDeleteTest {
    @Test
    fun buildKeepOneNonMatchBulkDeleteCandidateDeletesMatchingFileNames() {
        val candidate = buildKeepOneNonMatchBulkDeleteCandidate(
            group = group(size = 10L, hash = "a", count = 3),
            members = listOf(
                file("/library/keep/final-cut.mkv"),
                file("/library/tmp/sample-1.mkv"),
                file("/library/tmp/sample-2.mkv")
            ),
            config = KeepOneNonMatchBulkDeleteCommandConfig(
                target = ResultsBulkDeleteTextTarget.FileName,
                operator = ResultsFilterTextOperator.Contains,
                phrase = "sample"
            )
        )

        requireNotNull(candidate)
        assertEquals("/library/keep/final-cut.mkv", candidate.survivor.normalizedPath)
        assertEquals(
            listOf("/library/tmp/sample-1.mkv", "/library/tmp/sample-2.mkv"),
            candidate.deleteTargets.map { it.normalizedPath }
        )
    }

    @Test
    fun buildKeepOneNonMatchBulkDeleteCandidateReturnsNullWhenTwoSurvivorsRemain() {
        val candidate = buildKeepOneNonMatchBulkDeleteCandidate(
            group = group(size = 10L, hash = "a", count = 3),
            members = listOf(
                file("/library/keep/final-cut.mkv"),
                file("/library/keep/backup.mkv"),
                file("/library/tmp/sample-1.mkv")
            ),
            config = KeepOneNonMatchBulkDeleteCommandConfig(
                target = ResultsBulkDeleteTextTarget.FileName,
                operator = ResultsFilterTextOperator.Contains,
                phrase = "sample"
            )
        )

        assertNull(candidate)
    }

    @Test
    fun buildKeepOneNonMatchBulkDeleteCandidateMatchesFullPathStartsWith() {
        val candidate = buildKeepOneNonMatchBulkDeleteCandidate(
            group = group(size = 10L, hash = "a", count = 3),
            members = listOf(
                file("/keep/final-cut.mkv"),
                file("/trash/sample-1.mkv"),
                file("/trash/sample-2.mkv")
            ),
            config = KeepOneNonMatchBulkDeleteCommandConfig(
                target = ResultsBulkDeleteTextTarget.FullPath,
                operator = ResultsFilterTextOperator.StartsWith,
                phrase = "/trash/"
            )
        )

        requireNotNull(candidate)
        assertEquals("/keep/final-cut.mkv", candidate.survivor.normalizedPath)
        assertEquals(
            listOf("/trash/sample-1.mkv", "/trash/sample-2.mkv"),
            candidate.deleteTargets.map { it.normalizedPath }
        )
    }

    @Test
    fun buildKeepModifiedBulkDeleteCandidateKeepsOldestFile() {
        val candidate = buildKeepModifiedBulkDeleteCandidate(
            group = group(size = 10L, hash = "a", count = 3),
            members = listOf(
                file("/keep/oldest.mkv", modified = 10L),
                file("/keep/middle.mkv", modified = 20L),
                file("/keep/newest.mkv", modified = 30L)
            ),
            keepNewest = false
        )

        requireNotNull(candidate)
        assertEquals("/keep/oldest.mkv", candidate.survivor.normalizedPath)
        assertEquals(
            listOf("/keep/middle.mkv", "/keep/newest.mkv"),
            candidate.deleteTargets.map { it.normalizedPath }
        )
    }

    @Test
    fun buildKeepModifiedBulkDeleteCandidateKeepsNewestFile() {
        val candidate = buildKeepModifiedBulkDeleteCandidate(
            group = group(size = 10L, hash = "a", count = 3),
            members = listOf(
                file("/keep/oldest.mkv", modified = 10L),
                file("/keep/middle.mkv", modified = 20L),
                file("/keep/newest.mkv", modified = 30L)
            ),
            keepNewest = true
        )

        requireNotNull(candidate)
        assertEquals("/keep/newest.mkv", candidate.survivor.normalizedPath)
        assertEquals(
            listOf("/keep/oldest.mkv", "/keep/middle.mkv"),
            candidate.deleteTargets.map { it.normalizedPath }
        )
    }

    @Test
    fun collectKeepOneNonMatchBulkDeleteCandidatesHonorsCurrentResultsFilter() {
        val candidates = collectKeepOneNonMatchBulkDeleteCandidates(
            groupsWithMembers = listOf(
                group(size = 10L, hash = "a", count = 3) to listOf(
                    file("/show/episode-final.mkv"),
                    file("/show/sample-1.mkv"),
                    file("/show/sample-2.mkv")
                ),
                group(size = 10L, hash = "b", count = 3) to listOf(
                    file("/movie/final.mkv"),
                    file("/movie/sample-1.mkv"),
                    file("/movie/sample-2.mkv")
                )
            ),
            filterDefinition = ResultsFilterDefinition(
                clusters = listOf(
                    ResultsFilterCluster(
                        id = "cluster_1",
                        name = "Episode only",
                        rules = listOf(
                            ResultsFilterRule(
                                id = "rule_1",
                                target = ResultsFilterTarget.FileName,
                                textOperator = ResultsFilterTextOperator.Contains,
                                value = "episode"
                            )
                        )
                    )
                )
            ),
            config = KeepOneNonMatchBulkDeleteCommandConfig(
                target = ResultsBulkDeleteTextTarget.FileName,
                operator = ResultsFilterTextOperator.Contains,
                phrase = "sample"
            )
        )

        assertEquals(1, candidates.size)
        assertEquals("10:a", "${candidates.single().group.sizeBytes}:${candidates.single().group.hashHex}")
    }

    @Test
    fun collectKeepModifiedBulkDeleteCandidatesHonorsCurrentResultsFilter() {
        val candidates = collectKeepModifiedBulkDeleteCandidates(
            groupsWithMembers = listOf(
                group(size = 10L, hash = "a", count = 3) to listOf(
                    file("/show/episode-old.mkv", modified = 10L),
                    file("/show/episode-mid.mkv", modified = 20L),
                    file("/show/episode-new.mkv", modified = 30L)
                ),
                group(size = 10L, hash = "b", count = 3) to listOf(
                    file("/movie/old.mkv", modified = 10L),
                    file("/movie/mid.mkv", modified = 20L),
                    file("/movie/new.mkv", modified = 30L)
                )
            ),
            filterDefinition = ResultsFilterDefinition(
                clusters = listOf(
                    ResultsFilterCluster(
                        id = "cluster_1",
                        name = "Episode only",
                        rules = listOf(
                            ResultsFilterRule(
                                id = "rule_1",
                                target = ResultsFilterTarget.FileName,
                                textOperator = ResultsFilterTextOperator.Contains,
                                value = "episode"
                            )
                        )
                    )
                )
            ),
            keepNewest = true
        )

        assertEquals(1, candidates.size)
        assertEquals("/show/episode-new.mkv", candidates.single().survivor.normalizedPath)
    }

    private fun file(path: String, modified: Long = 1L): FileMetadata {
        return FileMetadata(
            path = path,
            normalizedPath = path,
            sizeBytes = 10L,
            lastModifiedMillis = modified,
            hashHex = "hash"
        )
    }

    private fun group(size: Long, hash: String, count: Int): DuplicateGroupEntity {
        return DuplicateGroupEntity(
            sizeBytes = size,
            hashHex = hash,
            fileCount = count,
            totalBytes = size * count,
            updatedAtMillis = 1L
        )
    }
}
