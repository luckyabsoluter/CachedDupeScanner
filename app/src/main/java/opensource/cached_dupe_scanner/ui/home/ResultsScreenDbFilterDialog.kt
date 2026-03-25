package opensource.cached_dupe_scanner.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
internal fun ResultsFilterDialog(
    definition: ResultsFilterDefinition,
    onDefinitionChange: (ResultsFilterDefinition) -> Unit,
    onDismiss: () -> Unit,
    onApply: () -> Unit
) {
    val scrollState = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Filter results") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Enabled clusters are combined together. Inside each cluster, choose whether every rule must match or any rule can match.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "File name and folder rules match if any file inside the duplicate group matches the rule.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (definition.clusters.isEmpty()) {
                    Text("No filter clusters yet.")
                } else {
                    definition.clusters.forEachIndexed { clusterIndex, cluster ->
                        ResultsFilterClusterEditor(
                            clusterIndex = clusterIndex,
                            cluster = cluster,
                            canRemove = definition.clusters.size > 1,
                            onClusterChange = { updatedCluster ->
                                onDefinitionChange(
                                    definition.updateCluster(
                                        clusterId = cluster.id,
                                        updatedCluster = updatedCluster
                                    )
                                )
                            },
                            onAddRule = {
                                onDefinitionChange(
                                    definition.updateCluster(
                                        clusterId = cluster.id,
                                        updatedCluster = cluster.copy(
                                            rules = cluster.rules + createResultsFilterRule()
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
                    }
                }

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
        },
        confirmButton = {
            Button(onClick = onApply) {
                Text("Apply")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun ResultsFilterClusterEditor(
    clusterIndex: Int,
    cluster: ResultsFilterCluster,
    canRemove: Boolean,
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
                FilterOptionButtons(
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
            FilterOptionButtons(
                options = ResultsFilterTarget.entries,
                selected = rule.target,
                label = { it.label },
                onSelect = { target ->
                    onRuleChange(
                        rule.copy(
                            target = target,
                            value = "",
                            textOperator = ResultsFilterTextOperator.Contains,
                            countOperator = ResultsFilterCountOperator.AtLeast
                        )
                    )
                }
            )

            if (rule.target == ResultsFilterTarget.GroupItemCount) {
                Text("Operator")
                FilterOptionButtons(
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
            } else {
                Text("Operator")
                FilterOptionButtons(
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

@Composable
private fun <T> FilterOptionButtons(
    options: Iterable<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.chunked(2).forEach { rowOptions ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowOptions.forEach { option ->
                    val isSelected = option == selected
                    if (isSelected) {
                        Button(onClick = { onSelect(option) }) {
                            Text(label(option))
                        }
                    } else {
                        OutlinedButton(onClick = { onSelect(option) }) {
                            Text(label(option))
                        }
                    }
                }
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
