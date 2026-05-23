package opensource.cached_dupe_scanner.ui.home

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import opensource.cached_dupe_scanner.ui.components.AppTopBar
import opensource.cached_dupe_scanner.ui.components.ScreenScrollColumn

@Composable
fun AboutScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    ScreenScrollColumn(modifier = modifier) {
        item {
            AppTopBar(title = "About", onBack = onBack)
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
        }

        item {
            Text(
                text = "CachedDupeScanner",
                style = MaterialTheme.typography.titleMedium
            )
        }

        item {
            Spacer(modifier = Modifier.height(6.dp))
        }

        item {
            Text(
                text = "Fast duplicate scans with a persistent cache.",
                style = MaterialTheme.typography.bodySmall
            )
        }

        item {
            Spacer(modifier = Modifier.height(12.dp))
        }

        item {
            Text(
                text = "https://github.com/luckyabsoluter/CachedDupeScanner",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
