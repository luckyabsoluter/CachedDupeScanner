package opensource.cached_dupe_scanner.ui.components

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

internal data class HeapMemorySnapshot(
    val allocatedBytes: Long,
    val maxBytes: Long
)

internal fun snapshotHeapMemory(runtime: Runtime = Runtime.getRuntime()): HeapMemorySnapshot {
    return HeapMemorySnapshot(
        allocatedBytes = runtime.totalMemory(),
        maxBytes = runtime.maxMemory()
    )
}

internal fun memoryOverlayText(snapshot: HeapMemorySnapshot): String {
    val allocatedMb = snapshot.allocatedBytes / BYTES_PER_MB
    val maxMb = snapshot.maxBytes / BYTES_PER_MB
    return "RAM ${allocatedMb}/${maxMb} MB"
}

@Composable
fun BoxScope.MemoryUsageOverlay(
    modifier: Modifier = Modifier
) {
    val snapshot by produceState(initialValue = snapshotHeapMemory()) {
        while (true) {
            value = snapshotHeapMemory()
            delay(MEMORY_OVERLAY_REFRESH_MS)
        }
    }

    Text(
        text = memoryOverlayText(snapshot),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .align(Alignment.TopEnd)
            .padding(end = ScrollbarDefaults.ThumbWidth + 12.dp, top = 30.dp)
    )
}

private const val BYTES_PER_MB = 1024L * 1024L
private const val MEMORY_OVERLAY_REFRESH_MS = 1000L
