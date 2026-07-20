package opensource.cached_dupe_scanner.tasks

import java.util.Locale
import kotlin.math.roundToLong

data class ProgressMetrics(
    val itemsPerSecond: Double?,
    val elapsedMillis: Long,
    val remainingMillis: Long?
)

fun calculateProgressMetrics(
    processed: Long?,
    total: Long?,
    startedAtMillis: Long,
    nowMillis: Long
): ProgressMetrics {
    val elapsedMillis = (nowMillis - startedAtMillis).coerceAtLeast(0L)
    val safeProcessed = processed?.coerceAtLeast(0L)
    val safeTotal = total?.coerceAtLeast(0L)
    val itemsPerSecond = if (
        safeProcessed != null && safeProcessed > 0L && elapsedMillis > 0L
    ) {
        (safeProcessed.toDouble() * MILLIS_PER_SECOND) / elapsedMillis.toDouble()
    } else {
        null
    }
    val remainingItems = if (safeProcessed != null && safeTotal != null) {
        (safeTotal - safeProcessed).coerceAtLeast(0L)
    } else {
        null
    }
    val remainingMillis = when {
        remainingItems == 0L -> 0L
        remainingItems == null || itemsPerSecond == null || itemsPerSecond <= 0.0 -> null
        else -> ((remainingItems.toDouble() / itemsPerSecond) * MILLIS_PER_SECOND)
            .takeIf(Double::isFinite)
            ?.coerceAtMost(Long.MAX_VALUE.toDouble())
            ?.roundToLong()
    }
    return ProgressMetrics(
        itemsPerSecond = itemsPerSecond,
        elapsedMillis = elapsedMillis,
        remainingMillis = remainingMillis
    )
}

fun TaskSnapshot.progressMetrics(nowMillis: Long): ProgressMetrics {
    return calculateProgressMetrics(
        processed = processed?.toLong(),
        total = total?.toLong(),
        startedAtMillis = startedAt,
        nowMillis = nowMillis
    )
}

fun formatProgressMetrics(metrics: ProgressMetrics): String {
    return "Speed: ${formatProcessingSpeed(metrics.itemsPerSecond)} | " +
        "Elapsed: ${formatProgressDuration(metrics.elapsedMillis)} | " +
        "Remaining: ${formatProgressDuration(metrics.remainingMillis)}"
}

fun formatProcessingSpeed(itemsPerSecond: Double?): String {
    if (itemsPerSecond == null || !itemsPerSecond.isFinite() || itemsPerSecond < 0.0) return "--"
    val value = when {
        itemsPerSecond >= 100.0 -> String.format(Locale.US, "%.0f", itemsPerSecond)
        itemsPerSecond >= 10.0 -> String.format(Locale.US, "%.1f", itemsPerSecond)
        else -> String.format(Locale.US, "%.2f", itemsPerSecond)
    }
    return "$value items/s"
}

fun formatProgressDuration(durationMillis: Long?): String {
    if (durationMillis == null) return "--"
    val totalSeconds = durationMillis.coerceAtLeast(0L) / MILLIS_PER_SECOND
    val hours = totalSeconds / SECONDS_PER_HOUR
    val minutes = (totalSeconds % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE
    val seconds = totalSeconds % SECONDS_PER_MINUTE
    return when {
        hours > 0L -> "${hours}h ${minutes.toString().padStart(2, '0')}m " +
            "${seconds.toString().padStart(2, '0')}s"
        minutes > 0L -> "${minutes}m ${seconds.toString().padStart(2, '0')}s"
        else -> "${seconds}s"
    }
}

private const val MILLIS_PER_SECOND = 1_000L
private const val SECONDS_PER_MINUTE = 60L
private const val SECONDS_PER_HOUR = SECONDS_PER_MINUTE * 60L
