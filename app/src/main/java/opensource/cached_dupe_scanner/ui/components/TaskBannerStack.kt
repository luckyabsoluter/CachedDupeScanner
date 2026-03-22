package opensource.cached_dupe_scanner.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.io.File
import opensource.cached_dupe_scanner.tasks.TaskSnapshot

@Composable
fun TaskBannerStack(
    tasks: List<TaskSnapshot>,
    onOpenTask: (TaskSnapshot) -> Unit,
    onCancelTask: (TaskSnapshot) -> Unit,
    modifier: Modifier = Modifier
) {
    var collapsed by rememberSaveable { mutableStateOf(false) }
    var lastSeenStartedAt by rememberSaveable { mutableLongStateOf(0L) }

    if (tasks.isEmpty()) return

    val newestStartedAt = tasks.maxOfOrNull { it.startedAt } ?: 0L
    LaunchedEffect(newestStartedAt) {
        if (newestStartedAt > lastSeenStartedAt) {
            collapsed = false
            lastSeenStartedAt = newestStartedAt
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.screenPadding, vertical = Spacing.itemGap)
    ) {
        if (collapsed) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .size(44.dp)
                    .clickable { collapsed = false },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                tonalElevation = Spacing.xs
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = tasks.size.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center
                    )
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
                                    OutlinedButton(onClick = { collapsed = true }) {
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
    }
}
