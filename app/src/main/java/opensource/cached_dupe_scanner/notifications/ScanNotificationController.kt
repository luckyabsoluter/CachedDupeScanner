package opensource.cached_dupe_scanner.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import opensource.cached_dupe_scanner.R
import opensource.cached_dupe_scanner.core.ScanResult
import opensource.cached_dupe_scanner.engine.ScanPhase

class ScanNotificationController(context: Context) {
    private val appContext = context.applicationContext
    private val notificationManager = NotificationManagerCompat.from(appContext)

    fun showStarted(targetPath: String?) {
        beginNotificationSession()
        showProgress(
            phase = ScanPhase.Collecting,
            scanned = 0,
            total = null,
            targetPath = targetPath,
            currentPath = null
        )
    }

    fun showProgress(
        phase: ScanPhase,
        scanned: Int,
        total: Int?,
        targetPath: String?,
        currentPath: String?
    ) {
        val now = SystemClock.elapsedRealtime()
        val timeSinceLast = now - lastProgressUpdateAt
        if (timeSinceLast < PROGRESS_UPDATE_THROTTLE_MS) {
            pendingProgress = PendingProgress(phase, scanned, total, targetPath, currentPath)
            return
        }
        if (!notificationPermissionGranted) return
        if (!channelEnsured) {
            ensureChannel()
            channelEnsured = true
        }
        val minBatch = resolveMinBatch(total)
        val last = lastProgressSnapshot
        if (last != null) {
            val samePhase = last.phase == phase
            val sameTarget = last.targetPath == targetPath
            val delta = scanned - last.scanned
            if (samePhase && sameTarget && delta in 0 until minBatch) {
                pendingProgress = PendingProgress(phase, scanned, total, targetPath, currentPath)
                return
            }
        }
        lastProgressUpdateAt = now
        val pending = pendingProgress
        pendingProgress = null
        val effective = pending ?: PendingProgress(phase, scanned, total, targetPath, currentPath)
        lastProgressSnapshot = effective
        val content = buildScanNotificationContent(
            effective.phase,
            effective.scanned,
            effective.total,
            effective.targetPath,
            effective.currentPath
        )
        val builder = baseBuilder()
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentTitle(content.title)
            .setContentText(content.text)
            .setSubText(content.subText)
            .setPriority(NotificationCompat.PRIORITY_LOW)
        if (effective.total == null || effective.total <= 0) {
            builder.setProgress(0, 0, true)
        } else {
            builder.setProgress(effective.total, effective.scanned.coerceAtMost(effective.total), false)
        }
        notificationManager.notify(NOTIFICATION_ID, builder.build())
    }

    fun showCompleted(result: ScanResult) {
        if (!notificationPermissionGranted) return
        if (!channelEnsured) {
            ensureChannel()
            channelEnsured = true
        }
        resetScanProgressState()
        showTerminalNotification(
            title = "Scan complete",
            text = "Files: ${result.files.size} • Groups: ${result.duplicateGroups.size}"
        )
    }

    fun showCancelled() {
        if (!notificationPermissionGranted) return
        if (!channelEnsured) {
            ensureChannel()
            channelEnsured = true
        }
        resetScanProgressState()
        showTerminalNotification(
            title = "Scan cancelled",
            text = "The scan was stopped before completion."
        )
    }

    fun showTaskStarted(title: String, text: String, subText: String? = null) {
        beginNotificationSession()
        showTaskProgress(
            title = title,
            text = text,
            subText = subText,
            progress = null,
            total = null
        )
    }

