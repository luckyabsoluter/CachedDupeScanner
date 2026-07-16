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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import opensource.cached_dupe_scanner.cache.CacheDatabaseStartupPlan
import opensource.cached_dupe_scanner.cache.CacheDatabaseStartupProgress
import opensource.cached_dupe_scanner.tasks.calculateProgressMetrics
import opensource.cached_dupe_scanner.tasks.formatProgressMetrics
import opensource.cached_dupe_scanner.ui.components.boundedProgressFraction

@Composable
internal fun <T : Any> cacheDatabaseStartupGate(
    inspect: suspend () -> CacheDatabaseStartupPlan,
    openDatabase: suspend (
        CacheDatabaseStartupPlan,
        (CacheDatabaseStartupProgress) -> Unit
    ) -> T,
    onClose: () -> Unit
): T? {
    var plan by remember { mutableStateOf<CacheDatabaseStartupPlan?>(null) }
    var checking by remember { mutableStateOf(true) }
    var opening by remember { mutableStateOf(false) }
    var database by remember { mutableStateOf<T?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }
    var openRequest by remember { mutableIntStateOf(0) }
    var openingStartedAt by remember { mutableLongStateOf(0L) }
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val progress = remember {
        MutableStateFlow(CacheDatabaseStartupProgress(stage = "Preparing database"))
    }
    val progressSnapshot by progress.collectAsState()

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
        openingStartedAt = System.currentTimeMillis()
        nowMillis = openingStartedAt
        progress.value = CacheDatabaseStartupProgress(stage = "Preparing database")
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

    LaunchedEffect(opening, openingStartedAt) {
        while (opening) {
            delay(PROGRESS_METRICS_REFRESH_MILLIS)
            nowMillis = System.currentTimeMillis()
        }
    }

    database?.let { return it }
    BackHandler(enabled = true) {}
    CacheDatabaseStartupScreen(
        plan = plan,
        checking = checking,
        opening = opening,
        progress = progressSnapshot,
        progressMetricsText = formatProgressMetrics(
            calculateProgressMetrics(
                processed = progressSnapshot.processed,
                total = progressSnapshot.total,
                startedAtMillis = openingStartedAt,
                nowMillis = nowMillis
            )
        ),
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
    progress: CacheDatabaseStartupProgress,
    progressMetricsText: String,
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
                    CacheDatabaseStartupProgressIndicator(progress)
                    Text(
                        text = progress.stage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = progressMetricsText,
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

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun CacheDatabaseStartupProgressIndicator(progress: CacheDatabaseStartupProgress) {
    val processed = progress.processed
    val total = progress.total
    if (processed == null || total == null || total <= 0L) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        return
    }
    LinearProgressIndicator(
        progress = { boundedProgressFraction(processed = processed, total = total) },
        modifier = Modifier.fillMaxWidth(),
        gapSize = ProgressIndicatorDefaults.LinearIndicatorTrackGapSize,
        drawStopIndicator = {}
    )
}

private const val PROGRESS_METRICS_REFRESH_MILLIS = 1_000L
