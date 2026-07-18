package opensource.cached_dupe_scanner.ui.home

import opensource.cached_dupe_scanner.cache.DuplicateGroupEntity
import opensource.cached_dupe_scanner.core.FileMetadata
import java.math.BigInteger
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

internal enum class ResultsFilterTarget(val label: String) {
    GroupItemCount("Group count"),
    FileName("File name"),
    FolderPath("Folder"),
    ModifiedTime("Modified time"),
    SameFolder("All same folder"),
    SameFileSize("All same size"),
    SameResolution("All same resolution"),
    DurationFromAverage("All near average duration")
}

internal enum class ResultsFilterDurationUnit(val label: String) {
    Seconds("s"),
    Milliseconds("ms")
}

internal enum class ResultsFilterClusterMode(val label: String) {
    All("Match all"),
    Any("Match any")
}

internal enum class ResultsFilterMemberMatchMode(val label: String) {
    Any("Any member"),
    All("All members")
}

internal enum class ResultsFilterTextOperator(val label: String) {
    StartsWith("Starts with"),
    EndsWith("Ends with"),
    Contains("Contains"),
    Equals("Equals")
}

internal enum class ResultsFilterCountOperator(val label: String) {
    AtLeast("At least"),
    AtMost("At most"),
    Equals("Equals")
}

internal enum class ResultsFilterTimeOperator(val label: String) {
    OnOrAfter("On or after"),
    OnOrBefore("On or before"),
    OnDate("At date/time")
}

internal data class ResultsFilterRule(
    val id: String,
    val enabled: Boolean = true,
    val target: ResultsFilterTarget = ResultsFilterTarget.FileName,
    val memberMatchMode: ResultsFilterMemberMatchMode = ResultsFilterMemberMatchMode.Any,
    val textOperator: ResultsFilterTextOperator = ResultsFilterTextOperator.Contains,
    val countOperator: ResultsFilterCountOperator = ResultsFilterCountOperator.AtLeast,
    val timeOperator: ResultsFilterTimeOperator = ResultsFilterTimeOperator.OnOrAfter,
    val value: String = "",
    val durationToleranceSeconds: String = "",
    val durationToleranceMilliseconds: String = ""
)

internal data class ResultsFilterCluster(
    val id: String,
    val name: String,
    val enabled: Boolean = true,
    val mode: ResultsFilterClusterMode = ResultsFilterClusterMode.All,
    val rules: List<ResultsFilterRule> = listOf(createResultsFilterRule())
)

internal data class ResultsFilterDefinition(
    val clusters: List<ResultsFilterCluster> = emptyList()
)

internal val FILE_FILTER_TARGETS: Set<ResultsFilterTarget> = setOf(
    ResultsFilterTarget.FileName,
    ResultsFilterTarget.FolderPath,
    ResultsFilterTarget.ModifiedTime
)

internal val RESULT_FILTER_TARGETS: Set<ResultsFilterTarget> =
    ResultsFilterTarget.entries.toSet() - setOf(
        ResultsFilterTarget.DurationFromAverage,
        ResultsFilterTarget.SameResolution
    )

internal val SIMILARITY_FILTER_TARGETS: Set<ResultsFilterTarget> =
    ResultsFilterTarget.entries.toSet()

internal fun ResultsFilterTarget.supportsMemberMatchMode(): Boolean {
    return this == ResultsFilterTarget.FileName ||
        this == ResultsFilterTarget.FolderPath ||
        this == ResultsFilterTarget.ModifiedTime
}

private object ResultsFilterIdGenerator {
    private var nextId = 1L

    fun next(prefix: String): String {
        val current = nextId
        nextId += 1
        return "${prefix}_$current"
    }

    fun observePersistedId(id: String) {
        val persisted = id.substringAfterLast('_', "").toLongOrNull() ?: return
        if (persisted >= nextId) {
            nextId = persisted + 1
        }
    }
}

internal fun createResultsFilterRule(
    target: ResultsFilterTarget = ResultsFilterTarget.FileName
): ResultsFilterRule {
    return ResultsFilterRule(
        id = ResultsFilterIdGenerator.next("rule"),
        target = target
    )
}

internal fun createResultsFilterCluster(
    name: String? = null
): ResultsFilterCluster {
    val clusterId = ResultsFilterIdGenerator.next("cluster")
    val clusterIndex = clusterId.substringAfterLast('_').toIntOrNull() ?: 1
    return ResultsFilterCluster(
        id = clusterId,
        name = name ?: "Cluster $clusterIndex"
    )
}

internal fun ResultsFilterDefinition.hasActiveRules(): Boolean {
    return hasActiveRules(supportedTargets = ResultsFilterTarget.entries.toSet())
}

