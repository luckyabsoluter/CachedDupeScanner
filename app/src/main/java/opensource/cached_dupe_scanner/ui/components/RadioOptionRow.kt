package opensource.cached_dupe_scanner.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun <T> RadioOptionRow(
    option: T,
    selected: T,
    label: String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.clickable { onSelect(option) },
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = option == selected,
            onClick = { onSelect(option) }
        )
        Text(label)
    }
}
