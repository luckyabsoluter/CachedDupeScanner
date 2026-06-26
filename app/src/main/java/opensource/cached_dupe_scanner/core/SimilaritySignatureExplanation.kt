package opensource.cached_dupe_scanner.core

internal data class ExactThumbnailClusterExplanation(
    val mediaScope: String,
    val colorMode: String,
    val resize: String,
    val quantization: String,
    val frameSeconds: List<String>,
    val sampleSignatures: List<String>
)

internal fun exactThumbnailClusterExplanation(signature: String): ExactThumbnailClusterExplanation? {
    val parts = signature.split(":", limit = 7)
    if (parts.size != 7 || parts[0] != "thumb-v1") return null
    val mediaScope = parts[1].takeIf { it.isNotBlank() } ?: return null
    val colorMode = parts[2].takeIf { it == "gray" || it == "color" } ?: return null
    val resize = parts[3].takeIf { it.contains("x") } ?: return null
    val quantization = parts[4].takeIf { it.isNotBlank() } ?: return null
    val frameSeconds = parts[5]
        .split(',')
        .map { frame -> frame.trim() }
        .filter { frame -> frame.isNotEmpty() }
    val sampleSignatures = parts[6]
        .split('|')
        .map { sample -> sample.trim() }
        .filter { sample -> sample.isNotEmpty() }

    return ExactThumbnailClusterExplanation(
        mediaScope = mediaScope,
        colorMode = colorMode,
        resize = resize,
        quantization = quantization,
        frameSeconds = frameSeconds,
        sampleSignatures = sampleSignatures
    )
}

internal data class DurationClusterExplanation(
    val toleranceMillis: Long,
    val minDurationMillis: Long,
    val maxDurationMillis: Long
)

internal fun durationClusterExplanation(signature: String): DurationClusterExplanation? {
    val parts = signature.split(":", limit = 3)
    if (parts.size != 3 || parts[0] != "duration-v1") return null
    val toleranceMillis = parts[1].toLongOrNull()?.coerceAtLeast(0L) ?: return null
    val minDurationMillis = parts[2].substringBefore('-').toLongOrNull()?.coerceAtLeast(0L) ?: return null
    val maxDurationMillis = parts[2].substringAfter('-', missingDelimiterValue = "")
        .toLongOrNull()
        ?.coerceAtLeast(minDurationMillis)
        ?: return null
    return DurationClusterExplanation(
        toleranceMillis = toleranceMillis,
        minDurationMillis = minDurationMillis,
        maxDurationMillis = maxDurationMillis
    )
}

internal data class DurationNeighborClusterExplanation(
    val toleranceMillis: Long,
    val minDurationMillis: Long,
    val maxDurationMillis: Long
)

internal fun durationNeighborClusterExplanation(signature: String): DurationNeighborClusterExplanation? {
    val parts = signature.split(":", limit = 3)
    if (parts.size != 3 || !isDurationNeighborListSignature(signature)) return null
    val toleranceMillis = parts[1].toLongOrNull()?.coerceAtLeast(0L) ?: return null
    val minDurationMillis = parts[2].substringBefore('-').toLongOrNull()?.coerceAtLeast(0L) ?: return null
    val maxDurationMillis = parts[2].substringAfter('-', missingDelimiterValue = "")
        .toLongOrNull()
        ?.coerceAtLeast(minDurationMillis)
        ?: return null
    return DurationNeighborClusterExplanation(
        toleranceMillis = toleranceMillis,
        minDurationMillis = minDurationMillis,
        maxDurationMillis = maxDurationMillis
    )
}

private fun isDurationNeighborListSignature(signature: String): Boolean {
    return signature.startsWith("duration-neighbor-list-v1:") ||
        signature.startsWith("duration-neighbor-v1:")
}
