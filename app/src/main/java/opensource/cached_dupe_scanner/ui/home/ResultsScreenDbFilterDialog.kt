package opensource.cached_dupe_scanner.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import opensource.cached_dupe_scanner.ui.components.AppTopBar
import opensource.cached_dupe_scanner.ui.components.OptionButtonGrid
import opensource.cached_dupe_scanner.ui.components.ScrollbarDefaults
import opensource.cached_dupe_scanner.ui.components.Spacing
import opensource.cached_dupe_scanner.ui.components.VerticalLazyScrollbar

@Composable
internal fun ResultsFilterScreen(
    definition: ResultsFilterDefinition,
    onDefinitionChange: (ResultsFilterDefinition) -> Unit,
    onBack: () -> Unit,
    onApply: () -> Unit
) {
    FilterEditorScreen(
        title = "Result filters",
        summaryTitle = "Current summary",
        introLines = listOf(
            "Build filter clusters in a dedicated screen so long rule sets stay readable while you edit them.",
            "Enabled clusters are combined together. Inside each cluster, choose whether every rule must match or any rule can match.",
            "File name, folder, and modified-time rules match if any file inside the duplicate group matches the rule. Same-folder and same-size rules check every file in the group."
        ),
        definition = definition,
        supportedTargets = RESULT_FILTER_TARGETS,
        onDefinitionChange = onDefinitionChange,
        onBack = onBack,
        onApply = onApply
    )
}

@Composable
internal fun FileFilterScreen(
    definition: ResultsFilterDefinition,
    onDefinitionChange: (ResultsFilterDefinition) -> Unit,
    onBack: () -> Unit,
    onApply: () -> Unit
) {
    FilterEditorScreen(
        title = "File filters",
        summaryTitle = "Current summary",
        introLines = listOf(
            "Filter the file manager list by matching file names, folder paths, or modified times before items are added to the visible page.",
            "Enabled clusters are combined together. Inside each cluster, choose whether every rule must match or any rule can match.",
            "File name, folder path, and modified-time rules are used on this screen."
        ),
        definition = definition,
        supportedTargets = FILE_FILTER_TARGETS,
        onDefinitionChange = onDefinitionChange,
        onBack = onBack,
        onApply = onApply
    )
}

@Composable
internal fun SimilarityFilterScreen(
    definition: ResultsFilterDefinition,
    onDefinitionChange: (ResultsFilterDefinition) -> Unit,
    onBack: () -> Unit,
    onApply: () -> Unit
) {
    FilterEditorScreen(
        title = "Similarity filters",
        summaryTitle = "Current summary",
        introLines = listOf(
            "Filter stored similarity groups with the same rules available in duplicate results.",
            "Enabled clusters are combined together. Inside each cluster, choose whether every rule must match or any rule can match.",
            "File name, folder, and modified-time rules match any member. Same-folder, same-size, and average-duration rules check every member in the group."
        ),
        definition = definition,
        supportedTargets = SIMILARITY_FILTER_TARGETS,
        onDefinitionChange = onDefinitionChange,
        onBack = onBack,
        onApply = onApply
    )
}

