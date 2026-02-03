package opensource.cached_dupe_scanner.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
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
        if (!canPostNotifications()) return
        ensureChannel()
        val content = buildScanNotificationContent(phase, scanned, total, targetPath, currentPath)
        val builder = baseBuilder()
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentTitle(content.title)
            .setContentText(content.text)
            .setSubText(content.subText)
            .setPriority(NotificationCompat.PRIORITY_LOW)
        if (total == null || total <= 0) {
            builder.setProgress(0, 0, true)
        } else {
            builder.setProgress(total, scanned.coerceAtMost(total), false)
        }
        notificationManager.notify(NOTIFICATION_ID, builder.build())
    }

    fun showCompleted(result: ScanResult) {
        if (!canPostNotifications()) return
        ensureChannel()
        val builder = baseBuilder()
            .setOngoing(false)
            .setAutoCancel(true)
            .setContentTitle("Scan complete")
            .setContentText("Files: ${result.files.size} • Groups: ${result.duplicateGroups.size}")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setProgress(0, 0, false)
        notificationManager.notify(NOTIFICATION_ID, builder.build())
    }

    fun showCancelled() {
        if (!canPostNotifications()) return
        ensureChannel()
        val builder = baseBuilder()
            .setOngoing(false)
            .setAutoCancel(true)
            .setContentTitle("Scan cancelled")
            .setContentText("The scan was stopped before completion.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setProgress(0, 0, false)
        notificationManager.notify(NOTIFICATION_ID, builder.build())
    }

    fun clear() {
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
            "Scan progress",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows progress while scans are running"
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

    companion object {
        private const val CHANNEL_ID = "scan_progress"
        private const val NOTIFICATION_ID = 1001
    }
}
