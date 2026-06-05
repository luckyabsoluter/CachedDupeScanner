package opensource.cached_dupe_scanner.ui.home.similarity

import opensource.cached_dupe_scanner.core.DurationNeighborListStep
import opensource.cached_dupe_scanner.core.DurationToleranceStep
import opensource.cached_dupe_scanner.core.ExactThumbnailHashStep
import opensource.cached_dupe_scanner.core.normalizedDurationNeighborListStep
import opensource.cached_dupe_scanner.core.normalizedDurationToleranceStep
import opensource.cached_dupe_scanner.core.normalizedExactThumbnailHashStep

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
    return normalizedExactThumbnailHashStep(
        ExactThumbnailHashStep(
            frameSeconds = parsedFrameSeconds(frameSecondsInput),
            resizeWidthPx = resizeWidthInput.toIntOrNull() ?: 1,
            resizeHeightPx = resizeHeightInput.toIntOrNull() ?: 1,
            quantizationLevels = if (quantizationEnabled) quantizationInput.toIntOrNull() ?: 16 else null,
            grayscale = grayscale
        )
    )
}

internal fun parsedDurationToleranceStep(
    input: String,
    unit: SimilarityTimeUnit = SimilarityTimeUnit.S
): DurationToleranceStep {
    return normalizedDurationToleranceStep(
        DurationToleranceStep(
            toleranceMillis = parsedDurationMillis(
                input = input,
                unit = unit
            )
        )
    )
}

internal fun parsedDurationNeighborListStep(
    input: String,
    unit: SimilarityTimeUnit = SimilarityTimeUnit.S
): DurationNeighborListStep {
    return normalizedDurationNeighborListStep(
        DurationNeighborListStep(
            toleranceMillis = parsedDurationMillis(
                input = input,
                unit = unit
            )
        )
    )
}

private fun parsedDurationMillis(
    input: String,
    unit: SimilarityTimeUnit
): Long {
    val amount = input.toLongOrNull() ?: 1L
    return amount.coerceAtLeast(0L) * unit.millis
}
