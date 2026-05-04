package opensource.cached_dupe_scanner.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import opensource.cached_dupe_scanner.core.FileMetadata

@Composable
internal fun DuplicateGroupDetailContent(
    title: String,
    memberCount: Int,
    totalBytes: Long,
    summaryLines: List<String>,
    members: List<FileMetadata>,
    deletedPaths: Set<String>,
    imageLoader: ImageLoader,
    keepLoadedThumbnailsInMemory: Boolean,
    rememberedPreviewCache: MutableMap<String, ImageBitmap>,
    previewMemoryKey: String,
    previewHeight: Dp,
    showMemberThumbnails: Boolean = false,
    onDeleteFile: (suspend (FileMetadata) -> Boolean)?
) {
    val context = LocalContext.current
    val selectedFile = remember { mutableStateOf<FileMetadata?>(null) }
    val hasPreviewMedia = members.any { isMediaFile(it.normalizedPath) }
    val previewCandidates = mediaPreviewCandidates(
        files = members,
        deletedPaths = deletedPaths
    )

    Text(title)
    Spacer(modifier = Modifier.height(8.dp))
    if (hasPreviewMedia) {
        GroupPreviewThumbnail(
            candidatePaths = previewCandidates,
            previewMemoryKey = previewMemoryKey,
            rememberedPreviewCache = rememberedPreviewCache,
            imageLoader = imageLoader,
            keepLoadedInMemory = keepLoadedThumbnailsInMemory,
            contentDescription = "Thumbnail",
            modifier = Modifier
                .fillMaxWidth()
                .height(previewHeight)
        )
        Spacer(modifier = Modifier.height(8.dp))
    }
    Text("$memberCount files · Total ${formatBytes(totalBytes)}")
    summaryLines.forEach { line ->
        Text(
            text = line,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    Spacer(modifier = Modifier.height(8.dp))

    members.sortedBy { it.normalizedPath }.forEach { file ->
        val date = formatDate(file.lastModifiedMillis)
        val isDeleted = deletedPaths.contains(file.normalizedPath)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { selectedFile.value = file },
            colors = if (isDeleted) {
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            } else {
                CardDefaults.cardColors()
            }
        ) {
            Row(
                modifier = Modifier
                    .padding(10.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (showMemberThumbnails && isMediaFile(file.normalizedPath)) {
                    GroupPreviewThumbnail(
                        candidatePaths = if (isDeleted) emptyList() else listOf(file.normalizedPath),
                        previewMemoryKey = memberPreviewMemoryKey(
                            previewMemoryKey = previewMemoryKey,
                            file = file
                        ),
                        rememberedPreviewCache = rememberedPreviewCache,
                        imageLoader = imageLoader,
                        keepLoadedInMemory = keepLoadedThumbnailsInMemory,
                        contentDescription = "Member thumbnail",
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = file.normalizedPath,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = if (isDeleted) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${formatBytesWithExact(file.sizeBytes)} · $date",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isDeleted) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }

    selectedFile.value?.let { file ->
        FileDetailsDialogWithDeleteConfirm(
            file = file,
            showName = false,
            onOpen = {
                openFile(context, file.normalizedPath)
                selectedFile.value = null
            },
            onDelete = {
                val handler = onDeleteFile ?: return@FileDetailsDialogWithDeleteConfirm false
                handler(file)
            },
            onDeleteResult = { deleted ->
                if (deleted) {
                    selectedFile.value = null
                }
            },
            onDismiss = { selectedFile.value = null }
        )
    }
}

private fun memberPreviewMemoryKey(
    previewMemoryKey: String,
    file: FileMetadata
): String {
    return "$previewMemoryKey:member:${file.normalizedPath}:${file.sizeBytes}:${file.lastModifiedMillis}"
}
