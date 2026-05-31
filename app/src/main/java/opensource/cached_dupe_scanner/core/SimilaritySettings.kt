package opensource.cached_dupe_scanner.core

import org.json.JSONObject

const val DEFAULT_SIMILARITY_MIN_SIZE_BYTES: Long = 100L * 1024L * 1024L
const val SIMILARITY_METHOD_EXACT_THUMBNAIL = "exact-thumbnail"
const val SIMILARITY_METHOD_DURATION_TOLERANCE = "duration-tolerance"
const val SIMILARITY_METHOD_DURATION_NEIGHBOR_LIST = "duration-neighbor-list"

data class SimilaritySettingDraft(
    val methodId: String,
    val mediaScope: SimilarityMediaScope,
    val minSizeBytes: Long,
    val paramsJson: String,
    val paramsHash: String,
    val displayName: String
)

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

data class ExactThumbnailHashStep(
    val frameSeconds: List<Int>,
    val resizeWidthPx: Int,
    val resizeHeightPx: Int,
    val quantizationLevels: Int?,
    val grayscale: Boolean
) {
    val summary: String
        get() {
            val colorMode = if (grayscale) "grayscale" else "color"
            val frames = frameSeconds.joinToString(", ") { second -> "${second}s" }
            val quantization = quantizationLevels?.let { "$it-level quantization" } ?: "no quantization"
            return "$frames frames, ${resizeWidthPx}x${resizeHeightPx}, $colorMode, $quantization"
        }
}

data class DurationToleranceStep(
    val toleranceMillis: Long
) {
    constructor(toleranceSeconds: Int) : this(toleranceSeconds.coerceAtLeast(0).toLong() * 1_000L)

    val toleranceSeconds: Int
        get() = (toleranceMillis.coerceAtLeast(0L) / 1_000L).toInt()
}

data class DurationNeighborListStep(
    val toleranceMillis: Long
) {
    constructor(toleranceSeconds: Int) : this(toleranceSeconds.coerceAtLeast(0).toLong() * 1_000L)

    val toleranceSeconds: Int
        get() = (toleranceMillis.coerceAtLeast(0L) / 1_000L).toInt()
}

fun defaultSimilaritySettingDrafts(): List<SimilaritySettingDraft> {
    return listOf(
        exactThumbnailSettingDraft(
            mediaScope = SimilarityMediaScope.Video,
            minSizeBytes = DEFAULT_SIMILARITY_MIN_SIZE_BYTES,
            step = ExactThumbnailHashStep(
                frameSeconds = listOf(0, 1, 10),
                resizeWidthPx = 1,
                resizeHeightPx = 1,
                quantizationLevels = 16,
                grayscale = false
            )
        ),
        exactThumbnailSettingDraft(
            mediaScope = SimilarityMediaScope.Video,
            minSizeBytes = DEFAULT_SIMILARITY_MIN_SIZE_BYTES,
            step = ExactThumbnailHashStep(
                frameSeconds = listOf(0, 1, 10),
                resizeWidthPx = 2,
                resizeHeightPx = 2,
                quantizationLevels = 16,
                grayscale = false
            )
        ),
        durationToleranceSettingDraft(
            minSizeBytes = DEFAULT_SIMILARITY_MIN_SIZE_BYTES,
            step = DurationToleranceStep(toleranceSeconds = 1)
        ),
        durationNeighborListSettingDraft(
            minSizeBytes = DEFAULT_SIMILARITY_MIN_SIZE_BYTES,
            step = DurationNeighborListStep(toleranceSeconds = 1)
        )
    )
}

fun exactThumbnailSettingDraft(
    mediaScope: SimilarityMediaScope,
    minSizeBytes: Long,
    step: ExactThumbnailHashStep,
    displayName: String = "${mediaScope.name} exact thumbnail ${step.resizeWidthPx}x${step.resizeHeightPx}"
): SimilaritySettingDraft {
    val paramsJson = exactThumbnailParamsJson(step)
    return SimilaritySettingDraft(
        methodId = SIMILARITY_METHOD_EXACT_THUMBNAIL,
        mediaScope = mediaScope,
        minSizeBytes = minSizeBytes,
        paramsJson = paramsJson,
        paramsHash = similarityParamsHash(paramsJson),
        displayName = displayName
    )
}

