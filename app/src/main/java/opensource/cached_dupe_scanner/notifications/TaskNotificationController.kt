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
import opensource.cached_dupe_scanner.tasks.TaskArea
import opensource.cached_dupe_scanner.tasks.TaskSnapshot
import opensource.cached_dupe_scanner.tasks.TaskTerminalSummary

class TaskNotificationController(context: Context) {
    private val appContext = context.applicationContext
    private val notificationManager = NotificationManagerCompat.from(appContext)
    private val lastUpdateAt = mutableMapOf<TaskArea, Long>()
    private val pendingSnapshots = mutableMapOf<TaskArea, TaskSnapshot>()

    fun showActive(snapshot: TaskSnapshot) {
        val now = SystemClock.elapsedRealtime()
        val timeSinceLast = now - (lastUpdateAt[snapshot.area] ?: 0L)
        if (timeSinceLast < PROGRESS_UPDATE_THROTTLE_MS) {
            pendingSnapshots[snapshot.area] = snapshot
            return
        }
        beginNotificationSession()
        if (!notificationPermissionGranted) return
        ensureChannelIfNeeded()
        lastUpdateAt[snapshot.area] = now
        val effective = pendingSnapshots.remove(snapshot.area) ?: snapshot
        val content = buildTaskNotificationContent(effective)
        notificationManager.notify(
            notificationIdFor(effective.area),
            buildProgressNotification(
                title = content.title,
                text = content.text,
                subText = content.subText,
                progress = effective.processed,
                total = effective.total,
                indeterminate = effective.indeterminate
            )
        )
    }

    fun showTerminal(summary: TaskTerminalSummary) {
        beginNotificationSession()
        if (!notificationPermissionGranted) return
        ensureChannelIfNeeded()
        pendingSnapshots.remove(summary.area)
        lastUpdateAt.remove(summary.area)
        val content = buildTaskTerminalNotificationContent(summary)
        val builder = baseBuilder()
            .setOngoing(false)
            .setAutoCancel(true)
            .setContentTitle(content.title)
            .setContentText(content.text)
            .setSubText(content.subText)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setProgress(0, 0, false)
        notificationManager.notify(notificationIdFor(summary.area), builder.build())
    }

    fun clear(area: TaskArea) {
        pendingSnapshots.remove(area)
        lastUpdateAt.remove(area)
        notificationManager.cancel(notificationIdFor(area))
    }

    private fun ensureChannelIfNeeded() {
        if (channelEnsured) return
        ensureChannel()
        channelEnsured = true
    }

    private fun baseBuilder(): NotificationCompat.Builder {
        return NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setShowWhen(false)
    }

    private fun buildProgressNotification(
        title: String,
        text: String,
        subText: String?,
        progress: Int?,
        total: Int?,
        indeterminate: Boolean
    ) = baseBuilder()
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setContentTitle(title)
        .setContentText(text)
        .setSubText(subText)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setProgress(
            if (!indeterminate && total != null && total > 0) total else 0,
            if (!indeterminate && total != null && total > 0) (progress ?: 0).coerceAtMost(total) else 0,
            indeterminate || total == null || total <= 0
        )
        .build()

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Background tasks",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows progress while scans, DB tasks, and trash tasks are running"
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
    }

    companion object {
        private const val CHANNEL_ID = "task_progress"
        private const val PROGRESS_UPDATE_THROTTLE_MS = 1000L
    }

    @Volatile private var notificationPermissionGranted: Boolean = true
    @Volatile private var channelEnsured: Boolean = false
}
