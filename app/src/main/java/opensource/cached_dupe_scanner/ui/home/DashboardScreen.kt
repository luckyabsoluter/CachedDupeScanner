package opensource.cached_dupe_scanner.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import opensource.cached_dupe_scanner.ui.components.AppTopBar

@Composable
fun DashboardScreen(
    onOpenPermission: () -> Unit,
    onOpenScan: () -> Unit,
    onOpenResults: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AppTopBar(title = "CachedDupeScanner")

        DashboardCard(
            title = "Permission",
            description = "Grant all-files access for scanning any folder.",
            actionLabel = "Open permission",
            onAction = onOpenPermission
        )

        DashboardCard(
            title = "Scan target",
            description = "Choose the folder path and start a scan.",
            actionLabel = "Open scan",
            onAction = onOpenScan
        )

        DashboardCard(
            title = "Scan results",
            description = "View duplicates and export results.",
            actionLabel = "Open results",
            onAction = onOpenResults
        )
    }
}

@Composable
private fun DashboardCard(
    title: String,
    description: String,
    actionLabel: String,
    onAction: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = description, style = MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.height(10.dp))
            Button(onClick = onAction, modifier = Modifier.fillMaxWidth()) {
                Text(actionLabel)
            }
        }
    }
}
