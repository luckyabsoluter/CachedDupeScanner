package opensource.cached_dupe_scanner.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.ScrollState
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.math.max

object ScrollbarDefaults {
    val ThumbWidth: Dp = 16.dp
    val MinThumbHeight: Dp = 64.dp
}

private data class ScrollbarDragSnapshot(
    val maxScrollPx: Float,
    val maxThumbOffsetPx: Float,
    val thumbHeightPx: Float
)

private data class LazyScrollbarDragSnapshot(
    val maxScrollPx: Float,
    val maxThumbOffsetPx: Float,
    val thumbHeightPx: Float,
    val typicalItemSizePx: Float,
    val totalItems: Int,
    val knownItemSizesPxByIndex: List<Pair<Int, Int>>
)

internal data class LazyListScrollTarget(
    val index: Int,
    val scrollOffsetPx: Int
)

internal fun thumbOffsetForPointerY(
    pointerY: Float,
    thumbHeightPx: Float,
    maxThumbOffsetPx: Float
): Float {
    val safeMaxThumbOffsetPx = maxThumbOffsetPx.coerceAtLeast(0f)
    val thumbCenterOffsetPx = thumbHeightPx.coerceAtLeast(0f) / 2f
    return (pointerY - thumbCenterOffsetPx).coerceIn(0f, safeMaxThumbOffsetPx)
}

internal fun displayedLazyThumbOffsetPx(
    estimatedThumbOffsetPx: Float,
    dragPointerY: Float?,
    thumbHeightPx: Float,
    maxThumbOffsetPx: Float
): Float {
    return if (dragPointerY == null) {
        estimatedThumbOffsetPx.coerceIn(0f, maxThumbOffsetPx.coerceAtLeast(0f))
    } else {
        thumbOffsetForPointerY(
            pointerY = dragPointerY,
            thumbHeightPx = thumbHeightPx,
            maxThumbOffsetPx = maxThumbOffsetPx
        )
    }
}

internal fun estimateLazyListScrollOffsetPx(
    firstVisibleItemIndex: Int,
    firstVisibleItemScrollOffsetPx: Int,
    typicalItemSizePx: Float,
    knownItemSizesPxByIndex: List<Pair<Int, Int>>
): Float {
    val safeTypicalItemSizePx = typicalItemSizePx.coerceAtLeast(1f)
    val safeFirstIndex = firstVisibleItemIndex.coerceAtLeast(0)
    var knownBeforeSumPx = 0f
    var knownBeforeCount = 0
    var firstItemSizePx = safeTypicalItemSizePx
    for ((index, sizePx) in knownItemSizesPxByIndex) {
        if (index in 0 until safeFirstIndex && sizePx > 0) {
            knownBeforeSumPx += sizePx.toFloat()
            knownBeforeCount += 1
        } else if (index == safeFirstIndex && sizePx > 0) {
            firstItemSizePx = sizePx.toFloat()
            break
        } else if (index > safeFirstIndex) {
            break
        }
    }
    val unknownBeforeCount = (safeFirstIndex - knownBeforeCount).coerceAtLeast(0)
    val estimatedBeforeSumPx = knownBeforeSumPx + (unknownBeforeCount * safeTypicalItemSizePx)
    val inItemOffsetPx = firstVisibleItemScrollOffsetPx
        .toFloat()
        .coerceIn(0f, firstItemSizePx)
    return estimatedBeforeSumPx + inItemOffsetPx
}

