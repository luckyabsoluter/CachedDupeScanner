package opensource.cached_dupe_scanner.ui.home

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
import androidx.compose.ui.platform.LocalContext
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import opensource.cached_dupe_scanner.core.FileMetadata
import java.io.File

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

@Composable
internal fun GroupPreviewThumbnail(
    candidatePaths: List<String>,
    imageLoader: ImageLoader,
    modifier: Modifier = Modifier,
    contentDescription: String = "Thumbnail"
) {
    val context = LocalContext.current
    var failedPaths by remember(candidatePaths) { mutableStateOf(emptySet<String>()) }
    val activePath = remember(candidatePaths, failedPaths) {
        candidatePaths.firstOrNull { path -> !failedPaths.contains(path) }
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
        onError = {
            failedPaths = failedPaths + activePath
        }
    )
}

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
