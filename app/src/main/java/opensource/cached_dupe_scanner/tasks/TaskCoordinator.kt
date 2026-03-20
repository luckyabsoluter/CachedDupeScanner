package opensource.cached_dupe_scanner.tasks

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.Snapshot

enum class TaskArea {
    Scan,
    Db,
    Trash
}

enum class TaskKind {
    ScanTarget,
    ScanAll,
    DbMaintenance,
    RebuildGroups,
    ClearCache,
    EmptyTrash
}

enum class TaskStatus {
    Running,
    Completed,
    Failed,
    Cancelled
}

data class TaskSnapshot(
    val area: TaskArea,
    val kind: TaskKind,
    val title: String,
    val detail: String,
    val currentPath: String?,
    val processed: Int?,
    val total: Int?,
    val indeterminate: Boolean,
    val startedAt: Long,
    val isCancellable: Boolean,
    val status: TaskStatus
)

data class TaskTerminalSummary(
    val area: TaskArea,
    val kind: TaskKind,
    val title: String,
    val detail: String,
    val currentPath: String?,
    val processed: Int?,
    val total: Int?,
    val indeterminate: Boolean,
    val startedAt: Long,
    val finishedAt: Long,
    val status: TaskStatus
)

class TaskCoordinator {
    private val cancelActions = linkedMapOf<TaskArea, () -> Unit>()

    val activeTasks = mutableStateListOf<TaskSnapshot>()
    val terminalSummaries = mutableStateMapOf<TaskArea, TaskTerminalSummary>()

    fun isAreaBusy(area: TaskArea): Boolean {
        return Snapshot.withoutReadObservation {
            activeTasks.any { it.area == area }
        }
    }

    fun activeTask(area: TaskArea): TaskSnapshot? {
        return activeTasks.firstOrNull { it.area == area }
    }

    fun terminalSummary(area: TaskArea): TaskTerminalSummary? {
        return terminalSummaries[area]
    }

    fun tryStart(
        area: TaskArea,
        kind: TaskKind,
        title: String,
        detail: String,
        currentPath: String? = null,
        processed: Int? = null,
        total: Int? = null,
        indeterminate: Boolean = total == null || total <= 0,
        isCancellable: Boolean = false,
        onCancel: (() -> Unit)? = null
    ): TaskSnapshot? {
        var started: TaskSnapshot? = null
        Snapshot.withMutableSnapshot {
            if (activeTasks.any { it.area == area }) {
                return@withMutableSnapshot
            }
            terminalSummaries.remove(area)
            val snapshot = TaskSnapshot(
                area = area,
                kind = kind,
                title = title,
                detail = detail,
                currentPath = currentPath,
                processed = processed,
                total = total,
                indeterminate = indeterminate,
                startedAt = System.currentTimeMillis(),
                isCancellable = isCancellable,
                status = TaskStatus.Running
            )
            activeTasks.add(0, snapshot)
            if (onCancel != null) {
                cancelActions[area] = onCancel
            } else {
                cancelActions.remove(area)
            }
            started = snapshot
        }
        return started
    }

    fun update(
        area: TaskArea,
        transform: (TaskSnapshot) -> TaskSnapshot
    ): TaskSnapshot? {
        var updated: TaskSnapshot? = null
        Snapshot.withMutableSnapshot {
            val index = activeTasks.indexOfFirst { it.area == area }
            if (index < 0) {
                return@withMutableSnapshot
            }
            val current = activeTasks[index]
            val next = transform(current).copy(
                area = current.area,
                kind = current.kind,
                startedAt = current.startedAt,
                isCancellable = current.isCancellable,
                status = TaskStatus.Running
            )
            activeTasks[index] = next
            updated = next
        }
        return updated
    }

    fun requestCancel(area: TaskArea): Boolean {
        val action = Snapshot.withoutReadObservation { cancelActions[area] } ?: return false
        action()
        return true
    }

    fun complete(
        area: TaskArea,
        title: String,
        detail: String,
        currentPath: String? = null,
        processed: Int? = null,
        total: Int? = null,
        indeterminate: Boolean = total == null || total <= 0
    ): TaskTerminalSummary? {
        return finish(
            area = area,
            status = TaskStatus.Completed,
            title = title,
            detail = detail,
            currentPath = currentPath,
            processed = processed,
            total = total,
            indeterminate = indeterminate
        )
    }

    fun fail(
        area: TaskArea,
        title: String,
        detail: String,
        currentPath: String? = null,
        processed: Int? = null,
        total: Int? = null,
        indeterminate: Boolean = total == null || total <= 0
    ): TaskTerminalSummary? {
        return finish(
            area = area,
            status = TaskStatus.Failed,
            title = title,
            detail = detail,
            currentPath = currentPath,
            processed = processed,
            total = total,
            indeterminate = indeterminate
        )
    }

    fun cancel(
        area: TaskArea,
        title: String,
        detail: String,
        currentPath: String? = null,
        processed: Int? = null,
        total: Int? = null,
        indeterminate: Boolean = total == null || total <= 0
    ): TaskTerminalSummary? {
        return finish(
            area = area,
            status = TaskStatus.Cancelled,
            title = title,
            detail = detail,
            currentPath = currentPath,
            processed = processed,
            total = total,
            indeterminate = indeterminate
        )
    }

    private fun finish(
        area: TaskArea,
        status: TaskStatus,
        title: String,
        detail: String,
        currentPath: String?,
        processed: Int?,
        total: Int?,
        indeterminate: Boolean
    ): TaskTerminalSummary? {
        var summary: TaskTerminalSummary? = null
        Snapshot.withMutableSnapshot {
            val index = activeTasks.indexOfFirst { it.area == area }
            if (index < 0) {
                cancelActions.remove(area)
                return@withMutableSnapshot
            }
            val current = activeTasks.removeAt(index)
            cancelActions.remove(area)
            val terminal = TaskTerminalSummary(
                area = current.area,
                kind = current.kind,
                title = title,
                detail = detail,
                currentPath = currentPath,
                processed = processed,
                total = total,
                indeterminate = indeterminate,
                startedAt = current.startedAt,
                finishedAt = System.currentTimeMillis(),
                status = status
            )
            terminalSummaries[area] = terminal
            summary = terminal
        }
        return summary
    }
}
