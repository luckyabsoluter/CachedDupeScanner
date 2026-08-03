package opensource.cached_dupe_scanner.ui.home

internal data class SourcePage<T, C>(
    val items: List<T>,
    val nextCursor: C?,
    val exhausted: Boolean
)

internal data class FilteredSourcePage<T, C>(
    val items: List<T>,
    val nextCursor: C?,
    val exhausted: Boolean,
    val sourceLoadedCount: Int
)

internal fun <Source, Match, Cursor> loadFilteredSourcePage(
    startCursor: Cursor,
    minMatches: Int,
    loadPage: (Cursor) -> SourcePage<Source, Cursor>,
    transformMatch: (Source) -> Match?,
    trimToMinMatches: Boolean = true,
    shouldContinue: () -> Boolean = { true }
): FilteredSourcePage<Match, Cursor> {
    val matchedItems = mutableListOf<Match>()
    var sourceLoaded = 0
    var nextCursor: Cursor? = startCursor
    var exhausted = false

    while (matchedItems.size < minMatches && !exhausted) {
        ensureUiListLoadContinues(shouldContinue)
        val currentCursor = nextCursor ?: startCursor
        val page = loadPage(currentCursor)
        ensureUiListLoadContinues(shouldContinue)
        if (page.items.isEmpty()) {
            exhausted = page.exhausted
            nextCursor = page.nextCursor
            break
        }
        sourceLoaded += page.items.size
        page.items.forEach { source ->
            ensureUiListLoadContinues(shouldContinue)
            transformMatch(source)?.let { match -> matchedItems += match }
        }
        nextCursor = page.nextCursor
        exhausted = page.exhausted
    }
    ensureUiListLoadContinues(shouldContinue)

    val returnedItems = if (trimToMinMatches) {
        matchedItems.take(minMatches)
    } else {
        matchedItems
    }
    return FilteredSourcePage(
        items = returnedItems,
        nextCursor = nextCursor,
        exhausted = exhausted,
        sourceLoadedCount = sourceLoaded
    )
}
