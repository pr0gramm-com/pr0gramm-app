package com.pr0gramm.app.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.res.stringResource
import com.pr0gramm.app.R
import com.pr0gramm.app.ui.compose.ComposeDialogFragment

class VersionNotSupportedDialogFragment : ComposeDialogFragment("VersionNotSupportedDialogFragment") {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isCancelable = false
    }

    @Composable
    override fun DialogContent() {
        val url = "https://app.pr0gramm.com"

        AlertDialog(
            onDismissRequest = {},
            properties = DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
            ),
            text = {
                Text(
                    buildAnnotatedString {
                        append("Support für diese Version der App ist eingestellt. ")
                        append("Um die pr0gramm App weiter benutzen zu können, lade die ")
                        append("aktuelle Version von ")
                        withLink(LinkAnnotation.Url(url)) { append(url) }
                        append(" herunter.")
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    activity?.finish()
                }) {
                    Text(stringResource(R.string.okay))
                }
            },
        )
    }
}