internal fun estimateLazyListScrollTarget(
    targetThumbOffsetPx: Float,
    maxThumbOffsetPx: Float,
    maxScrollPx: Float,
    typicalItemSizePx: Float,
    totalItems: Int,
    knownItemSizesPxByIndex: List<Pair<Int, Int>> = emptyList()
): LazyListScrollTarget {
    if (totalItems <= 0) {
        return LazyListScrollTarget(index = 0, scrollOffsetPx = 0)
    }
    if (maxThumbOffsetPx > 0f && targetThumbOffsetPx >= maxThumbOffsetPx) {
        return LazyListScrollTarget(index = totalItems - 1, scrollOffsetPx = 0)
    }
    val safeTypicalItemSizePx = typicalItemSizePx.coerceAtLeast(1f)
    val scrollFraction = if (maxThumbOffsetPx <= 0f) {
        0f
    } else {
        (targetThumbOffsetPx / maxThumbOffsetPx).coerceIn(0f, 1f)
    }
    val targetScrollPx = (scrollFraction * maxScrollPx).coerceIn(0f, maxScrollPx.coerceAtLeast(0f))
    var remainingScrollPx = targetScrollPx
    var nextIndex = 0
    for ((knownIndex, knownSizePx) in knownItemSizesPxByIndex) {
        if (knownIndex !in nextIndex until totalItems || knownSizePx <= 0) continue
        val unknownItemCount = (knownIndex - nextIndex).coerceAtLeast(0)
        val unknownItemsSizePx = unknownItemCount * safeTypicalItemSizePx
        if (remainingScrollPx < unknownItemsSizePx) {
            return estimateTargetWithinUniformItems(
                firstIndex = nextIndex,
                itemCount = unknownItemCount,
                targetScrollPx = remainingScrollPx,
                itemSizePx = safeTypicalItemSizePx
            )
        }
        remainingScrollPx -= unknownItemsSizePx

        val safeKnownSizePx = knownSizePx.toFloat().coerceAtLeast(1f)
        if (remainingScrollPx < safeKnownSizePx) {
            return LazyListScrollTarget(
                index = knownIndex,
                scrollOffsetPx = remainingScrollPx.roundToInt().coerceAtLeast(0)
            )
        }
        remainingScrollPx -= safeKnownSizePx
        nextIndex = knownIndex + 1
    }

    return estimateTargetWithinUniformItems(
        firstIndex = nextIndex.coerceAtMost(totalItems - 1),
        itemCount = (totalItems - nextIndex).coerceAtLeast(1),
        targetScrollPx = remainingScrollPx.coerceAtLeast(0f),
        itemSizePx = safeTypicalItemSizePx
    )
}

private fun estimateTargetWithinUniformItems(
    firstIndex: Int,
    itemCount: Int,
    targetScrollPx: Float,
    itemSizePx: Float
): LazyListScrollTarget {
    val safeItemSizePx = itemSizePx.coerceAtLeast(1f)
    val itemOffset = (targetScrollPx / safeItemSizePx)
        .toInt()
        .coerceIn(0, (itemCount - 1).coerceAtLeast(0))
    val estimatedIndexStartPx = itemOffset * safeItemSizePx
    return LazyListScrollTarget(
        index = firstIndex + itemOffset,
        scrollOffsetPx = (targetScrollPx - estimatedIndexStartPx)
            .roundToInt()
            .coerceAtLeast(0)
    )
}

