package opensource.cached_dupe_scanner.ui.home

import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.HideImage
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
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
    candidatePaths: List<String>,
    failedPaths: Set<String>,
    hasRememberedPreview: Boolean
): Boolean {
    return hasRememberedPreview && activePreviewPath(candidatePaths, failedPaths) == null
}

@Composable
internal fun GroupPreviewThumbnail(
    candidatePaths: List<String>,
    previewMemoryKey: String,
    rememberedPreviewCache: MutableMap<String, ImageBitmap>,
    imageLoader: ImageLoader,
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

    if (shouldUseRememberedPreview(candidatePaths, failedPaths, rememberedPreview != null)) {
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

private fun rememberPreviewBitmap(drawable: Drawable): ImageBitmap? {
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

@Composable
private fun MissingPreviewThumbnail(
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
