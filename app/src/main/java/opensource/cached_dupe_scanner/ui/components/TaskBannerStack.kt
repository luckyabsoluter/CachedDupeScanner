package opensource.cached_dupe_scanner.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.io.File
import kotlin.math.roundToInt
import opensource.cached_dupe_scanner.tasks.TaskArea
import opensource.cached_dupe_scanner.tasks.TaskSnapshot

private val TaskBubbleFillColor = Color(0xFF36B8FF)
private val IntSizeSaver = listSaver<IntSize, Int>(
    save = { listOf(it.width, it.height) },
    restore = { restored -> IntSize(restored[0], restored[1]) }
)

@Composable
fun TaskBannerStack(
    tasks: List<TaskSnapshot>,
    onOpenTask: (TaskSnapshot) -> Unit,
    onCancelTask: (TaskSnapshot) -> Unit,
    modifier: Modifier = Modifier
) {
    var collapsed by rememberSaveable { mutableStateOf(false) }
    var lastSeenStartedAt by rememberSaveable { mutableLongStateOf(0L) }
    var collapsedAreas by rememberSaveable { mutableStateOf<List<String>>(emptyList()) }
    var bubbleOffsetX by rememberSaveable { mutableStateOf(0f) }
    var bubbleOffsetY by rememberSaveable { mutableStateOf(0f) }
    var bubbleSize by rememberSaveable(stateSaver = IntSizeSaver) { mutableStateOf(IntSize.Zero) }

    if (tasks.isEmpty()) return

    val newestStartedAt = tasks.maxOfOrNull { it.startedAt } ?: 0L
    val bubbleAreaOrder = collapsedAreas.toTaskAreas().ifEmpty { tasks.map { it.area } }
    val bubbleSegments = tasks.toCollapsedBubbleSegments(bubbleAreaOrder)
    val collapsedProgress by animateFloatAsState(
        targetValue = bubbleSegments.overallProgress(),
        animationSpec = tween(durationMillis = 220),
        label = "taskBannerCollapsedProgress"
    )
    LaunchedEffect(newestStartedAt) {
        if (newestStartedAt > lastSeenStartedAt) {
            collapsed = false
            lastSeenStartedAt = newestStartedAt
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.screenPadding, vertical = Spacing.itemGap)
    ) {
        val density = LocalDensity.current
        val maxOffsetX = with(density) { maxWidth.toPx() } - bubbleSize.width.toFloat()
        val maxOffsetY = with(density) { maxHeight.toPx() } - bubbleSize.height.toFloat()

        if (collapsed) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset {
                        IntOffset(
                            x = bubbleOffsetX.roundToInt(),
                            y = bubbleOffsetY.roundToInt()
                        )
                    }
                    .size(44.dp)
                    .onSizeChanged { bubbleSize = it }
                    .pointerInput(maxOffsetX, maxOffsetY) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            bubbleOffsetX = (bubbleOffsetX + dragAmount.x)
                                .coerceIn(0f, maxOffsetX.coerceAtLeast(0f))
                            bubbleOffsetY = (bubbleOffsetY + dragAmount.y)
                                .coerceIn(0f, maxOffsetY.coerceAtLeast(0f))
                        }
                    }
                    .clickable { collapsed = false },
                shape = CircleShape,
                color = Color.Black,
                contentColor = if (collapsedProgress >= 0.45f) {
                    Color.White
                } else {
                    Color.White
                },
                tonalElevation = Spacing.xs
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(Color.Black)
                        .border(
                            width = 2.dp,
                            color = MaterialTheme.colorScheme.outline,
                            shape = CircleShape
                        )
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .fillMaxHeight(collapsedProgress.coerceIn(0f, 1f))
                            .background(TaskBubbleFillColor)
                    )
                    if (bubbleSegments.size > 1) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(1.dp)
                        ) {
                            repeat(bubbleSegments.size) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth()
                                )
                            }
                        }
                    }
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = tasks.size.toString(),
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Spacing.itemGap)
            ) {
                tasks.forEachIndexed { index, task ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenTask(task) }
                    ) {
                        Column(
                            modifier = Modifier.padding(Spacing.cardPadding),
                            verticalArrangement = Arrangement.spacedBy(Spacing.compactGap)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (index == 0) {
                                    OutlinedButton(
                                        onClick = {
                                            collapsedAreas = tasks.map { it.area.name }
                                            collapsed = true
                                        }
                                    ) {
                                        Text("<")
                                    }
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = task.title,
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Text(
                                        text = task.detail,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (task.isCancellable) {
                                    Button(
                                        onClick = { onCancelTask(task) },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.errorContainer,
                                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                    ) {
                                        Text("Cancel")
                                    }
                                }
                            }
                            task.currentPath?.let { path ->
                                Text(
                                    text = File(path).name.ifBlank { path },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (task.indeterminate) {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            } else {
                                val progress = if ((task.total ?: 0) > 0) {
                                    (task.processed ?: 0).toFloat() / task.total!!.toFloat()
                                } else {
                                    0f
                                }
                                LinearProgressIndicator(
                                    progress = { progress.coerceIn(0f, 1f) },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class CollapsedBubbleSegment(
    val area: TaskArea,
    val progress: Float
)

private fun List<TaskSnapshot>.toCollapsedBubbleSegments(
    collapsedAreas: List<TaskArea>
): List<CollapsedBubbleSegment> {
    if (collapsedAreas.isEmpty()) {
        return listOf(CollapsedBubbleSegment(area = TaskArea.Scan, progress = 0f))
    }
    val activeByArea = associateBy { it.area }
    return collapsedAreas.map { area ->
        val task = activeByArea[area]
        val progress = when {
            task == null -> 1f
            task.bubbleIndeterminate -> 0f
            (task.bubbleTotal ?: 0) <= 0 -> 0f
            else -> ((task.bubbleProcessed ?: 0).toFloat() / task.bubbleTotal!!.toFloat()).coerceIn(0f, 1f)
        }
        CollapsedBubbleSegment(area = area, progress = progress)
    }
}

private fun List<CollapsedBubbleSegment>.overallProgress(): Float {
    if (isEmpty()) return 0f
    return (sumOf { it.progress.toDouble() } / size.toDouble()).toFloat().coerceIn(0f, 1f)
}

private fun List<String>.toTaskAreas(): List<TaskArea> {
    return mapNotNull { areaName ->
        TaskArea.entries.firstOrNull { it.name == areaName }
    }
}
