package com.pr0gramm.app.ui.upload

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.pr0gramm.app.R
import com.pr0gramm.app.ui.compose.ComposeDialogFragment
import com.pr0gramm.app.ui.compose.Pr0grammActionSheet
import com.pr0gramm.app.ui.compose.SheetAction

class UploadTypeDialogFragment : ComposeDialogFragment("UploadTypeDialogFragment") {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun DialogContent() {
        val context = LocalContext.current

        Pr0grammActionSheet(
            onDismissRequest = { dismiss() },
            title = stringResource(R.string.hint_upload),
            actions = listOf(
                SheetAction(stringResource(R.string.media_type_image), R.drawable.ic_type_image) {
                    UploadActivity.openForType(context, UploadMediaType.IMAGE)
                    dismiss()
                },
                SheetAction(stringResource(R.string.media_type_video), R.drawable.ic_type_video) {
                    UploadActivity.openForType(context, UploadMediaType.VIDEO)
                    dismiss()
                },
            ),
        )
    }
}