@Composable
private fun FilterEditorScreen(
    title: String,
    summaryTitle: String,
    introLines: List<String>,
    definition: ResultsFilterDefinition,
    supportedTargets: Set<ResultsFilterTarget>,
    onDefinitionChange: (ResultsFilterDefinition) -> Unit,
    onBack: () -> Unit,
    onApply: () -> Unit
) {
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box {
            LazyColumn(
                state = listState,
                modifier = Modifier.padding(Spacing.screenPadding),
                contentPadding = PaddingValues(
                    end = ScrollbarDefaults.ThumbWidth + 8.dp,
                    bottom = 24.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    // AppTopBar(title = "Result filters", onBack = onBack)
                    AppTopBar(title = title, onBack = onBack)
                }
                introLines.forEachIndexed { index, line ->
                    item {
                        Text(
                            text = line,
                            style = if (index == 0) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodySmall,
                            color = if (index == 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                item {
                    Card {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(summaryTitle, style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = summarizeResultsFilter(definition, supportedTargets),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

                item {
                    Text(
                        text = if (definition.clusters.isEmpty()) "No filter clusters yet." else "Clusters",
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                if (definition.clusters.isEmpty()) {
                    item { Text("No filter clusters yet.") }
                } else {
                    itemsIndexed(definition.clusters, key = { _, cluster -> cluster.id }) { clusterIndex, cluster ->
                        ResultsFilterClusterEditor(
                            clusterIndex = clusterIndex,
                            cluster = cluster,
                            canRemove = definition.clusters.size > 1,
                            supportedTargets = supportedTargets,
                            onClusterChange = { updatedCluster ->
                                onDefinitionChange(
                                    definition.updateCluster(
                                        clusterId = cluster.id,
                                        updatedCluster = updatedCluster
                                    )
                                )
                            },
                            onAddRule = {
                                val defaultTarget = supportedTargets.firstOrNull() ?: ResultsFilterTarget.FileName
                                onDefinitionChange(
                                    definition.updateCluster(
                                        clusterId = cluster.id,
                                        updatedCluster = cluster.copy(
                                            rules = cluster.rules + createResultsFilterRule(defaultTarget)
                                        )
                                    )
                                )
                            },
                            onRemoveRule = { ruleId ->
                                onDefinitionChange(
                                    definition.updateCluster(
                                        clusterId = cluster.id,
                                        updatedCluster = cluster.copy(
                                            rules = cluster.rules.filterNot { it.id == ruleId }
                                        )
                                    )
                                )
                            },
                            onRemoveCluster = {
                                onDefinitionChange(
                                    ResultsFilterDefinition(
                                        clusters = definition.clusters.filterNot { it.id == cluster.id }
                                    )
                                )
                            }
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }

                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                onDefinitionChange(
                                    definition.copy(
                                        clusters = definition.clusters + createResultsFilterCluster()
                                    )
                                )
                            }
                        ) {
                            Text("Add cluster")
                        }
                        OutlinedButton(
                            onClick = {
                                onDefinitionChange(
                                    ResultsFilterDefinition(
                                        clusters = listOf(createResultsFilterCluster())
                                    )
                                )
                            }
                        ) {
                            Text("Reset")
                        }
                    }
                }

                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onApply) {
                            Text("Apply")
                        }
                        OutlinedButton(onClick = onBack) {
                            Text("Cancel")
                        }
                    }
                }
            }
            VerticalLazyScrollbar(
                listState = listState,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .padding(end = 4.dp)
            )
        }
    }

    BackHandler(onBack = onBack)
}

@Composable
private fun ResultsFilterClusterEditor(
    clusterIndex: Int,
    cluster: ResultsFilterCluster,
    canRemove: Boolean,
    supportedTargets: Set<ResultsFilterTarget>,
    onClusterChange: (ResultsFilterCluster) -> Unit,
    onAddRule: () -> Unit,
    onRemoveRule: (String) -> Unit,
    onRemoveCluster: () -> Unit
) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = cluster.enabled,
                            onCheckedChange = { enabled ->
                                onClusterChange(cluster.copy(enabled = enabled))
                            }
                        )
                        Text("Cluster ${clusterIndex + 1}")
                    }
                    if (canRemove) {
                        OutlinedButton(onClick = onRemoveCluster) {
                            Text("Remove")
                        }
                    }
                }

                OutlinedTextField(
                    value = cluster.name,
                    onValueChange = { value ->
                        onClusterChange(cluster.copy(name = value))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Cluster name") },
                    singleLine = true
                )

                Text("Cluster logic")
                OptionButtonGrid(
                    options = ResultsFilterClusterMode.entries,
                    selected = cluster.mode,
                    label = { it.label },
                    onSelect = { mode -> onClusterChange(cluster.copy(mode = mode)) }
                )

                cluster.rules.forEachIndexed { ruleIndex, rule ->
                    ResultsFilterRuleEditor(
                        ruleIndex = ruleIndex,
                        rule = rule,
                        canRemove = cluster.rules.size > 1,
                        supportedTargets = supportedTargets,
                        onRuleChange = { updatedRule ->
                            onClusterChange(
                                cluster.copy(
                                    rules = cluster.rules.map { current ->
                                        if (current.id == rule.id) updatedRule else current
                                    }
                                )
                            )
                        },
                        onRemove = { onRemoveRule(rule.id) }
                    )
                }

                OutlinedButton(onClick = onAddRule) {
                    Text("Add rule")
                }
            }
        }
    }
}

