package opensource.cached_dupe_scanner.core

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

fun buildThumbnailHash(
    mediaScope: SimilarityMediaScope,
    step: ExactThumbnailHashStep,
    frameSignatures: List<String>
): String {
    return thumbnailPayloadHashHex(
        buildThumbnailPayload(
            mediaScope = mediaScope,
            step = step,
            frameSignatures = frameSignatures
        )
    )
}

fun buildThumbnailHashClusterKey(
    mediaScope: SimilarityMediaScope,
    step: ExactThumbnailHashStep,
    thumbnailHashHex: String
): String {
    require(isSha256HashHex(thumbnailHashHex)) { "Thumbnail hash must be SHA-256 hex" }
    val mode = if (step.grayscale) "gray" else "color"
    val quantization = step.quantizationLevels?.let { "q$it" } ?: "raw"
    return listOf(
        THUMBNAIL_HASH_CLUSTER_KEY_PREFIX,
        mediaScope.name.lowercase(),
        mode,
        "${step.resizeWidthPx}x${step.resizeHeightPx}",
        quantization,
        step.frameSeconds.joinToString(","),
        thumbnailHashHex.lowercase()
    ).joinToString(":")
}

internal fun thumbnailHashClusterKeyFromLegacyPayload(payload: String): String? {
    val parts = payload.split(":", limit = THUMBNAIL_KEY_PART_COUNT)
    if (parts.size != THUMBNAIL_KEY_PART_COUNT || parts[0] != THUMBNAIL_PAYLOAD_PREFIX) return null
    return listOf(
        THUMBNAIL_HASH_CLUSTER_KEY_PREFIX,
        parts[1],
        parts[2],
        parts[3],
        parts[4],
        parts[5],
        thumbnailPayloadHashHex(payload)
    ).joinToString(":")
}

internal fun isSha256HashHex(value: String): Boolean {
    return value.length == SHA_256_HEX_LENGTH && value.all { char ->
        char in '0'..'9' || char in 'a'..'f' || char in 'A'..'F'
    }
}

private fun buildThumbnailPayload(
    mediaScope: SimilarityMediaScope,
    step: ExactThumbnailHashStep,
    frameSignatures: List<String>
): String {
    val mode = if (step.grayscale) "gray" else "color"
    val quantization = step.quantizationLevels?.let { "q$it" } ?: "raw"
    return listOf(
        THUMBNAIL_PAYLOAD_PREFIX,
        mediaScope.name.lowercase(),
        mode,
        "${step.resizeWidthPx}x${step.resizeHeightPx}",
        quantization,
        step.frameSeconds.joinToString(","),
        frameSignatures.joinToString("|")
    ).joinToString(":")
}

private fun thumbnailPayloadHashHex(payload: String): String {
    val bytes = MessageDigest.getInstance("SHA-256")
        .digest(payload.toByteArray(StandardCharsets.UTF_8))
    return buildString(SHA_256_HEX_LENGTH) {
        bytes.forEach { byte -> append(byte.toInt().and(0xff).toString(16).padStart(2, '0')) }
    }
}

private const val THUMBNAIL_PAYLOAD_PREFIX = "thumb-v1"
internal const val THUMBNAIL_HASH_CLUSTER_KEY_PREFIX = "thumb-v2"
private const val THUMBNAIL_KEY_PART_COUNT = 7
private const val SHA_256_HEX_LENGTH = 64
