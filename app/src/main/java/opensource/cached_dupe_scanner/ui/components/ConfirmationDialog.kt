package opensource.cached_dupe_scanner.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun ConfirmationDialog(
    title: String,
    text: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    dismissText: String = "Cancel",
    confirmEnabled: Boolean = true,
    dismissEnabled: Boolean = true,
    confirmStyle: ConfirmationDialogButtonStyle = ConfirmationDialogButtonStyle.Filled,
    dismissStyle: ConfirmationDialogButtonStyle = ConfirmationDialogButtonStyle.Outlined
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            ConfirmationDialogButton(
                text = confirmText,
                enabled = confirmEnabled,
                style = confirmStyle,
                onClick = onConfirm
            )
        },
        dismissButton = {
            ConfirmationDialogButton(
                text = dismissText,
                enabled = dismissEnabled,
                style = dismissStyle,
                onClick = onDismissRequest
            )
        }
    )
}

enum class ConfirmationDialogButtonStyle {
    Filled,
    Outlined
}

@Composable
private fun ConfirmationDialogButton(
    text: String,
    enabled: Boolean,
    style: ConfirmationDialogButtonStyle,
    onClick: () -> Unit
) {
    when (style) {
        ConfirmationDialogButtonStyle.Filled -> {
            Button(
                enabled = enabled,
                onClick = onClick
            ) {
                Text(text)
            }
        }

        ConfirmationDialogButtonStyle.Outlined -> {
            OutlinedButton(
                enabled = enabled,
                onClick = onClick
            ) {
                Text(text)
            }
        }
    }
}
