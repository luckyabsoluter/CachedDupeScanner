package opensource.cached_dupe_scanner.ui.home

import opensource.cached_dupe_scanner.cache.DuplicateGroupEntity
import opensource.cached_dupe_scanner.core.FileMetadata

internal enum class ResultsFilterTarget(val label: String) {
    GroupItemCount("Group count"),
    FileName("File name"),
    FolderPath("Folder")
}

internal enum class ResultsFilterClusterMode(val label: String) {
    All("Match all"),
    Any("Match any")
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

internal data class ResultsFilterRule(
    val id: String,
    val enabled: Boolean = true,
    val target: ResultsFilterTarget = ResultsFilterTarget.FileName,
    val textOperator: ResultsFilterTextOperator = ResultsFilterTextOperator.Contains,
    val countOperator: ResultsFilterCountOperator = ResultsFilterCountOperator.AtLeast,
    val value: String = ""
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

private object ResultsFilterIdGenerator {
    private var nextId = 1L

    fun next(prefix: String): String {
        val current = nextId
        nextId += 1
        return "${prefix}_$current"
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
    return clusters.any { cluster ->
        cluster.enabled && configuredRules(cluster).isNotEmpty()
    }
}

internal fun ResultsFilterDefinition.requiresGroupMembers(): Boolean {
    return clusters.any { cluster ->
        cluster.enabled && configuredRules(cluster).any { rule ->
            rule.target != ResultsFilterTarget.GroupItemCount
        }
    }
}

internal fun ResultsFilterDefinition.activeClusterCount(): Int {
    return clusters.count { cluster ->
        cluster.enabled && configuredRules(cluster).isNotEmpty()
    }
}

internal fun ResultsFilterDefinition.activeRuleCount(): Int {
    return clusters.sumOf { cluster ->
        if (!cluster.enabled) {
            0
        } else {
            configuredRules(cluster).size
        }
    }
}

internal fun summarizeResultsFilter(definition: ResultsFilterDefinition): String {
    val clusterCount = definition.activeClusterCount()
    val ruleCount = definition.activeRuleCount()
    return when {
        clusterCount <= 0 -> "No filters"
        ruleCount == 1 -> "1 active rule"
        else -> "$clusterCount clusters · $ruleCount rules"
    }
}

internal fun matchesResultsFilter(
    definition: ResultsFilterDefinition,
    group: DuplicateGroupEntity,
    members: List<FileMetadata>
): Boolean {
    val activeClusters = definition.clusters.mapNotNull { cluster ->
        if (!cluster.enabled) {
            null
        } else {
            val rules = configuredRules(cluster)
            if (rules.isEmpty()) null else cluster to rules
        }
    }
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

private fun configuredRules(cluster: ResultsFilterCluster): List<ResultsFilterRule> {
    return cluster.rules.filter { rule ->
        rule.enabled && isResultsFilterRuleConfigured(rule)
    }
}

private fun isResultsFilterRuleConfigured(rule: ResultsFilterRule): Boolean {
    return when (rule.target) {
        ResultsFilterTarget.GroupItemCount -> rule.value.trim().toIntOrNull() != null
        ResultsFilterTarget.FileName -> rule.value.isNotBlank()
        ResultsFilterTarget.FolderPath -> rule.value.isNotBlank()
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
            members.any { member ->
                matchesTextOperator(
                    source = fileNameFromPath(member.normalizedPath),
                    expected = rule.value,
                    operator = rule.textOperator
                )
            }
        }
        ResultsFilterTarget.FolderPath -> {
            members.any { member ->
                matchesTextOperator(
                    source = folderPathFromPath(member.normalizedPath),
                    expected = rule.value,
                    operator = rule.textOperator
                )
            }
        }
    }
}

private fun matchesTextOperator(
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
