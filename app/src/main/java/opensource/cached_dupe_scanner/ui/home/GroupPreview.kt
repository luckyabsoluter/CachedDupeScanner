package opensource.cached_dupe_scanner.ui.home

import android.graphics.drawable.Drawable
import android.media.MediaMetadataRetriever
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.HideImage
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.videoFramePercent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import opensource.cached_dupe_scanner.core.FileMetadata
import opensource.cached_dupe_scanner.core.AndroidVideoDurationExtractor
import java.io.File
import kotlin.math.min

internal fun mediaPreviewCandidates(
    files: List<FileMetadata>,
    deletedPaths: Set<String>,
    pathExists: (String) -> Boolean = { path -> File(path).exists() }
): List<String> {
    return files.asSequence()
        .map { it.normalizedPath }
        .filter { path -> isMediaFile(path) && !deletedPaths.contains(path) && pathExists(path) }
        .distinct()
        .toList()
}

internal fun activePreviewPath(
    candidatePaths: List<String>,
    failedPaths: Set<String>
): String? {
    return candidatePaths.firstOrNull { path -> !failedPaths.contains(path) }
}

internal fun shouldUseRememberedPreview(
    activePath: String?,
    hasRememberedPreview: Boolean,
    keepLoadedInMemory: Boolean
): Boolean {
    return hasRememberedPreview && (keepLoadedInMemory || activePath == null)
}

internal data class VideoTimelineFrame(
    val percent: Float,
    val keySuffix: String
)

internal fun dynamicTimelineFrameCount(
    containerWidth: Dp,
    frameHeight: Dp,
    frameSpacing: Dp = VIDEO_TIMELINE_FRAME_SPACING
): Int {
    if (containerWidth <= 0.dp || frameHeight <= 0.dp) return 1

    val frameWidth = (frameHeight * VIDEO_TIMELINE_FRAME_WIDTH_RATIO).value
    val availableWidth = containerWidth.value
    val spacing = frameSpacing.value
    val count = ((availableWidth + spacing) / (frameWidth + spacing)).toInt()
    return count.coerceAtLeast(1)
}

internal fun snappedTimelineFrameWidth(
    containerWidth: Dp,
    frameCount: Int,
    frameSpacing: Dp = VIDEO_TIMELINE_FRAME_SPACING
): Dp {
    if (frameCount <= 0 || containerWidth <= 0.dp) return 1.dp

    val spacingTotal = frameSpacing * (frameCount - 1)
    val availableWidth = (containerWidth - spacingTotal).coerceAtLeast(1.dp)
    return availableWidth / frameCount
}

internal fun totalTimelineFrameCount(
    framesPerRow: Int,
    lineCount: Int
): Int {
    return framesPerRow.coerceAtLeast(1) * lineCount.coerceAtLeast(1)
}

internal fun timelineFrameRows(
    frameSpecs: List<VideoTimelineFrame>,
    framesPerRow: Int
): List<List<VideoTimelineFrame>> {
    return frameSpecs.chunked(framesPerRow.coerceAtLeast(1))
}

internal fun buildVideoTimelineFrames(frameCount: Int = DEFAULT_VIDEO_TIMELINE_FRAME_COUNT): List<VideoTimelineFrame> {
    if (frameCount <= 0) return emptyList()
    if (frameCount == 1) return listOf(VideoTimelineFrame(percent = 0f, keySuffix = "start"))

    val lastIndex = frameCount - 1
    return List(frameCount) { index ->
        val rawPercent = index.toFloat() / lastIndex.toFloat()
        val clampedPercent = rawPercent.coerceIn(0f, VIDEO_TIMELINE_END_PERCENT)
        val keySuffix = when (index) {
            0 -> "start"
            lastIndex / 2 -> "middle"
            lastIndex -> "end"
            else -> "p$index"
        }
        VideoTimelineFrame(percent = clampedPercent, keySuffix = keySuffix)
    }
}

