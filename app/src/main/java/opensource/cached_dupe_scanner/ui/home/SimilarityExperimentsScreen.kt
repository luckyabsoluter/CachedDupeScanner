package opensource.cached_dupe_scanner.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.decode.VideoFrameDecoder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import opensource.cached_dupe_scanner.cache.SimilarityClusterEntity
import opensource.cached_dupe_scanner.cache.SimilarityExperimentRunEntity
import opensource.cached_dupe_scanner.core.DurationToleranceStep
import opensource.cached_dupe_scanner.core.ExactThumbnailHashStep
import opensource.cached_dupe_scanner.core.FileMetadata
import opensource.cached_dupe_scanner.core.SimilarityExperimentSpec
import opensource.cached_dupe_scanner.core.SimilarityExperimentStep
import opensource.cached_dupe_scanner.core.SimilarityMediaScope
import opensource.cached_dupe_scanner.core.durationToleranceMillis
import opensource.cached_dupe_scanner.core.defaultSimilarityExperimentSpecs
import opensource.cached_dupe_scanner.notifications.TaskNotificationController
import opensource.cached_dupe_scanner.storage.SimilarityExperimentProgress
import opensource.cached_dupe_scanner.storage.SimilarityExperimentRepository
import opensource.cached_dupe_scanner.storage.SimilarityExperimentRunRequest
import opensource.cached_dupe_scanner.storage.SimilarityExperimentSummary
import opensource.cached_dupe_scanner.tasks.TaskArea
import opensource.cached_dupe_scanner.tasks.TaskCoordinator
import opensource.cached_dupe_scanner.tasks.TaskKind
import opensource.cached_dupe_scanner.tasks.similarityExperimentCancelledDetail
import opensource.cached_dupe_scanner.tasks.similarityExperimentCompletedDetail
import opensource.cached_dupe_scanner.tasks.similarityExperimentTaskDetail
import opensource.cached_dupe_scanner.tasks.similarityExperimentTaskTitle
import opensource.cached_dupe_scanner.tasks.withLinearProgress
import opensource.cached_dupe_scanner.ui.components.AppTopBar
import opensource.cached_dupe_scanner.ui.components.ScrollbarDefaults
import opensource.cached_dupe_scanner.ui.components.Spacing
import opensource.cached_dupe_scanner.ui.components.VerticalScrollbar
import java.util.concurrent.atomic.AtomicBoolean

private data class SimilarityClusterMembersState(
    val members: List<FileMetadata>,
    val complete: Boolean
)

private enum class SimilarityExperimentPane {
    List,
    Create,
    TemplateDetail,
    RunDetail
}

