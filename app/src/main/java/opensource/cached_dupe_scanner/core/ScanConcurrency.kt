package opensource.cached_dupe_scanner.core

const val MIN_SCAN_WORKER_COUNT = 1
const val MAX_SCAN_WORKER_COUNT = 32

fun defaultScanWorkerCount(
    availableProcessors: Int = Runtime.getRuntime().availableProcessors()
): Int {
    return availableProcessors.coerceIn(MIN_SCAN_WORKER_COUNT, DEFAULT_SCAN_WORKER_COUNT_CAP)
}

fun sanitizeScanWorkerCount(value: Int): Int {
    return value.coerceIn(MIN_SCAN_WORKER_COUNT, MAX_SCAN_WORKER_COUNT)
}

private const val DEFAULT_SCAN_WORKER_COUNT_CAP = 4
