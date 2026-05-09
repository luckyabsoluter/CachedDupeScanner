package opensource.cached_dupe_scanner.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun <T> OptionButtonGrid(
    options: Iterable<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    columns: Int = 2
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        optionButtonGridRows(options, columns).forEach { rowOptions ->
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

internal fun <T> optionButtonGridRows(
    options: Iterable<T>,
    columns: Int = 2
): List<List<T>> {
    require(columns > 0) { "columns must be positive" }
    return options.toList().chunked(columns)
}
