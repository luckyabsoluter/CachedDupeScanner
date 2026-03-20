package opensource.cached_dupe_scanner.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import java.io.File
import opensource.cached_dupe_scanner.tasks.TaskSnapshot

@Composable
fun TaskBannerStack(
    tasks: List<TaskSnapshot>,
    onOpenTask: (TaskSnapshot) -> Unit,
    onCancelTask: (TaskSnapshot) -> Unit,
    modifier: Modifier = Modifier
) {
    if (tasks.isEmpty()) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.screenPadding, vertical = Spacing.itemGap),
        verticalArrangement = Arrangement.spacedBy(Spacing.itemGap)
    ) {
        tasks.forEach { task ->
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
                            TextButton(onClick = { onCancelTask(task) }) {
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