@Composable
fun SimilarityExperimentsScreen(
    repository: SimilarityExperimentRepository,
    taskCoordinator: TaskCoordinator,
    notificationController: TaskNotificationController,
    keepLoadedThumbnailsInMemory: Boolean,
    thumbnailSizeScale: Float,
    rememberedPreviewCache: MutableMap<String, ImageBitmap>,
    deletedPaths: Set<String>,
    showFullPaths: Boolean,
    onDeleteFile: (suspend (FileMetadata) -> Boolean)?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    experiments: List<SimilarityExperimentSpec> = defaultSimilarityExperimentSpecs()
) {
    val context = LocalContext.current
    var minSizeInput by remember { mutableStateOf("100") }
    var minSizeUnit by remember { mutableStateOf(SimilaritySizeUnit.MB) }
    var mediaScope by remember { mutableStateOf(SimilarityMediaScope.Video) }
    var frameSecondsInput by remember { mutableStateOf("0,1,10") }
    var resizeWidthInput by remember { mutableStateOf("1") }
    var resizeHeightInput by remember { mutableStateOf("1") }
    var quantizationEnabled by remember { mutableStateOf(false) }
    var quantizationInput by remember { mutableStateOf("16") }
    var grayscale by remember { mutableStateOf(false) }
    var durationToleranceInput by remember { mutableStateOf("1") }
    var candidateCountText by remember { mutableStateOf("Loading candidate count...") }
    var runStatusText by remember { mutableStateOf("No experiment running.") }
    val runs = remember { mutableStateListOf<SimilarityExperimentRunEntity>() }
    val clusters = remember { mutableStateListOf<SimilarityClusterEntity>() }
    var selectedTemplateId by remember { mutableStateOf<String?>(null) }
    var selectedRunExperimentId by remember { mutableStateOf<String?>(null) }
    var selectedClusterKey by remember { mutableStateOf<String?>(null) }
    var pane by remember { mutableStateOf(SimilarityExperimentPane.List) }
    var clustersLoading by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val imageLoader = remember {
        ImageLoader.Builder(context)
            .components { add(VideoFrameDecoder.Factory()) }
            .build()
    }
    val loadedClusterMembers = remember { mutableStateMapOf<String, SimilarityClusterMembersState>() }
    val clusterMemberLoadErrors = remember { mutableStateMapOf<String, String>() }
    val normalizedThumbnailScale = thumbnailSizeScale.coerceAtLeast(0f)
    val groupCardThumbnailSizeDp = 72.dp * normalizedThumbnailScale
    val groupDetailPreviewHeightDp = 180.dp * normalizedThumbnailScale
    val minSizeBytes = parsedMinSizeBytes(
        input = minSizeInput,
        unit = minSizeUnit
    )
    val exactStep = parsedExactThumbnailStep(
        frameSecondsInput = frameSecondsInput,
        resizeWidthInput = resizeWidthInput,
        resizeHeightInput = resizeHeightInput,
        quantizationEnabled = quantizationEnabled,
        quantizationInput = quantizationInput,
        grayscale = grayscale
    )
    val durationStep = parsedDurationToleranceStep(durationToleranceInput)
    val selectedTemplate = selectedSimilarityExperimentTemplate(
        experiments = experiments,
        selectedId = selectedTemplateId
    )
    val selectedTemplateExactStep = selectedTemplate?.let(::executableExactThumbnailStep)
    val selectedTemplateDurationStep = selectedTemplate?.let(::executableDurationToleranceStep)
    val selectedRun = selectedRunExperimentId?.let { selectedId ->
        runs.firstOrNull { run -> run.experimentId == selectedId }
    }
    val activeSimilarityTask = taskCoordinator.activeTask(TaskArea.Similarity)
    val displayedRunStatusText = activeSimilarityTask?.detail ?: runStatusText

    fun applyTemplateDefaults(experiment: SimilarityExperimentSpec) {
        selectedTemplateId = experiment.id
        mediaScope = experiment.mediaScope
        val defaultSize = defaultSizeInputForUnit(
            bytes = experiment.defaultMinSizeBytes,
            unit = SimilaritySizeUnit.MB
        )
        minSizeUnit = defaultSize.unit
        minSizeInput = defaultSize.input
        executableDurationToleranceStep(experiment)?.let { duration ->
            durationToleranceInput = duration.toleranceSeconds.toString()
        }
        val exact = executableExactThumbnailStep(experiment) ?: return
        frameSecondsInput = exact.frameSeconds.joinToString(",")
        resizeWidthInput = exact.resizeWidthPx.toString()
        resizeHeightInput = exact.resizeHeightPx.toString()
        quantizationEnabled = exact.quantizationLevels != null
        quantizationInput = exact.quantizationLevels?.toString() ?: "16"
        grayscale = exact.grayscale
    }

    fun refreshStoredResults(preferredRunExperimentId: String? = selectedRunExperimentId) {
        clustersLoading = preferredRunExperimentId != null
        scope.launch {
            val nextRuns = withContext(Dispatchers.IO) { repository.listRuns() }
            val nextSelectedRun = preferredRunExperimentId?.let { selectedId ->
                nextRuns.firstOrNull { run -> run.experimentId == selectedId }
            }
            val nextClusters = nextSelectedRun?.let { run ->
                withContext(Dispatchers.IO) { repository.listClusters(run.experimentId) }
            }.orEmpty()
            runs.clear()
            runs.addAll(nextRuns)
            selectedRunExperimentId = nextSelectedRun?.experimentId
            clusters.clear()
            clusters.addAll(nextClusters)
            clustersLoading = false
            if (selectedClusterKey != null && nextClusters.none { clusterStableKey(it) == selectedClusterKey }) {
                selectedClusterKey = null
            }
        }
    }

    fun openListPane() {
        pane = SimilarityExperimentPane.List
        selectedRunExperimentId = null
        selectedClusterKey = null
        clusters.clear()
        refreshStoredResults(null)
    }

    fun openCreatePane(clearTemplateSelection: Boolean = true) {
        pane = SimilarityExperimentPane.Create
        if (clearTemplateSelection) {
            selectedTemplateId = null
        }
        selectedRunExperimentId = null
        selectedClusterKey = null
        clusters.clear()
    }

    fun openTemplateDetailPane(experiment: SimilarityExperimentSpec) {
        applyTemplateDefaults(experiment)
        pane = SimilarityExperimentPane.TemplateDetail
        selectedRunExperimentId = null
        selectedClusterKey = null
        clusters.clear()
    }

    fun openRunPane(run: SimilarityExperimentRunEntity) {
        pane = SimilarityExperimentPane.RunDetail
        selectedRunExperimentId = run.experimentId
        selectedClusterKey = null
        clusters.clear()
        refreshStoredResults(run.experimentId)
    }

    LaunchedEffect(Unit) {
        refreshStoredResults(null)
    }

    LaunchedEffect(mediaScope, minSizeBytes) {
        candidateCountText = runCatching {
            withContext(Dispatchers.IO) {
                repository.countCandidates(
                    mediaScope = mediaScope,
                    minSizeBytes = minSizeBytes
                )
            }
        }.fold(
            onSuccess = { count -> "$count cached ${mediaScope.name.lowercase()} files match the current size filter." },
            onFailure = { "Candidate count is unavailable." }
        )
    }

    val selectedCluster = selectedClusterKey?.let { key ->
        clusters.firstOrNull { cluster -> clusterStableKey(cluster) == key }
    }
    if (selectedCluster != null) {
        SimilarityClusterDetailScreen(
            repository = repository,
            cluster = selectedCluster,
            deletedPaths = deletedPaths,
            imageLoader = imageLoader,
            keepLoadedThumbnailsInMemory = keepLoadedThumbnailsInMemory,
            rememberedPreviewCache = rememberedPreviewCache,
            previewHeight = groupDetailPreviewHeightDp,
            onDeleteFile = onDeleteFile,
            loadedClusterMembers = loadedClusterMembers,
            clusterMemberLoadErrors = clusterMemberLoadErrors,
            onBack = { selectedClusterKey = null },
            modifier = modifier
        )
        return
    }

    BackHandler(enabled = pane != SimilarityExperimentPane.List) {
        when (pane) {
            SimilarityExperimentPane.TemplateDetail -> openCreatePane(clearTemplateSelection = false)
            SimilarityExperimentPane.Create,
            SimilarityExperimentPane.RunDetail -> openListPane()
            SimilarityExperimentPane.List -> Unit
        }
    }

    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .padding(Spacing.screenPadding)
                .padding(end = ScrollbarDefaults.ThumbWidth + 8.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (pane) {
                SimilarityExperimentPane.List -> {
                    AppTopBar(
                        title = "Similarity experiments",
                        onBack = onBack
                    )
                    Button(
                        onClick = { openCreatePane() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("New experiment")
                    }
                    ExperimentRunsListCard(
                        runs = runs,
                        onSelectRun = ::openRunPane
                    )
                }
                SimilarityExperimentPane.Create -> {
                    AppTopBar(
                        title = "New experiment",
                        onBack = ::openListPane
                    )
                    ExperimentTemplatesCard(
                        experiments = experiments,
                        selectedExperimentId = selectedTemplateId,
                        onSelectExperiment = ::openTemplateDetailPane
                    )
                }
                SimilarityExperimentPane.TemplateDetail -> {
                    AppTopBar(
                        title = selectedTemplate?.name ?: "Experiment template",
                        onBack = { openCreatePane(clearTemplateSelection = false) }
                    )
                    if (selectedTemplate != null && selectedTemplateExactStep != null) {
                        ExactThumbnailRunCard(
                            experimentName = selectedTemplate.name,
                            minSizeInput = minSizeInput,
                            onMinSizeInputChange = { minSizeInput = sanitizeNumberDraftInput(it) },
                            minSizeUnit = minSizeUnit,
                            onMinSizeUnitChange = { minSizeUnit = it },
                            mediaScope = mediaScope,
                            onMediaScopeChange = { mediaScope = it },
                            frameSecondsInput = frameSecondsInput,
                            onFrameSecondsInputChange = { frameSecondsInput = sanitizeFrameSecondsInput(it) },
                            resizeWidthInput = resizeWidthInput,
                            onResizeWidthInputChange = { resizeWidthInput = sanitizeNumberDraftInput(it) },
                            resizeHeightInput = resizeHeightInput,
                            onResizeHeightInputChange = { resizeHeightInput = sanitizeNumberDraftInput(it) },
                            quantizationEnabled = quantizationEnabled,
                            onQuantizationEnabledChange = { quantizationEnabled = it },
                            quantizationInput = quantizationInput,
                            onQuantizationInputChange = { quantizationInput = sanitizeNumberDraftInput(it) },
                            grayscale = grayscale,
                            onGrayscaleChange = { grayscale = it },
                            candidateCountText = candidateCountText,
                            runStatusText = displayedRunStatusText,
                            isRunning = activeSimilarityTask != null,
                            onRun = {
                                val step = exactStep
                                if (step.frameSeconds.isEmpty()) {
                                    runStatusText = "Add at least one frame timestamp."
                                    return@ExactThumbnailRunCard
                                }
                                val experiment = exactThumbnailExperimentForRun(
                                    mediaScope = mediaScope,
                                    minSizeBytes = minSizeBytes,
                                    step = step
                                )
                                startSimilarityExperimentTask(
                                    repository = repository,
                                    request = SimilarityExperimentRunRequest(
                                        experiment = experiment,
                                        mediaScope = mediaScope,
                                        minSizeBytes = minSizeBytes,
                                        exactThumbnailStep = step
                                    ),
                                    scope = scope,
                                    taskCoordinator = taskCoordinator,
                                    notificationController = notificationController,
                                    onStatusText = { status -> runStatusText = status },
                                    onRunFinished = { summary ->
                                        selectedRunExperimentId = summary.experimentId
                                        pane = SimilarityExperimentPane.RunDetail
                                        refreshStoredResults(summary.experimentId)
                                    }
                                )
                            },
                            onCancel = {
                                taskCoordinator.requestCancel(TaskArea.Similarity)
                            }
                        )
                    } else if (selectedTemplate != null && selectedTemplateDurationStep != null) {
                        DurationToleranceRunCard(
                            experimentName = selectedTemplate.name,
                            minSizeInput = minSizeInput,
                            onMinSizeInputChange = { minSizeInput = sanitizeNumberDraftInput(it) },
                            minSizeUnit = minSizeUnit,
                            onMinSizeUnitChange = { minSizeUnit = it },
                            toleranceInput = durationToleranceInput,
                            onToleranceInputChange = { durationToleranceInput = sanitizeNumberDraftInput(it) },
                            candidateCountText = candidateCountText,
                            runStatusText = displayedRunStatusText,
                            isRunning = activeSimilarityTask != null,
                            onRun = {
                                val step = durationStep
                                val experiment = durationToleranceExperimentForRun(
                                    minSizeBytes = minSizeBytes,
                                    step = step
                                )
                                startSimilarityExperimentTask(
                                    repository = repository,
                                    request = SimilarityExperimentRunRequest(
                                        experiment = experiment,
                                        mediaScope = SimilarityMediaScope.Video,
                                        minSizeBytes = minSizeBytes,
                                        durationToleranceStep = step
                                    ),
                                    scope = scope,
                                    taskCoordinator = taskCoordinator,
                                    notificationController = notificationController,
                                    onStatusText = { status -> runStatusText = status },
                                    onRunFinished = { summary ->
                                        selectedRunExperimentId = summary.experimentId
                                        pane = SimilarityExperimentPane.RunDetail
                                        refreshStoredResults(summary.experimentId)
                                    }
                                )
                            },
                            onCancel = {
                                taskCoordinator.requestCancel(TaskArea.Similarity)
                            }
                        )
                    } else if (selectedTemplate != null) {
                        SelectedExperimentMethodCard(experiment = selectedTemplate)
                    } else {
                        Text(
                            text = "Select a template to configure this experiment.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Button(
                            onClick = { openCreatePane(clearTemplateSelection = true) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Back to templates")
                        }
                    }
                }
                SimilarityExperimentPane.RunDetail -> {
                    AppTopBar(
                        title = selectedRun?.experimentName ?: "Experiment detail",
                        onBack = ::openListPane
                    )
                    if (selectedRun == null) {
                        Text(
                            text = "This experiment run is no longer available.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Button(
                            onClick = ::openListPane,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Back to experiments")
                        }
                    } else {
                        SimilarityRunSummaryCard(run = selectedRun)
                        StoredSimilarityResultsCard(
                            repository = repository,
                            selectedRun = selectedRun,
                            clusters = clusters,
                            isLoading = clustersLoading,
                            deletedPaths = deletedPaths,
                            imageLoader = imageLoader,
                            keepLoadedThumbnailsInMemory = keepLoadedThumbnailsInMemory,
                            previewThumbnailSizeDp = groupCardThumbnailSizeDp,
                            rememberedPreviewCache = rememberedPreviewCache,
                            showFullPaths = showFullPaths,
                            loadedClusterMembers = loadedClusterMembers,
                            clusterMemberLoadErrors = clusterMemberLoadErrors,
                            onOpenCluster = { cluster -> selectedClusterKey = clusterStableKey(cluster) }
                        )
                    }
                }
            }
        }

        VerticalScrollbar(
            scrollState = scrollState,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .padding(end = 4.dp)
        )
    }
}

@Composable
private fun ExperimentRunsListCard(
    runs: List<SimilarityExperimentRunEntity>,
    onSelectRun: (SimilarityExperimentRunEntity) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(text = "Experiment list", style = MaterialTheme.typography.titleMedium)
        if (runs.isEmpty()) {
            Text(text = "No saved experiment runs yet.", style = MaterialTheme.typography.bodySmall)
            return@Column
        }

        runs.forEach { run ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectRun(run) }
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = run.experimentName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = run.experimentId,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${run.clusterCount} clusters · ${run.duplicateFileCount} files · ${run.skippedCount} skipped",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ExperimentTemplatesCard(
    experiments: List<SimilarityExperimentSpec>,
    selectedExperimentId: String?,
    onSelectExperiment: (SimilarityExperimentSpec) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(text = "Experiment templates", style = MaterialTheme.typography.titleMedium)
        experiments.forEach { experiment ->
            val selected = experiment.id == selectedExperimentId
            val templateKind = executableTemplateKind(experiment)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectExperiment(experiment) },
                colors = if (selected) {
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                } else {
                    CardDefaults.cardColors()
                }
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = experiment.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = templateKind,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = experiment.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectedExperimentMethodCard(experiment: SimilarityExperimentSpec) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text = experiment.name, style = MaterialTheme.typography.titleMedium)
            Text(text = experiment.description, style = MaterialTheme.typography.bodySmall)
            Text(
                text = "This methodology is listed independently, but execution is not wired yet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            experiment.steps.forEachIndexed { index, step ->
                SimilarityStepRow(index = index + 1, step = step)
            }
        }
    }
}

