package opensource.cached_dupe_scanner.core

const val DEFAULT_SIMILARITY_EXPERIMENT_MIN_SIZE_BYTES: Long = 100L * 1024L * 1024L

data class SimilarityExperimentSpec(
    val id: String,
    val name: String,
    val description: String,
    val defaultMinSizeBytes: Long,
    val mediaScope: SimilarityMediaScope,
    val steps: List<SimilarityExperimentStep>
) {
    fun accepts(
        path: String,
        sizeBytes: Long,
        minSizeBytes: Long = defaultMinSizeBytes
    ): Boolean {
        return sizeBytes >= minSizeBytes && mediaScope.accepts(path)
    }
}

enum class SimilarityMediaScope {
    Video,
    Image;

    fun accepts(path: String): Boolean {
        return when (this) {
            Video -> isVideoPath(path)
            Image -> isImagePath(path)
        }
    }
}

sealed interface SimilarityExperimentStep {
    val title: String
    val summary: String
}

data class ExactThumbnailHashStep(
    val frameSeconds: List<Int>,
    val resizeWidthPx: Int,
    val resizeHeightPx: Int,
    val quantizationLevels: Int?,
    val grayscale: Boolean
) : SimilarityExperimentStep {
    override val title: String = "Exact thumbnail hash"
    override val summary: String
        get() {
            val colorMode = if (grayscale) "grayscale" else "color"
            val frames = frameSeconds.joinToString(", ") { second -> "${second}s" }
            val quantization = quantizationLevels?.let { "$it-level quantization" } ?: "no quantization"
            return "$frames frames, ${resizeWidthPx}x${resizeHeightPx}, $colorMode, $quantization"
        }
}

data class DurationToleranceStep(
    val toleranceSeconds: Int
) : SimilarityExperimentStep {
    override val title: String = "Duration tolerance"
    override val summary: String = "Group videos with durations within ${toleranceSeconds.coerceAtLeast(0)}s"
}

data class DurationNeighborListStep(
    val toleranceSeconds: Int
) : SimilarityExperimentStep {
    override val title: String = "Duration neighbor list"
    override val summary: String = "List videos whose nearest duration neighbor is within ${toleranceSeconds.coerceAtLeast(0)}s"
}

data class PerceptualHashRefinementStep(
    val maxHammingDistance: Int
) : SimilarityExperimentStep {
    override val title: String = "pHash refinement"
    override val summary: String = "Refine remaining groups with max Hamming distance $maxHammingDistance"
}

fun defaultSimilarityExperimentSpecs(): List<SimilarityExperimentSpec> {
    return listOf(
        exactThumbnailHashExperiment(grayscale = false),
        exactThumbnailHashExperiment(grayscale = true),
        durationToleranceExperiment(),
        durationNeighborListExperiment(),
        durationThenPerceptualHashExperiment(),
        stagedThumbnailThenPerceptualHashExperiment()
    )
}

fun exactThumbnailHashExperiment(grayscale: Boolean): SimilarityExperimentSpec {
    val suffix = if (grayscale) "grayscale" else "color"
    return SimilarityExperimentSpec(
        id = "video-thumbnail-exact-$suffix",
        name = "Video thumbnail exact hash ($suffix)",
        description = "Build independent duplicate clusters from cached videos using exact hashes of configurable quantized thumbnails.",
        defaultMinSizeBytes = DEFAULT_SIMILARITY_EXPERIMENT_MIN_SIZE_BYTES,
        mediaScope = SimilarityMediaScope.Video,
        steps = listOf(
            ExactThumbnailHashStep(
                frameSeconds = listOf(0, 1, 10),
                resizeWidthPx = 1,
                resizeHeightPx = 1,
                quantizationLevels = 16,
                grayscale = grayscale
            )
        )
    )
}

fun durationThenPerceptualHashExperiment(): SimilarityExperimentSpec {
    return SimilarityExperimentSpec(
        id = "video-duration-phash",
        name = "Duration gate with pHash",
        description = "First reduce candidates by similar duration, then compare perceptual hashes inside each reduced group.",
        defaultMinSizeBytes = DEFAULT_SIMILARITY_EXPERIMENT_MIN_SIZE_BYTES,
        mediaScope = SimilarityMediaScope.Video,
        steps = listOf(
            DurationToleranceStep(toleranceSeconds = 1),
            PerceptualHashRefinementStep(maxHammingDistance = 8)
        )
    )
}

