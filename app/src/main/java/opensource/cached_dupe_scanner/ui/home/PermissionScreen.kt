package opensource.cached_dupe_scanner.ui.home

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import opensource.cached_dupe_scanner.ui.components.AppTopBar
import opensource.cached_dupe_scanner.ui.components.ScreenScrollColumn

@Composable
fun PermissionScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val allFilesAccessGranted = hasAllFilesAccess()
    val notificationsGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    } else {
        NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    ScreenScrollColumn(modifier = modifier) {
        item {
            AppTopBar(title = "Permission", onBack = onBack)
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
        }

        item {
            Text(
                text = allFilesAccessStatusText(
                    sdkInt = Build.VERSION.SDK_INT,
                    hasAccess = allFilesAccessGranted
                ),
                style = MaterialTheme.typography.bodySmall
            )
        }

        item {
            Spacer(modifier = Modifier.height(12.dp))
        }

        item {
            Button(
                onClick = {
                    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        val uri = Uri.parse("package:${context.packageName}")
                        Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, uri)
                    } else {
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                    }
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Grant all-files access")
            }
        }

        item {
            Spacer(modifier = Modifier.height(12.dp))
        }

        item {
            Text(
                text = "Notifications: ${if (notificationsGranted) "enabled" else "disabled"}",
                style = MaterialTheme.typography.bodySmall
            )
        }

        item {
            Spacer(modifier = Modifier.height(12.dp))
        }

        item {
            Button(
                onClick = {
                    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        }
                    } else {
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                    }
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Enable notifications")
            }
        }

        item {
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

internal fun hasAllFilesAccess(
    sdkInt: Int = Build.VERSION.SDK_INT,
    isExternalStorageManager: () -> Boolean = {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()
    }
): Boolean {
    return if (sdkInt >= Build.VERSION_CODES.R) {
        isExternalStorageManager()
    } else {
        true
    }
}

internal fun allFilesAccessStatusText(
    sdkInt: Int = Build.VERSION.SDK_INT,
    hasAccess: Boolean = hasAllFilesAccess(sdkInt)
): String {
    return if (sdkInt >= Build.VERSION_CODES.R) {
        if (hasAccess) {
            "All-files access: granted"
        } else {
            "All-files access: not granted"
        }
    } else {
        "All-files access: not required"
    }
}
