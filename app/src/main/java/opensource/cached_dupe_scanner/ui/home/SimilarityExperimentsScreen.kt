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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.snapshotFlow
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import opensource.cached_dupe_scanner.cache.SimilarityClusterEntity
import opensource.cached_dupe_scanner.cache.SimilarityExperimentRunEntity
import opensource.cached_dupe_scanner.core.FileMetadata
import opensource.cached_dupe_scanner.core.SimilarityExperimentSpec
import opensource.cached_dupe_scanner.core.SimilarityExperimentStep
import opensource.cached_dupe_scanner.core.SimilarityMediaScope
import opensource.cached_dupe_scanner.core.SortDirection
import opensource.cached_dupe_scanner.core.durationNeighborToleranceMillis
import opensource.cached_dupe_scanner.core.durationToleranceMillis
import opensource.cached_dupe_scanner.core.defaultSimilarityExperimentSpecs
import opensource.cached_dupe_scanner.notifications.TaskNotificationController
import opensource.cached_dupe_scanner.storage.SimilarityClusterMember
import opensource.cached_dupe_scanner.storage.SimilarityClusterCursor
import opensource.cached_dupe_scanner.storage.SimilarityExperimentRepository
import opensource.cached_dupe_scanner.tasks.TaskArea
import opensource.cached_dupe_scanner.tasks.TaskCoordinator
import opensource.cached_dupe_scanner.ui.components.AppTopBar
import opensource.cached_dupe_scanner.ui.components.RadioOptionRow
import opensource.cached_dupe_scanner.ui.components.ScrollbarDefaults
import opensource.cached_dupe_scanner.ui.components.Spacing
import opensource.cached_dupe_scanner.ui.components.TopRightLoadIndicator
import opensource.cached_dupe_scanner.ui.components.VerticalLazyScrollbar
import opensource.cached_dupe_scanner.ui.components.formatLoadProgressText
import opensource.cached_dupe_scanner.ui.home.similarity.SimilarityRunRequestBuildResult
import opensource.cached_dupe_scanner.ui.home.similarity.SimilarityRunRequestDraft
import opensource.cached_dupe_scanner.ui.home.similarity.SimilaritySizeUnit
import opensource.cached_dupe_scanner.ui.home.similarity.SimilarityTimeInput
import opensource.cached_dupe_scanner.ui.home.similarity.SimilarityTimeUnit
import opensource.cached_dupe_scanner.ui.home.similarity.buildSimilarityRunRequest
import opensource.cached_dupe_scanner.ui.home.similarity.defaultSizeInputForUnit
import opensource.cached_dupe_scanner.ui.home.similarity.defaultTimeInputForUnit
import opensource.cached_dupe_scanner.ui.home.similarity.executableDurationNeighborListStep
import opensource.cached_dupe_scanner.ui.home.similarity.executableDurationToleranceStep
import opensource.cached_dupe_scanner.ui.home.similarity.executableExactThumbnailStep
import opensource.cached_dupe_scanner.ui.home.similarity.executableTemplateKind
import opensource.cached_dupe_scanner.ui.home.similarity.parsedDurationNeighborListStep
import opensource.cached_dupe_scanner.ui.home.similarity.parsedDurationToleranceStep
import opensource.cached_dupe_scanner.ui.home.similarity.parsedExactThumbnailStep
import opensource.cached_dupe_scanner.ui.home.similarity.parsedMinSizeBytes
import opensource.cached_dupe_scanner.ui.home.similarity.sanitizeFrameSecondsInput
import opensource.cached_dupe_scanner.ui.home.similarity.sanitizeNumberDraftInput
import opensource.cached_dupe_scanner.ui.home.similarity.selectedSimilarityExperimentTemplate
import opensource.cached_dupe_scanner.ui.home.similarity.startSimilarityExperimentTask

private data class SimilarityClusterMembersState(
    val members: List<FileMetadata>,
    val durationMillisByNormalizedPath: Map<String, Long>,
    val complete: Boolean
)

private enum class SimilarityExperimentPane {
    List,
    Create,
    TemplateDetail,
    RunDetail
}

internal const val SIMILARITY_RUN_DETAIL_CLUSTER_FIRST_ITEM_INDEX = 3
private const val SIMILARITY_CLUSTER_PAGE_SIZE = 50
private const val DURATION_NEIGHBOR_MEMBER_PAGE_SIZE = 200
private const val SIMILARITY_CLUSTER_LOAD_MORE_BUFFER = 12
private const val DURATION_NEIGHBOR_MEMBER_LOAD_MORE_BUFFER = 50

internal fun similarityClusterLoadIndicatorText(
    isRunDetailPane: Boolean,
    totalClusterCount: Int,
    loadedClusterCount: Int,
    topVisibleItemIndex: Int,
    clustersLoading: Boolean,
    resultItemsLabel: String = "clusters"
): String? {
    if (!isRunDetailPane || totalClusterCount <= 0) return null
    val safeLoaded = loadedClusterCount.coerceIn(0, totalClusterCount)
    if (clustersLoading && safeLoaded == 0) {
        return "Loading 0/$totalClusterCount $resultItemsLabel"
    }
    val loadedForDisplay = safeLoaded.coerceAtLeast(1)
    val currentClusterIndex = (topVisibleItemIndex - SIMILARITY_RUN_DETAIL_CLUSTER_FIRST_ITEM_INDEX)
        .coerceAtLeast(0)
    return formatLoadProgressText(
        current = currentClusterIndex + 1,
        loaded = loadedForDisplay,
        total = totalClusterCount
    )
}

internal fun shouldLoadMoreSimilarityResults(
    isRunDetailPane: Boolean,
    isDurationNeighborList: Boolean,
    lastVisibleItemIndex: Int,
    totalItemsCount: Int,
    clustersLoading: Boolean,
    clustersExhausted: Boolean,
    durationNeighborMembersLoading: Boolean,
    durationNeighborMembersExhausted: Boolean
): Boolean {
    if (!isRunDetailPane || totalItemsCount <= 0) return false
    val loadMoreBuffer = if (isDurationNeighborList) {
        DURATION_NEIGHBOR_MEMBER_LOAD_MORE_BUFFER
    } else {
        SIMILARITY_CLUSTER_LOAD_MORE_BUFFER
    }
    val closeToEnd = lastVisibleItemIndex >= totalItemsCount - loadMoreBuffer
    if (!closeToEnd) return false
    return if (isDurationNeighborList) {
        !durationNeighborMembersLoading && !durationNeighborMembersExhausted
    } else {
        !clustersLoading && !clustersExhausted
    }
}