internal fun videoDurationLabel(durationMillis: Long): String {
    val safeDuration = durationMillis.coerceAtLeast(0L)
    val totalSeconds = safeDuration / 1_000L
    val millis = safeDuration % 1_000L
    val seconds = totalSeconds % 60L
    val minutes = (totalSeconds / 60L) % 60L
    val hours = totalSeconds / 3_600L
    val secondsText = seconds.toString().padStart(2, '0') + if (millis == 0L) {
        ""
    } else {
        ".${millis.toString().padStart(3, '0')}"
    }

    return if (hours > 0L) {
        "$hours:${minutes.toString().padStart(2, '0')}:$secondsText"
    } else {
        "$minutes:$secondsText"
    }
}

internal fun videoDurationPreviewText(durationMillis: Long?): String {
    return durationMillis?.let { duration -> "Duration ${videoDurationLabel(duration)}" }
        ?: "Duration unavailable"
}

internal data class VideoResolution(
    val width: Int,
    val height: Int
)

internal fun normalizedVideoResolution(
    width: Int,
    height: Int,
    rotationDegrees: Int
): VideoResolution? {
    if (width <= 0 || height <= 0) return null

    val normalizedRotation = ((rotationDegrees % 360) + 360) % 360
    return if (normalizedRotation == 90 || normalizedRotation == 270) {
        VideoResolution(width = height, height = width)
    } else {
        VideoResolution(width = width, height = height)
    }
}

internal fun videoResolutionPreviewText(videoResolution: VideoResolution?): String {
    return videoResolution?.let { resolution -> "Resolution ${resolution.width}x${resolution.height}" }
        ?: "Resolution unavailable"
}

