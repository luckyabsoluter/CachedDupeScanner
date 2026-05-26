package opensource.cached_dupe_scanner.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import opensource.cached_dupe_scanner.core.FileMetadata

internal class LazyDetailSelectionState {
    var isSelectAllMode by mutableStateOf(false)
        private set
    var selectedPaths by mutableStateOf<Set<String>>(emptySet())
        private set
    var deselectedPathsInSelectAll by mutableStateOf<Set<String>>(emptySet())
        private set

    val isSelectionMode: Boolean
        get() = isSelectAllMode || selectedPaths.isNotEmpty()

    val allSelectedAcrossGroup: Boolean
        get() = isSelectAllMode && deselectedPathsInSelectAll.isEmpty()

    fun clear() {
        isSelectAllMode = false
        selectedPaths = emptySet()
        deselectedPathsInSelectAll = emptySet()
    }

    fun toggleSelectAll() {
        if (allSelectedAcrossGroup) {
            clear()
        } else {
            isSelectAllMode = true
            selectedPaths = emptySet()
            deselectedPathsInSelectAll = emptySet()
        }
    }

    fun togglePath(path: String, isDeleted: Boolean) {
        if (isSelectAllMode) {
            deselectedPathsInSelectAll = togglePathSelection(
                selectedPaths = deselectedPathsInSelectAll,
                path = path
            )
        } else if (!isDeleted) {
            selectedPaths = togglePathSelection(
                selectedPaths = selectedPaths,
                path = path
            )
        }
    }

    fun isPathSelected(path: String): Boolean {
        return isPathSelectedForMode(
            path = path,
            selectAllMode = isSelectAllMode,
            selectedPaths = selectedPaths,
            deselectedPaths = deselectedPathsInSelectAll
        )
    }

    fun selectedCount(totalCount: Int): Int {
        return countSelectedForDisplay(
            totalCount = totalCount,
            selectAllMode = isSelectAllMode,
            selectedPaths = selectedPaths,
            deselectedPaths = deselectedPathsInSelectAll
        )
    }

    fun statusText(totalCount: Int): String {
        return selectionStatusText(
            totalCount = totalCount,
            selectAllMode = isSelectAllMode,
            selectedPaths = selectedPaths,
            deselectedPaths = deselectedPathsInSelectAll
        )
    }

    fun filterPartialSelectionToLoadedMembers(
        members: List<FileMetadata>,
        deletedPaths: Set<String>
    ) {
        if (isSelectAllMode) return
        selectedPaths = selectedPaths.filterTo(linkedSetOf()) { path ->
            members.any { file -> file.normalizedPath == path } &&
                !deletedPaths.contains(path)
        }
    }

    fun selectedLoadedFilesForDelete(
        members: List<FileMetadata>,
        deletedPaths: Set<String>
    ): List<FileMetadata> {
        return if (isSelectAllMode) {
            emptyList()
        } else {
            selectedFilesForDelete(
                members = members,
                selectedPaths = selectedPaths,
                deletedPaths = deletedPaths
            )
        }
    }

    fun markFailedPaths(failedPaths: Set<String>) {
        isSelectAllMode = false
        deselectedPathsInSelectAll = emptySet()
        selectedPaths = failedPaths
    }
}

@Composable
internal fun rememberLazyDetailSelectionState(key: Any): LazyDetailSelectionState {
    return remember(key) { LazyDetailSelectionState() }
}
