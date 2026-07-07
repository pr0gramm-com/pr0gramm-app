package com.pr0gramm.app.ui.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * A themed Material 3 [AlertDialog] wrapper meant to replace `DialogBuilder` usage incrementally.
 * Buttons are optional so it can also present purely informational dialogs.
 */
@Composable
fun Pr0grammAlertDialog(
    onDismissRequest: () -> Unit,
    title: String? = null,
    text: String? = null,
    confirmText: String? = null,
    onConfirm: (() -> Unit)? = null,
    dismissText: String? = null,
    onDismiss: (() -> Unit)? = null,
    content: (@Composable () -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = title?.let { { Text(it) } },
        text = when {
            content != null -> content
            text != null -> {
                { Text(text) }
            }

            else -> null
        },
        confirmButton = {
            if (confirmText != null) {
                TextButton(onClick = { onConfirm?.invoke() }) { Text(confirmText) }
            }
        },
        dismissButton = dismissText?.let {
            { TextButton(onClick = { onDismiss?.invoke() ?: onDismissRequest() }) { Text(it) } }
        },
    )
}

/**
 * A themed Material 3 [ModalBottomSheet] wrapper meant to replace `MenuSheetView` usage
 * incrementally.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Pr0grammModalBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(),
    content: @Composable () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        modifier = modifier,
    ) {
        Column {
            content()
        }
    }
}