internal fun ResultsFilterDefinition.hasActiveRules(
    supportedTargets: Set<ResultsFilterTarget>
): Boolean {
    return clusters.any { cluster ->
        cluster.enabled && configuredRules(cluster, supportedTargets).isNotEmpty()
    }
}

internal fun ResultsFilterDefinition.hasActiveTarget(
    target: ResultsFilterTarget,
    supportedTargets: Set<ResultsFilterTarget> = ResultsFilterTarget.entries.toSet()
): Boolean {
    return clusters.any { cluster ->
        cluster.enabled && configuredRules(cluster, supportedTargets).any { rule ->
            rule.target == target
        }
    }
}

internal fun ResultsFilterDefinition.requiresGroupMembers(
    supportedTargets: Set<ResultsFilterTarget> = ResultsFilterTarget.entries.toSet()
): Boolean {
    return clusters.any { cluster ->
        cluster.enabled && configuredRules(cluster, supportedTargets).any { rule ->
            rule.target != ResultsFilterTarget.GroupItemCount
        }
    }
}

internal fun ResultsFilterDefinition.activeClusterCount(): Int {
    return activeClusterCount(supportedTargets = ResultsFilterTarget.entries.toSet())
}

internal fun ResultsFilterDefinition.activeClusterCount(
    supportedTargets: Set<ResultsFilterTarget>
): Int {
    return clusters.count { cluster ->
        cluster.enabled && configuredRules(cluster, supportedTargets).isNotEmpty()
    }
}

internal fun ResultsFilterDefinition.activeRuleCount(): Int {
    return activeRuleCount(supportedTargets = ResultsFilterTarget.entries.toSet())
}

internal fun ResultsFilterDefinition.activeRuleCount(
    supportedTargets: Set<ResultsFilterTarget>
): Int {
    return clusters.sumOf { cluster ->
        if (!cluster.enabled) {
            0
        } else {
            configuredRules(cluster, supportedTargets).size
        }
    }
}

internal fun summarizeResultsFilter(definition: ResultsFilterDefinition): String {
    return summarizeResultsFilter(
        definition = definition,
        supportedTargets = ResultsFilterTarget.entries.toSet()
    )
}

internal fun summarizeResultsFilter(
    definition: ResultsFilterDefinition,
    supportedTargets: Set<ResultsFilterTarget>
): String {
    val clusterCount = definition.activeClusterCount(supportedTargets)
    val ruleCount = definition.activeRuleCount(supportedTargets)
    return when {
        clusterCount <= 0 -> "No filters"
        ruleCount == 1 -> "1 active rule"
        else -> "$clusterCount clusters · $ruleCount rules"
    }
}

internal fun matchesResultsFilter(
    definition: ResultsFilterDefinition,
    group: DuplicateGroupEntity,
    members: List<FileMetadata>,
    supportedTargets: Set<ResultsFilterTarget> = RESULT_FILTER_TARGETS
): Boolean {
    val activeClusters = activeResultFilterClusters(definition, supportedTargets)
    if (activeClusters.isEmpty()) return true
    return activeClusters.all { (cluster, rules) ->
        val results = rules.map { rule ->
            matchesResultsFilterRule(
                rule = rule,
                group = group,
                members = members
            )
        }
        when (cluster.mode) {
            ResultsFilterClusterMode.All -> results.all { it }
            ResultsFilterClusterMode.Any -> results.any { it }
        }
    }
}

internal fun matchesResultsFilterPagedMembers(
    definition: ResultsFilterDefinition,
    group: DuplicateGroupEntity,
    supportedTargets: Set<ResultsFilterTarget> = RESULT_FILTER_TARGETS,
    memberPages: () -> Sequence<List<FileMetadata>>,
    onPreviewMembers: (List<FileMetadata>) -> Unit = {}
): Boolean {
    val matcher = ResultsFilterPagedMatcher(
        definition = definition,
        group = group,
        supportedTargets = supportedTargets
    )
    matcher.resolved()?.let { return it }

    val preview = mutableListOf<FileMetadata>()
    memberPages().forEach { page ->
        if (preview.size < 10) {
            preview += page.take(10 - preview.size)
        }
        matcher.consume(page)
        matcher.resolved()?.let { result ->
            if (preview.isNotEmpty()) onPreviewMembers(preview)
            return result
        }
    }

    if (preview.isNotEmpty()) onPreviewMembers(preview)
    return matcher.finish()
}

