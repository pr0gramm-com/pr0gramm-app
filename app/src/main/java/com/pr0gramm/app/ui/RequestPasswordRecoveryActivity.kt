package com.pr0gramm.app.ui

import android.os.Bundle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.util.PatternsCompat
import com.pr0gramm.app.R
import com.pr0gramm.app.services.ThemeHelper
import com.pr0gramm.app.services.UserService
import com.pr0gramm.app.ui.base.BaseAppCompatActivity
import com.pr0gramm.app.ui.base.launchWhenStarted
import com.pr0gramm.app.ui.compose.Pr0grammAlertDialog
import com.pr0gramm.app.ui.compose.setComposeContent
import com.pr0gramm.app.util.di.instance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

class RequestPasswordRecoveryActivity : BaseAppCompatActivity("RequestPasswordRecoveryActivity") {
    private val userService: UserService by instance()

    private var submitting by mutableStateOf(false)
    private var showSuccessDialog by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(ThemeHelper.theme.noActionBar)

        super.onCreate(savedInstanceState)

        setComposeContent {
            RequestPasswordRecoveryScreen(
                submitting = submitting,
                onBack = { onBackPressedDispatcher.onBackPressed() },
                onSubmit = { email -> submitButtonClicked(email) },
            )

            if (showSuccessDialog) {
                Pr0grammAlertDialog(
                    onDismissRequest = { finish() },
                    text = stringResource(R.string.request_password_recovery_popup_hint),
                    confirmText = stringResource(R.string.okay),
                    onConfirm = { finish() },
                )
            }
        }
    }

    private fun submitButtonClicked(email: String) {
        launchWhenStarted(busyIndicator = true) {
            submitting = true
            try {
                withContext(NonCancellable + Dispatchers.Default) {
                    userService.requestPasswordRecovery(email)
                }

                showSuccessDialog = true
            } finally {
                submitting = false
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RequestPasswordRecoveryScreen(
    submitting: Boolean,
    onBack: () -> Unit,
    onSubmit: (String) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.request_password_recovery_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            var email by rememberSaveable { mutableStateOf("") }

            val valid = PatternsCompat.EMAIL_ADDRESS.matcher(email.trim()).matches()

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text(stringResource(R.string.hint_email)) },
                singleLine = true,
                enabled = !submitting,
                modifier = Modifier.fillMaxWidth(),
            )

            Button(
                onClick = { onSubmit(email.trim()) },
                enabled = valid && !submitting,
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(stringResource(R.string.submit))
            }
        }
    }
}
