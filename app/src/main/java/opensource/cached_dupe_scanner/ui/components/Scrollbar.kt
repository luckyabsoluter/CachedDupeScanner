package opensource.cached_dupe_scanner.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.fillMaxWidth
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

object ScrollbarDefaults {
    val ThumbWidth: Dp = 16.dp
    val MinThumbHeight: Dp = 64.dp
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
    BoxWithConstraints(
        modifier = modifier
            .width(thumbWidth)
            .fillMaxHeight()
    ) {
        val viewportHeightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)
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

        Canvas(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth()
                .pointerInput(maxScrollPx, viewportHeightPx, thumbHeightPx) {
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
                    ) { change, dragAmount ->
                        change.consume()
                        if (maxScrollPx <= 0f) return@detectDragGestures
                        val deltaScroll = if (maxThumbOffsetPx <= 0f) {
                            0f
                        } else {
                            (dragAmount.y / maxThumbOffsetPx) * maxScrollPx
                        }
                        val newScroll = (scrollState.value + deltaScroll)
                            .coerceIn(0f, maxScrollPx)
                        scope.launch {
                            scrollState.scrollTo(newScroll.roundToInt())
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
    val layoutInfo by remember {
        derivedStateOf { listState.layoutInfo }
    }
    val visibleItems = layoutInfo.visibleItemsInfo
    val smoothedAverageItemSizePx = remember { mutableFloatStateOf(0f) }
    val measuredAverageItemSizePx = if (visibleItems.isNotEmpty()) {
        visibleItems.map { it.size }.average().toFloat().coerceAtLeast(1f)
    } else {
        0f
    }
    SideEffect {
        val current = smoothedAverageItemSizePx.floatValue
        smoothedAverageItemSizePx.floatValue = when {
            measuredAverageItemSizePx <= 0f -> current
            current <= 0f -> measuredAverageItemSizePx
            else -> (current * 0.8f) + (measuredAverageItemSizePx * 0.2f)
        }.coerceAtLeast(1f)
    }

    BoxWithConstraints(
        modifier = modifier
            .width(thumbWidth)
            .fillMaxHeight()
    ) {
        val trackHeightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        val totalItems = layoutInfo.totalItemsCount.coerceAtLeast(1)
        val visibleCount = visibleItems.size.coerceAtLeast(1)
        val averageItemSizePx = smoothedAverageItemSizePx.floatValue.coerceAtLeast(1f)
        val firstVisibleSizePx = (visibleItems.firstOrNull()?.size ?: 0)
            .toFloat()
            .coerceAtLeast(averageItemSizePx)

        val maxScrollItems = (totalItems - visibleCount).coerceAtLeast(0)
        val maxScrollPxEstimate = averageItemSizePx * maxScrollItems

        val currentScrollItemsEstimate = listState.firstVisibleItemIndex.toFloat() +
            (listState.firstVisibleItemScrollOffset.toFloat() / firstVisibleSizePx)

        val minThumbHeightPx = with(density) { minThumbHeight.toPx() }
        val thumbHeightPx = (trackHeightPx * (visibleCount.toFloat() / totalItems.toFloat()))
            .coerceAtLeast(minThumbHeightPx)
            .coerceAtMost(trackHeightPx)
        val maxThumbOffsetPx = (trackHeightPx - thumbHeightPx).coerceAtLeast(0f)

        val scrollFraction = when {
            maxScrollItems <= 0 -> 0f
            !listState.canScrollBackward -> 0f
            !listState.canScrollForward -> 1f
            else -> (currentScrollItemsEstimate / maxScrollItems.toFloat()).coerceIn(0f, 1f)
        }
        val thumbOffsetPx = maxThumbOffsetPx * scrollFraction
        val cornerRadiusPx = with(density) { (thumbWidth / 2).toPx() }

        Canvas(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth()
                .pointerInput(maxScrollPxEstimate, trackHeightPx, thumbHeightPx) {
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
                    ) { change, dragAmount ->
                        change.consume()
                        if (maxScrollPxEstimate <= 0f) return@detectDragGestures
                        val deltaScrollPx = if (maxThumbOffsetPx <= 0f) {
                            0f
                        } else {
                            (dragAmount.y / maxThumbOffsetPx) * maxScrollPxEstimate
                        }
                        scope.launch {
                            listState.scroll {
                                scrollBy(deltaScrollPx)
                            }
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
}