internal class ResultsFilterPagedMatcher(
    definition: ResultsFilterDefinition,
    group: DuplicateGroupEntity,
    supportedTargets: Set<ResultsFilterTarget> = RESULT_FILTER_TARGETS
) {
    private val activeClusters = activeResultFilterClusters(definition, supportedTargets)
    private val immediateResult = when {
        activeClusters.isEmpty() -> true
        !definition.requiresGroupMembers(supportedTargets) -> matchesResultsFilter(
            definition = definition,
            group = group,
            members = emptyList(),
            supportedTargets = supportedTargets
        )
        else -> null
    }
    private val clusters = if (immediateResult == null) {
        activeClusters.map { (cluster, rules) ->
            ResultFilterClusterProgress(
                mode = cluster.mode,
                rules = rules.map { rule -> ResultFilterRuleProgress(rule = rule, group = group) }
            )
        }
    } else {
        emptyList()
    }

    fun consume(page: List<FileMetadata>) {
        if (immediateResult != null) return
        clusters.forEach { cluster -> cluster.consume(page) }
    }

    fun resolved(): Boolean? {
        immediateResult?.let { result -> return result }
        val results = clusters.map { cluster -> cluster.result(ended = false) }
        return when {
            results.any { result -> result == false } -> false
            results.all { result -> result == true } -> true
            else -> null
        }
    }

    fun finish(): Boolean {
        immediateResult?.let { result -> return result }
        return clusters.all { cluster -> cluster.result(ended = true) == true }
    }
}

private fun activeResultFilterClusters(
    definition: ResultsFilterDefinition,
    supportedTargets: Set<ResultsFilterTarget>
): List<Pair<ResultsFilterCluster, List<ResultsFilterRule>>> {
    return definition.clusters.mapNotNull { cluster ->
        if (!cluster.enabled) {
            null
        } else {
            val rules = configuredRules(cluster, supportedTargets)
            if (rules.isEmpty()) null else cluster to rules
        }
    }
}

private class ResultFilterClusterProgress(
    private val mode: ResultsFilterClusterMode,
    private val rules: List<ResultFilterRuleProgress>
) {
    fun consume(page: List<FileMetadata>) {
        rules.forEach { rule -> rule.consume(page) }
    }

    fun result(ended: Boolean): Boolean? {
        val results = rules.map { rule -> rule.result(ended) }
        return when (mode) {
            ResultsFilterClusterMode.All -> when {
                results.any { it == false } -> false
                results.all { it == true } -> true
                else -> null
            }
            ResultsFilterClusterMode.Any -> when {
                results.any { it == true } -> true
                results.all { it == false } -> false
                else -> null
            }
        }
    }
}

private class ResultFilterRuleProgress(
    private val rule: ResultsFilterRule,
    group: DuplicateGroupEntity
) {
    private var matched = false
    private var memberMismatch = false
    private var sawMember = false
    private var firstFolder: String? = null
    private var folderMismatch = false
    private var firstFileSize: Long? = null
    private var fileSizeMismatch = false
    private var firstResolution: Pair<Int, Int>? = null
    private var resolutionMismatch = false
    private var resolutionInvalid = false
    private val durationAccumulator = DurationAverageAccumulator()
    private val groupResult: Boolean? = if (rule.target == ResultsFilterTarget.GroupItemCount) {
        val threshold = rule.value.trim().toIntOrNull()
        if (threshold == null) {
            false
        } else {
            when (rule.countOperator) {
                ResultsFilterCountOperator.AtLeast -> group.fileCount >= threshold
                ResultsFilterCountOperator.AtMost -> group.fileCount <= threshold
                ResultsFilterCountOperator.Equals -> group.fileCount == threshold
            }
        }
    } else {
        null
    }

    fun consume(page: List<FileMetadata>) {
        val memberRuleResolved = if (rule.target.supportsMemberMatchMode()) {
            when (rule.memberMatchMode) {
                ResultsFilterMemberMatchMode.Any -> matched
                ResultsFilterMemberMatchMode.All -> memberMismatch
            }
        } else {
            false
        }
        if (groupResult != null || memberRuleResolved) return
        page.forEach { member ->
            when (rule.target) {
                ResultsFilterTarget.FileName -> {
                    consumeMemberMatch(
                        matchesTextOperator(
                            source = fileNameFromPath(member.normalizedPath),
                            expected = rule.value,
                            operator = rule.textOperator
                        )
                    )
                }
                ResultsFilterTarget.FolderPath -> {
                    consumeMemberMatch(
                        matchesTextOperator(
                            source = folderPathFromPath(member.normalizedPath),
                            expected = rule.value,
                            operator = rule.textOperator
                        )
                    )
                }
                ResultsFilterTarget.ModifiedTime -> {
                    val timeValue = parseResultsFilterTimeValue(rule.value) ?: return@forEach
                    consumeMemberMatch(
                        matchesTimeOperator(
                            sourceMillis = member.lastModifiedMillis,
                            expected = timeValue,
                            operator = rule.timeOperator
                        )
                    )
                }
                ResultsFilterTarget.SameFolder -> {
                    sawMember = true
                    val folder = folderPathFromPath(member.normalizedPath).lowercase()
                    val currentFirst = firstFolder
                    if (currentFirst == null) {
                        firstFolder = folder
                    } else if (currentFirst != folder) {
                        folderMismatch = true
                    }
                }
                ResultsFilterTarget.SameFileSize -> {
                    sawMember = true
                    val currentFirst = firstFileSize
                    if (currentFirst == null) {
                        firstFileSize = member.sizeBytes
                    } else if (currentFirst != member.sizeBytes) {
                        fileSizeMismatch = true
                    }
                }
                ResultsFilterTarget.SameResolution -> {
                    sawMember = true
                    val resolution = member.mediaResolution()
                    if (resolution == null) {
                        resolutionInvalid = true
                    } else {
                        val currentFirst = firstResolution
                        if (currentFirst == null) {
                            firstResolution = resolution
                        } else if (currentFirst != resolution) {
                            resolutionMismatch = true
                        }
                    }
                }
                ResultsFilterTarget.DurationFromAverage -> {
                    durationAccumulator.consume(member.durationMillis)
                }
                ResultsFilterTarget.GroupItemCount -> Unit
            }
        }
    }

    private fun consumeMemberMatch(matches: Boolean) {
        sawMember = true
        if (matches) {
            matched = true
        } else {
            memberMismatch = true
        }
    }

    private fun memberMatchResult(ended: Boolean): Boolean? {
        return when (rule.memberMatchMode) {
            ResultsFilterMemberMatchMode.Any -> when {
                matched -> true
                ended -> false
                else -> null
            }
            ResultsFilterMemberMatchMode.All -> when {
                memberMismatch -> false
                ended -> sawMember
                else -> null
            }
        }
    }

    fun result(ended: Boolean): Boolean? {
        groupResult?.let { return it }
        return when (rule.target) {
            ResultsFilterTarget.FileName,
            ResultsFilterTarget.FolderPath,
            ResultsFilterTarget.ModifiedTime -> memberMatchResult(ended)
            ResultsFilterTarget.SameFolder -> when {
                folderMismatch -> false
                ended -> sawMember
                else -> null
            }
            ResultsFilterTarget.SameFileSize -> when {
                fileSizeMismatch -> false
                ended -> sawMember
                else -> null
            }
            ResultsFilterTarget.SameResolution -> when {
                resolutionInvalid || resolutionMismatch -> false
                ended -> sawMember
                else -> null
            }
            ResultsFilterTarget.DurationFromAverage -> when {
                durationAccumulator.invalid -> false
                ended -> rule.durationToleranceMillis()?.let(durationAccumulator::matches) ?: false
                else -> null
            }
            ResultsFilterTarget.GroupItemCount -> groupResult
        }
    }
}