@Composable
fun VerticalScrollbar(
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
    thumbWidth: Dp = ScrollbarDefaults.ThumbWidth,
    minThumbHeight: Dp = ScrollbarDefaults.MinThumbHeight,
    thumbColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.95f),
    trackColor: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
    thumbPressedColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 1f)
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val isDragging = remember { mutableStateOf(false) }
    val trackHeightPxState = remember { mutableFloatStateOf(1f) }

    val viewportHeightPx = trackHeightPxState.floatValue.coerceAtLeast(1f)
    val maxScrollPx = scrollState.maxValue.toFloat().coerceAtLeast(0f)
    val contentHeightPx = viewportHeightPx + maxScrollPx
    val minThumbHeightPx = with(density) { minThumbHeight.toPx() }
    val thumbHeightPx = if (contentHeightPx <= 0f) {
        viewportHeightPx
    } else {
        (viewportHeightPx * viewportHeightPx / contentHeightPx)
            .coerceAtLeast(minThumbHeightPx)
            .coerceAtMost(viewportHeightPx)
    }
    val scrollFraction = if (maxScrollPx <= 0f) 0f else scrollState.value.toFloat() / maxScrollPx
    val maxThumbOffsetPx = (viewportHeightPx - thumbHeightPx).coerceAtLeast(0f)
    val thumbOffsetPx = maxThumbOffsetPx * scrollFraction
    val cornerRadiusPx = with(density) { (thumbWidth / 2).toPx() }
    val latestDragSnapshot = rememberUpdatedState(
        ScrollbarDragSnapshot(
            maxScrollPx = maxScrollPx,
            maxThumbOffsetPx = maxThumbOffsetPx,
            thumbHeightPx = thumbHeightPx
        )
    )

    Canvas(
        modifier = modifier
            .width(thumbWidth)
            .fillMaxHeight()
            .fillMaxWidth()
            .onSizeChanged { size ->
                trackHeightPxState.floatValue = size.height.toFloat().coerceAtLeast(1f)
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = {
                        isDragging.value = true
                    },
                    onDragEnd = {
                        isDragging.value = false
                    },
                    onDragCancel = {
                        isDragging.value = false
                    }
                ) { change, _ ->
                    change.consume()
                    val dragSnapshot = latestDragSnapshot.value
                    if (dragSnapshot.maxScrollPx <= 0f) return@detectDragGestures
                    val targetThumbOffsetPx = thumbOffsetForPointerY(
                        pointerY = change.position.y,
                        thumbHeightPx = dragSnapshot.thumbHeightPx,
                        maxThumbOffsetPx = dragSnapshot.maxThumbOffsetPx
                    )
                    val newScroll = if (dragSnapshot.maxThumbOffsetPx <= 0f) {
                        0f
                    } else {
                        (targetThumbOffsetPx / dragSnapshot.maxThumbOffsetPx) *
                            dragSnapshot.maxScrollPx
                    }
                    scope.launch {
                        scrollState.scrollTo(
                            newScroll.coerceIn(0f, dragSnapshot.maxScrollPx).roundToInt()
                        )
                    }
                }
            }
    ) {
        drawRoundRect(
            color = trackColor,
            topLeft = Offset.Zero,
            size = Size(size.width, size.height),
            cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx)
        )
        drawRoundRect(
            color = if (isDragging.value) thumbPressedColor else thumbColor,
            topLeft = Offset(0f, thumbOffsetPx),
            size = Size(size.width, thumbHeightPx),
            cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx)
        )
    }
}