@Composable
internal fun VideoMetadataLabelText(
    filePath: String,
    showDuration: Boolean,
    showResolution: Boolean,
    modifier: Modifier = Modifier,
    suffixText: String? = null
) {
    if (!showDuration && !showResolution && suffixText == null) return

    var durationMillis by remember(filePath) { mutableStateOf<Long?>(null) }
    var durationLoaded by remember(filePath) { mutableStateOf(false) }
    var videoResolution by remember(filePath) { mutableStateOf<VideoResolution?>(null) }
    var resolutionLoaded by remember(filePath) { mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(filePath, showDuration) {
        if (!showDuration) {
            durationMillis = null
            durationLoaded = false
            return@LaunchedEffect
        }

        durationLoaded = false
        durationMillis = withContext(Dispatchers.IO) {
            AndroidVideoDurationExtractor().durationMillis(
                file = File(filePath),
                shouldContinue = { isActive }
            )
        }
        durationLoaded = true
    }

    androidx.compose.runtime.LaunchedEffect(filePath, showResolution) {
        if (!showResolution) {
            videoResolution = null
            resolutionLoaded = false
            return@LaunchedEffect
        }

        resolutionLoaded = false
        videoResolution = withContext(Dispatchers.IO) {
            readVideoResolution(
                file = File(filePath),
                shouldContinue = { isActive }
            )
        }
        resolutionLoaded = true
    }

    val loadedDurationText = videoDurationPreviewText(durationMillis)
    val durationText = if (durationLoaded) loadedDurationText else "Duration loading..."
    val loadedResolutionText = videoResolutionPreviewText(videoResolution)
    val resolutionText = if (resolutionLoaded) loadedResolutionText else "Resolution loading..."
    val metadataText = listOfNotNull(
        if (showDuration) durationText else null,
        if (showResolution) resolutionText else null
    ).joinToString(" · ")
    val labelText = listOfNotNull(
        metadataText.takeIf { it.isNotEmpty() },
        suffixText
    ).joinToString(" · ")
    if (labelText.isEmpty()) return

    Text(
        text = labelText,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
    )
}

@Composable
internal fun VideoTimelinePreviewStrip(
    filePath: String,
    rememberedPreviewCache: MutableMap<String, ImageBitmap>,
    imageLoader: ImageLoader,
    keepLoadedInMemory: Boolean,
    snapToFillWidth: Boolean = false,
    lineCount: Int = 1,
    frameHeight: Dp = 44.dp,
    showDuration: Boolean = false,
    showResolution: Boolean = false,
    modifier: Modifier = Modifier
) {
    val safeLineCount = lineCount.coerceAtLeast(1)

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        val guideText = "Start - ... - Middle - ... - End"
        VideoMetadataLabelText(
            filePath = filePath,
            showDuration = showDuration,
            showResolution = showResolution,
            suffixText = guideText
        )
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val framesPerRow = remember(maxWidth, frameHeight) {
                dynamicTimelineFrameCount(
                    containerWidth = maxWidth,
                    frameHeight = frameHeight
                )
            }
            val totalFrameCount = remember(framesPerRow, safeLineCount) {
                totalTimelineFrameCount(
                    framesPerRow = framesPerRow,
                    lineCount = safeLineCount
                )
            }
            val frameSpecs = remember(totalFrameCount) {
                buildVideoTimelineFrames(frameCount = totalFrameCount)
            }
            val frameRows = remember(frameSpecs, framesPerRow) {
                timelineFrameRows(
                    frameSpecs = frameSpecs,
                    framesPerRow = framesPerRow
                )
            }
            val frameWidth = remember(maxWidth, frameHeight, framesPerRow, snapToFillWidth) {
                if (snapToFillWidth) {
                    snappedTimelineFrameWidth(
                        containerWidth = maxWidth,
                        frameCount = framesPerRow
                    )
                } else {
                    frameHeight * VIDEO_TIMELINE_FRAME_WIDTH_RATIO
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(VIDEO_TIMELINE_ROW_SPACING)) {
                frameRows.forEach { rowFrames ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(VIDEO_TIMELINE_FRAME_SPACING)
                    ) {
                        rowFrames.forEach { frame ->
                            RememberingVideoFrameThumbnail(
                                filePath = filePath,
                                framePercent = frame.percent,
                                previewMemoryKey = "$filePath#timeline#${frame.keySuffix}",
                                rememberedPreviewCache = rememberedPreviewCache,
                                imageLoader = imageLoader,
                                keepLoadedInMemory = keepLoadedInMemory,
                                contentDescription = "Timeline frame ${frame.keySuffix}",
                                modifier = Modifier
                                    .width(frameWidth)
                                    .height(frameHeight)
                                    .clip(MaterialTheme.shapes.small)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun readVideoResolution(
    file: File,
    shouldContinue: () -> Boolean
): VideoResolution? {
    if (!shouldContinue()) return null

    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(file.absolutePath)
        if (!shouldContinue()) return null

        val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            .positiveIntOrNull()
            ?: return null
        val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            .positiveIntOrNull()
            ?: return null
        val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
            ?.toIntOrNull()
            ?: 0

        normalizedVideoResolution(
            width = width,
            height = height,
            rotationDegrees = rotation
        )
    } catch (_: RuntimeException) {
        null
    } finally {
        runCatching { retriever.release() }
    }
}

private fun String?.positiveIntOrNull(): Int? {
    return this?.toIntOrNull()?.takeIf { value -> value > 0 }
}

@Composable
internal fun GroupPreviewThumbnail(
    candidatePaths: List<String>,
    previewMemoryKey: String,
    rememberedPreviewCache: MutableMap<String, ImageBitmap>,
    imageLoader: ImageLoader,
    keepLoadedInMemory: Boolean,
    modifier: Modifier = Modifier,
    contentDescription: String = "Thumbnail"
) {
    val context = LocalContext.current
    var failedPaths by remember(candidatePaths) { mutableStateOf(emptySet<String>()) }
    val activePath = remember(candidatePaths, failedPaths) {
        activePreviewPath(
            candidatePaths = candidatePaths,
            failedPaths = failedPaths
        )
    }
    val rememberedPreview = rememberedPreviewCache[previewMemoryKey]

    if (shouldUseRememberedPreview(activePath, rememberedPreview != null, keepLoadedInMemory)) {
        Image(
            bitmap = rememberedPreview!!,
            contentDescription = contentDescription,
            modifier = modifier
        )
        return
    }

    if (activePath == null) {
        MissingPreviewThumbnail(
            modifier = modifier,
            contentDescription = contentDescription
        )
        return
    }

    AsyncImage(
        model = ImageRequest.Builder(context)
            .data(File(activePath))
            .build(),
        imageLoader = imageLoader,
        contentDescription = contentDescription,
        modifier = modifier,
        onSuccess = { result ->
            val previewBitmap = rememberPreviewBitmap(result.result.drawable) ?: return@AsyncImage
            rememberedPreviewCache[previewMemoryKey] = previewBitmap
        },
        onError = {
            failedPaths = failedPaths + activePath
        }
    )
}

@Composable
internal fun RememberingAsyncThumbnail(
    filePath: String,
    previewMemoryKey: String,
    rememberedPreviewCache: MutableMap<String, ImageBitmap>,
    imageLoader: ImageLoader,
    keepLoadedInMemory: Boolean,
    modifier: Modifier = Modifier,
    contentDescription: String = "Thumbnail"
) {
    val context = LocalContext.current
    val rememberedPreview = rememberedPreviewCache[previewMemoryKey]
    val activePath = filePath.takeIf { File(it).exists() }

    if (shouldUseRememberedPreview(activePath, rememberedPreview != null, keepLoadedInMemory)) {
        Image(
            bitmap = rememberedPreview!!,
            contentDescription = contentDescription,
            modifier = modifier
        )
        return
    }

    if (activePath == null) {
        MissingPreviewThumbnail(
            modifier = modifier,
            contentDescription = contentDescription
        )
        return
    }

    AsyncImage(
        model = ImageRequest.Builder(context)
            .data(File(activePath))
            .build(),
        imageLoader = imageLoader,
        contentDescription = contentDescription,
        modifier = modifier,
        onSuccess = { result ->
            if (!keepLoadedInMemory) return@AsyncImage
            val previewBitmap = rememberPreviewBitmap(result.result.drawable) ?: return@AsyncImage
            rememberedPreviewCache[previewMemoryKey] = previewBitmap
        }
    )
}

@Composable
internal fun RememberingVideoFrameThumbnail(
    filePath: String,
    framePercent: Float,
    previewMemoryKey: String,
    rememberedPreviewCache: MutableMap<String, ImageBitmap>,
    imageLoader: ImageLoader,
    keepLoadedInMemory: Boolean,
    modifier: Modifier = Modifier,
    contentDescription: String = "Video frame"
) {
    val context = LocalContext.current
    val rememberedPreview = rememberedPreviewCache[previewMemoryKey]
    val activePath = filePath.takeIf { File(it).exists() }

    if (shouldUseRememberedPreview(activePath, rememberedPreview != null, keepLoadedInMemory)) {
        Image(
            bitmap = rememberedPreview!!,
            contentDescription = contentDescription,
            modifier = modifier
        )
        return
    }

    if (activePath == null) {
        MissingPreviewThumbnail(
            modifier = modifier,
            contentDescription = contentDescription
        )
        return
    }

    AsyncImage(
        model = ImageRequest.Builder(context)
            .data(File(activePath))
            .videoFramePercent(framePercent.toDouble())
            .build(),
        imageLoader = imageLoader,
        contentDescription = contentDescription,
        modifier = modifier,
        onSuccess = { result ->
            if (!keepLoadedInMemory) return@AsyncImage
            val previewBitmap = rememberPreviewBitmap(result.result.drawable) ?: return@AsyncImage
            rememberedPreviewCache[previewMemoryKey] = previewBitmap
        }
    )
}

internal fun rememberPreviewBitmap(drawable: Drawable): ImageBitmap? {
    val width = drawable.intrinsicWidth
        .takeIf { it > 0 }
        ?.let { min(it, MAX_REMEMBERED_PREVIEW_DIMENSION_PX) }
        ?: return null
    val height = drawable.intrinsicHeight
        .takeIf { it > 0 }
        ?.let { min(it, MAX_REMEMBERED_PREVIEW_DIMENSION_PX) }
        ?: return null
    return drawable.toBitmap(width = width, height = height).asImageBitmap()
}

private const val MAX_REMEMBERED_PREVIEW_DIMENSION_PX = 1024
private const val DEFAULT_VIDEO_TIMELINE_FRAME_COUNT = 7
private const val VIDEO_TIMELINE_FRAME_WIDTH_RATIO = 1.6f
private const val VIDEO_TIMELINE_END_PERCENT = 0.98f
private val VIDEO_TIMELINE_FRAME_SPACING = 4.dp
private val VIDEO_TIMELINE_ROW_SPACING = 4.dp

@Composable
internal fun MissingPreviewThumbnail(
    modifier: Modifier,
    contentDescription: String
) {
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.HideImage,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxSize(0.42f)
        )
    }
}
