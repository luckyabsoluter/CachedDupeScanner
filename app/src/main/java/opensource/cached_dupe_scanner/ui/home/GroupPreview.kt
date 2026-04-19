package opensource.cached_dupe_scanner.ui.home

import android.graphics.drawable.Drawable
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
import opensource.cached_dupe_scanner.core.FileMetadata
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

@Composable
internal fun VideoTimelinePreviewStrip(
    filePath: String,
    rememberedPreviewCache: MutableMap<String, ImageBitmap>,
    imageLoader: ImageLoader,
    keepLoadedInMemory: Boolean,
    snapToFillWidth: Boolean = false,
    frameHeight: Dp = 44.dp,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "Start - ... - Middle - ... - End",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val frameCount = remember(maxWidth, frameHeight) {
                dynamicTimelineFrameCount(
                    containerWidth = maxWidth,
                    frameHeight = frameHeight
                )
            }
            val frameSpecs = remember(frameCount) {
                buildVideoTimelineFrames(frameCount = frameCount)
            }
            val frameWidth = remember(maxWidth, frameHeight, frameCount, snapToFillWidth) {
                if (snapToFillWidth) {
                    snappedTimelineFrameWidth(
                        containerWidth = maxWidth,
                        frameCount = frameCount
                    )
                } else {
                    frameHeight * VIDEO_TIMELINE_FRAME_WIDTH_RATIO
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(VIDEO_TIMELINE_FRAME_SPACING)
            ) {
                frameSpecs.forEach { frame ->
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