@Composable
fun VerticalLazyScrollbar(
    listState: LazyListState,
    modifier: Modifier = Modifier,
    thumbWidth: Dp = ScrollbarDefaults.ThumbWidth,
    minThumbHeight: Dp = ScrollbarDefaults.MinThumbHeight,
    thumbColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.95f),
    trackColor: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
    thumbPressedColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 1f)
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val isDragging = remember { mutableStateOf(false) }
    val dragPointerY = remember { mutableStateOf<Float?>(null) }
    val trackHeightPxState = remember { mutableFloatStateOf(1f) }
    val dragJobState = remember { mutableStateOf<Job?>(null) }
    val itemSizePxByIndex: SnapshotStateMap<Int, Int> = remember { mutableStateMapOf() }
    val knownItemSizesPxByIndex by remember {
        derivedStateOf {
            val totalItemsCount = listState.layoutInfo.totalItemsCount
            itemSizePxByIndex.entries
                .filter { entry -> entry.key in 0 until totalItemsCount }
                .sortedBy { entry -> entry.key }
                .map { entry -> entry.key to entry.value }
        }
    }
    val layoutInfo by remember {
        derivedStateOf { listState.layoutInfo }
    }
    val visibleItems = layoutInfo.visibleItemsInfo
    val smoothedTypicalItemSizePx = remember { mutableFloatStateOf(0f) }
    val measuredTypicalItemSizePx = if (visibleItems.isNotEmpty()) {
        val sizes = visibleItems.map { it.size }.sorted()
        val mid = sizes.size / 2
        val median = if (sizes.size % 2 == 1) {
            sizes[mid].toFloat()
        } else {
            ((sizes[max(0, mid - 1)] + sizes[mid]) / 2f)
        }
        median.coerceAtLeast(1f)
    } else {
        0f
    }
    SideEffect {
        for (info in visibleItems) {
            if (info.size > 0) {
                itemSizePxByIndex[info.index] = info.size
            }
        }
        if (itemSizePxByIndex.size > 3000) {
            val firstIndex = listState.firstVisibleItemIndex
            val minKeep = (firstIndex - 1500).coerceAtLeast(0)
            val maxKeep = firstIndex + 1500
            val keysToRemove = itemSizePxByIndex.keys
                .filter { it < minKeep || it > maxKeep }
            for (key in keysToRemove) {
                itemSizePxByIndex.remove(key)
            }
        }

        val current = smoothedTypicalItemSizePx.floatValue
        val alpha = if (isDragging.value || listState.isScrollInProgress) 0.05f else 0.2f
        smoothedTypicalItemSizePx.floatValue = when {
            measuredTypicalItemSizePx <= 0f -> current
            current <= 0f -> measuredTypicalItemSizePx
            else -> (current * (1f - alpha)) + (measuredTypicalItemSizePx * alpha)
        }.coerceAtLeast(1f)
    }

    val smoothedThumbHeightPxState = remember { mutableFloatStateOf(0f) }

    val trackHeightPx = trackHeightPxState.floatValue.coerceAtLeast(1f)
    val totalItems = layoutInfo.totalItemsCount.coerceAtLeast(1)
    val typicalItemSizePx = smoothedTypicalItemSizePx.floatValue.coerceAtLeast(1f)

    val knownSumPx = knownItemSizesPxByIndex.fold(0f) { totalPx, (_, sizePx) ->
        totalPx + sizePx
    }
    val knownCount = knownItemSizesPxByIndex.size
    val unknownCount = (totalItems - knownCount).coerceAtLeast(0)
    val estimatedContentHeightPx = (knownSumPx + (unknownCount * typicalItemSizePx))
        .coerceAtLeast(trackHeightPx)
    val maxScrollPxEstimate = (estimatedContentHeightPx - trackHeightPx).coerceAtLeast(0f)

    val currentScrollPxEstimate = estimateLazyListScrollOffsetPx(
        firstVisibleItemIndex = listState.firstVisibleItemIndex,
        firstVisibleItemScrollOffsetPx = listState.firstVisibleItemScrollOffset,
        typicalItemSizePx = typicalItemSizePx,
        knownItemSizesPxByIndex = knownItemSizesPxByIndex
    )

    val minThumbHeightPx = with(density) { minThumbHeight.toPx() }
    val effectiveMinThumbHeightPx = minThumbHeightPx.coerceAtMost(trackHeightPx)
    val targetThumbHeightPx = (trackHeightPx * (trackHeightPx / estimatedContentHeightPx))
        .coerceIn(effectiveMinThumbHeightPx, trackHeightPx)
    SideEffect {
        val current = smoothedThumbHeightPxState.floatValue
        val alpha = if (isDragging.value || listState.isScrollInProgress) 0.06f else 0.12f
        smoothedThumbHeightPxState.floatValue = when {
            targetThumbHeightPx <= 0f -> current
            current <= 0f -> targetThumbHeightPx
            else -> (current * (1f - alpha)) + (targetThumbHeightPx * alpha)
        }.coerceIn(effectiveMinThumbHeightPx, trackHeightPx)
    }

    val thumbHeightPx = smoothedThumbHeightPxState.floatValue
        .coerceIn(effectiveMinThumbHeightPx, trackHeightPx)
    val maxThumbOffsetPx = (trackHeightPx - thumbHeightPx).coerceAtLeast(0f)

    val scrollFraction = when {
        maxScrollPxEstimate <= 0f -> 0f
        !listState.canScrollBackward -> 0f
        !listState.canScrollForward -> 1f
        else -> (currentScrollPxEstimate / maxScrollPxEstimate).coerceIn(0f, 1f)
    }
    val estimatedThumbOffsetPx = maxThumbOffsetPx * scrollFraction
    val thumbOffsetPx = displayedLazyThumbOffsetPx(
        estimatedThumbOffsetPx = estimatedThumbOffsetPx,
        dragPointerY = dragPointerY.value,
        thumbHeightPx = thumbHeightPx,
        maxThumbOffsetPx = maxThumbOffsetPx
    )
    val cornerRadiusPx = with(density) { (thumbWidth / 2).toPx() }
    val latestDragSnapshot = rememberUpdatedState(
        LazyScrollbarDragSnapshot(
            maxScrollPx = maxScrollPxEstimate,
            maxThumbOffsetPx = maxThumbOffsetPx,
            thumbHeightPx = thumbHeightPx,
            typicalItemSizePx = typicalItemSizePx,
            totalItems = totalItems,
            knownItemSizesPxByIndex = knownItemSizesPxByIndex
        )
    )

    Canvas(
        modifier = modifier
            .width(thumbWidth)
            .fillMaxHeight()
            .fillMaxWidth()
            .onSizeChanged { size ->
                trackHeightPxState.floatValue = size.height.toFloat().coerceAtLeast(1f)
            }
            .pointerInput(Unit) {
                fun scrollToPointerY(pointerY: Float) {
                    dragPointerY.value = pointerY
                    val dragSnapshot = latestDragSnapshot.value
                    if (dragSnapshot.maxScrollPx <= 0f) return
                    val targetThumbOffsetPx = thumbOffsetForPointerY(
                        pointerY = pointerY,
                        thumbHeightPx = dragSnapshot.thumbHeightPx,
                        maxThumbOffsetPx = dragSnapshot.maxThumbOffsetPx
                    )
                    val target = estimateLazyListScrollTarget(
                        targetThumbOffsetPx = targetThumbOffsetPx,
                        maxThumbOffsetPx = dragSnapshot.maxThumbOffsetPx,
                        maxScrollPx = dragSnapshot.maxScrollPx,
                        typicalItemSizePx = dragSnapshot.typicalItemSizePx,
                        totalItems = dragSnapshot.totalItems,
                        knownItemSizesPxByIndex = dragSnapshot.knownItemSizesPxByIndex
                    )
                    dragJobState.value?.cancel()
                    dragJobState.value = scope.launch {
                        listState.scrollToItem(target.index, target.scrollOffsetPx)
                    }
                }

                detectDragGestures(
                    onDragStart = { startOffset ->
                        isDragging.value = true
                        scrollToPointerY(startOffset.y)
                    },
                    onDragEnd = {
                        isDragging.value = false
                        dragPointerY.value = null
                        dragJobState.value = null
                    },
                    onDragCancel = {
                        isDragging.value = false
                        dragPointerY.value = null
                        dragJobState.value = null
                    }
                ) { change, _ ->
                    change.consume()
                    scrollToPointerY(change.position.y)
                }
            }
    ) {
        drawRoundRect(
            color = trackColor,
            topLeft = Offset.Zero,
            size = Size(size.width, size.height),
            cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx)
        )
        drawRoundRect(
            color = if (isDragging.value) thumbPressedColor else thumbColor,
            topLeft = Offset(0f, thumbOffsetPx),
            size = Size(size.width, thumbHeightPx),
            cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx)
        )
    }
}
