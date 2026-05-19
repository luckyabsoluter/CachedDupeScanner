package opensource.cached_dupe_scanner.ui.home.similarity

import opensource.cached_dupe_scanner.core.DurationNeighborListStep
import opensource.cached_dupe_scanner.core.DurationToleranceStep
import opensource.cached_dupe_scanner.core.ExactThumbnailHashStep

internal enum class SimilaritySizeUnit(
    val label: String,
    val bytes: Long
) {
    B("B", 1L),
    KB("KB", 1024L),
    MB("MB", 1024L * 1024L),
    GB("GB", 1024L * 1024L * 1024L)
}

internal enum class SimilarityTimeUnit(
    val label: String,
    val millis: Long
) {
    S("s", 1_000L),
    MS("ms", 1L),
    MIN("min", 60_000L)
}

internal data class SimilaritySizeInput(
    val input: String,
    val unit: SimilaritySizeUnit
)

internal data class SimilarityTimeInput(
    val input: String,
    val unit: SimilarityTimeUnit
)

internal fun parsedMinSizeBytes(
    input: String,
    unit: SimilaritySizeUnit
): Long {
    val amount = input.toLongOrNull() ?: 0L
    return amount.coerceAtLeast(0L) * unit.bytes
}

internal fun sanitizeNumberDraftInput(input: String): String {
    return input.filter { char -> char.isDigit() }
}

internal fun sanitizeFrameSecondsInput(input: String): String {
    return input.filter { char -> char.isDigit() || char == ',' || char.isWhitespace() }
}

internal fun parsedFrameSeconds(input: String): List<Int> {
    return input.split(',')
        .mapNotNull { part -> part.trim().toIntOrNull() }
        .map { second -> second.coerceAtLeast(0) }
        .distinct()
}

internal fun parsedExactThumbnailStep(
    frameSecondsInput: String,
    resizeWidthInput: String,
    resizeHeightInput: String,
    quantizationEnabled: Boolean,
    quantizationInput: String,
    grayscale: Boolean
): ExactThumbnailHashStep {
    return ExactThumbnailHashStep(
        frameSeconds = parsedFrameSeconds(frameSecondsInput),
        resizeWidthPx = (resizeWidthInput.toIntOrNull() ?: 1).coerceAtLeast(1),
        resizeHeightPx = (resizeHeightInput.toIntOrNull() ?: 1).coerceAtLeast(1),
        quantizationLevels = if (quantizationEnabled) {
            (quantizationInput.toIntOrNull() ?: 16).coerceAtLeast(2)
        } else {
            null
        },
        grayscale = grayscale
    )
}

internal fun parsedDurationToleranceStep(
    input: String,
    unit: SimilarityTimeUnit = SimilarityTimeUnit.S
): DurationToleranceStep {
    return DurationToleranceStep(
        toleranceMillis = parsedDurationToleranceMillis(
            input = input,
            unit = unit
        )
    )
}

internal fun parsedDurationNeighborListStep(
    input: String,
    unit: SimilarityTimeUnit = SimilarityTimeUnit.S
): DurationNeighborListStep {
    return DurationNeighborListStep(
        toleranceMillis = parsedDurationToleranceMillis(
            input = input,
            unit = unit
        )
    )
}

internal fun parsedDurationToleranceMillis(
    input: String,
    unit: SimilarityTimeUnit
): Long {
    val amount = input.toLongOrNull() ?: 1L
    return amount.coerceAtLeast(0L) * unit.millis
}

internal fun defaultSizeInputForUnit(
    bytes: Long,
    unit: SimilaritySizeUnit
): SimilaritySizeInput {
    if (unit.bytes > 0L && bytes % unit.bytes == 0L) {
        return SimilaritySizeInput(
            input = (bytes / unit.bytes).toString(),
            unit = unit
        )
    }
    return SimilaritySizeInput(
        input = bytes.coerceAtLeast(0L).toString(),
        unit = SimilaritySizeUnit.B
    )
}

internal fun defaultTimeInputForUnit(
    millis: Long,
    unit: SimilarityTimeUnit
): SimilarityTimeInput {
    val safeMillis = millis.coerceAtLeast(0L)
    if (unit.millis > 0L && safeMillis % unit.millis == 0L) {
        return SimilarityTimeInput(
            input = (safeMillis / unit.millis).toString(),
            unit = unit
        )
    }
    return SimilarityTimeInput(
        input = safeMillis.toString(),
        unit = SimilarityTimeUnit.MS
    )
}