@Composable
private fun DurationToleranceRunCard(
    experimentName: String,
    minSizeInput: String,
    onMinSizeInputChange: (String) -> Unit,
    minSizeUnit: SimilaritySizeUnit,
    onMinSizeUnitChange: (SimilaritySizeUnit) -> Unit,
    toleranceInput: String,
    onToleranceInputChange: (String) -> Unit,
    candidateCountText: String,
    runStatusText: String,
    isRunning: Boolean,
    onRun: () -> Unit,
    onCancel: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = experimentName,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "Runs a cached-video experiment that extracts each video's duration and clusters candidates whose durations fall within the configured tolerance.",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "Media type: Video",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = minSizeInput,
                    onValueChange = onMinSizeInputChange,
                    label = { Text("Minimum size") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                Button(
                    onClick = { onMinSizeUnitChange(minSizeUnit.next()) },
                    modifier = Modifier.align(Alignment.CenterVertically)
                ) {
                    Text("Unit: ${minSizeUnit.label}")
                }
            }
            Text(
                text = "Minimum size filters cached video candidates before duration extraction. The default is 100 MB, and Unit cycles through B, KB, MB, and GB.",
                style = MaterialTheme.typography.bodySmall
            )
            OutlinedTextField(
                value = toleranceInput,
                onValueChange = onToleranceInputChange,
                label = { Text("Duration tolerance seconds") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Text(
                text = "Tolerance is the maximum duration gap inside one cluster. Use 0 for exact millisecond duration matches.",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = candidateCountText,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(text = runStatusText, style = MaterialTheme.typography.bodySmall)
            Button(
                onClick = if (isRunning) onCancel else onRun,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isRunning) "Cancel run" else "Run duration experiment")
            }
        }
    }
}