@Composable
private fun ResultsFilterRuleEditor(
    ruleIndex: Int,
    rule: ResultsFilterRule,
    canRemove: Boolean,
    supportedTargets: Set<ResultsFilterTarget>,
    onRuleChange: (ResultsFilterRule) -> Unit,
    onRemove: () -> Unit
) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = rule.enabled,
                        onCheckedChange = { enabled ->
                            onRuleChange(rule.copy(enabled = enabled))
                        }
                    )
                    Text("Rule ${ruleIndex + 1}")
                }
                if (canRemove) {
                    OutlinedButton(onClick = onRemove) {
                        Text("Remove")
                    }
                }
            }

            Text("Target")
            OptionButtonGrid(
                options = supportedTargets.toList(),
                selected = rule.target,
                label = { it.label },
                onSelect = { target ->
                    onRuleChange(
                        rule.copy(
                            target = target,
                            value = "",
                            textOperator = ResultsFilterTextOperator.Contains,
                            countOperator = ResultsFilterCountOperator.AtLeast,
                            timeOperator = ResultsFilterTimeOperator.OnOrAfter,
                            durationToleranceSeconds = "",
                            durationToleranceMilliseconds = ""
                        )
                    )
                }
            )

            if (rule.target == ResultsFilterTarget.GroupItemCount) {
                Text("Operator")
                OptionButtonGrid(
                    options = ResultsFilterCountOperator.entries,
                    selected = rule.countOperator,
                    label = { it.label },
                    onSelect = { operator ->
                        onRuleChange(rule.copy(countOperator = operator))
                    }
                )
                OutlinedTextField(
                    value = rule.value,
                    onValueChange = { value -> onRuleChange(rule.copy(value = value)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Item count") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            } else if (rule.target == ResultsFilterTarget.ModifiedTime) {
                Text("Operator")
                OptionButtonGrid(
                    options = ResultsFilterTimeOperator.entries,
                    selected = rule.timeOperator,
                    label = { it.label },
                    onSelect = { operator ->
                        onRuleChange(rule.copy(timeOperator = operator))
                    }
                )
                OutlinedTextField(
                    value = rule.value,
                    onValueChange = { value -> onRuleChange(rule.copy(value = value)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Modified time (UTC)") },
                    placeholder = { Text("2026-04-20 13:45:00") },
                    singleLine = true
                )
            } else if (rule.target == ResultsFilterTarget.SameFolder) {
                Text(
                    text = "Matches only when every file in the duplicate group is inside the same folder.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (rule.target == ResultsFilterTarget.SameFileSize) {
                Text(
                    text = "Matches only when every file in the group has the same byte size.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (rule.target == ResultsFilterTarget.DurationFromAverage) {
                Text(
                    text = "Matches only when every stored video duration is within this tolerance of the group average.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = rule.durationToleranceSeconds,
                    onValueChange = { value ->
                        if (value.isEmpty() || value.toLongOrNull()?.let { it >= 0L } == true) {
                            onRuleChange(rule.copy(durationToleranceSeconds = value))
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("duration-average-seconds"),
                    label = { Text("Seconds") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                OutlinedTextField(
                    value = rule.durationToleranceMilliseconds,
                    onValueChange = { value ->
                        if (value.isEmpty() || value.toIntOrNull()?.let { it in 0..999 } == true) {
                            onRuleChange(rule.copy(durationToleranceMilliseconds = value))
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("duration-average-milliseconds"),
                    label = { Text("Milliseconds (0-999)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            } else {
                Text("Operator")
                OptionButtonGrid(
                    options = ResultsFilterTextOperator.entries,
                    selected = rule.textOperator,
                    label = { it.label },
                    onSelect = { operator ->
                        onRuleChange(rule.copy(textOperator = operator))
                    }
                )
                OutlinedTextField(
                    value = rule.value,
                    onValueChange = { value -> onRuleChange(rule.copy(value = value)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = {
                        Text(
                            if (rule.target == ResultsFilterTarget.FileName) {
                                "File name text"
                            } else {
                                "Folder text"
                            }
                        )
                    },
                    singleLine = true
                )
            }
        }
    }
}

private fun ResultsFilterDefinition.updateCluster(
    clusterId: String,
    updatedCluster: ResultsFilterCluster
): ResultsFilterDefinition {
    return copy(
        clusters = clusters.map { cluster ->
            if (cluster.id == clusterId) updatedCluster else cluster
        }
    )
}