private class DurationAverageAccumulator {
    private var count = 0L
    private var sum = 0L
    private var overflowSum: BigInteger? = null
    private var minimum = Long.MAX_VALUE
    private var maximum = Long.MIN_VALUE
    var invalid: Boolean = false
        private set

    fun consume(durationMillis: Long?) {
        if (durationMillis == null || durationMillis < 0L) {
            invalid = true
            return
        }
        count += 1L
        minimum = minOf(minimum, durationMillis)
        maximum = maxOf(maximum, durationMillis)
        val currentOverflowSum = overflowSum
        if (currentOverflowSum != null) {
            overflowSum = currentOverflowSum.add(BigInteger.valueOf(durationMillis))
        } else {
            try {
                sum = Math.addExact(sum, durationMillis)
            } catch (_: ArithmeticException) {
                overflowSum = BigInteger.valueOf(sum).add(BigInteger.valueOf(durationMillis))
            }
        }
    }

    fun matches(toleranceMillis: Long): Boolean {
        if (invalid || count <= 0L || toleranceMillis < 0L) return false
        val countValue = BigInteger.valueOf(count)
        val sumValue = overflowSum ?: BigInteger.valueOf(sum)
        val toleranceValue = BigInteger.valueOf(toleranceMillis).multiply(countValue)
        val minimumDeviation = sumValue
            .subtract(BigInteger.valueOf(minimum).multiply(countValue))
            .abs()
        val maximumDeviation = BigInteger.valueOf(maximum)
            .multiply(countValue)
            .subtract(sumValue)
            .abs()
        return minimumDeviation <= toleranceValue && maximumDeviation <= toleranceValue
    }
}

private fun configuredRules(
    cluster: ResultsFilterCluster,
    supportedTargets: Set<ResultsFilterTarget> = ResultsFilterTarget.entries.toSet()
): List<ResultsFilterRule> {
    return cluster.rules.filter { rule ->
        rule.enabled &&
            supportedTargets.contains(rule.target) &&
            isResultsFilterRuleConfigured(rule)
    }
}

