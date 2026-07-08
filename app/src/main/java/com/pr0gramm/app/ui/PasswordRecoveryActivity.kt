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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.google.code.regexp.Pattern
import com.pr0gramm.app.R
import com.pr0gramm.app.services.ThemeHelper
import com.pr0gramm.app.services.Track
import com.pr0gramm.app.services.UserService
import com.pr0gramm.app.ui.base.BaseAppCompatActivity
import com.pr0gramm.app.ui.base.launchWhenStarted
import com.pr0gramm.app.ui.compose.Pr0grammAlertDialog
import com.pr0gramm.app.ui.compose.setComposeContent
import com.pr0gramm.app.util.di.instance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

class PasswordRecoveryActivity : BaseAppCompatActivity("PasswordRecoveryActivity") {
    private lateinit var user: String
    private lateinit var token: String

    private val userService: UserService by instance()

    private var submitting by mutableStateOf(false)
    private var resultDialog by mutableStateOf<Boolean?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(ThemeHelper.theme.noActionBar)

        super.onCreate(savedInstanceState)

        val url = intent.getStringExtra("url")
        val matcher = Pattern.compile("/user/(?<user>[^/]+)/resetpass/(?<token>[^/]+)").matcher(url)
        if (matcher.find()) {
            this.user = matcher.group("user")
            this.token = matcher.group("token")
        } else {
            finish()
            return
        }

        setComposeContent {
            PasswordRecoveryScreen(
                submitting = submitting,
                onBack = { onBackPressedDispatcher.onBackPressed() },
                onSubmit = { password -> submitButtonClicked(password) },
            )

            resultDialog?.let { success ->
                Pr0grammAlertDialog(
                    onDismissRequest = { finish() },
                    text = stringResource(
                        if (success) R.string.password_recovery_success else R.string.password_recovery_error,
                    ),
                    confirmText = stringResource(R.string.okay),
                    onConfirm = { finish() },
                )
            }
        }
    }

    private fun submitButtonClicked(password: String) {
        launchWhenStarted(busyIndicator = true) {
            submitting = true
            try {
                val result = withContext(NonCancellable + Dispatchers.Default) {
                    userService.resetPassword(user, token, password)
                }

                requestCompleted(result)
            } finally {
                submitting = false
            }
        }
    }

    private fun requestCompleted(success: Boolean) {
        Track.passwordChanged()
        resultDialog = success
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PasswordRecoveryScreen(
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
            var password by rememberSaveable { mutableStateOf("") }

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(stringResource(R.string.hint_password)) },
                singleLine = true,
                enabled = !submitting,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )

            Button(
                onClick = { onSubmit(password.trim()) },
                enabled = password.trim().length > 6 && !submitting,
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(stringResource(R.string.action_change))
            }
        }
    }
}
