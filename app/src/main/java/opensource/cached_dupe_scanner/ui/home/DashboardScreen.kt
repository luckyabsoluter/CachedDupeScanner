package opensource.cached_dupe_scanner.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import opensource.cached_dupe_scanner.ui.components.AppTopBar
import opensource.cached_dupe_scanner.ui.components.Spacing

@Composable
fun DashboardScreen(
    onOpenPermission: () -> Unit,
    onOpenTargets: () -> Unit,
    onOpenScanCommand: () -> Unit,
    onOpenResults: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .padding(Spacing.screenPadding)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AppTopBar(title = "CachedDupeScanner")

        DashboardCard(
            title = "Permission",
            description = "Grant all-files access for scanning any folder.",
            actionLabel = "Open permission",
            onAction = onOpenPermission
        )

        DashboardCard(
            title = "Scan target",
            description = "Manage target folders for scanning.",
            actionLabel = "Open targets",
            onAction = onOpenTargets
        )

        DashboardCard(
            title = "Scan command",
            description = "Run a scan for a selected target.",
            actionLabel = "Open scan",
            onAction = onOpenScanCommand
        )

        DashboardCard(
            title = "Scan results",
            description = "View duplicates and export results.",
            actionLabel = "Open results",
            onAction = onOpenResults
        )

        DashboardCard(
            title = "Settings",
            description = "Configure scan behavior.",
            actionLabel = "Open settings",
            onAction = onOpenSettings
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
