package opensource.cached_dupe_scanner.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import opensource.cached_dupe_scanner.tasks.TaskSnapshot

@Composable
fun TaskProgressCard(
    task: TaskSnapshot,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    cancelText: String = "Cancel",
    cancelButton: TaskProgressCancelButton = TaskProgressCancelButton.Outlined,
    currentPathText: (String) -> String = { it },
    maxCurrentPathLines: Int = Int.MAX_VALUE,
    showTitle: Boolean = true,
    showCancelWhenDisabled: Boolean = true,
    leadingContent: @Composable RowScope.() -> Unit = {},
    extraContent: @Composable ColumnScope.() -> Unit = {}
) {
    Card(modifier = modifier.fillMaxWidth()) {
        TaskProgressContent(
            task = task,
            onCancel = onCancel,
            cancelText = cancelText,
            cancelButton = cancelButton,
            currentPathText = currentPathText,
            maxCurrentPathLines = maxCurrentPathLines,
            showTitle = showTitle,
            showCancelWhenDisabled = showCancelWhenDisabled,
            leadingContent = leadingContent,
            extraContent = extraContent
        )
    }
}

@Composable
fun TaskProgressContent(
    task: TaskSnapshot,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    cancelText: String = "Cancel",
    cancelButton: TaskProgressCancelButton = TaskProgressCancelButton.Outlined,
    currentPathText: (String) -> String = { it },
    maxCurrentPathLines: Int = Int.MAX_VALUE,
    showTitle: Boolean = true,
    showCancelWhenDisabled: Boolean = true,
    leadingContent: @Composable RowScope.() -> Unit = {},
    extraContent: @Composable ColumnScope.() -> Unit = {}
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(Spacing.cardPadding),
        verticalArrangement = Arrangement.spacedBy(Spacing.compactGap)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            leadingContent()
            Column(modifier = Modifier.weight(1f)) {
                if (showTitle) {
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.titleSmall
                    )
                }
                Text(
                    text = task.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (task.isCancellable || showCancelWhenDisabled) {
                RenderTaskProgressCancelButton(
                    text = cancelText,
                    style = cancelButton,
                    enabled = task.isCancellable,
                    onClick = onCancel
                )
            }
        }
        extraContent()
        task.currentPath?.let { path ->
            Text(
                text = currentPathText(path),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = maxCurrentPathLines,
                overflow = TextOverflow.Ellipsis
            )
        }
        TaskProgressIndicator(task = task)
    }
}

enum class TaskProgressCancelButton {
    FilledError,
    Outlined
}

@Composable
private fun RenderTaskProgressCancelButton(
    text: String,
    style: TaskProgressCancelButton,
    enabled: Boolean,
    onClick: () -> Unit
) {
    when (style) {
        TaskProgressCancelButton.FilledError -> {
            Button(
                onClick = onClick,
                enabled = enabled,
                colors = taskProgressErrorButtonColors()
            ) {
                Text(text)
            }
        }

        TaskProgressCancelButton.Outlined -> {
            OutlinedButton(
                onClick = onClick,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text)
            }
        }
    }
}

@Composable
private fun taskProgressErrorButtonColors(): ButtonColors {
    return ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer
    )
}

@Composable
private fun TaskProgressIndicator(task: TaskSnapshot) {
    if (task.indeterminate) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    } else {
        LinearProgressIndicator(
            progress = { task.progressFraction() },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

internal fun TaskSnapshot.progressFraction(): Float {
    val totalValue = total ?: 0
    return if (totalValue > 0) {
        ((processed ?: 0).toFloat() / totalValue.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
}