private fun isResultsFilterRuleConfigured(rule: ResultsFilterRule): Boolean {
    return when (rule.target) {
        ResultsFilterTarget.GroupItemCount -> rule.value.trim().toIntOrNull() != null
        ResultsFilterTarget.FileName -> rule.value.isNotBlank()
        ResultsFilterTarget.FolderPath -> rule.value.isNotBlank()
        ResultsFilterTarget.ModifiedTime -> parseResultsFilterTimeValue(rule.value) != null
        ResultsFilterTarget.SameFolder -> true
        ResultsFilterTarget.SameFileSize -> true
        ResultsFilterTarget.SameResolution -> true
        ResultsFilterTarget.DurationFromAverage -> rule.durationToleranceMillis() != null
    }
}

private fun matchesResultsFilterRule(
    rule: ResultsFilterRule,
    group: DuplicateGroupEntity,
    members: List<FileMetadata>
): Boolean {
    return when (rule.target) {
        ResultsFilterTarget.GroupItemCount -> {
            val threshold = rule.value.trim().toIntOrNull() ?: return false
            when (rule.countOperator) {
                ResultsFilterCountOperator.AtLeast -> group.fileCount >= threshold
                ResultsFilterCountOperator.AtMost -> group.fileCount <= threshold
                ResultsFilterCountOperator.Equals -> group.fileCount == threshold
            }
        }
        ResultsFilterTarget.FileName -> {
            membersMatchRule(members, rule.memberMatchMode) { member ->
                matchesTextOperator(
                    source = fileNameFromPath(member.normalizedPath),
                    expected = rule.value,
                    operator = rule.textOperator
                )
            }
        }
        ResultsFilterTarget.FolderPath -> {
            membersMatchRule(members, rule.memberMatchMode) { member ->
                matchesTextOperator(
                    source = folderPathFromPath(member.normalizedPath),
                    expected = rule.value,
                    operator = rule.textOperator
                )
            }
        }
        ResultsFilterTarget.ModifiedTime -> {
            val timeValue = parseResultsFilterTimeValue(rule.value) ?: return false
            membersMatchRule(members, rule.memberMatchMode) { member ->
                matchesTimeOperator(
                    sourceMillis = member.lastModifiedMillis,
                    expected = timeValue,
                    operator = rule.timeOperator
                )
            }
        }
        ResultsFilterTarget.SameFolder -> {
            if (members.isEmpty()) {
                false
            } else {
                members.map { folderPathFromPath(it.normalizedPath).lowercase() }
                    .distinct()
                    .size == 1
            }
        }
        ResultsFilterTarget.SameFileSize -> {
            members.isNotEmpty() && members.map { member -> member.sizeBytes }.distinct().size == 1
        }
        ResultsFilterTarget.SameResolution -> {
            val resolutions = members.map { member -> member.mediaResolution() }
            resolutions.isNotEmpty() && resolutions.none { resolution -> resolution == null } &&
                resolutions.distinct().size == 1
        }
        ResultsFilterTarget.DurationFromAverage -> {
            val toleranceMillis = rule.durationToleranceMillis() ?: return false
            DurationAverageAccumulator().run {
                members.forEach { member -> consume(member.durationMillis) }
                matches(toleranceMillis)
            }
        }
    }
}

private inline fun membersMatchRule(
    members: List<FileMetadata>,
    mode: ResultsFilterMemberMatchMode,
    predicate: (FileMetadata) -> Boolean
): Boolean {
    if (members.isEmpty()) return false
    return when (mode) {
        ResultsFilterMemberMatchMode.Any -> members.any(predicate)
        ResultsFilterMemberMatchMode.All -> members.all(predicate)
    }
}

internal fun ResultsFilterRule.durationToleranceMillis(): Long? {
    val secondsText = durationToleranceSeconds.trim()
    val millisecondsText = durationToleranceMilliseconds.trim()
    if (secondsText.isEmpty() && millisecondsText.isEmpty()) return null
    return try {
        when {
            secondsText.isNotEmpty() && millisecondsText.isNotEmpty() -> {
                val seconds = secondsText.toLongOrNull() ?: return null
                val milliseconds = millisecondsText.toLongOrNull() ?: return null
                if (seconds < 0L || milliseconds !in 0L..999L) return null
                Math.addExact(Math.multiplyExact(seconds, 1_000L), milliseconds)
            }
            secondsText.isNotEmpty() -> {
                val seconds = secondsText.toLongOrNull()?.takeIf { value -> value >= 0L } ?: return null
                Math.multiplyExact(seconds, 1_000L)
            }
            else -> millisecondsText.toLongOrNull()?.takeIf { value -> value >= 0L }
        }
    } catch (_: ArithmeticException) {
        null
    }
}

internal fun ResultsFilterRule.durationToleranceUnit(): ResultsFilterDurationUnit {
    return if (durationToleranceMilliseconds.isNotBlank()) {
        ResultsFilterDurationUnit.Milliseconds
    } else {
        ResultsFilterDurationUnit.Seconds
    }
}

