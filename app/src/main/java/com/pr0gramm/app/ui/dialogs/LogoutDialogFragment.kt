package com.pr0gramm.app.ui.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pr0gramm.app.R
import com.pr0gramm.app.ui.MainActionHandler
import com.pr0gramm.app.ui.compose.ComposeDialogFragment
import com.pr0gramm.app.ui.compose.Pr0grammModalBottomSheet


class LogoutDialogFragment : ComposeDialogFragment("LogoutDialogFragment") {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun DialogContent() {
        Pr0grammModalBottomSheet(onDismissRequest = { dismiss() }) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(stringResource(R.string.are_you_sure_to_logout))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                ) {
                    TextButton(onClick = { dismiss() }) {
                        Text(stringResource(R.string.cancel))
                    }

                    Button(onClick = {
                        logout()
                        dismiss()
                    }) {
                        Text(stringResource(R.string.logout))
                    }
                }
            }
        }
    }

    private fun logout() {
        val handler = activity as MainActionHandler
        handler.onLogoutClicked()
    }
}
