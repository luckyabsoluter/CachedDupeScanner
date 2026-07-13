package opensource.cached_dupe_scanner.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.style.TextOverflow
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
            "File name, folder, and modified-time rules can match any member or require every member. Same-folder and same-size rules check every file in the group."
        ),
        definition = definition,
        supportedTargets = RESULT_FILTER_TARGETS,
        showMemberMatchMode = true,
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
        showMemberMatchMode = false,
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
            "File name, folder, and modified-time rules can match any member or require every member. Same-folder, same-size, and average-duration rules check every member in the group."
        ),
        definition = definition,
        supportedTargets = SIMILARITY_FILTER_TARGETS,
        showMemberMatchMode = true,
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
    showMemberMatchMode: Boolean,
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
                            showMemberMatchMode = showMemberMatchMode,
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
    showMemberMatchMode: Boolean,
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

                HorizontalDivider()
                Text(
                    text = "Rules (${cluster.rules.size})",
                    style = MaterialTheme.typography.titleSmall
                )

                cluster.rules.forEachIndexed { ruleIndex, rule ->
                    ResultsFilterRuleEditor(
                        ruleIndex = ruleIndex,
                        rule = rule,
                        canRemove = cluster.rules.size > 1,
                        supportedTargets = supportedTargets,
                        showMemberMatchMode = showMemberMatchMode,
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
    showMemberMatchMode: Boolean,
    onRuleChange: (ResultsFilterRule) -> Unit,
    onRemove: () -> Unit
) {
    val ruleAccent = when (ruleIndex % 3) {
        0 -> MaterialTheme.colorScheme.primary
        1 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.secondary
    }
    val visibleAccent = if (rule.enabled) ruleAccent else MaterialTheme.colorScheme.outline
    val containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(
        alpha = if (rule.enabled) 0.55f else 0.25f
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("filter-rule:${rule.id}"),
        shape = RoundedCornerShape(4.dp),
        color = containerColor,
        border = BorderStroke(2.dp, visibleAccent.copy(alpha = 0.55f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = rule.enabled,
                    onCheckedChange = { enabled ->
                        onRuleChange(rule.copy(enabled = enabled))
                    }
                )
                Text(
                    text = "Rule ${ruleIndex + 1} - ${rule.target.label}",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    color = if (rule.enabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (canRemove) {
                    IconButton(onClick = onRemove) {
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = "Remove rule ${ruleIndex + 1}"
                        )
                    }
                }
            }

            HorizontalDivider(color = visibleAccent.copy(alpha = 0.25f))

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

            if (showMemberMatchMode && rule.target.supportsMemberMatchMode()) {
                Text("Member match")
                OptionButtonGrid(
                    options = ResultsFilterMemberMatchMode.entries,
                    selected = rule.memberMatchMode,
                    label = { mode -> mode.label },
                    onSelect = { mode ->
                        onRuleChange(rule.copy(memberMatchMode = mode))
                    }
                )
            }

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
            } else if (rule.target == ResultsFilterTarget.SameResolution) {
                Text(
                    text = "Matches only when every media file in the group has the same width and height.",
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
                    value = rule.durationToleranceInput(),
                    onValueChange = { value ->
                        if (value.all { character -> character.isDigit() }) {
                            onRuleChange(rule.withDurationToleranceInput(value))
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("duration-average-tolerance"),
                    label = { Text("Tolerance") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                Text("Unit")
                OptionButtonGrid(
                    options = ResultsFilterDurationUnit.entries,
                    selected = rule.durationToleranceUnit(),
                    label = { unit -> unit.label },
                    onSelect = { unit ->
                        onRuleChange(rule.withDurationToleranceUnit(unit))
                    }
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