internal fun ResultsFilterRule.durationToleranceInput(): String {
    val secondsText = durationToleranceSeconds.trim()
    val millisecondsText = durationToleranceMilliseconds.trim()
    return if (secondsText.isNotEmpty() && millisecondsText.isNotEmpty()) {
        durationToleranceMillis()?.toString().orEmpty()
    } else if (millisecondsText.isNotEmpty()) {
        millisecondsText
    } else {
        secondsText
    }
}

internal fun ResultsFilterRule.withDurationToleranceInput(input: String): ResultsFilterRule {
    return when (durationToleranceUnit()) {
        ResultsFilterDurationUnit.Seconds -> copy(
            durationToleranceSeconds = input,
            durationToleranceMilliseconds = ""
        )
        ResultsFilterDurationUnit.Milliseconds -> copy(
            durationToleranceSeconds = "",
            durationToleranceMilliseconds = input
        )
    }
}

internal fun ResultsFilterRule.withDurationToleranceUnit(
    unit: ResultsFilterDurationUnit
): ResultsFilterRule {
    val input = durationToleranceInput()
    return when (unit) {
        ResultsFilterDurationUnit.Seconds -> copy(
            durationToleranceSeconds = input,
            durationToleranceMilliseconds = ""
        )
        ResultsFilterDurationUnit.Milliseconds -> copy(
            durationToleranceSeconds = "",
            durationToleranceMilliseconds = input
        )
    }
}

private fun FileMetadata.mediaResolution(): Pair<Int, Int>? {
    val width = widthPixels?.takeIf { value -> value > 0 } ?: return null
    val height = heightPixels?.takeIf { value -> value > 0 } ?: return null
    return width to height
}

internal fun matchesTextOperator(
    source: String,
    expected: String,
    operator: ResultsFilterTextOperator
): Boolean {
    val term = expected.trim()
    if (term.isEmpty()) return false
    return when (operator) {
        ResultsFilterTextOperator.StartsWith -> source.startsWith(term, ignoreCase = true)
        ResultsFilterTextOperator.EndsWith -> source.endsWith(term, ignoreCase = true)
        ResultsFilterTextOperator.Contains -> source.contains(term, ignoreCase = true)
        ResultsFilterTextOperator.Equals -> source.equals(term, ignoreCase = true)
    }
}

internal fun matchesFileFilter(
    definition: ResultsFilterDefinition,
    file: FileMetadata
): Boolean {
    val activeClusters = definition.clusters.mapNotNull { cluster ->
        if (!cluster.enabled) {
            null
        } else {
            val rules = configuredRules(cluster, FILE_FILTER_TARGETS)
            if (rules.isEmpty()) null else cluster to rules
        }
    }
    if (activeClusters.isEmpty()) return true
    return activeClusters.all { (cluster, rules) ->
        val results = rules.map { rule ->
            when (rule.target) {
                ResultsFilterTarget.FileName -> {
                    matchesTextOperator(
                        source = fileNameFromPath(file.normalizedPath),
                        expected = rule.value,
                        operator = rule.textOperator
                    )
                }

                ResultsFilterTarget.FolderPath -> {
                    matchesTextOperator(
                        source = folderPathFromPath(file.normalizedPath),
                        expected = rule.value,
                        operator = rule.textOperator
                    )
                }

                ResultsFilterTarget.ModifiedTime -> {
                    val timeValue = parseResultsFilterTimeValue(rule.value) ?: return@map false
                    matchesTimeOperator(
                        sourceMillis = file.lastModifiedMillis,
                        expected = timeValue,
                        operator = rule.timeOperator
                    )
                }

                else -> false
            }
        }
        when (cluster.mode) {
            ResultsFilterClusterMode.All -> results.all { it }
            ResultsFilterClusterMode.Any -> results.any { it }
        }
    }
}

internal fun fileNameFromPath(path: String): String {
    val normalized = path.replace('\\', '/')
    val lastSlash = normalized.lastIndexOf('/')
    return if (lastSlash >= 0) normalized.substring(lastSlash + 1) else normalized
}

internal fun folderPathFromPath(path: String): String {
    val normalized = path.replace('\\', '/')
    val lastSlash = normalized.lastIndexOf('/')
    return if (lastSlash >= 0) normalized.substring(0, lastSlash) else ""
}

internal data class ResultsFilterTimeValue(
    val startMillis: Long,
    val endMillisExclusive: Long
)

internal fun parseResultsFilterTimeValue(value: String): ResultsFilterTimeValue? {
    val trimmed = value.trim()
    if (trimmed.isEmpty()) return null
    trimmed.toLongOrNull()?.let { millis ->
        return ResultsFilterTimeValue(
            startMillis = millis,
            endMillisExclusive = millis + 1L
        )
    }
    return DATE_TIME_PATTERNS.firstNotNullOfOrNull { pattern ->
        val parsed = parseUtcDateTime(trimmed, pattern.pattern) ?: return@firstNotNullOfOrNull null
        ResultsFilterTimeValue(
            startMillis = parsed,
            endMillisExclusive = parsed + pattern.durationMillis
        )
    }
}

