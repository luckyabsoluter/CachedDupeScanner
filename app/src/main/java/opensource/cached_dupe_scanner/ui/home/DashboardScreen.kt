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
import opensource.cached_dupe_scanner.ui.components.ScreenScrollColumn

@Composable
fun DashboardScreen(
    onOpenPermission: () -> Unit,
    onOpenTargets: () -> Unit,
    onOpenScanCommand: () -> Unit,
    onOpenResults: () -> Unit,
    onOpenFiles: () -> Unit,
    onOpenTrash: () -> Unit,
    onOpenDbManagement: () -> Unit,
    onOpenSimilaritySettings: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenReports: () -> Unit,
    onOpenAbout: () -> Unit,
    modifier: Modifier = Modifier
) {
    ScreenScrollColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            AppTopBar(title = "CachedDupeScanner")
        }

        item {
            DashboardCard(
                title = "Permission",
                description = "Grant all-files access for scanning any folder.",
                actionLabel = "Open permission",
                onAction = onOpenPermission
            )
        }

        item {
            DashboardCard(
                title = "Scan target",
                description = "Manage target folders for scanning.",
                actionLabel = "Open targets",
                onAction = onOpenTargets
            )
        }

        item {
            DashboardCard(
                title = "Scan command",
                description = "Run a scan for a selected target.",
                actionLabel = "Open scan",
                onAction = onOpenScanCommand
            )
        }

        item {
            DashboardCard(
                title = "Scan results",
                description = "View duplicates.",
                actionLabel = "Open results",
                onAction = onOpenResults
            )
        }

        item {
            DashboardCard(
                title = "File browser",
                description = "Browse cached files with sort and actions.",
                actionLabel = "Open files",
                onAction = onOpenFiles
            )
        }

        item {
            DashboardCard(
                title = "Trash",
                description = "Review and restore files moved to trash.",
                actionLabel = "Open trash",
                onAction = onOpenTrash
            )
        }

        item {
            DashboardCard(
                title = "DB management",
                description = "Validate cached entries and rehash when needed.",
                actionLabel = "Open DB manager",
                onAction = onOpenDbManagement
            )
        }

        item {
            DashboardCard(
                title = "Similarity settings",
                description = "Manage similarity settings and maintained duplicate-candidate clusters.",
                actionLabel = "Open similarity",
                onAction = onOpenSimilaritySettings
            )
        }

        item {
            DashboardCard(
                title = "Settings",
                description = "Configure scan behavior.",
                actionLabel = "Open settings",
                onAction = onOpenSettings
            )
        }

        item {
            DashboardCard(
                title = "Scan reports",
                description = "Review past scan reports.",
                actionLabel = "Open reports",
                onAction = onOpenReports
            )
        }

        item {
            DashboardCard(
                title = "About",
                description = "Project summary and links.",
                actionLabel = "Open about",
                onAction = onOpenAbout
            )
        }
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