fun durationToleranceExperiment(): SimilarityExperimentSpec {
    return SimilarityExperimentSpec(
        id = "video-duration-tolerance",
        name = "Duration tolerance only",
        description = "Cluster cached videos only by extracted duration using a configurable tolerance.",
        defaultMinSizeBytes = DEFAULT_SIMILARITY_EXPERIMENT_MIN_SIZE_BYTES,
        mediaScope = SimilarityMediaScope.Video,
        steps = listOf(DurationToleranceStep(toleranceSeconds = 1))
    )
}

fun durationNeighborListExperiment(): SimilarityExperimentSpec {
    return SimilarityExperimentSpec(
        id = "video-duration-neighbor-list",
        name = "Duration neighbor list",
        description = "Sort cached videos by extracted duration as one list and show only items with adjacent neighbors inside the configured tolerance.",
        defaultMinSizeBytes = DEFAULT_SIMILARITY_EXPERIMENT_MIN_SIZE_BYTES,
        mediaScope = SimilarityMediaScope.Video,
        steps = listOf(DurationNeighborListStep(toleranceSeconds = 1))
    )
}

fun stagedThumbnailThenPerceptualHashExperiment(): SimilarityExperimentSpec {
    return SimilarityExperimentSpec(
        id = "video-thumbnail-phash-staged",
        name = "Thumbnail hash gate with pHash refinement",
        description = "Run the cheap thumbnail hash first, then apply pHash only to groups that remain similar.",
        defaultMinSizeBytes = DEFAULT_SIMILARITY_EXPERIMENT_MIN_SIZE_BYTES,
        mediaScope = SimilarityMediaScope.Video,
        steps = listOf(
            ExactThumbnailHashStep(
                frameSeconds = listOf(0, 1, 10),
                resizeWidthPx = 1,
                resizeHeightPx = 1,
                quantizationLevels = 16,
                grayscale = true
            ),
            PerceptualHashRefinementStep(maxHammingDistance = 6)
        )
    )
}

fun buildDurationToleranceSignature(
    minDurationMillis: Long,
    maxDurationMillis: Long,
    step: DurationToleranceStep
): String {
    val toleranceMillis = durationToleranceMillis(step)
    val min = minDurationMillis.coerceAtLeast(0L)
    val max = maxDurationMillis.coerceAtLeast(min)
    return "duration-v1:$toleranceMillis:$min-$max"
}

fun buildDurationNeighborListSignature(
    minDurationMillis: Long,
    maxDurationMillis: Long,
    step: DurationNeighborListStep
): String {
    val toleranceMillis = durationNeighborToleranceMillis(step)
    val min = minDurationMillis.coerceAtLeast(0L)
    val max = maxDurationMillis.coerceAtLeast(min)
    return "duration-neighbor-list-v1:$toleranceMillis:${paddedDurationMillis(min)}-${paddedDurationMillis(max)}"
}

fun durationToleranceMillis(step: DurationToleranceStep): Long {
    return step.toleranceSeconds.coerceAtLeast(0).toLong() * 1_000L
}

fun durationNeighborToleranceMillis(step: DurationNeighborListStep): Long {
    return step.toleranceSeconds.coerceAtLeast(0).toLong() * 1_000L
}

private fun paddedDurationMillis(durationMillis: Long): String {
    return durationMillis.coerceAtLeast(0L).toString().padStart(13, '0')
}

fun isVideoPath(path: String): Boolean {
    val lower = path.lowercase()
    return videoExtensions.any { extension -> lower.endsWith(extension) }
}

fun isImagePath(path: String): Boolean {
    val lower = path.lowercase()
    return imageExtensions.any { extension -> lower.endsWith(extension) }
}

private val videoExtensions = setOf(
    ".3gp",
    ".avi",
    ".flv",
    ".m2ts",
    ".m4v",
    ".mkv",
    ".mov",
    ".mp4",
    ".mpeg",
    ".mpg",
    ".mts",
    ".ts",
    ".webm",
    ".wmv"
)

private val imageExtensions = setOf(
    ".bmp",
    ".gif",
    ".heic",
    ".heif",
    ".jpeg",
    ".jpg",
    ".png",
    ".webp"
)