fun durationToleranceSettingDraft(
    minSizeBytes: Long,
    step: DurationToleranceStep,
    displayName: String = "Video duration tolerance ${durationStepLabel(step.toleranceMillis)}"
): SimilaritySettingDraft {
    val paramsJson = durationParamsJson(step.toleranceMillis)
    return SimilaritySettingDraft(
        methodId = SIMILARITY_METHOD_DURATION_TOLERANCE,
        mediaScope = SimilarityMediaScope.Video,
        minSizeBytes = minSizeBytes,
        paramsJson = paramsJson,
        paramsHash = similarityParamsHash(paramsJson),
        displayName = displayName
    )
}

fun durationNeighborListSettingDraft(
    minSizeBytes: Long,
    step: DurationNeighborListStep,
    displayName: String = "Video duration neighbor list ${durationStepLabel(step.toleranceMillis)}"
): SimilaritySettingDraft {
    val paramsJson = durationParamsJson(step.toleranceMillis)
    return SimilaritySettingDraft(
        methodId = SIMILARITY_METHOD_DURATION_NEIGHBOR_LIST,
        mediaScope = SimilarityMediaScope.Video,
        minSizeBytes = minSizeBytes,
        paramsJson = paramsJson,
        paramsHash = similarityParamsHash(paramsJson),
        displayName = displayName
    )
}

fun exactThumbnailStepFromParams(paramsJson: String): ExactThumbnailHashStep {
    val obj = JSONObject(paramsJson)
    val frameArray = obj.getJSONArray("frameSeconds")
    val frameSeconds = (0 until frameArray.length()).map { index -> frameArray.getInt(index) }
    val quantization = if (obj.isNull("quantizationLevels")) {
        null
    } else {
        obj.getInt("quantizationLevels")
    }
    return ExactThumbnailHashStep(
        frameSeconds = frameSeconds,
        resizeWidthPx = obj.getInt("resizeWidthPx"),
        resizeHeightPx = obj.getInt("resizeHeightPx"),
        quantizationLevels = quantization,
        grayscale = obj.getBoolean("grayscale")
    )
}

fun durationToleranceStepFromParams(paramsJson: String): DurationToleranceStep {
    return DurationToleranceStep(JSONObject(paramsJson).getLong("toleranceMillis"))
}

fun durationNeighborListStepFromParams(paramsJson: String): DurationNeighborListStep {
    return DurationNeighborListStep(JSONObject(paramsJson).getLong("toleranceMillis"))
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
    return step.toleranceMillis.coerceAtLeast(0L)
}

fun durationNeighborToleranceMillis(step: DurationNeighborListStep): Long {
    return step.toleranceMillis.coerceAtLeast(0L)
}

fun similarityMethodLabel(methodId: String): String {
    return when (methodId) {
        SIMILARITY_METHOD_EXACT_THUMBNAIL -> "Exact thumbnail"
        SIMILARITY_METHOD_DURATION_TOLERANCE -> "Duration tolerance"
        SIMILARITY_METHOD_DURATION_NEIGHBOR_LIST -> "Duration neighbor list"
        else -> methodId
    }
}

private fun exactThumbnailParamsJson(step: ExactThumbnailHashStep): String {
    val frames = step.frameSeconds.joinToString(prefix = "[", postfix = "]")
    val quantization = step.quantizationLevels?.toString() ?: "null"
    return "{" +
        "\"frameSeconds\":$frames," +
        "\"resizeWidthPx\":${step.resizeWidthPx}," +
        "\"resizeHeightPx\":${step.resizeHeightPx}," +
        "\"quantizationLevels\":$quantization," +
        "\"grayscale\":${step.grayscale}" +
        "}"
}

private fun durationParamsJson(toleranceMillis: Long): String {
    return "{\"toleranceMillis\":${toleranceMillis.coerceAtLeast(0L)}}"
}

private fun similarityParamsHash(paramsJson: String): String {
    return Hashing.sha256Hex(paramsJson.toByteArray(Charsets.UTF_8))
}

private fun paddedDurationMillis(durationMillis: Long): String {
    return durationMillis.coerceAtLeast(0L).toString().padStart(13, '0')
}

private fun durationStepLabel(durationMillis: Long): String {
    val safeValue = durationMillis.coerceAtLeast(0L)
    return if (safeValue % 1_000L == 0L) {
        "${safeValue / 1_000L}s"
    } else {
        "${safeValue}ms"
    }
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

