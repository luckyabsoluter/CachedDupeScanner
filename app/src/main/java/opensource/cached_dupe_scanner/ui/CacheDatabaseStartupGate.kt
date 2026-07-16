package opensource.cached_dupe_scanner.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import opensource.cached_dupe_scanner.cache.CacheDatabaseStartupPlan

@Composable
internal fun <T : Any> cacheDatabaseStartupGate(
    inspect: suspend () -> CacheDatabaseStartupPlan,
    openDatabase: suspend (CacheDatabaseStartupPlan, (String) -> Unit) -> T,
    onClose: () -> Unit
): T? {
    var plan by remember { mutableStateOf<CacheDatabaseStartupPlan?>(null) }
    var checking by remember { mutableStateOf(true) }
    var opening by remember { mutableStateOf(false) }
    var database by remember { mutableStateOf<T?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }
    var openRequest by remember { mutableIntStateOf(0) }
    val progress = remember { MutableStateFlow("Preparing database") }
    val progressText by progress.collectAsState()

    LaunchedEffect(Unit) {
        try {
            val inspected = withContext(Dispatchers.IO) { inspect() }
            plan = inspected
            checking = false
            when (inspected) {
                is CacheDatabaseStartupPlan.OpenCurrent -> openRequest += 1
                is CacheDatabaseStartupPlan.UpgradeRequired -> Unit
                is CacheDatabaseStartupPlan.UnsupportedVersion -> {
                    failure = "Database version ${inspected.version} is not supported."
                }
            }
        } catch (_: Exception) {
            checking = false
            failure = "The database could not be checked."
        }
    }

    LaunchedEffect(openRequest) {
        if (openRequest == 0) return@LaunchedEffect
        val currentPlan = plan ?: return@LaunchedEffect
        opening = true
        failure = null
        try {
            database = withContext(Dispatchers.IO) {
                openDatabase(currentPlan) { stage ->
                    progress.value = stage
                }
            }
        } catch (_: Exception) {
            failure = if (currentPlan is CacheDatabaseStartupPlan.UpgradeRequired) {
                "The database upgrade failed. Existing data was not replaced."
            } else {
                "The database could not be opened."
            }
        } finally {
            opening = false
        }
    }

    database?.let { return it }
    BackHandler(enabled = true) {}
    CacheDatabaseStartupScreen(
        plan = plan,
        checking = checking,
        opening = opening,
        progressText = progressText,
        failure = failure,
        onUpgrade = { openRequest += 1 },
        onClose = onClose
    )
    return null
}

@Composable
private fun CacheDatabaseStartupScreen(
    plan: CacheDatabaseStartupPlan?,
    checking: Boolean,
    opening: Boolean,
    progressText: String,
    failure: String?,
    onUpgrade: () -> Unit,
    onClose: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 480.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val upgrade = plan as? CacheDatabaseStartupPlan.UpgradeRequired
            val title = when {
                failure != null -> "Database upgrade failed"
                checking -> "Checking database"
                opening -> "Upgrading database"
                upgrade != null -> "Database upgrade required"
                else -> "Opening database"
            }
            Text(text = title, style = MaterialTheme.typography.headlineSmall)
            upgrade?.let { required ->
                Text(
                    text = "Version ${required.fromVersion} to ${required.toVersion}",
                    style = MaterialTheme.typography.titleMedium
                )
            }
            when {
                failure != null -> Text(
                    text = failure,
                    color = MaterialTheme.colorScheme.error
                )
                checking -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                opening -> {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text(
                        text = progressText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                upgrade != null -> Text(
                    text = "Existing scan and similarity results must be upgraded before the app can be used.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            when {
                failure != null -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (
                        plan is CacheDatabaseStartupPlan.OpenCurrent ||
                        plan is CacheDatabaseStartupPlan.UpgradeRequired
                    ) {
                        Button(onClick = onUpgrade) {
                            Icon(imageVector = Icons.Outlined.Storage, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Retry")
                        }
                    }
                    TextButton(onClick = onClose) {
                        Icon(imageVector = Icons.Outlined.Close, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Close app")
                    }
                }
                upgrade != null && !opening -> Button(onClick = onUpgrade) {
                    Icon(imageVector = Icons.Outlined.Storage, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Upgrade database")
                }
            }
        }
    }
}