@Composable
private fun ExactThumbnailRunCard(
    experimentName: String,
    minSizeInput: String,
    onMinSizeInputChange: (String) -> Unit,
    minSizeUnit: SimilaritySizeUnit,
    onMinSizeUnitChange: (SimilaritySizeUnit) -> Unit,
    mediaScope: SimilarityMediaScope,
    onMediaScopeChange: (SimilarityMediaScope) -> Unit,
    frameSecondsInput: String,
    onFrameSecondsInputChange: (String) -> Unit,
    resizeWidthInput: String,
    onResizeWidthInputChange: (String) -> Unit,
    resizeHeightInput: String,
    onResizeHeightInputChange: (String) -> Unit,
    quantizationEnabled: Boolean,
    onQuantizationEnabledChange: (Boolean) -> Unit,
    quantizationInput: String,
    onQuantizationInputChange: (String) -> Unit,
    grayscale: Boolean,
    onGrayscaleChange: (Boolean) -> Unit,
    candidateCountText: String,
    runStatusText: String,
    isRunning: Boolean,
    onRun: () -> Unit,
    onCancel: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = experimentName,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "Runs a real cached-media experiment with editable media type, candidate size, frame timestamps, resize target, optional quantization, and color mode.",
                style = MaterialTheme.typography.bodySmall
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onMediaScopeChange(SimilarityMediaScope.Video) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (mediaScope == SimilarityMediaScope.Video) "Video selected" else "Video")
                }
                Button(
                    onClick = { onMediaScopeChange(SimilarityMediaScope.Image) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (mediaScope == SimilarityMediaScope.Image) "Image selected" else "Image")
                }
            }
            Text(
                text = "Media type selects which cached files become candidates. Video uses extracted frames at the configured seconds; Image uses the image itself.",
                style = MaterialTheme.typography.bodySmall
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = minSizeInput,
                    onValueChange = onMinSizeInputChange,
                    label = { Text("Minimum size") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                Button(
                    onClick = { onMinSizeUnitChange(minSizeUnit.next()) },
                    modifier = Modifier.align(Alignment.CenterVertically)
                ) {
                    Text("Unit: ${minSizeUnit.label}")
                }
            }
            Text(
                text = "Minimum size filters candidates before signature extraction. The default is 100 MB, and Unit cycles through B, KB, MB, and GB.",
                style = MaterialTheme.typography.bodySmall
            )
            OutlinedTextField(
                value = frameSecondsInput,
                onValueChange = onFrameSecondsInputChange,
                label = { Text("Frame seconds") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Text(
                text = "Frame seconds is a comma-separated list for video runs, such as 0,1,10. Image runs ignore this field.",
                style = MaterialTheme.typography.bodySmall
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = resizeWidthInput,
                    onValueChange = onResizeWidthInputChange,
                    label = { Text("Width") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                OutlinedTextField(
                    value = resizeHeightInput,
                    onValueChange = onResizeHeightInputChange,
                    label = { Text("Height") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
            }
            Text(
                text = "Width and Height resize each sampled frame or image before hashing. 1 x 1 is the cheapest coarse signature.",
                style = MaterialTheme.typography.bodySmall
            )
            OutlinedTextField(
                value = quantizationInput,
                onValueChange = onQuantizationInputChange,
                label = { Text("Quantization levels") },
                modifier = Modifier.fillMaxWidth(),
                enabled = quantizationEnabled,
                singleLine = true
            )
            Text(
                text = "Quantization is optional. Enable it to reduce channel precision before comparison; leave it off for raw resized pixel values.",
                style = MaterialTheme.typography.bodySmall
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Checkbox(
                    checked = quantizationEnabled,
                    onCheckedChange = onQuantizationEnabledChange
                )
                Text(text = "Apply quantization", style = MaterialTheme.typography.bodySmall)
            }
            Text(
                text = "Grayscale converts sampled pixels to luminance before hashing. Leave it off to compare color channels.",
                style = MaterialTheme.typography.bodySmall
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Checkbox(
                    checked = grayscale,
                    onCheckedChange = onGrayscaleChange
                )
                Text(text = "Grayscale before quantization", style = MaterialTheme.typography.bodySmall)
            }
            Text(
                text = candidateCountText,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(text = runStatusText, style = MaterialTheme.typography.bodySmall)
            Button(
                onClick = if (isRunning) onCancel else onRun,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isRunning) "Cancel run" else "Run exact thumbnail experiment")
            }
        }
    }
}

@Composable
private fun SimilarityRunSummaryCard(run: SimilarityExperimentRunEntity) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = run.experimentName,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = run.experimentId,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${run.clusterCount} clusters · ${run.duplicateFileCount} files · ${run.skippedCount} skipped",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Processed ${run.processedCount}/${run.candidateCount} candidates.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StoredSimilarityResultsCard(
    repository: SimilarityExperimentRepository,
    selectedRun: SimilarityExperimentRunEntity?,
    clusters: List<SimilarityClusterEntity>,
    isLoading: Boolean,
    deletedPaths: Set<String>,
    imageLoader: ImageLoader,
    keepLoadedThumbnailsInMemory: Boolean,
    previewThumbnailSizeDp: Dp,
    rememberedPreviewCache: MutableMap<String, ImageBitmap>,
    showFullPaths: Boolean,
    loadedClusterMembers: MutableMap<String, SimilarityClusterMembersState>,
    clusterMemberLoadErrors: MutableMap<String, String>,
    onOpenCluster: (SimilarityClusterEntity) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(text = "Selected experiment clusters", style = MaterialTheme.typography.titleMedium)
        if (selectedRun == null) {
            Text(text = "Select or run an experiment to review its clusters.", style = MaterialTheme.typography.bodySmall)
            return@Column
        }
        Text(
            text = "${selectedRun.experimentName}: ${selectedRun.clusterCount} clusters, ${selectedRun.duplicateFileCount} files, ${selectedRun.skippedCount} skipped.",
            style = MaterialTheme.typography.bodySmall
        )
        if (isLoading) {
            Text(text = "Loading experiment clusters...", style = MaterialTheme.typography.bodySmall)
            return@Column
        }
        if (clusters.isEmpty()) {
            Text(text = "No duplicate-like similarity clusters found for the latest run.", style = MaterialTheme.typography.bodySmall)
            return@Column
        }
        clusters.forEach { cluster ->
            SimilarityClusterCard(
                repository = repository,
                cluster = cluster,
                deletedPaths = deletedPaths,
                imageLoader = imageLoader,
                keepLoadedThumbnailsInMemory = keepLoadedThumbnailsInMemory,
                previewThumbnailSizeDp = previewThumbnailSizeDp,
                rememberedPreviewCache = rememberedPreviewCache,
                showFullPaths = showFullPaths,
                loadedClusterMembers = loadedClusterMembers,
                clusterMemberLoadErrors = clusterMemberLoadErrors,
                onOpen = { onOpenCluster(cluster) }
            )
        }
    }
}

