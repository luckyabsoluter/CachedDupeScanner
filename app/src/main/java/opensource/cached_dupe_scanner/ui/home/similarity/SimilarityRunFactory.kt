package opensource.cached_dupe_scanner.ui.home.similarity

import opensource.cached_dupe_scanner.cache.SimilarityExperimentRunEntity
import opensource.cached_dupe_scanner.core.DurationNeighborListStep
import opensource.cached_dupe_scanner.core.DurationToleranceStep
import opensource.cached_dupe_scanner.core.ExactThumbnailHashStep
import opensource.cached_dupe_scanner.core.SimilarityExperimentSpec
import opensource.cached_dupe_scanner.core.SimilarityMediaScope
import opensource.cached_dupe_scanner.core.durationNeighborToleranceMillis
import opensource.cached_dupe_scanner.core.durationToleranceMillis
import opensource.cached_dupe_scanner.storage.SimilarityExperimentRunRequest

internal data class SimilarityRunRequestDraft(
    val mediaScope: SimilarityMediaScope,
    val minSizeBytes: Long,
    val exactThumbnailStep: ExactThumbnailHashStep? = null,
    val durationToleranceStep: DurationToleranceStep? = null,
    val durationNeighborListStep: DurationNeighborListStep? = null
)

internal sealed class SimilarityRunRequestBuildResult {
    data class Valid(
        val request: SimilarityExperimentRunRequest
    ) : SimilarityRunRequestBuildResult()

    data class Invalid(
        val message: String
    ) : SimilarityRunRequestBuildResult()
}

internal fun buildSimilarityRunRequest(
    template: SimilarityExperimentSpec,
    draft: SimilarityRunRequestDraft
): SimilarityRunRequestBuildResult {
    val exactStep = executableExactThumbnailStep(template)
    if (exactStep != null) {
        val requestedStep = draft.exactThumbnailStep ?: exactStep
        if (requestedStep.frameSeconds.isEmpty()) {
            return SimilarityRunRequestBuildResult.Invalid("Add at least one frame timestamp.")
        }
        return SimilarityRunRequestBuildResult.Valid(
            SimilarityExperimentRunRequest(
                experiment = exactThumbnailExperimentForRun(
                    mediaScope = draft.mediaScope,
                    minSizeBytes = draft.minSizeBytes,
                    step = requestedStep
                ),
                mediaScope = draft.mediaScope,
                minSizeBytes = draft.minSizeBytes,
                exactThumbnailStep = requestedStep
            )
        )
    }

    val durationStep = executableDurationToleranceStep(template)
    if (durationStep != null) {
        val requestedStep = draft.durationToleranceStep ?: durationStep
        return SimilarityRunRequestBuildResult.Valid(
            SimilarityExperimentRunRequest(
                experiment = durationToleranceExperimentForRun(
                    minSizeBytes = draft.minSizeBytes,
                    step = requestedStep
                ),
                mediaScope = SimilarityMediaScope.Video,
                minSizeBytes = draft.minSizeBytes,
                durationToleranceStep = requestedStep
            )
        )
    }

    val durationNeighborStep = executableDurationNeighborListStep(template)
    if (durationNeighborStep != null) {
        val requestedStep = draft.durationNeighborListStep ?: durationNeighborStep
        return SimilarityRunRequestBuildResult.Valid(
            SimilarityExperimentRunRequest(
                experiment = durationNeighborListExperimentForRun(
                    minSizeBytes = draft.minSizeBytes,
                    step = requestedStep
                ),
                mediaScope = SimilarityMediaScope.Video,
                minSizeBytes = draft.minSizeBytes,
                durationNeighborListStep = requestedStep
            )
        )
    }

    return SimilarityRunRequestBuildResult.Invalid("Selected experiment template is not directly runnable.")
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

internal fun durationNeighborListExperimentForRun(
    minSizeBytes: Long,
    step: DurationNeighborListStep
): SimilarityExperimentSpec {
    val toleranceMillis = durationNeighborToleranceMillis(step)
    return SimilarityExperimentSpec(
        id = "video-duration-neighbor-${minSizeBytes}-${toleranceMillis}",
        name = "Video duration neighbor list",
        description = "Runtime-configured duration neighbor-list experiment.",
        defaultMinSizeBytes = minSizeBytes,
        mediaScope = SimilarityMediaScope.Video,
        steps = listOf(step)
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

internal fun executableDurationNeighborListStep(experiment: SimilarityExperimentSpec): DurationNeighborListStep? {
    if (experiment.steps.size != 1) return null
    return experiment.steps.singleOrNull() as? DurationNeighborListStep
}

internal fun executableTemplateKind(experiment: SimilarityExperimentSpec): String {
    return when {
        executableExactThumbnailStep(experiment) != null -> "Executable exact-hash experiment"
        executableDurationToleranceStep(experiment) != null -> "Executable duration experiment"
        executableDurationNeighborListStep(experiment) != null -> "Executable duration neighbor-list experiment"
        else -> "Methodology template"
    }
}
