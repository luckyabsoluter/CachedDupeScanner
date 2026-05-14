package opensource.cached_dupe_scanner.notifications

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import opensource.cached_dupe_scanner.tasks.TaskSnapshot

class ScanForegroundService : Service() {
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW -> {
                ensureTaskNotificationChannel(applicationContext)
                val notification = buildTaskProgressNotification(
                    context = applicationContext,
                    title = intent.getStringExtra(EXTRA_TITLE).orEmpty(),
                    text = intent.getStringExtra(EXTRA_TEXT).orEmpty(),
                    subText = intent.getStringExtra(EXTRA_SUB_TEXT),
                    progress = intent.getIntExtra(EXTRA_PROGRESS, NO_PROGRESS).takeIf { it != NO_PROGRESS },
                    total = intent.getIntExtra(EXTRA_TOTAL, NO_PROGRESS).takeIf { it != NO_PROGRESS },
                    indeterminate = intent.getBooleanExtra(EXTRA_INDETERMINATE, true)
                )
                startForeground(notificationIdFor(opensource.cached_dupe_scanner.tasks.TaskArea.Scan), notification)
            }
            ACTION_STOP -> {
                stopForegroundCompat()
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    companion object {
        private const val ACTION_SHOW = "opensource.cached_dupe_scanner.notifications.SHOW_SCAN_FOREGROUND"
        private const val ACTION_STOP = "opensource.cached_dupe_scanner.notifications.STOP_SCAN_FOREGROUND"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_TEXT = "text"
        private const val EXTRA_SUB_TEXT = "subText"
        private const val EXTRA_PROGRESS = "progress"
        private const val EXTRA_TOTAL = "total"
        private const val EXTRA_INDETERMINATE = "indeterminate"
        private const val NO_PROGRESS = -1

        fun show(context: Context, snapshot: TaskSnapshot) {
            val content = buildTaskNotificationContent(snapshot)
            val intent = Intent(context, ScanForegroundService::class.java).apply {
                action = ACTION_SHOW
                putExtra(EXTRA_TITLE, content.title)
                putExtra(EXTRA_TEXT, content.text)
                putExtra(EXTRA_SUB_TEXT, content.subText)
                putExtra(EXTRA_PROGRESS, snapshot.bubbleProcessed ?: NO_PROGRESS)
                putExtra(EXTRA_TOTAL, snapshot.bubbleTotal ?: NO_PROGRESS)
                putExtra(EXTRA_INDETERMINATE, snapshot.bubbleIndeterminate)
            }
            ContextCompat.startForegroundService(context.applicationContext, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, ScanForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.applicationContext.startService(intent)
        }
    }
}