internal fun matchesTimeOperator(
    sourceMillis: Long,
    expected: ResultsFilterTimeValue,
    operator: ResultsFilterTimeOperator
): Boolean {
    return when (operator) {
        ResultsFilterTimeOperator.OnOrAfter -> sourceMillis >= expected.startMillis
        ResultsFilterTimeOperator.OnOrBefore -> sourceMillis < expected.endMillisExclusive
        ResultsFilterTimeOperator.OnDate -> sourceMillis >= expected.startMillis &&
            sourceMillis < expected.endMillisExclusive
    }
}

internal fun resultsFilterDefinitionToJson(definition: ResultsFilterDefinition): String {
    return buildString {
        append("v1\n")
        definition.clusters.forEach { cluster ->
            append(
                listOf(
                    "cluster",
                    cluster.id,
                    cluster.enabled.toString(),
                    cluster.mode.name,
                    encodeFilterToken(cluster.name)
                ).joinToString("\t")
            )
            append('\n')
            cluster.rules.forEach { rule ->
                append(
                    listOf(
                        "rule",
                        cluster.id,
                        rule.id,
                        rule.enabled.toString(),
                        rule.target.name,
                        rule.textOperator.name,
                        rule.countOperator.name,
                        encodeFilterToken(rule.value),
                        rule.timeOperator.name,
                        encodeFilterToken(rule.durationToleranceSeconds),
                        encodeFilterToken(rule.durationToleranceMilliseconds),
                        rule.memberMatchMode.name
                    ).joinToString("\t")
                )
                append('\n')
            }
        }
    }
}

internal fun resultsFilterDefinitionFromJson(json: String?): ResultsFilterDefinition {
    if (json.isNullOrBlank()) {
        return ResultsFilterDefinition()
    }
    return runCatching {
        val lines = json.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toList()
        if (lines.isEmpty()) {
            return@runCatching ResultsFilterDefinition()
        }
        if (lines.first() != "v1") {
            return@runCatching ResultsFilterDefinition()
        }
        val clusterRecords = mutableListOf<FilterClusterRecord>()
        val rulesByClusterId = linkedMapOf<String, MutableList<ResultsFilterRule>>()

        lines.drop(1).forEach { line ->
            val parts = line.split('\t')
            when (parts.firstOrNull()) {
                "cluster" -> {
                    if (parts.size < 5) return@forEach
                    val clusterId = parts[1].ifBlank {
                        ResultsFilterIdGenerator.next("cluster")
                    }
                    ResultsFilterIdGenerator.observePersistedId(clusterId)
                    clusterRecords.add(
                        FilterClusterRecord(
                            id = clusterId,
                            enabled = parts[2].toBoolean(),
                            mode = parseResultsFilterClusterMode(parts[3]),
                            name = decodeFilterToken(parts[4])
                                .ifBlank { "Cluster ${clusterRecords.size + 1}" }
                        )
                    )
                }
                "rule" -> {
                    if (parts.size < 8) return@forEach
                    val clusterId = parts[1]
                    val ruleId = parts[2].ifBlank {
                        ResultsFilterIdGenerator.next("rule")
                    }
                    ResultsFilterIdGenerator.observePersistedId(ruleId)
                    rulesByClusterId.getOrPut(clusterId) { mutableListOf() }
                        .add(
                            ResultsFilterRule(
                                id = ruleId,
                                enabled = parts[3].toBoolean(),
                                target = parseResultsFilterTarget(parts[4]),
                                textOperator = parseResultsFilterTextOperator(parts[5]),
                                countOperator = parseResultsFilterCountOperator(parts[6]),
                                value = decodeFilterToken(parts[7]),
                                timeOperator = parseResultsFilterTimeOperator(parts.getOrNull(8).orEmpty()),
                                durationToleranceSeconds = decodeFilterToken(parts.getOrNull(9).orEmpty()),
                                durationToleranceMilliseconds = decodeFilterToken(parts.getOrNull(10).orEmpty()),
                                memberMatchMode = parseResultsFilterMemberMatchMode(parts.getOrNull(11).orEmpty())
                            )
                        )
                }
            }
        }

        val clusters = buildList {
            clusterRecords.forEachIndexed { index, cluster ->
                val rules = rulesByClusterId[cluster.id].orEmpty()
                add(
                    ResultsFilterCluster(
                        id = cluster.id,
                        name = cluster.name.ifBlank { "Cluster ${index + 1}" },
                        enabled = cluster.enabled,
                        mode = cluster.mode,
                        rules = if (rules.isEmpty()) listOf(createResultsFilterRule()) else rules
                    )
                )
            }
        }
        ResultsFilterDefinition(clusters = clusters)
    }.getOrDefault(ResultsFilterDefinition())
}

