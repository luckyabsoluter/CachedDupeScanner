package opensource.cached_dupe_scanner.tasks

internal fun TaskSnapshot.withLinearProgress(
    detail: String,
    processed: Int,
    total: Int,
    title: String = this.title,
    currentPath: String? = this.currentPath
): TaskSnapshot {
    val indeterminate = total <= 0
    return copy(
        title = title,
        detail = detail,
        currentPath = currentPath,
        processed = processed,
        total = total,
        indeterminate = indeterminate,
        bubbleProcessed = processed,
        bubbleTotal = total,
        bubbleIndeterminate = indeterminate
    )
}