@Composable
fun SimilarityExperimentsScreen(
    repository: SimilarityExperimentRepository,
    appScope: CoroutineScope,
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
    var durationToleranceUnit by remember { mutableStateOf(SimilarityTimeUnit.S) }
    var durationRebuildToleranceInput by remember { mutableStateOf("1") }
    var durationRebuildToleranceUnit by remember { mutableStateOf(SimilarityTimeUnit.S) }
    var candidateCountText by remember { mutableStateOf("Loading candidate count...") }
    var runStatusText by remember { mutableStateOf("No experiment running.") }
    val runs = remember { mutableStateListOf<SimilarityExperimentRunEntity>() }
    val clusters = remember { mutableStateListOf<SimilarityClusterEntity>() }
    var clusterNextCursor by remember { mutableStateOf<SimilarityClusterCursor?>(null) }
    var clustersExhausted by remember { mutableStateOf(true) }
    val durationNeighborMembers = remember { mutableStateListOf<SimilarityClusterMember>() }
    var durationNeighborMemberNextOffset by remember { mutableStateOf(0) }
    var durationNeighborMembersExhausted by remember { mutableStateOf(true) }
    var durationNeighborStoredDurationCount by remember { mutableStateOf(0) }
    var durationNeighborSortDirection by remember { mutableStateOf(SortDirection.Asc) }
    var similarityGroupMemberSortKey by remember { mutableStateOf(ResultGroupMemberSortKey.Path) }
    var similarityGroupMemberSortDirection by remember { mutableStateOf(SortDirection.Asc) }
    var selectedTemplateId by remember { mutableStateOf<String?>(null) }
    var selectedRunExperimentId by remember { mutableStateOf<String?>(null) }
    var selectedClusterKey by remember { mutableStateOf<String?>(null) }
    var selectedDurationNeighborFile by remember { mutableStateOf<FileMetadata?>(null) }
    var pane by remember { mutableStateOf(SimilarityExperimentPane.List) }
    var clustersLoading by remember { mutableStateOf(false) }
    var durationNeighborMembersLoading by remember { mutableStateOf(false) }
    var durationNeighborRebuildRunning by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    var topVisibleItemIndex by remember { mutableStateOf(0) }
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
    val durationStep = parsedDurationToleranceStep(
        input = durationToleranceInput,
        unit = durationToleranceUnit
    )
    val selectedTemplate = selectedSimilarityExperimentTemplate(
        experiments = experiments,
        selectedId = selectedTemplateId
    )
    val selectedTemplateExactStep = selectedTemplate?.let(::executableExactThumbnailStep)
    val selectedTemplateDurationStep = selectedTemplate?.let(::executableDurationToleranceStep)
    val selectedTemplateDurationNeighborStep = selectedTemplate?.let(::executableDurationNeighborListStep)
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
            val tolerance = defaultTimeInputForUnit(
                millis = durationToleranceMillis(duration),
                unit = SimilarityTimeUnit.S
            )
            durationToleranceUnit = tolerance.unit
            durationToleranceInput = tolerance.input
        }
        executableDurationNeighborListStep(experiment)?.let { duration ->
            val tolerance = defaultTimeInputForUnit(
                millis = durationNeighborToleranceMillis(duration),
                unit = SimilarityTimeUnit.S
            )
            durationToleranceUnit = tolerance.unit
            durationToleranceInput = tolerance.input
        }
        val exact = executableExactThumbnailStep(experiment) ?: return
        frameSecondsInput = exact.frameSeconds.joinToString(",")
        resizeWidthInput = exact.resizeWidthPx.toString()
        resizeHeightInput = exact.resizeHeightPx.toString()
        quantizationEnabled = exact.quantizationLevels != null
        quantizationInput = exact.quantizationLevels?.toString() ?: "16"
        grayscale = exact.grayscale
    }

    fun loadMoreClusters() {
        if (clustersLoading || clustersExhausted) return
        val runId = selectedRunExperimentId ?: return
        val cursor = clusterNextCursor ?: return
        clustersLoading = true
        scope.launch {
            val page = withContext(Dispatchers.IO) {
                repository.loadClusterPageAfter(
                    experimentId = runId,
                    cursor = cursor,
                    limit = SIMILARITY_CLUSTER_PAGE_SIZE
                )
            }
            clusters.addAll(page.clusters)
            clusterNextCursor = page.nextCursor
            clustersExhausted = page.exhausted
            clustersLoading = false
        }
    }

    fun loadMoreDurationNeighborMembers(direction: SortDirection = durationNeighborSortDirection) {
        if (!isDurationNeighborListExperiment(selectedRunExperimentId)) return
        if (durationNeighborMembersLoading || durationNeighborMembersExhausted) return
        val cluster = clusters.firstOrNull() ?: return
        val totalMembers = selectedRun?.duplicateFileCount ?: cluster.fileCount
        val offset = durationNeighborMemberNextOffset
        durationNeighborMembersLoading = true
        scope.launch {
            try {
                val nextMembers = withContext(Dispatchers.IO) {
                    repository.listClusterMemberRows(
                        cluster = cluster,
                        offset = offset,
                        limit = DURATION_NEIGHBOR_MEMBER_PAGE_SIZE,
                        direction = direction
                    )
                }
                val nextDurationNeighborMemberNextOffset =
                    (offset + DURATION_NEIGHBOR_MEMBER_PAGE_SIZE).coerceAtMost(totalMembers)
                val knownPaths = durationNeighborMembers.map { member -> member.metadata.normalizedPath }.toHashSet()
                durationNeighborMembers.addAll(
                    nextMembers
                        .distinctBy { member -> member.metadata.normalizedPath }
                        .filter { member -> knownPaths.add(member.metadata.normalizedPath) }
                )
                durationNeighborMemberNextOffset = nextDurationNeighborMemberNextOffset
                durationNeighborMembersExhausted = nextDurationNeighborMemberNextOffset >= totalMembers
            } finally {
                durationNeighborMembersLoading = false
            }
        }
    }

    fun refreshStoredResults(preferredRunExperimentId: String? = selectedRunExperimentId) {
        clustersLoading = preferredRunExperimentId != null
        durationNeighborMembersLoading = false
        scope.launch {
            val nextRuns = withContext(Dispatchers.IO) { repository.listRuns() }
            val nextSelectedRun = preferredRunExperimentId?.let { selectedId ->
                nextRuns.firstOrNull { run -> run.experimentId == selectedId }
            }
            val nextClusterPage = nextSelectedRun?.let { run ->
                withContext(Dispatchers.IO) {
                    repository.loadFirstClusterPage(
                        experimentId = run.experimentId,
                        limit = SIMILARITY_CLUSTER_PAGE_SIZE
                    )
                }
            }
            val nextClusters = nextClusterPage?.clusters.orEmpty()
            val nextIsDurationNeighborList = isDurationNeighborListExperiment(nextSelectedRun?.experimentId)
            durationNeighborMembersLoading = nextIsDurationNeighborList
            val nextDurationNeighborMembers = if (nextIsDurationNeighborList) {
                val firstCluster = nextClusters.firstOrNull()
                if (firstCluster == null) {
                    emptyList()
                } else {
                    withContext(Dispatchers.IO) {
                        repository.listClusterMemberRows(
                            cluster = firstCluster,
                            offset = 0,
                            limit = DURATION_NEIGHBOR_MEMBER_PAGE_SIZE,
                            direction = durationNeighborSortDirection
                        )
                        .distinctBy { member -> member.metadata.normalizedPath }
                    }
                }
            } else {
                emptyList()
            }
            val nextDurationNeighborTotalMembers = if (nextIsDurationNeighborList) {
                nextSelectedRun?.duplicateFileCount ?: nextClusters.firstOrNull()?.fileCount ?: 0
            } else {
                0
            }
            val nextDurationNeighborMemberNextOffset = if (nextIsDurationNeighborList && nextClusters.isNotEmpty()) {
                DURATION_NEIGHBOR_MEMBER_PAGE_SIZE.coerceAtMost(nextDurationNeighborTotalMembers)
            } else {
                0
            }
            val nextDurationNeighborMembersExhausted = if (nextIsDurationNeighborList) {
                nextDurationNeighborMemberNextOffset >= nextDurationNeighborTotalMembers
            } else {
                true
            }
            val nextDurationNeighborStoredDurationCount = if (nextIsDurationNeighborList && nextSelectedRun != null) {
                withContext(Dispatchers.IO) {
                    repository.countDurationCandidates(nextSelectedRun.experimentId)
                }
            } else {
                0
            }
            runs.clear()
            runs.addAll(nextRuns)
            selectedRunExperimentId = nextSelectedRun?.experimentId
            clusters.clear()
            clusters.addAll(nextClusters)
            clusterNextCursor = nextClusterPage?.nextCursor
            clustersExhausted = nextClusterPage?.exhausted ?: true
            durationNeighborMembers.clear()
            durationNeighborMembers.addAll(nextDurationNeighborMembers)
            durationNeighborMemberNextOffset = nextDurationNeighborMemberNextOffset
            durationNeighborMembersExhausted = nextDurationNeighborMembersExhausted
            durationNeighborStoredDurationCount = nextDurationNeighborStoredDurationCount
            if (nextIsDurationNeighborList) {
                val nextToleranceInput = durationNeighborTimeInputForClusters(nextClusters)
                    ?: if (nextDurationNeighborStoredDurationCount == 0) {
                        durationNeighborTimeInputForRun(run = nextSelectedRun, clusters = emptyList())
                    } else {
                        null
                    }
                nextToleranceInput?.let { input ->
                    durationRebuildToleranceUnit = input.unit
                    durationRebuildToleranceInput = input.input
                }
            }
            clustersLoading = false
            durationNeighborMembersLoading = false
            if (selectedClusterKey != null && nextClusters.none { clusterStableKey(it) == selectedClusterKey }) {
                selectedClusterKey = null
            }
        }
    }

    fun applyDurationNeighborSortDirection(direction: SortDirection) {
        if (durationNeighborSortDirection == direction || durationNeighborMembersLoading) return
        durationNeighborSortDirection = direction
        selectedDurationNeighborFile = null
        durationNeighborMembers.clear()
        durationNeighborMemberNextOffset = 0
        durationNeighborMembersExhausted =
            (selectedRun?.duplicateFileCount ?: clusters.firstOrNull()?.fileCount ?: 0) <= 0
        scope.launch { listState.scrollToItem(0) }
        loadMoreDurationNeighborMembers(direction = direction)
    }

    fun startSelectedTemplateRun(draft: SimilarityRunRequestDraft) {
        val template = selectedTemplate ?: return
        when (
            val result = buildSimilarityRunRequest(
                template = template,
                draft = draft
            )
        ) {
            is SimilarityRunRequestBuildResult.Valid -> {
                startSimilarityExperimentTask(
                    repository = repository,
                    request = result.request,
                    scope = appScope,
                    taskCoordinator = taskCoordinator,
                    notificationController = notificationController,
                    onStatusText = { status -> runStatusText = status },
                    onRunFinished = { summary ->
                        selectedRunExperimentId = summary.experimentId
                        pane = SimilarityExperimentPane.RunDetail
                        refreshStoredResults(summary.experimentId)
                    }
                )
            }
            is SimilarityRunRequestBuildResult.Invalid -> {
                runStatusText = result.message
            }
        }
    }

    fun openListPane() {
        pane = SimilarityExperimentPane.List
        selectedRunExperimentId = null
        selectedClusterKey = null
        selectedDurationNeighborFile = null
        clusters.clear()
        clusterNextCursor = null
        clustersExhausted = true
        durationNeighborMembers.clear()
        durationNeighborMemberNextOffset = 0
        durationNeighborMembersExhausted = true
        durationNeighborStoredDurationCount = 0
        refreshStoredResults(null)
    }

    fun openCreatePane(clearTemplateSelection: Boolean = true) {
        pane = SimilarityExperimentPane.Create
        if (clearTemplateSelection) {
            selectedTemplateId = null
        }
        selectedRunExperimentId = null
        selectedClusterKey = null
        selectedDurationNeighborFile = null
        clusters.clear()
        clusterNextCursor = null
        clustersExhausted = true
        durationNeighborMembers.clear()
        durationNeighborMemberNextOffset = 0
        durationNeighborMembersExhausted = true
        durationNeighborStoredDurationCount = 0
    }

    fun openTemplateDetailPane(experiment: SimilarityExperimentSpec) {
        applyTemplateDefaults(experiment)
        pane = SimilarityExperimentPane.TemplateDetail
        selectedRunExperimentId = null
        selectedClusterKey = null
        selectedDurationNeighborFile = null
        clusters.clear()
        clusterNextCursor = null
        clustersExhausted = true
        durationNeighborMembers.clear()
        durationNeighborMemberNextOffset = 0
        durationNeighborMembersExhausted = true
        durationNeighborStoredDurationCount = 0
    }

    fun openRunPane(run: SimilarityExperimentRunEntity) {
        pane = SimilarityExperimentPane.RunDetail
        selectedRunExperimentId = run.experimentId
        selectedClusterKey = null
        selectedDurationNeighborFile = null
        clusters.clear()
        clusterNextCursor = null
        clustersExhausted = true
        durationNeighborMembers.clear()
        durationNeighborMemberNextOffset = 0
        durationNeighborMembersExhausted = true
        durationNeighborStoredDurationCount = 0
        refreshStoredResults(run.experimentId)
    }

    fun rebuildSelectedDurationNeighborList() {
        val run = selectedRun ?: return
        if (!isDurationNeighborListExperiment(run.experimentId) || durationNeighborRebuildRunning) return
        val step = parsedDurationNeighborListStep(
            input = durationRebuildToleranceInput,
            unit = durationRebuildToleranceUnit
        )
        durationNeighborRebuildRunning = true
        clustersLoading = true
        durationNeighborMembersLoading = true
        runStatusText = "Rebuilding duration-neighbor list from stored video lengths."
        appScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    repository.rebuildDurationNeighborListFromStoredDurations(
                        experimentId = run.experimentId,
                        neighborStep = step
                    )
                }
            }.onSuccess { summary ->
                durationToleranceInput = durationRebuildToleranceInput
                durationToleranceUnit = durationRebuildToleranceUnit
                runStatusText = "Rebuilt: ${summary.duplicateFileCount} videos match the current tolerance."
                refreshStoredResults(summary.experimentId)
            }.onFailure {
                clustersLoading = false
                durationNeighborMembersLoading = false
                runStatusText = "Duration-neighbor rebuild failed."
            }
            durationNeighborRebuildRunning = false
        }
    }

    LaunchedEffect(Unit) {
        refreshStoredResults(null)
    }

    LaunchedEffect(Unit) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { topVisibleItemIndex = it }
    }

    LaunchedEffect(
        pane,
        selectedRunExperimentId,
        clusters.size,
        clustersLoading,
        clustersExhausted,
        durationNeighborMembers.size,
        durationNeighborMemberNextOffset,
        durationNeighborSortDirection,
        durationNeighborMembersLoading,
        durationNeighborMembersExhausted
    ) {
        if (pane != SimilarityExperimentPane.RunDetail) return@LaunchedEffect
        snapshotFlow {
            val layoutInfo = listState.layoutInfo
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val totalItems = layoutInfo.totalItemsCount
            shouldLoadMoreSimilarityResults(
                isRunDetailPane = pane == SimilarityExperimentPane.RunDetail,
                isDurationNeighborList = isDurationNeighborListExperiment(selectedRunExperimentId),
                lastVisibleItemIndex = lastVisible,
                totalItemsCount = totalItems,
                clustersLoading = clustersLoading,
                clustersExhausted = clustersExhausted,
                durationNeighborMembersLoading = durationNeighborMembersLoading,
                durationNeighborMembersExhausted = durationNeighborMembersExhausted
            )
        }
            .distinctUntilChanged()
            .filter { it }
            .collect {
                if (isDurationNeighborListExperiment(selectedRunExperimentId)) {
                    loadMoreDurationNeighborMembers()
                } else {
                    loadMoreClusters()
                }
            }
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
            sortKey = similarityGroupMemberSortKey,
            sortDirection = similarityGroupMemberSortDirection,
            onApplySort = { key, direction ->
                similarityGroupMemberSortKey = key
                similarityGroupMemberSortDirection = direction
            },
            onBack = { selectedClusterKey = null },
            modifier = modifier
        )
        return
    }

    selectedDurationNeighborFile?.let { file ->
        FileDetailsDialogWithDeleteConfirm(
            file = file,
            showName = true,
            onOpen = {
                openFile(context, file.normalizedPath)
                selectedDurationNeighborFile = null
            },
            onDelete = {
                val handler = onDeleteFile ?: return@FileDetailsDialogWithDeleteConfirm false
                handler(file)
            },
            onDeleteResult = { deleted ->
                if (deleted) {
                    selectedDurationNeighborFile = null
                }
            },
            onDismiss = { selectedDurationNeighborFile = null }
        )
    }

    BackHandler(enabled = pane != SimilarityExperimentPane.List) {
        when (pane) {
            SimilarityExperimentPane.TemplateDetail -> openCreatePane(clearTemplateSelection = false)
            SimilarityExperimentPane.Create,
            SimilarityExperimentPane.RunDetail -> openListPane()
            SimilarityExperimentPane.List -> Unit
        }
    }

    val selectedRunIsDurationNeighbor = isDurationNeighborListExperiment(selectedRun?.experimentId)
    val resultItemsLabel = similarityResultItemsLabel(
        experimentId = selectedRun?.experimentId,
        count = if (selectedRunIsDurationNeighbor) selectedRun?.duplicateFileCount else selectedRun?.clusterCount
    )
    val resultItemCount = if (selectedRunIsDurationNeighbor) {
        selectedRun?.duplicateFileCount ?: 0
    } else {
        selectedRun?.clusterCount ?: 0
    }
    val loadedResultItemCount = if (selectedRunIsDurationNeighbor) {
        durationNeighborMembers.size
    } else {
        clusters.size
    }
    val resultItemsLoading = clustersLoading || durationNeighborMembersLoading
    val loadIndicatorText = similarityClusterLoadIndicatorText(
        isRunDetailPane = pane == SimilarityExperimentPane.RunDetail,
        totalClusterCount = resultItemCount,
        loadedClusterCount = loadedResultItemCount,
        topVisibleItemIndex = topVisibleItemIndex,
        clustersLoading = resultItemsLoading,
        resultItemsLabel = resultItemsLabel
    )

    SimilarityExperimentLazyPane(
        listState = listState,
        loadIndicatorText = loadIndicatorText,
        modifier = modifier
    ) {
            when (pane) {
                SimilarityExperimentPane.List -> {
                    item(key = "top_bar") {
                        AppTopBar(
                            title = "Similarity experiments",
                            onBack = onBack
                        )
                    }
                    item(key = "new_experiment") {
                        Button(
                            onClick = { openCreatePane() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("New experiment")
                        }
                    }
                    item(key = "run_list_header") {
                        ExperimentRunsHeader(hasRuns = runs.isNotEmpty())
                    }
                    if (runs.isNotEmpty()) {
                        items(
                            items = runs,
                            key = { run -> run.experimentId }
                        ) { run ->
                            ExperimentRunCard(
                                run = run,
                                onSelectRun = ::openRunPane
                            )
                        }
                    }
                }
                SimilarityExperimentPane.Create -> {
                    item(key = "top_bar") {
                        AppTopBar(
                            title = "New experiment",
                            onBack = ::openListPane
                        )
                    }
                    item(key = "template_header") {
                        ExperimentTemplatesHeader(hasTemplates = experiments.isNotEmpty())
                    }
                    items(
                        items = experiments,
                        key = { experiment -> experiment.id }
                    ) { experiment ->
                        ExperimentTemplateCard(
                            experiment = experiment,
                            selected = experiment.id == selectedTemplateId,
                            onSelectExperiment = ::openTemplateDetailPane
                        )
                    }
                }
                SimilarityExperimentPane.TemplateDetail -> {
                    item(key = "top_bar") {
                        AppTopBar(
                            title = selectedTemplate?.name ?: "Experiment template",
                            onBack = { openCreatePane(clearTemplateSelection = false) }
                        )
                    }
                    if (selectedTemplate != null && selectedTemplateExactStep != null) {
                        item(key = "exact_thumbnail_run") {
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
                                    startSelectedTemplateRun(
                                        SimilarityRunRequestDraft(
                                            mediaScope = mediaScope,
                                            minSizeBytes = minSizeBytes,
                                            exactThumbnailStep = exactStep
                                        )
                                    )
                                },
                                onCancel = {
                                    taskCoordinator.requestCancel(TaskArea.Similarity)
                                }
                            )
                        }
                    } else if (selectedTemplate != null && selectedTemplateDurationStep != null) {
                        item(key = "duration_tolerance_run") {
                            DurationToleranceRunCard(
                                experimentName = selectedTemplate.name,
                                description = "Runs a cached-video experiment that extracts each video's duration and clusters candidates whose durations fall within the configured tolerance.",
                                minSizeInput = minSizeInput,
                                onMinSizeInputChange = { minSizeInput = sanitizeNumberDraftInput(it) },
                                minSizeUnit = minSizeUnit,
                                onMinSizeUnitChange = { minSizeUnit = it },
                                toleranceInput = durationToleranceInput,
                                onToleranceInputChange = { durationToleranceInput = sanitizeNumberDraftInput(it) },
                                toleranceUnit = durationToleranceUnit,
                                onToleranceUnitChange = { durationToleranceUnit = it },
                                toleranceDescription = "Tolerance is the maximum duration gap inside one cluster. Use 0 for exact millisecond duration matches.",
                                candidateCountText = candidateCountText,
                                runStatusText = displayedRunStatusText,
                                isRunning = activeSimilarityTask != null,
                                runButtonText = "Run duration experiment",
                                onRun = {
                                    startSelectedTemplateRun(
                                        SimilarityRunRequestDraft(
                                            mediaScope = SimilarityMediaScope.Video,
                                            minSizeBytes = minSizeBytes,
                                            durationToleranceStep = durationStep
                                        )
                                    )
                                },
                                onCancel = {
                                    taskCoordinator.requestCancel(TaskArea.Similarity)
                                }
                            )
                        }
                    } else if (selectedTemplate != null && selectedTemplateDurationNeighborStep != null) {
                        item(key = "duration_neighbor_run") {
                            DurationToleranceRunCard(
                                experimentName = selectedTemplate.name,
                                description = "Runs a cached-video experiment that builds one duration-sorted list, keeps only adjacent neighbors inside the tolerance, and omits videos without a nearby neighbor.",
                                minSizeInput = minSizeInput,
                                onMinSizeInputChange = { minSizeInput = sanitizeNumberDraftInput(it) },
                                minSizeUnit = minSizeUnit,
                                onMinSizeUnitChange = { minSizeUnit = it },
                                toleranceInput = durationToleranceInput,
                                onToleranceInputChange = { durationToleranceInput = sanitizeNumberDraftInput(it) },
                                toleranceUnit = durationToleranceUnit,
                                onToleranceUnitChange = { durationToleranceUnit = it },
                                toleranceDescription = "Tolerance is the maximum duration gap between adjacent sorted videos. The displayed result is one filtered list; videos with no adjacent neighbor inside this gap are not listed.",
                                candidateCountText = candidateCountText,
                                runStatusText = displayedRunStatusText,
                                isRunning = activeSimilarityTask != null,
                                runButtonText = "Run duration neighbor list",
                                onRun = {
                                    startSelectedTemplateRun(
                                        SimilarityRunRequestDraft(
                                            mediaScope = SimilarityMediaScope.Video,
                                            minSizeBytes = minSizeBytes,
                                            durationNeighborListStep = parsedDurationNeighborListStep(
                                                input = durationToleranceInput,
                                                unit = durationToleranceUnit
                                            )
                                        )
                                    )
                                },
                                onCancel = {
                                    taskCoordinator.requestCancel(TaskArea.Similarity)
                                }
                            )
                        }
                    } else if (selectedTemplate != null) {
                        item(key = "selected_method") {
                            SelectedExperimentMethodCard(experiment = selectedTemplate)
                        }
                    } else {
                        item(key = "missing_template") {
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
                }
                SimilarityExperimentPane.RunDetail -> {
                    item(key = "top_bar") {
                        AppTopBar(
                            title = selectedRun?.experimentName ?: "Experiment detail",
                            onBack = ::openListPane
                        )
                    }
                    if (selectedRun == null) {
                        item(key = "missing_run") {
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
                        }
                    } else {
                        item(key = "run_summary") {
                            SimilarityRunSummaryCard(run = selectedRun)
                        }
                        if (selectedRunIsDurationNeighbor) {
                            item(key = "duration_neighbor_rebuild") {
                                DurationNeighborStoredRebuildCard(
                                    toleranceInput = durationRebuildToleranceInput,
                                    onToleranceInputChange = {
                                        durationRebuildToleranceInput = sanitizeNumberDraftInput(it)
                                    },
                                    toleranceUnit = durationRebuildToleranceUnit,
                                    onToleranceUnitChange = { durationRebuildToleranceUnit = it },
                                    storedDurationCount = durationNeighborStoredDurationCount,
                                    runStatusText = displayedRunStatusText,
                                    isRunning = activeSimilarityTask != null ||
                                        durationNeighborRebuildRunning ||
                                        resultItemsLoading,
                                    onRebuild = ::rebuildSelectedDurationNeighborList
                                )
                            }
                            item(key = "duration_neighbor_sort") {
                                DurationNeighborSortDirectionCard(
                                    direction = durationNeighborSortDirection,
                                    enabled = !durationNeighborMembersLoading,
                                    onDirectionChange = ::applyDurationNeighborSortDirection
                                )
                            }
                        }
                        item(key = "cluster_header") {
                            StoredSimilarityResultsHeader(
                                selectedRun = selectedRun,
                                clusters = clusters,
                                durationNeighborVideoCount = durationNeighborMembers.size,
                                isLoading = resultItemsLoading
                            )
                        }
                        if (resultItemsLoading && loadedResultItemCount == 0) {
                            item(key = "clusters_loading") {
                                SimilarityClusterLoadingIndicator(
                                    loadedClusterCount = loadedResultItemCount,
                                    totalClusterCount = resultItemCount,
                                    resultItemsLabel = resultItemsLabel
                                )
                            }
                        } else if (selectedRunIsDurationNeighbor) {
                            if (durationNeighborMembers.isEmpty()) {
                                item(key = "duration_neighbor_empty") {
                                    Text(
                                        text = "No duration-neighbor list entries found for the latest run.",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            } else {
                                items(
                                    items = durationNeighborMembers,
                                    key = { member -> "duration-neighbor-video:${member.metadata.normalizedPath}" }
                                ) { member ->
                                    DurationNeighborVideoCard(
                                        member = member,
                                        deletedPaths = deletedPaths,
                                        imageLoader = imageLoader,
                                        keepLoadedThumbnailsInMemory = keepLoadedThumbnailsInMemory,
                                        previewThumbnailSizeDp = groupCardThumbnailSizeDp,
                                        rememberedPreviewCache = rememberedPreviewCache,
                                        showFullPaths = showFullPaths,
                                        onOpen = {
                                            selectedDurationNeighborFile = member.metadata
                                        }
                                    )
                                }
                            }
                        } else if (clusters.isEmpty()) {
                            item(key = "clusters_empty") {
                                Text(
                                    text = "No duplicate-like similarity clusters found for the latest run.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        } else {
                            items(
                                items = clusters,
                                key = { cluster -> clusterStableKey(cluster) }
                            ) { cluster ->
                                SimilarityClusterCard(
                                    repository = repository,
                                    cluster = cluster,
                                    deletedPaths = deletedPaths,
                                    imageLoader = imageLoader,
                                    keepLoadedThumbnailsInMemory = keepLoadedThumbnailsInMemory,
                                    previewThumbnailSizeDp = groupCardThumbnailSizeDp,
                                    rememberedPreviewCache = rememberedPreviewCache,
                                    showFullPaths = showFullPaths,
                                    loadedClusterMembers = loadedClusterMembers,
                                    clusterMemberLoadErrors = clusterMemberLoadErrors,
                                    onOpen = { selectedClusterKey = clusterStableKey(cluster) }
                                )
                            }
                        }
                    }
                }
            }
    }
}

@Composable
private fun SimilarityExperimentLazyPane(
    listState: LazyListState,
    loadIndicatorText: String?,
    modifier: Modifier = Modifier,
    content: LazyListScope.() -> Unit
) {
    Box(modifier = modifier) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(Spacing.screenPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(end = ScrollbarDefaults.ThumbWidth + 8.dp),
            content = content
        )

        VerticalLazyScrollbar(
            listState = listState,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .padding(end = 4.dp)
        )
        TopRightLoadIndicator(text = loadIndicatorText)
    }
}

@Composable
private fun ExperimentRunsHeader(hasRuns: Boolean) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(text = "Experiment list", style = MaterialTheme.typography.titleMedium)
        if (!hasRuns) {
            Text(text = "No saved experiment runs yet.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ExperimentRunCard(
    run: SimilarityExperimentRunEntity,
    onSelectRun: (SimilarityExperimentRunEntity) -> Unit
) {
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

@Composable
private fun ExperimentTemplatesHeader(hasTemplates: Boolean) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(text = "Experiment templates", style = MaterialTheme.typography.titleMedium)
        if (!hasTemplates) {
            Text(text = "No experiment templates are available.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ExperimentTemplateCard(
    experiment: SimilarityExperimentSpec,
    selected: Boolean,
    onSelectExperiment: (SimilarityExperimentSpec) -> Unit
) {
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
private fun <T> UnitDropdown(
    label: String,
    selectedLabel: String,
    options: List<T>,
    optionLabel: (T) -> String,
    onOptionSelected: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        Button(onClick = { expanded = true }) {
            Text("$label: $selectedLabel")
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = {
                        expanded = false
                        onOptionSelected(option)
                    }
                )
            }
        }
    }
}

@Composable
private fun DurationToleranceRunCard(
    experimentName: String,
    description: String,
    minSizeInput: String,
    onMinSizeInputChange: (String) -> Unit,
    minSizeUnit: SimilaritySizeUnit,
    onMinSizeUnitChange: (SimilaritySizeUnit) -> Unit,
    toleranceInput: String,
    onToleranceInputChange: (String) -> Unit,
    toleranceUnit: SimilarityTimeUnit,
    onToleranceUnitChange: (SimilarityTimeUnit) -> Unit,
    toleranceDescription: String,
    candidateCountText: String,
    runStatusText: String,
    isRunning: Boolean,
    runButtonText: String,
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
                text = description,
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
                UnitDropdown(
                    label = "Unit",
                    selectedLabel = minSizeUnit.label,
                    options = SimilaritySizeUnit.entries,
                    optionLabel = { it.label },
                    onOptionSelected = onMinSizeUnitChange,
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
            }
            Text(
                text = "Minimum size filters cached video candidates before duration extraction. The default is 100 MB, and Unit opens a menu for B, KB, MB, and GB.",
                style = MaterialTheme.typography.bodySmall
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = toleranceInput,
                    onValueChange = onToleranceInputChange,
                    label = { Text("Duration tolerance") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                UnitDropdown(
                    label = "Unit",
                    selectedLabel = toleranceUnit.label,
                    options = SimilarityTimeUnit.entries,
                    optionLabel = { it.label },
                    onOptionSelected = onToleranceUnitChange,
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
            }
            Text(
                text = "$toleranceDescription Unit opens a menu for seconds, milliseconds, and minutes.",
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
                Text(if (isRunning) "Cancel run" else runButtonText)
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
                UnitDropdown(
                    label = "Unit",
                    selectedLabel = minSizeUnit.label,
                    options = SimilaritySizeUnit.entries,
                    optionLabel = { it.label },
                    onOptionSelected = onMinSizeUnitChange,
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
            }
            Text(
                text = "Minimum size filters candidates before signature extraction. The default is 100 MB, and Unit opens a menu for B, KB, MB, and GB.",
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
    val isDurationNeighborList = isDurationNeighborListExperiment(run.experimentId)
    val resultItemsLabel = similarityResultItemsLabel(
        experimentId = run.experimentId,
        count = if (isDurationNeighborList) run.duplicateFileCount else run.clusterCount
    )
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
                text = if (isDurationNeighborList) {
                    "${run.duplicateFileCount} $resultItemsLabel · ${run.skippedCount} skipped"
                } else {
                    "${run.clusterCount} $resultItemsLabel · ${run.duplicateFileCount} files · ${run.skippedCount} skipped"
                },
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
private fun DurationNeighborStoredRebuildCard(
    toleranceInput: String,
    onToleranceInputChange: (String) -> Unit,
    toleranceUnit: SimilarityTimeUnit,
    onToleranceUnitChange: (SimilarityTimeUnit) -> Unit,
    storedDurationCount: Int,
    runStatusText: String,
    isRunning: Boolean,
    onRebuild: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Rebuild duration list",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "$storedDurationCount stored video lengths",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = toleranceInput,
                    onValueChange = onToleranceInputChange,
                    label = { Text("Tolerance") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                UnitDropdown(
                    label = "Unit",
                    selectedLabel = toleranceUnit.label,
                    options = SimilarityTimeUnit.entries,
                    optionLabel = { it.label },
                    onOptionSelected = onToleranceUnitChange,
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
            }
            Text(
                text = runStatusText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = onRebuild,
                enabled = !isRunning && storedDurationCount > 0,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Rebuild from stored lengths")
            }
        }
    }
}

@Composable
private fun DurationNeighborSortDirectionCard(
    direction: SortDirection,
    enabled: Boolean,
    onDirectionChange: (SortDirection) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Duration order",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Sort by extracted video length.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RadioOptionRow(
                    option = SortDirection.Asc,
                    selected = direction,
                    label = "Ascending",
                    onSelect = { selectedDirection ->
                        if (enabled) onDirectionChange(selectedDirection)
                    },
                    modifier = Modifier.weight(1f)
                )
                RadioOptionRow(
                    option = SortDirection.Desc,
                    selected = direction,
                    label = "Descending",
                    onSelect = { selectedDirection ->
                        if (enabled) onDirectionChange(selectedDirection)
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun StoredSimilarityResultsHeader(
    selectedRun: SimilarityExperimentRunEntity?,
    clusters: List<SimilarityClusterEntity>,
    durationNeighborVideoCount: Int = 0,
    isLoading: Boolean
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val resultItemsLabel = similarityResultItemsLabel(
            experimentId = selectedRun?.experimentId,
            count = if (isDurationNeighborListExperiment(selectedRun?.experimentId)) {
                selectedRun?.duplicateFileCount
            } else {
                selectedRun?.clusterCount
            }
        )
        val isDurationNeighborList = isDurationNeighborListExperiment(selectedRun?.experimentId)
        Text(
            text = if (isDurationNeighborList) {
                "Selected duration-neighbor videos"
            } else {
                "Selected experiment clusters"
            },
            style = MaterialTheme.typography.titleMedium
        )
        if (selectedRun == null) {
            Text(
                text = "Select or run an experiment to review its saved results.",
                style = MaterialTheme.typography.bodySmall
            )
            return@Column
        }
        Text(
            text = if (isDurationNeighborList) {
                "${selectedRun.experimentName}: ${selectedRun.duplicateFileCount} $resultItemsLabel, " +
                    "${selectedRun.skippedCount} skipped."
            } else {
                "${selectedRun.experimentName}: ${selectedRun.clusterCount} $resultItemsLabel, " +
                    "${selectedRun.duplicateFileCount} files, ${selectedRun.skippedCount} skipped."
            },
            style = MaterialTheme.typography.bodySmall
        )
        if (isLoading) {
            Text(
                text = if (isDurationNeighborList) {
                    "Loading duration-neighbor videos..."
                } else {
                    "Loading experiment $resultItemsLabel..."
                },
                style = MaterialTheme.typography.bodySmall
            )
            return@Column
        }
        Text(
            text = if (isDurationNeighborList) {
                "Loaded $durationNeighborVideoCount/${selectedRun.duplicateFileCount} $resultItemsLabel."
            } else {
                "Loaded ${clusters.size}/${selectedRun.clusterCount} $resultItemsLabel."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SimilarityClusterLoadingIndicator(
    loadedClusterCount: Int,
    totalClusterCount: Int,
    resultItemsLabel: String = "clusters"
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.compactGap)
    ) {
        LinearProgressIndicator(
            progress = {
                if (totalClusterCount <= 0) {
                    0f
                } else {
                    (loadedClusterCount.toFloat() / totalClusterCount.toFloat()).coerceIn(0f, 1f)
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = "Loading experiment $resultItemsLabel " +
                "${loadedClusterCount.coerceAtLeast(0)}/${totalClusterCount.coerceAtLeast(0)}...",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DurationNeighborVideoCard(
    member: SimilarityClusterMember,
    deletedPaths: Set<String>,
    imageLoader: ImageLoader,
    keepLoadedThumbnailsInMemory: Boolean,
    previewThumbnailSizeDp: Dp,
    rememberedPreviewCache: MutableMap<String, ImageBitmap>,
    showFullPaths: Boolean,
    onOpen: () -> Unit
) {
    val file = member.metadata
    val deleted = deletedPaths.contains(file.normalizedPath)
    val previewCandidates = mediaPreviewCandidates(
        files = listOf(file),
        deletedPaths = deletedPaths
    )

    Card(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth(),
        colors = if (deleted) {
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
            GroupPreviewThumbnail(
                candidatePaths = previewCandidates,
                previewMemoryKey = "duration-neighbor-video:${file.normalizedPath}",
                rememberedPreviewCache = rememberedPreviewCache,
                imageLoader = imageLoader,
                keepLoadedInMemory = keepLoadedThumbnailsInMemory,
                contentDescription = "Thumbnail",
                modifier = Modifier
                    .height(previewThumbnailSizeDp)
                    .width(previewThumbnailSizeDp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = durationNeighborVideoTitle(member = member, showFullPaths = showFullPaths),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${formatBytesWithExact(file.sizeBytes)} · ${formatDate(file.lastModifiedMillis)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
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
    val durationMillisByNormalizedPath = memberState?.durationMillisByNormalizedPath.orEmpty()
    val loadError = clusterMemberLoadErrors[clusterKey]

    LaunchedEffect(clusterKey) {
        if (loadedClusterMembers.containsKey(clusterKey) || clusterMemberLoadErrors.containsKey(clusterKey)) {
            return@LaunchedEffect
        }
        val previewMembers = runCatching {
            withContext(Dispatchers.IO) {
                repository.listClusterMemberRows(
                    cluster = cluster,
                    limit = SIMILARITY_CLUSTER_PREVIEW_MEMBER_LIMIT
                )
            }
        }
        previewMembers.fold(
            onSuccess = {
                loadedClusterMembers[clusterKey] = SimilarityClusterMembersState(
                    members = it.map { member -> member.metadata },
                    durationMillisByNormalizedPath = similarityMemberDurationMap(it),
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
    val durationNeighborExplanation = durationNeighborClusterExplanation(cluster.signature)

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
                    text = if (durationNeighborExplanation != null) {
                        "${cluster.fileCount} listed videos · Total ${formatBytes(cluster.totalBytes)}"
                    } else {
                        "${cluster.fileCount} files · Total ${formatBytes(cluster.totalBytes)}"
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = exactHashExplanation?.let(::exactHashClusterSummary)
                        ?: durationNeighborExplanation?.let(::durationNeighborClusterSummary)
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
                if (durationNeighborExplanation != null) {
                    Text(
                        text = "One duration-sorted list; isolated videos are omitted.",
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
                    showFullPaths = showFullPaths,
                    durationMillisByNormalizedPath = durationMillisByNormalizedPath,
                    preserveOrder = durationNeighborExplanation != null
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
    showFullPaths: Boolean,
    durationMillisByNormalizedPath: Map<String, Long> = emptyMap(),
    preserveOrder: Boolean = false
) {
    similarityClusterPreviewLineTexts(
        members = members,
        showFullPaths = showFullPaths,
        durationMillisByNormalizedPath = durationMillisByNormalizedPath,
        preserveOrder = preserveOrder
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
    sortKey: ResultGroupMemberSortKey,
    sortDirection: SortDirection,
    onApplySort: (ResultGroupMemberSortKey, SortDirection) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val detailListState = rememberLazyListState()
    val clusterKey = remember(cluster.experimentId, cluster.signature) { clusterStableKey(cluster) }
    val memberState = loadedClusterMembers[clusterKey]
    val members = memberState?.members.orEmpty()
    val loadError = clusterMemberLoadErrors[clusterKey]
    var isLoading by remember(clusterKey) { mutableStateOf(memberState?.complete != true) }
    var loadAttempt by remember(clusterKey) { mutableStateOf(0) }
    val exactHashExplanation = exactThumbnailClusterExplanation(cluster.signature)
    val durationNeighborExplanation = durationNeighborClusterExplanation(cluster.signature)

    LaunchedEffect(clusterKey, loadAttempt) {
        if (loadedClusterMembers[clusterKey]?.complete == true) {
            isLoading = false
            return@LaunchedEffect
        }
        isLoading = true
        val result = runCatching {
            withContext(Dispatchers.IO) {
                repository.listClusterMemberRows(cluster = cluster)
            }
        }
        result.fold(
            onSuccess = {
                loadedClusterMembers[clusterKey] = SimilarityClusterMembersState(
                    members = it.map { member -> member.metadata },
                    durationMillisByNormalizedPath = similarityMemberDurationMap(it),
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
        LazyColumn(
            state = detailListState,
            modifier = Modifier
                .fillMaxSize()
                .padding(Spacing.screenPadding),
            contentPadding = PaddingValues(end = ScrollbarDefaults.ThumbWidth + 8.dp)
        ) {
            item {
                AppTopBar(
                    title = if (durationNeighborExplanation != null) {
                        "Similarity list detail"
                    } else {
                        "Similarity cluster detail"
                    },
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
                            title = if (durationNeighborExplanation != null) "List detail" else "Group detail",
                            memberCount = cluster.fileCount,
                            totalBytes = cluster.totalBytes,
                            summaryLines = similarityClusterDetailLines(
                                cluster = cluster,
                                exactHashExplanation = exactHashExplanation,
                                durationNeighborExplanation = durationNeighborExplanation
                            ),
                            members = members,
                            deletedPaths = deletedPaths,
                            imageLoader = imageLoader,
                            keepLoadedThumbnailsInMemory = keepLoadedThumbnailsInMemory,
                            rememberedPreviewCache = rememberedPreviewCache,
                            previewMemoryKey = clusterPreviewMemoryKey(cluster),
                            previewHeight = previewHeight,
                            showMemberThumbnails = true,
                            sortKey = sortKey,
                            sortDirection = sortDirection,
                            sortingEnabled = durationNeighborExplanation == null,
                            onApplySort = onApplySort,
                            onDeleteFile = onDeleteFile
                        )
                    }
                }
            }
        }

        VerticalLazyScrollbar(
            listState = detailListState,
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

internal data class DurationNeighborClusterExplanation(
    val toleranceMillis: Long,
    val minDurationMillis: Long,
    val maxDurationMillis: Long
)

internal fun durationNeighborClusterExplanation(signature: String): DurationNeighborClusterExplanation? {
    val parts = signature.split(":", limit = 3)
    if (parts.size != 3 || !isDurationNeighborListSignature(signature)) return null
    val toleranceMillis = parts[1].toLongOrNull()?.coerceAtLeast(0L) ?: return null
    val minDurationMillis = parts[2].substringBefore('-').toLongOrNull()?.coerceAtLeast(0L) ?: return null
    val maxDurationMillis = parts[2].substringAfter('-', missingDelimiterValue = "")
        .toLongOrNull()
        ?.coerceAtLeast(minDurationMillis)
        ?: return null
    return DurationNeighborClusterExplanation(
        toleranceMillis = toleranceMillis,
        minDurationMillis = minDurationMillis,
        maxDurationMillis = maxDurationMillis
    )
}

internal fun durationNeighborTimeInputForRun(
    run: SimilarityExperimentRunEntity?,
    clusters: List<SimilarityClusterEntity>
): SimilarityTimeInput? {
    val toleranceMillis = durationNeighborToleranceMillisForClusters(clusters)
        ?: run?.experimentId
            ?.takeIf(::isDurationNeighborListExperiment)
            ?.substringAfterLast('-', missingDelimiterValue = "")
            ?.toLongOrNull()
    return toleranceMillis?.let { millis ->
        defaultTimeInputForUnit(
            millis = millis,
            unit = SimilarityTimeUnit.S
        )
    }
}

internal fun durationNeighborTimeInputForClusters(
    clusters: List<SimilarityClusterEntity>
): SimilarityTimeInput? {
    return durationNeighborToleranceMillisForClusters(clusters)?.let { millis ->
        defaultTimeInputForUnit(
            millis = millis,
            unit = SimilarityTimeUnit.S
        )
    }
}

private fun durationNeighborToleranceMillisForClusters(
    clusters: List<SimilarityClusterEntity>
): Long? {
    return clusters.asSequence()
        .mapNotNull { cluster -> durationNeighborClusterExplanation(cluster.signature)?.toleranceMillis }
        .firstOrNull()
}

internal fun exactHashClusterSummary(explanation: ExactThumbnailClusterExplanation): String {
    return "Exact hash: ${mediaScopeLabel(explanation.mediaScope)}, ${framesLabel(explanation)}, ${explanation.resize}, " +
        "${colorModeLabel(explanation.colorMode)}, ${quantizationLabel(explanation.quantization)}"
}

internal fun durationNeighborClusterSummary(explanation: DurationNeighborClusterExplanation): String {
    return "Duration neighbor list: ${durationMillisLabel(explanation.minDurationMillis)} - " +
        "${durationMillisLabel(explanation.maxDurationMillis)}, tolerance ${durationMillisLabel(explanation.toleranceMillis)}"
}

internal fun similarityClusterDetailLines(
    cluster: SimilarityClusterEntity,
    exactHashExplanation: ExactThumbnailClusterExplanation?,
    durationNeighborExplanation: DurationNeighborClusterExplanation? = null
): List<String> {
    if (exactHashExplanation == null && durationNeighborExplanation == null) {
        return listOf(
            "Similarity signature ${cluster.signature}",
            "Snapshot ${formatDate(cluster.updatedAtMillis)}"
        )
    }

    if (durationNeighborExplanation != null) {
        return listOf(
            "List rule: duration-sorted neighbor filter",
            "Why included: the full candidate set is sorted by extracted duration, then only videos with a previous or next item inside the tolerance are shown.",
            "Visible duration span: ${durationMillisLabel(durationNeighborExplanation.minDurationMillis)} - ${durationMillisLabel(durationNeighborExplanation.maxDurationMillis)}",
            "Tolerance: ${durationMillisLabel(durationNeighborExplanation.toleranceMillis)}",
            "Order: sorted by extracted video duration",
            "Snapshot ${formatDate(cluster.updatedAtMillis)}"
        )
    }

    requireNotNull(exactHashExplanation)
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
    durationMillisByNormalizedPath: Map<String, Long> = emptyMap(),
    itemsPerLine: Int = SIMILARITY_CLUSTER_PREVIEW_ITEMS_PER_LINE,
    maxItems: Int = SIMILARITY_CLUSTER_PREVIEW_TEXT_MEMBER_LIMIT,
    preserveOrder: Boolean = false
): List<String> {
    val orderedMembers = if (preserveOrder) {
        members
    } else {
        members.sortedBy { file -> file.normalizedPath }
    }
    return orderedMembers
        .take(maxItems.coerceAtLeast(0))
        .chunked(itemsPerLine.coerceAtLeast(1))
        .map { row ->
            row.joinToString("  •  ") { file ->
                similarityClusterPreviewMemberText(
                    file = file,
                    showFullPaths = showFullPaths,
                    durationMillisByNormalizedPath = durationMillisByNormalizedPath
                )
            }
        }
}

private fun similarityMemberDurationMap(
    members: List<SimilarityClusterMember>
): Map<String, Long> {
    return members.mapNotNull { member ->
        member.durationMillis?.let { durationMillis ->
            member.metadata.normalizedPath to durationMillis
        }
    }.toMap()
}

private fun similarityClusterPreviewMemberText(
    file: FileMetadata,
    showFullPaths: Boolean,
    durationMillisByNormalizedPath: Map<String, Long>
): String {
    val path = formatPath(file.normalizedPath, showFullPaths)
    val durationMillis = durationMillisByNormalizedPath[file.normalizedPath] ?: return path
    return "${durationMillisLabel(durationMillis)} · $path"
}

internal fun durationNeighborVideoTitle(
    member: SimilarityClusterMember,
    showFullPaths: Boolean
): String {
    val path = formatPath(member.metadata.normalizedPath, showFullPaths)
    val durationMillis = member.durationMillis ?: return path
    return "${durationMillisLabel(durationMillis)} · $path"
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

private fun durationMillisLabel(value: Long): String {
    val safeValue = value.coerceAtLeast(0L)
    val seconds = safeValue / 1_000L
    val millis = safeValue % 1_000L
    return if (millis == 0L) {
        "${seconds}s"
    } else {
        "$seconds.${millis.toString().padStart(3, '0')}s"
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

private fun similarityResultItemsLabel(
    experimentId: String?,
    count: Int? = null
): String {
    val isDurationNeighborList = isDurationNeighborListExperiment(experimentId)
    if (count == 1) {
        return if (isDurationNeighborList) "video" else "cluster"
    }
    return if (isDurationNeighborList) "videos" else "clusters"
}

private fun isDurationNeighborListExperiment(experimentId: String?): Boolean {
    return experimentId?.startsWith(DURATION_NEIGHBOR_LIST_EXPERIMENT_ID_PREFIX) == true
}

private fun isDurationNeighborListSignature(signature: String): Boolean {
    return signature.startsWith("duration-neighbor-list-v1:") ||
        signature.startsWith("duration-neighbor-v1:")
}

private const val SIMILARITY_CLUSTER_PREVIEW_MEMBER_LIMIT = 10
private const val SIMILARITY_CLUSTER_PREVIEW_TEXT_MEMBER_LIMIT = 4
private const val SIMILARITY_CLUSTER_PREVIEW_ITEMS_PER_LINE = 2
private const val SIMILARITY_SIGNATURE_SAMPLE_DISPLAY_LIMIT = 32
private const val DURATION_NEIGHBOR_LIST_EXPERIMENT_ID_PREFIX = "video-duration-neighbor"
