package opensource.cached_dupe_scanner.ui.components

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

fun formatLoadProgressText(current: Int, loaded: Int, total: Int): String? {
    if (total <= 0) return null
    val safeLoaded = loaded.coerceAtMost(total).coerceAtLeast(1)
    val safeCurrent = current.coerceAtLeast(1).coerceAtMost(safeLoaded)
    val currentPercent = ((safeCurrent.toDouble() / safeLoaded.toDouble()) * 100).toInt()
    val loadedPercent = ((safeLoaded.toDouble() / total.toDouble()) * 100).toInt()
    return "$safeCurrent/$safeLoaded/$total (${currentPercent}%/${loadedPercent}%)"
}

@Composable
fun BoxScope.TopRightLoadIndicator(
    text: String?,
    modifier: Modifier = Modifier
) {
    if (text == null) return
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .align(Alignment.TopEnd)
            .padding(end = ScrollbarDefaults.ThumbWidth + 12.dp, top = 12.dp)
    )
}
