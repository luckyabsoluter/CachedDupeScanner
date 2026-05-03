package opensource.cached_dupe_scanner.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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

    if (showMemberThumbnails) {
        MemberThumbnailGrid(
            members = members,
            deletedPaths = deletedPaths,
            imageLoader = imageLoader,
            keepLoadedThumbnailsInMemory = keepLoadedThumbnailsInMemory,
            rememberedPreviewCache = rememberedPreviewCache,
            previewMemoryKey = previewMemoryKey,
            onSelectFile = { file -> selectedFile.value = file }
        )
    } else {
        members.sortedBy { it.normalizedPath }.forEach { file ->
            MemberListRow(
                file = file,
                isDeleted = deletedPaths.contains(file.normalizedPath),
                onSelectFile = { selectedFile.value = file }
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
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

@Composable
private fun MemberThumbnailGrid(
    members: List<FileMetadata>,
    deletedPaths: Set<String>,
    imageLoader: ImageLoader,
    keepLoadedThumbnailsInMemory: Boolean,
    rememberedPreviewCache: MutableMap<String, ImageBitmap>,
    previewMemoryKey: String,
    onSelectFile: (FileMetadata) -> Unit
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val columns = memberThumbnailGridColumns(maxWidth)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            members.sortedBy { it.normalizedPath }
                .chunked(columns)
                .forEach { rowMembers ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowMembers.forEach { file ->
                            MemberThumbnailGridTile(
                                file = file,
                                isDeleted = deletedPaths.contains(file.normalizedPath),
                                imageLoader = imageLoader,
                                keepLoadedThumbnailsInMemory = keepLoadedThumbnailsInMemory,
                                rememberedPreviewCache = rememberedPreviewCache,
                                previewMemoryKey = previewMemoryKey,
                                onSelectFile = onSelectFile,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        repeat(columns - rowMembers.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
        }
    }
}

@Composable
private fun MemberThumbnailGridTile(
    file: FileMetadata,
    isDeleted: Boolean,
    imageLoader: ImageLoader,
    keepLoadedThumbnailsInMemory: Boolean,
    rememberedPreviewCache: MutableMap<String, ImageBitmap>,
    previewMemoryKey: String,
    onSelectFile: (FileMetadata) -> Unit,
    modifier: Modifier = Modifier
) {
    val date = formatDate(file.lastModifiedMillis)
    Card(
        modifier = modifier.clickable { onSelectFile(file) },
        colors = if (isDeleted) {
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        } else {
            CardDefaults.cardColors()
        }
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (isMediaFile(file.normalizedPath)) {
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(96.dp)
                )
            }
            Text(
                text = file.normalizedPath,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = if (isDeleted) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
            Text(
                text = "${formatBytesWithExact(file.sizeBytes)} · $date",
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (isDeleted) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}

@Composable
private fun MemberListRow(
    file: FileMetadata,
    isDeleted: Boolean,
    onSelectFile: () -> Unit
) {
    val date = formatDate(file.lastModifiedMillis)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelectFile() },
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
}

internal fun memberThumbnailGridColumns(availableWidth: Dp): Int {
    return when {
        availableWidth >= 720.dp -> 4
        availableWidth >= 520.dp -> 3
        else -> 2
    }
}

private fun memberPreviewMemoryKey(
    previewMemoryKey: String,
    file: FileMetadata
): String {
    return "$previewMemoryKey:member:${file.normalizedPath}:${file.sizeBytes}:${file.lastModifiedMillis}"
}