private fun parseResultsFilterTarget(value: String): ResultsFilterTarget {
    return runCatching { ResultsFilterTarget.valueOf(value) }
        .getOrDefault(ResultsFilterTarget.FileName)
}

private fun parseResultsFilterClusterMode(value: String): ResultsFilterClusterMode {
    return runCatching { ResultsFilterClusterMode.valueOf(value) }
        .getOrDefault(ResultsFilterClusterMode.All)
}

private fun parseResultsFilterMemberMatchMode(value: String): ResultsFilterMemberMatchMode {
    return runCatching { ResultsFilterMemberMatchMode.valueOf(value) }
        .getOrDefault(ResultsFilterMemberMatchMode.Any)
}

private fun parseResultsFilterTextOperator(value: String): ResultsFilterTextOperator {
    return runCatching { ResultsFilterTextOperator.valueOf(value) }
        .getOrDefault(ResultsFilterTextOperator.Contains)
}

private fun parseResultsFilterCountOperator(value: String): ResultsFilterCountOperator {
    return runCatching { ResultsFilterCountOperator.valueOf(value) }
        .getOrDefault(ResultsFilterCountOperator.AtLeast)
}

private fun parseResultsFilterTimeOperator(value: String): ResultsFilterTimeOperator {
    return runCatching { ResultsFilterTimeOperator.valueOf(value) }
        .getOrDefault(ResultsFilterTimeOperator.OnOrAfter)
}

private data class FilterClusterRecord(
    val id: String,
    val enabled: Boolean,
    val mode: ResultsFilterClusterMode,
    val name: String
)

private fun encodeFilterToken(value: String): String {
    if (value.isEmpty()) return "-"
    val bytes = value.toByteArray(Charsets.UTF_8)
    val output = StringBuilder(((bytes.size + 2) / 3) * 4)
    var index = 0
    while (index < bytes.size) {
        val b0 = bytes[index].toInt() and 0xff
        val hasB1 = index + 1 < bytes.size
        val hasB2 = index + 2 < bytes.size
        val b1 = if (hasB1) bytes[index + 1].toInt() and 0xff else 0
        val b2 = if (hasB2) bytes[index + 2].toInt() and 0xff else 0
        output.append(BASE64_URL_ALPHABET[b0 ushr 2])
        output.append(BASE64_URL_ALPHABET[((b0 and 0x03) shl 4) or (b1 ushr 4)])
        if (hasB1) {
            output.append(BASE64_URL_ALPHABET[((b1 and 0x0f) shl 2) or (b2 ushr 6)])
        }
        if (hasB2) {
            output.append(BASE64_URL_ALPHABET[b2 and 0x3f])
        }
        index += 3
    }
    return output.toString()
}

private fun decodeFilterToken(token: String): String {
    if (token == "-") return ""
    return runCatching {
        val output = ArrayList<Byte>((token.length * 3) / 4)
        var index = 0
        while (index < token.length) {
            val c0 = decodeBase64UrlChar(token[index])
            val c1 = decodeBase64UrlChar(token[index + 1])
            val hasC2 = index + 2 < token.length
            val hasC3 = index + 3 < token.length
            val c2 = if (hasC2) decodeBase64UrlChar(token[index + 2]) else 0
            val c3 = if (hasC3) decodeBase64UrlChar(token[index + 3]) else 0
            output.add(((c0 shl 2) or (c1 ushr 4)).toByte())
            if (hasC2) {
                output.add((((c1 and 0x0f) shl 4) or (c2 ushr 2)).toByte())
            }
            if (hasC3) {
                output.add((((c2 and 0x03) shl 6) or c3).toByte())
            }
            index += 4
        }
        String(output.toByteArray(), Charsets.UTF_8)
    }.getOrDefault("")
}

private fun decodeBase64UrlChar(char: Char): Int {
    val index = BASE64_URL_ALPHABET.indexOf(char)
    if (index < 0) throw IllegalArgumentException("Invalid base64url character")
    return index
}

private const val BASE64_URL_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

private data class DateTimePattern(
    val pattern: String,
    val durationMillis: Long
)

private val DATE_TIME_PATTERNS = listOf(
    DateTimePattern("yyyy-MM-dd HH:mm:ss", 1_000L),
    DateTimePattern("yyyy-MM-dd HH:mm", 60_000L),
    DateTimePattern("yyyy-MM-dd", 86_400_000L)
)

private fun parseUtcDateTime(value: String, pattern: String): Long? {
    val formatter = SimpleDateFormat(pattern, Locale.US)
    formatter.isLenient = false
    formatter.timeZone = TimeZone.getTimeZone("UTC")
    val position = ParsePosition(0)
    val parsed = formatter.parse(value, position) ?: return null
    return if (position.index == value.length) parsed.time else null
}