@Composable
private fun SimilarityClusterCard(
    repository: SimilarityExperimentRepository,
    cluster: SimilarityClusterEntity,
    deletedPaths: Set<String>,
    imageLoader: ImageLoader,
    keepLoadedThumbnailsInMemory: Boolean,
    previewThumbnailSizeDp: Dp,
    rememberedPreviewCache: MutableMap<String, ImageBitmap>,
    showFullPaths: Boolean,
    loadedClusterMembers: MutableMap<String, SimilarityClusterMembersState>,
    clusterMemberLoadErrors: MutableMap<String, String>,
    onOpen: () -> Unit
) {
    val clusterKey = remember(cluster.experimentId, cluster.signature) { clusterStableKey(cluster) }
    val memberState = loadedClusterMembers[clusterKey]
    val members = memberState?.members.orEmpty()
    val loadError = clusterMemberLoadErrors[clusterKey]

    LaunchedEffect(clusterKey) {
        if (loadedClusterMembers.containsKey(clusterKey) || clusterMemberLoadErrors.containsKey(clusterKey)) {
            return@LaunchedEffect
        }
        val previewMembers = runCatching {
            withContext(Dispatchers.IO) {
                repository.listClusterMembers(
                    cluster = cluster,
                    limit = SIMILARITY_CLUSTER_PREVIEW_MEMBER_LIMIT
                )
            }
        }
        previewMembers.fold(
            onSuccess = {
                loadedClusterMembers[clusterKey] = SimilarityClusterMembersState(
                    members = it,
                    complete = it.size >= cluster.fileCount
                )
            },
            onFailure = { clusterMemberLoadErrors[clusterKey] = "Cluster members are unavailable." }
        )
    }

    val hasPreviewMedia = members.any { isMediaFile(it.normalizedPath) }
    val previewCandidates = mediaPreviewCandidates(
        files = members,
        deletedPaths = deletedPaths
    )
    val groupDeleted = members.any { deletedPaths.contains(it.normalizedPath) }
    val exactHashExplanation = exactThumbnailClusterExplanation(cluster.signature)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        colors = if (groupDeleted) {
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        } else {
            CardDefaults.cardColors()
        }
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (hasPreviewMedia) {
                GroupPreviewThumbnail(
                    candidatePaths = previewCandidates,
                    previewMemoryKey = clusterPreviewMemoryKey(cluster),
                    rememberedPreviewCache = rememberedPreviewCache,
                    imageLoader = imageLoader,
                    keepLoadedInMemory = keepLoadedThumbnailsInMemory,
                    contentDescription = "Thumbnail",
                    modifier = Modifier
                        .height(previewThumbnailSizeDp)
                        .width(previewThumbnailSizeDp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }

            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "${cluster.fileCount} files · Total ${formatBytes(cluster.totalBytes)}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = exactHashExplanation?.let(::exactHashClusterSummary)
                        ?: "Signature ${cluster.signature.take(16)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (exactHashExplanation != null) {
                    Text(
                        text = "Members matched the same exact thumbnail signature.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                loadError?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                SimilarityClusterMemberPreviewLines(
                    members = members,
                    showFullPaths = showFullPaths
                )

                val remaining = (cluster.fileCount - similarityClusterPreviewDisplayCount(members))
                    .coerceAtLeast(0)
                if (remaining > 0) {
                    Text(
                        text = "+${remaining} more…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SimilarityClusterMemberPreviewLines(
    members: List<FileMetadata>,
    showFullPaths: Boolean
) {
    similarityClusterPreviewLineTexts(
        members = members,
        showFullPaths = showFullPaths
    ).forEach { line ->
        Text(
            text = line,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SimilarityClusterDetailScreen(
    repository: SimilarityExperimentRepository,
    cluster: SimilarityClusterEntity,
    deletedPaths: Set<String>,
    imageLoader: ImageLoader,
    keepLoadedThumbnailsInMemory: Boolean,
    rememberedPreviewCache: MutableMap<String, ImageBitmap>,
    previewHeight: Dp,
    onDeleteFile: (suspend (FileMetadata) -> Boolean)?,
    loadedClusterMembers: MutableMap<String, SimilarityClusterMembersState>,
    clusterMemberLoadErrors: MutableMap<String, String>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val detailScrollState = rememberScrollState()
    val clusterKey = remember(cluster.experimentId, cluster.signature) { clusterStableKey(cluster) }
    val memberState = loadedClusterMembers[clusterKey]
    val members = memberState?.members.orEmpty()
    val loadError = clusterMemberLoadErrors[clusterKey]
    var isLoading by remember(clusterKey) { mutableStateOf(memberState?.complete != true) }
    var loadAttempt by remember(clusterKey) { mutableStateOf(0) }
    val exactHashExplanation = exactThumbnailClusterExplanation(cluster.signature)

    LaunchedEffect(clusterKey, loadAttempt) {
        if (loadedClusterMembers[clusterKey]?.complete == true) {
            isLoading = false
            return@LaunchedEffect
        }
        isLoading = true
        val result = runCatching {
            withContext(Dispatchers.IO) {
                repository.listClusterMembers(cluster = cluster)
            }
        }
        result.fold(
            onSuccess = {
                loadedClusterMembers[clusterKey] = SimilarityClusterMembersState(
                    members = it,
                    complete = true
                )
                clusterMemberLoadErrors.remove(clusterKey)
            },
            onFailure = {
                clusterMemberLoadErrors[clusterKey] = "Cluster members are unavailable."
            }
        )
        isLoading = false
    }

    BackHandler(onBack = onBack)
    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .padding(Spacing.screenPadding)
                .padding(end = ScrollbarDefaults.ThumbWidth + 8.dp)
                .verticalScroll(detailScrollState)
        ) {
            AppTopBar(
                title = "Similarity cluster detail",
                onBack = onBack
            )
            Spacer(modifier = Modifier.height(8.dp))
            when {
                isLoading -> Text("Loading cluster members...")
                loadError != null -> {
                    Text(loadError, style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = {
                        clusterMemberLoadErrors.remove(clusterKey)
                        loadAttempt += 1
                    }) {
                        Text("Retry")
                    }
                }
                else -> {
                    ExactHashReductionPreviewCard(exactHashExplanation = exactHashExplanation)
                    Spacer(modifier = Modifier.height(8.dp))
                    DuplicateGroupDetailContent(
                        title = "Group detail",
                        memberCount = cluster.fileCount,
                        totalBytes = cluster.totalBytes,
                        summaryLines = similarityClusterDetailLines(
                            cluster = cluster,
                            exactHashExplanation = exactHashExplanation
                        ),
                        members = members,
                        deletedPaths = deletedPaths,
                        imageLoader = imageLoader,
                        keepLoadedThumbnailsInMemory = keepLoadedThumbnailsInMemory,
                        rememberedPreviewCache = rememberedPreviewCache,
                        previewMemoryKey = clusterPreviewMemoryKey(cluster),
                        previewHeight = previewHeight,
                        showMemberThumbnails = true,
                        onDeleteFile = onDeleteFile
                    )
                }
            }
        }

        VerticalScrollbar(
            scrollState = detailScrollState,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .padding(end = 4.dp)
        )
    }
}

@Composable
private fun ExactHashReductionPreviewCard(exactHashExplanation: ExactThumbnailClusterExplanation?) {
    val samples = exactHashExplanation?.let(::exactHashReductionSamples).orEmpty()
    if (samples.isEmpty()) return

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Reduction preview",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "These enlarged tiles show the exact reduced image values used for this experiment's equality check.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            samples.forEach { sample ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ExactHashReductionSampleGrid(sample = sample)
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = sample.label,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = sample.signature,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExactHashReductionSampleGrid(sample: ExactHashReductionSample) {
    val tileSize = when (maxOf(sample.width, sample.height)) {
        1 -> 56.dp
        2 -> 28.dp
        else -> 18.dp
    }
    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
        (0 until sample.height).forEach { y ->
            Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                (0 until sample.width).forEach { x ->
                    val color = sample.colors.getOrNull(y * sample.width + x)
                        ?: ExactHashReductionColor(red = 0, green = 0, blue = 0)
                    Box(
                        modifier = Modifier
                            .size(tileSize)
                            .background(
                                Color(
                                    red = color.red,
                                    green = color.green,
                                    blue = color.blue
                                )
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun SimilarityExperimentCard(experiment: SimilarityExperimentSpec) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = experiment.name,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = experiment.description,
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "Default scope: ${experiment.mediaScope.name.lowercase()}, no fixed size floor",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold
            )
            experiment.steps.forEachIndexed { index, step ->
                SimilarityStepRow(index = index + 1, step = step)
            }
        }
    }
}

@Composable
private fun SimilarityStepRow(index: Int, step: SimilarityExperimentStep) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "$index.",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold
        )
        Column {
            Text(
                text = step.title,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = step.summary,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

internal enum class SimilaritySizeUnit(
    val label: String,
    val bytes: Long
) {
    B("B", 1L),
    KB("KB", 1024L),
    MB("MB", 1024L * 1024L),
    GB("GB", 1024L * 1024L * 1024L);

    fun next(): SimilaritySizeUnit {
        val values = entries
        return values[(ordinal + 1) % values.size]
    }
}

internal fun parsedMinSizeBytes(
    input: String,
    unit: SimilaritySizeUnit
): Long {
    val amount = input.toLongOrNull() ?: 0L
    return amount.coerceAtLeast(0L) * unit.bytes
}

internal fun sanitizeFrameSecondsInput(input: String): String {
    return input.filter { char -> char.isDigit() || char == ',' || char.isWhitespace() }
}

internal fun parsedFrameSeconds(input: String): List<Int> {
    return input.split(',')
        .mapNotNull { part -> part.trim().toIntOrNull() }
        .map { second -> second.coerceAtLeast(0) }
        .distinct()
}

internal fun parsedExactThumbnailStep(
    frameSecondsInput: String,
    resizeWidthInput: String,
    resizeHeightInput: String,
    quantizationEnabled: Boolean,
    quantizationInput: String,
    grayscale: Boolean
): ExactThumbnailHashStep {
    return ExactThumbnailHashStep(
        frameSeconds = parsedFrameSeconds(frameSecondsInput),
        resizeWidthPx = (resizeWidthInput.toIntOrNull() ?: 1).coerceAtLeast(1),
        resizeHeightPx = (resizeHeightInput.toIntOrNull() ?: 1).coerceAtLeast(1),
        quantizationLevels = if (quantizationEnabled) {
            (quantizationInput.toIntOrNull() ?: 16).coerceAtLeast(2)
        } else {
            null
        },
        grayscale = grayscale
    )
}

internal fun parsedDurationToleranceStep(input: String): DurationToleranceStep {
    return DurationToleranceStep(
        toleranceSeconds = (input.toIntOrNull() ?: 1).coerceAtLeast(0)
    )
}

internal fun exactThumbnailExperimentForRun(
    mediaScope: SimilarityMediaScope,
    minSizeBytes: Long,
    step: ExactThumbnailHashStep
): SimilarityExperimentSpec {
    val mode = if (step.grayscale) "gray" else "color"
    val quantization = step.quantizationLevels?.let { "q$it" } ?: "raw"
    val frames = step.frameSeconds.joinToString("-").ifBlank { "none" }
    return SimilarityExperimentSpec(
        id = "${mediaScope.name.lowercase()}-thumb-exact-${minSizeBytes}-${frames}-${step.resizeWidthPx}x${step.resizeHeightPx}-$quantization-$mode",
        name = "${mediaScope.name} thumbnail exact hash",
        description = "Runtime-configured exact thumbnail hash experiment.",
        defaultMinSizeBytes = minSizeBytes,
        mediaScope = mediaScope,
        steps = listOf(step)
    )
}

internal fun durationToleranceExperimentForRun(
    minSizeBytes: Long,
    step: DurationToleranceStep
): SimilarityExperimentSpec {
    val toleranceMillis = durationToleranceMillis(step)
    return SimilarityExperimentSpec(
        id = "video-duration-${minSizeBytes}-${toleranceMillis}",
        name = "Video duration tolerance",
        description = "Runtime-configured duration tolerance experiment.",
        defaultMinSizeBytes = minSizeBytes,
        mediaScope = SimilarityMediaScope.Video,
        steps = listOf(step)
    )
}

internal data class SimilaritySizeInput(
    val input: String,
    val unit: SimilaritySizeUnit
)

internal fun defaultSizeInputForUnit(
    bytes: Long,
    unit: SimilaritySizeUnit
): SimilaritySizeInput {
    if (unit.bytes > 0L && bytes % unit.bytes == 0L) {
        return SimilaritySizeInput(
            input = (bytes / unit.bytes).toString(),
            unit = unit
        )
    }
    return SimilaritySizeInput(
        input = bytes.coerceAtLeast(0L).toString(),
        unit = SimilaritySizeUnit.B
    )
}

internal fun selectedSimilarityRun(
    runs: List<SimilarityExperimentRunEntity>,
    selectedId: String?
): SimilarityExperimentRunEntity? {
    if (selectedId != null) {
        runs.firstOrNull { run -> run.experimentId == selectedId }?.let { return it }
    }
    return runs.firstOrNull()
}

internal fun selectedSimilarityExperimentTemplate(
    experiments: List<SimilarityExperimentSpec>,
    selectedId: String?
): SimilarityExperimentSpec? {
    if (selectedId != null) {
        experiments.firstOrNull { experiment -> experiment.id == selectedId }?.let { return it }
    }
    return experiments.firstOrNull()
}

internal fun executableExactThumbnailStep(experiment: SimilarityExperimentSpec): ExactThumbnailHashStep? {
    if (experiment.steps.size != 1) return null
    return experiment.steps.singleOrNull() as? ExactThumbnailHashStep
}

internal fun executableDurationToleranceStep(experiment: SimilarityExperimentSpec): DurationToleranceStep? {
    if (experiment.steps.size != 1) return null
    return experiment.steps.singleOrNull() as? DurationToleranceStep
}

internal fun executableTemplateKind(experiment: SimilarityExperimentSpec): String {
    return when {
        executableExactThumbnailStep(experiment) != null -> "Executable exact-hash experiment"
        executableDurationToleranceStep(experiment) != null -> "Executable duration experiment"
        else -> "Methodology template"
    }
}

internal fun startSimilarityExperimentTask(
    repository: SimilarityExperimentRepository,
    request: SimilarityExperimentRunRequest,
    scope: CoroutineScope,
    taskCoordinator: TaskCoordinator,
    notificationController: TaskNotificationController,
    onStatusText: (String) -> Unit,
    onRunFinished: (SimilarityExperimentSummary) -> Unit
): Boolean {
    return startSimilarityExperimentTask(
        request = request,
        scope = scope,
        taskCoordinator = taskCoordinator,
        notificationController = notificationController,
        onStatusText = onStatusText,
        onRunFinished = onRunFinished
    ) { shouldContinue, onProgress ->
        when {
            request.exactThumbnailStep != null -> repository.runExactThumbnailHashExperiment(
                request = request,
                shouldContinue = shouldContinue,
                onProgress = onProgress
            )
            request.durationToleranceStep != null -> repository.runDurationToleranceExperiment(
                request = request,
                shouldContinue = shouldContinue,
                onProgress = onProgress
            )
            else -> error("Similarity experiment request has no executable step.")
        }
    }
}

internal fun startSimilarityExperimentTask(
    request: SimilarityExperimentRunRequest,
    scope: CoroutineScope,
    taskCoordinator: TaskCoordinator,
    notificationController: TaskNotificationController,
    onStatusText: (String) -> Unit,
    onRunFinished: (SimilarityExperimentSummary) -> Unit,
    runExperiment: ((() -> Boolean), (SimilarityExperimentProgress) -> Unit) -> SimilarityExperimentSummary
): Boolean {
    val cancelRequested = AtomicBoolean(false)
    val started = taskCoordinator.tryStart(
        area = TaskArea.Similarity,
        kind = TaskKind.SimilarityExperiment,
        title = similarityExperimentTaskTitle(),
        detail = "Starting ${request.experiment.name}.",
        processed = 0,
        total = null,
        indeterminate = true,
        isCancellable = true,
        onCancel = {
            cancelRequested.set(true)
            requestImmediateSimilarityCancel(
                taskCoordinator = taskCoordinator,
                notificationController = notificationController
            )
        }
    ) ?: return false
    notificationController.showActive(started)
    onStatusText(started.detail)

    scope.launch {
        runCatching {
            withContext(Dispatchers.IO) {
                runExperiment(
                    { !cancelRequested.get() },
                    { progress ->
                        val detail = similarityExperimentTaskDetail(progress)
                        taskCoordinator.update(TaskArea.Similarity) { task ->
                            task.withLinearProgress(
                                title = similarityExperimentTaskTitle(),
                                detail = detail,
                                currentPath = progress.currentPath,
                                processed = progress.processed,
                                total = progress.total
                            )
                        }?.let(notificationController::showActive)
                        scope.launch { onStatusText(detail) }
                    }
                )
            }
        }.onSuccess { summary ->
            val currentPath = taskCoordinator.activeTask(TaskArea.Similarity)?.currentPath
            if (summary.cancelled) {
                val detail = similarityExperimentCancelledDetail(summary)
                taskCoordinator.cancel(
                    area = TaskArea.Similarity,
                    title = "Similarity experiment cancelled",
                    detail = detail,
                    currentPath = currentPath,
                    processed = summary.processedCount,
                    total = summary.candidateCount,
                    indeterminate = summary.candidateCount <= 0
                )?.let(notificationController::showTerminal)
                onStatusText(detail)
            } else {
                val detail = similarityExperimentCompletedDetail(summary)
                taskCoordinator.complete(
                    area = TaskArea.Similarity,
                    title = "Similarity experiment complete",
                    detail = detail,
                    currentPath = currentPath,
                    processed = summary.processedCount,
                    total = summary.candidateCount,
                    indeterminate = summary.candidateCount <= 0
                )?.let(notificationController::showTerminal)
                onStatusText("Finished: ${summary.clusterCount} clusters, ${summary.duplicateFileCount} files.")
            }
            onRunFinished(summary)
        }.onFailure {
            taskCoordinator.fail(
                area = TaskArea.Similarity,
                title = "Similarity experiment failed",
                detail = "The similarity experiment did not finish."
            )?.let(notificationController::showTerminal)
            onStatusText("Similarity experiment failed.")
        }
    }
    return true
}

private fun requestImmediateSimilarityCancel(
    taskCoordinator: TaskCoordinator,
    notificationController: TaskNotificationController
) {
    val snapshot = taskCoordinator.activeTask(TaskArea.Similarity)
    taskCoordinator.cancel(
        area = TaskArea.Similarity,
        title = "Similarity experiment cancelled",
        detail = "Cancelling similarity experiment.",
        currentPath = snapshot?.currentPath,
        processed = snapshot?.processed,
        total = snapshot?.total,
        indeterminate = snapshot?.indeterminate ?: true
    )?.let(notificationController::showTerminal)
}

private fun clusterStableKey(cluster: SimilarityClusterEntity): String {
    return "${cluster.experimentId}:${cluster.signature}"
}

private fun clusterPreviewMemoryKey(cluster: SimilarityClusterEntity): String {
    return "similarity:${clusterStableKey(cluster)}"
}

internal data class ExactThumbnailClusterExplanation(
    val mediaScope: String,
    val colorMode: String,
    val resize: String,
    val quantization: String,
    val frameSeconds: List<String>,
    val sampleSignatures: List<String>
)

internal fun exactThumbnailClusterExplanation(signature: String): ExactThumbnailClusterExplanation? {
    val parts = signature.split(":", limit = 7)
    if (parts.size != 7 || parts[0] != "thumb-v1") return null
    val mediaScope = parts[1].takeIf { it.isNotBlank() } ?: return null
    val colorMode = parts[2].takeIf { it == "gray" || it == "color" } ?: return null
    val resize = parts[3].takeIf { it.contains("x") } ?: return null
    val quantization = parts[4].takeIf { it.isNotBlank() } ?: return null
    val frameSeconds = parts[5]
        .split(',')
        .map { frame -> frame.trim() }
        .filter { frame -> frame.isNotEmpty() }
    val sampleSignatures = parts[6]
        .split('|')
        .map { sample -> sample.trim() }
        .filter { sample -> sample.isNotEmpty() }

    return ExactThumbnailClusterExplanation(
        mediaScope = mediaScope,
        colorMode = colorMode,
        resize = resize,
        quantization = quantization,
        frameSeconds = frameSeconds,
        sampleSignatures = sampleSignatures
    )
}

internal fun exactHashClusterSummary(explanation: ExactThumbnailClusterExplanation): String {
    return "Exact hash: ${mediaScopeLabel(explanation.mediaScope)}, ${framesLabel(explanation)}, ${explanation.resize}, " +
        "${colorModeLabel(explanation.colorMode)}, ${quantizationLabel(explanation.quantization)}"
}

internal fun similarityClusterDetailLines(
    cluster: SimilarityClusterEntity,
    exactHashExplanation: ExactThumbnailClusterExplanation?
): List<String> {
    if (exactHashExplanation == null) {
        return listOf(
            "Similarity signature ${cluster.signature}",
            "Snapshot ${formatDate(cluster.updatedAtMillis)}"
        )
    }

    return listOf(
        "Group rule: exact thumbnail hash equality",
        "Why included: every member produced the same exact thumbnail signature.",
        "Media: ${mediaScopeLabel(exactHashExplanation.mediaScope)}",
        "Samples: ${framesLabel(exactHashExplanation)}",
        "Resize: ${exactHashExplanation.resize}",
        "Color mode: ${colorModeLabel(exactHashExplanation.colorMode)}",
        "Quantization: ${quantizationLabel(exactHashExplanation.quantization)}",
        "Sample signature values: ${sampleSignaturesLabel(exactHashExplanation.sampleSignatures)}",
        "Snapshot ${formatDate(cluster.updatedAtMillis)}"
    )
}

internal fun similarityClusterPreviewLineTexts(
    members: List<FileMetadata>,
    showFullPaths: Boolean,
    itemsPerLine: Int = SIMILARITY_CLUSTER_PREVIEW_ITEMS_PER_LINE,
    maxItems: Int = SIMILARITY_CLUSTER_PREVIEW_TEXT_MEMBER_LIMIT
): List<String> {
    return members
        .sortedBy { file -> file.normalizedPath }
        .take(maxItems.coerceAtLeast(0))
        .chunked(itemsPerLine.coerceAtLeast(1))
        .map { row ->
            row.joinToString("  •  ") { file ->
                formatPath(file.normalizedPath, showFullPaths)
            }
        }
}

internal fun similarityClusterPreviewDisplayCount(
    members: List<FileMetadata>,
    maxItems: Int = SIMILARITY_CLUSTER_PREVIEW_TEXT_MEMBER_LIMIT
): Int {
    return members.size.coerceAtMost(maxItems.coerceAtLeast(0))
}

internal data class ExactHashReductionSample(
    val label: String,
    val signature: String,
    val width: Int,
    val height: Int,
    val colors: List<ExactHashReductionColor>
)

internal data class ExactHashReductionColor(
    val red: Int,
    val green: Int,
    val blue: Int
)

internal fun exactHashReductionSamples(
    explanation: ExactThumbnailClusterExplanation
): List<ExactHashReductionSample> {
    val configuredGrid = exactHashReductionGridSize(explanation.resize)
    return explanation.sampleSignatures.mapIndexedNotNull { index, signature ->
        val colors = exactHashReductionColors(
            colorMode = explanation.colorMode,
            quantization = explanation.quantization,
            signature = signature
        )
        if (colors.isEmpty()) return@mapIndexedNotNull null
        val configuredPixelCount = configuredGrid.width * configuredGrid.height
        val sampleWidth = if (configuredPixelCount == colors.size) configuredGrid.width else colors.size
        val sampleHeight = if (configuredPixelCount == colors.size) configuredGrid.height else 1
        ExactHashReductionSample(
            label = exactHashReductionSampleLabel(
                explanation = explanation,
                index = index
            ),
            signature = signature,
            width = sampleWidth.coerceAtLeast(1),
            height = sampleHeight.coerceAtLeast(1),
            colors = colors
        )
    }
}

internal data class ExactHashReductionGridSize(
    val width: Int,
    val height: Int
)

internal fun exactHashReductionGridSize(resize: String): ExactHashReductionGridSize {
    val parts = resize.split('x', limit = 2)
    val width = parts.getOrNull(0)?.toIntOrNull()?.coerceAtLeast(1) ?: 1
    val height = parts.getOrNull(1)?.toIntOrNull()?.coerceAtLeast(1) ?: 1
    return ExactHashReductionGridSize(width = width, height = height)
}

internal fun exactHashReductionColor(
    colorMode: String,
    quantization: String,
    signature: String
): ExactHashReductionColor? {
    return exactHashReductionColors(
        colorMode = colorMode,
        quantization = quantization,
        signature = signature
    ).firstOrNull()
}

internal fun exactHashReductionColors(
    colorMode: String,
    quantization: String,
    signature: String
): List<ExactHashReductionColor> {
    return splitPixelSignatures(signature).mapNotNull { pixelSignature ->
        exactHashReductionPixelColor(
            colorMode = colorMode,
            quantization = quantization,
            signature = pixelSignature
        )
    }
}

private fun exactHashReductionPixelColor(
    colorMode: String,
    quantization: String,
    signature: String
): ExactHashReductionColor? {
    val trimmedSignature = signature.trim()
    val quantizationLevels = quantization
        .takeIf { value -> value.startsWith("q") }
        ?.drop(1)
        ?.toIntOrNull()
        ?.coerceAtLeast(2)
    return if (colorMode == "gray") {
        val gray = if (quantizationLevels == null) {
            parseHexChannel(trimmedSignature)
        } else {
            parseHexBucket(trimmedSignature)?.let { bucket -> bucketToChannel(bucket, quantizationLevels) }
        } ?: return null
        ExactHashReductionColor(red = gray, green = gray, blue = gray)
    } else if (colorMode == "color") {
        if (quantizationLevels == null) {
            if (trimmedSignature.length < 6) return null
            val red = parseHexChannel(trimmedSignature.substring(0, 2)) ?: return null
            val green = parseHexChannel(trimmedSignature.substring(2, 4)) ?: return null
            val blue = parseHexChannel(trimmedSignature.substring(4, 6)) ?: return null
            ExactHashReductionColor(red = red, green = green, blue = blue)
        } else {
            val channelWidth = quantizedChannelHexWidth(quantizationLevels)
            if (trimmedSignature.length < channelWidth * 3) return null
            val red = parseHexBucket(trimmedSignature.substring(0, channelWidth))
                ?.let { bucket -> bucketToChannel(bucket, quantizationLevels) }
                ?: return null
            val green = parseHexBucket(trimmedSignature.substring(channelWidth, channelWidth * 2))
                ?.let { bucket -> bucketToChannel(bucket, quantizationLevels) }
                ?: return null
            val blue = parseHexBucket(trimmedSignature.substring(channelWidth * 2, channelWidth * 3))
                ?.let { bucket -> bucketToChannel(bucket, quantizationLevels) }
                ?: return null
            ExactHashReductionColor(red = red, green = green, blue = blue)
        }
    } else {
        null
    }
}

private fun splitPixelSignatures(signature: String): List<String> {
    return if (signature.contains(',')) {
        signature.split(',')
            .map { part -> part.trim() }
            .filter { part -> part.isNotEmpty() }
    } else {
        listOf(signature.trim()).filter { part -> part.isNotEmpty() }
    }
}

private fun exactHashReductionSampleLabel(
    explanation: ExactThumbnailClusterExplanation,
    index: Int
): String {
    if (explanation.mediaScope == "image") return "Reduced image"
    val frameSecond = explanation.frameSeconds.getOrNull(index)
    return frameSecond?.let { second -> "Reduced frame ${second}s" } ?: "Reduced sample ${index + 1}"
}

private fun parseHexChannel(value: String): Int? {
    return value.toIntOrNull(radix = 16)?.coerceIn(0, 255)
}

private fun parseHexBucket(value: String): Int? {
    return value.toIntOrNull(radix = 16)?.coerceAtLeast(0)
}

private fun bucketToChannel(bucket: Int, levels: Int): Int {
    val normalizedLevels = levels.coerceAtLeast(2)
    return ((bucket.coerceIn(0, normalizedLevels - 1) * 255) / (normalizedLevels - 1))
        .coerceIn(0, 255)
}

private fun quantizedChannelHexWidth(levels: Int): Int {
    return (levels.coerceAtLeast(2) - 1).toString(16).length
}

private fun mediaScopeLabel(value: String): String {
    return when (value) {
        "video" -> "Video"
        "image" -> "Image"
        else -> value
    }
}

private fun framesLabel(explanation: ExactThumbnailClusterExplanation): String {
    if (explanation.mediaScope == "image") return "image pixels"
    if (explanation.frameSeconds.isEmpty()) return "no configured frame seconds"
    return explanation.frameSeconds.joinToString(", ") { second -> "${second}s" }
}

private fun colorModeLabel(value: String): String {
    return when (value) {
        "gray" -> "grayscale"
        "color" -> "color"
        else -> value
    }
}

private fun quantizationLabel(value: String): String {
    return if (value == "raw") {
        "raw pixels"
    } else if (value.startsWith("q")) {
        "${value.drop(1)} levels"
    } else {
        value
    }
}

private fun sampleSignaturesLabel(values: List<String>): String {
    if (values.isEmpty()) return "none"
    return values.joinToString(" | ") { value ->
        value.take(SIMILARITY_SIGNATURE_SAMPLE_DISPLAY_LIMIT)
    }
}

private const val SIMILARITY_CLUSTER_PREVIEW_MEMBER_LIMIT = 10
private const val SIMILARITY_CLUSTER_PREVIEW_TEXT_MEMBER_LIMIT = 4
private const val SIMILARITY_CLUSTER_PREVIEW_ITEMS_PER_LINE = 2
private const val SIMILARITY_SIGNATURE_SAMPLE_DISPLAY_LIMIT = 32
