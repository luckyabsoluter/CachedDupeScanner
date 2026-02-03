package opensource.cached_dupe_scanner.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import opensource.cached_dupe_scanner.core.FileMetadata

@Composable
fun FileDetailsDialog(
    file: FileMetadata,
    showName: Boolean,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    val confirmDelete = remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("File details") },
        text = {
            Column {
                if (showName) {
                    Text("Name: ${formatPath(file.normalizedPath, showFullPath = false)}")
                }
                Text("Path: ${file.normalizedPath}")
                Text("Size: ${formatBytesWithExact(file.sizeBytes)}")
                Text("Modified: ${formatDate(file.lastModifiedMillis)}")
            }
        },
        confirmButton = {
            OutlinedButton(onClick = onOpen) {
                Text("Open")
            }
        },
        dismissButton = {
            Row {
                OutlinedButton(onClick = { confirmDelete.value = true }) {
                    Text("Delete")
                }
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )

    if (confirmDelete.value) {
        AlertDialog(
            onDismissRequest = { confirmDelete.value = false },
            title = { Text("Delete file?") },
            text = {
                Text("This will permanently delete the file.")
            },
            confirmButton = {
                OutlinedButton(onClick = {
                    confirmDelete.value = false
                    onDelete()
                }) {
                    Text("Delete")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { confirmDelete.value = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
