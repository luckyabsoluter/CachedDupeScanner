package opensource.cached_dupe_scanner.ui.home

import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import opensource.cached_dupe_scanner.ui.components.AppTopBar
import opensource.cached_dupe_scanner.ui.components.Spacing

@Composable
fun PermissionScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current

    Column(modifier = modifier.padding(Spacing.screenPadding)) {
        AppTopBar(title = "Permission", onBack = onBack)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (Environment.isExternalStorageManager()) {
                "All-files access: granted"
            } else {
                "All-files access: not granted"
            },
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = {
                val uri = Uri.parse("package:${context.packageName}")
                val intent = Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, uri)
                context.startActivity(intent)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Grant all-files access")
        }
        Spacer(modifier = Modifier.height(12.dp))
    }
}
