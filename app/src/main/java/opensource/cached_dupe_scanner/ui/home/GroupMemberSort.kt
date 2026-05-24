package opensource.cached_dupe_scanner.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import opensource.cached_dupe_scanner.core.FileMetadata
import opensource.cached_dupe_scanner.core.SortDirection
import opensource.cached_dupe_scanner.ui.components.RadioOptionRow

internal enum class ResultGroupMemberSortKey(val label: String) {
    Path("Path"),
    Modified("Modified")
}

@Composable
internal fun GroupMemberSortButton(
    sortKey: ResultGroupMemberSortKey,
    sortDirection: SortDirection,
    onApplySort: (ResultGroupMemberSortKey, SortDirection) -> Unit,
    modifier: Modifier = Modifier
) {
    val dialogOpen = remember { mutableStateOf(false) }
    val pendingSortKey = remember { mutableStateOf(sortKey) }
    val pendingSortDirection = remember { mutableStateOf(sortDirection) }

    OutlinedButton(
        modifier = modifier,
        onClick = {
            pendingSortKey.value = sortKey
            pendingSortDirection.value = sortDirection
            dialogOpen.value = true
        }
    ) {
        Text("Sort")
    }

    if (dialogOpen.value) {
        AlertDialog(
            onDismissRequest = { dialogOpen.value = false },
            title = { Text("Group sort options") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Sort by")
                    RadioOptionRow(
                        option = ResultGroupMemberSortKey.Path,
                        selected = pendingSortKey.value,
                        label = ResultGroupMemberSortKey.Path.label,
                        onSelect = { pendingSortKey.value = it }
                    )
                    RadioOptionRow(
                        option = ResultGroupMemberSortKey.Modified,
                        selected = pendingSortKey.value,
                        label = ResultGroupMemberSortKey.Modified.label,
                        onSelect = { pendingSortKey.value = it }
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Direction")
                    RadioOptionRow(
                        option = SortDirection.Asc,
                        selected = pendingSortDirection.value,
                        label = SortDirection.Asc.label,
                        onSelect = { pendingSortDirection.value = it }
                    )
                    RadioOptionRow(
                        option = SortDirection.Desc,
                        selected = pendingSortDirection.value,
                        label = SortDirection.Desc.label,
                        onSelect = { pendingSortDirection.value = it }
                    )
                }
            },
            confirmButton = {
                OutlinedButton(
                    onClick = {
                        onApplySort(
                            pendingSortKey.value,
                            pendingSortDirection.value
                        )
                        dialogOpen.value = false
                    }
                ) {
                    Text("Apply")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { dialogOpen.value = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

internal fun sortGroupMembers(
    members: List<FileMetadata>,
    sortKey: ResultGroupMemberSortKey,
    direction: SortDirection
): List<FileMetadata> {
    val comparator = when (sortKey) {
        ResultGroupMemberSortKey.Path -> {
            compareBy<FileMetadata> { it.normalizedPath }
        }
        ResultGroupMemberSortKey.Modified -> {
            compareBy<FileMetadata> { it.lastModifiedMillis }
                .thenBy { it.normalizedPath }
        }
    }
    return if (direction == SortDirection.Asc) {
        members.sortedWith(comparator)
    } else {
        members.sortedWith(comparator.reversed())
    }
}
