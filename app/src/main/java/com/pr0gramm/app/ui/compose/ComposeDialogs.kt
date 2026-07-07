package com.pr0gramm.app.ui.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

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

/**
 * A small indeterminate progress dialog replacing the legacy `progress_dialog.xml` / `BusyDialog`.
 * It renders in its own window and is not dismissible by default (mirrors the old busy dialog,
 * which is dismissed programmatically once the background work finishes).
 */
@Composable
fun BusyOverlay(
    text: String? = null,
    onDismissRequest: () -> Unit = {},
    dismissible: Boolean = false,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            dismissOnBackPress = dismissible,
            dismissOnClickOutside = dismissible,
        ),
    ) {
        BusyOverlayContent(text)
    }
}

/**
 * The bare busy surface (spinner + optional text) without a surrounding window, so it can be
 * hosted either by [BusyOverlay] or directly inside a `ComposeView` in a legacy `Dialog`.
 */
@Composable
fun BusyOverlayContent(text: String? = null) {
    Surface(
        shape = MaterialTheme.shapes.large,
        tonalElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier.padding(24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CircularProgressIndicator(modifier = Modifier.size(36.dp))

            if (!text.isNullOrBlank()) {
                Text(text, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