    fun showTaskProgress(
        title: String,
        text: String,
        subText: String? = null,
        progress: Int? = null,
        total: Int? = null
    ) {
        val now = SystemClock.elapsedRealtime()
        val timeSinceLast = now - lastTaskUpdateAt
        if (timeSinceLast < PROGRESS_UPDATE_THROTTLE_MS) {
            pendingTaskProgress = PendingTaskProgress(title, text, subText, progress, total)
            return
        }
        if (!notificationPermissionGranted) return
        if (!channelEnsured) {
            ensureChannel()
            channelEnsured = true
        }
        lastTaskUpdateAt = now
        val pending = pendingTaskProgress
        pendingTaskProgress = null
        val effective = pending ?: PendingTaskProgress(title, text, subText, progress, total)
        notificationManager.notify(
            NOTIFICATION_ID,
            buildProgressNotification(
                title = effective.title,
                text = effective.text,
                subText = effective.subText,
                progress = effective.progress,
                total = effective.total
            )
        )
    }

    fun showTaskCompleted(title: String, text: String, subText: String? = null) {
        if (!notificationPermissionGranted) return
        if (!channelEnsured) {
            ensureChannel()
            channelEnsured = true
        }
        resetTaskProgressState()
        showTerminalNotification(title = title, text = text, subText = subText)
    }

    fun showTaskFailed(title: String, text: String, subText: String? = null) {
        if (!notificationPermissionGranted) return
        if (!channelEnsured) {
            ensureChannel()
            channelEnsured = true
        }
        resetTaskProgressState()
        showTerminalNotification(title = title, text = text, subText = subText)
    }

    fun clear() {
        resetScanProgressState()
        resetTaskProgressState()
        notificationManager.cancel(NOTIFICATION_ID)
    }

    private fun baseBuilder(): NotificationCompat.Builder {
        return NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setShowWhen(false)
        }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Background tasks",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows progress while scans and DB tasks are running"
        }
        val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    private fun canPostNotifications(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun beginNotificationSession() {
        notificationPermissionGranted = canPostNotifications()
        channelEnsured = false
    }

    private fun buildProgressNotification(
        title: String,
        text: String,
        subText: String?,
        progress: Int?,
        total: Int?
    ) = baseBuilder()
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setContentTitle(title)
        .setContentText(text)
        .setSubText(subText)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setProgress(
            if (total != null && total > 0) total else 0,
            if (total != null && total > 0) (progress ?: 0).coerceAtMost(total) else 0,
            total == null || total <= 0
        )
        .build()

    private fun showTerminalNotification(
        title: String,
        text: String,
        subText: String? = null
    ) {
        val builder = baseBuilder()
            .setOngoing(false)
            .setAutoCancel(true)
            .setContentTitle(title)
            .setContentText(text)
            .setSubText(subText)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setProgress(0, 0, false)
        notificationManager.notify(NOTIFICATION_ID, builder.build())
    }

    private fun resetScanProgressState() {
        pendingProgress = null
        lastProgressSnapshot = null
        lastProgressUpdateAt = 0L
    }

    private fun resetTaskProgressState() {
        pendingTaskProgress = null
        lastTaskUpdateAt = 0L
    }

    companion object {
        private const val CHANNEL_ID = "scan_progress"
        private const val NOTIFICATION_ID = 1001
        private const val PROGRESS_UPDATE_THROTTLE_MS = 1000L
    }

    private data class PendingProgress(
        val phase: ScanPhase,
        val scanned: Int,
        val total: Int?,
        val targetPath: String?,
        val currentPath: String?
    )

    private data class PendingTaskProgress(
        val title: String,
        val text: String,
        val subText: String?,
        val progress: Int?,
        val total: Int?
    )

    private var lastProgressUpdateAt: Long = 0L
    private var pendingProgress: PendingProgress? = null
    private var lastProgressSnapshot: PendingProgress? = null
    private var lastTaskUpdateAt: Long = 0L
    private var pendingTaskProgress: PendingTaskProgress? = null
    @Volatile private var notificationPermissionGranted: Boolean = true
    @Volatile private var channelEnsured: Boolean = false

    private fun resolveMinBatch(@Suppress("UNUSED_PARAMETER") total: Int?): Int {
        return 1
    }
}
