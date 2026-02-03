package opensource.cached_dupe_scanner.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
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
    val ThumbWidth: Dp = 32.dp
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
    val dragOffsetInThumb = remember { mutableFloatStateOf(0f) }
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
                        onDragStart = { start ->
                            dragOffsetInThumb.floatValue = (start.y - thumbOffsetPx)
                                .coerceIn(0f, thumbHeightPx)
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
                        if (maxScrollPx <= 0f) return@detectDragGestures
                        val newThumbOffset = (change.position.y - dragOffsetInThumb.floatValue)
                            .coerceIn(0f, maxThumbOffsetPx)
                        val newScroll = if (maxThumbOffsetPx <= 0f) {
                            0f
                        } else {
                            (newThumbOffset / maxThumbOffsetPx) * maxScrollPx
                        }
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
    val dragOffsetInThumb = remember { mutableFloatStateOf(0f) }
    val isDragging = remember { mutableStateOf(false) }
    val layoutInfo by remember {
        derivedStateOf { listState.layoutInfo }
    }
    val visibleItems = layoutInfo.visibleItemsInfo
    val viewportHeightPx = (layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset)
        .toFloat()
        .coerceAtLeast(1f)
    val totalItems = layoutInfo.totalItemsCount.coerceAtLeast(1)

    val averageItemSizePx = if (visibleItems.isNotEmpty()) {
        visibleItems.map { it.size }.average().toFloat().coerceAtLeast(1f)
    } else {
        viewportHeightPx
    }
    val totalContentHeightPx = averageItemSizePx * totalItems
    val maxScrollPx = (totalContentHeightPx - viewportHeightPx).coerceAtLeast(0f)
    val currentScrollPx = (listState.firstVisibleItemIndex * averageItemSizePx) +
        listState.firstVisibleItemScrollOffset
    val minThumbHeightPx = with(density) { minThumbHeight.toPx() }
    val thumbHeightPx = if (totalContentHeightPx <= 0f) {
        viewportHeightPx
    } else {
        (viewportHeightPx * viewportHeightPx / totalContentHeightPx)
            .coerceAtLeast(minThumbHeightPx)
            .coerceAtMost(viewportHeightPx)
    }
    val scrollFraction = if (maxScrollPx <= 0f) 0f else (currentScrollPx / maxScrollPx)
    val maxThumbOffsetPx = (viewportHeightPx - thumbHeightPx).coerceAtLeast(0f)
    val thumbOffsetPx = maxThumbOffsetPx * scrollFraction
    val cornerRadiusPx = with(density) { (thumbWidth / 2).toPx() }

    BoxWithConstraints(
        modifier = modifier
            .width(thumbWidth)
            .fillMaxHeight()
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth()
                .pointerInput(maxScrollPx, viewportHeightPx, thumbHeightPx) {
                    detectDragGestures(
                        onDragStart = { start ->
                            dragOffsetInThumb.floatValue = (start.y - thumbOffsetPx)
                                .coerceIn(0f, thumbHeightPx)
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
                        if (maxScrollPx <= 0f) return@detectDragGestures
                        val newThumbOffset = (change.position.y - dragOffsetInThumb.floatValue)
                            .coerceIn(0f, maxThumbOffsetPx)
                        val newScrollPx = if (maxThumbOffsetPx <= 0f) {
                            0f
                        } else {
                            (newThumbOffset / maxThumbOffsetPx) * maxScrollPx
                        }
                        val targetIndex = (newScrollPx / averageItemSizePx)
                            .roundToInt()
                            .coerceIn(0, totalItems - 1)
                        val targetOffset = (newScrollPx - (targetIndex * averageItemSizePx))
                            .roundToInt()
                            .coerceAtLeast(0)
                        scope.launch {
                            listState.scrollToItem(targetIndex, targetOffset)
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
