package com.pr0gramm.app.ui

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pr0gramm.app.R
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.services.InviteService
import com.pr0gramm.app.services.ThemeHelper
import com.pr0gramm.app.services.Track
import com.pr0gramm.app.services.UriHelper
import com.pr0gramm.app.ui.base.BaseAppCompatActivity
import com.pr0gramm.app.ui.base.launchWhenStarted
import com.pr0gramm.app.ui.compose.Pr0grammAlertDialog
import com.pr0gramm.app.ui.compose.components.Username
import com.pr0gramm.app.ui.compose.setComposeContent
import com.pr0gramm.app.ui.dialogs.ErrorDialogFragment.Companion.handleOnError
import com.pr0gramm.app.util.DurationFormat
import com.pr0gramm.app.util.di.instance
import com.pr0gramm.app.util.rootCause
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Compose invite screen. Lets the user send invites and shows the list of already-sent invites.
 */
class InviteActivity : BaseAppCompatActivity("InviteActivity") {
    private val inviteService: InviteService by instance()

    private var invites by mutableStateOf<InviteService.Invites?>(null)
    private var sending by mutableStateOf(false)
    private var errorMessage by mutableStateOf<String?>(null)
    private var successEvent by mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(ThemeHelper.theme.basic)
        super.onCreate(savedInstanceState)

        setComposeContent {
            InviteScreen(
                invites = invites,
                sending = sending,
                errorMessage = errorMessage,
                successEvent = successEvent,
                onBack = { finish() },
                onDismissError = { errorMessage = null },
                onSend = ::onInviteClicked,
                onUserClick = ::openUserProfile,
            )
        }
    }

    override fun onResume() {
        super.onResume()

        launchWhenStarted {
            invites = inviteService.invites()
        }
    }

    private fun onInviteClicked(email: String) {
        sending = true

        launchWhenStarted(busyIndicator = true) {
            try {
                withContext(NonCancellable + Dispatchers.Default) {
                    inviteService.send(email)
                }

                Track.inviteSent()
                successEvent += 1

                // re-query invites
                invites = inviteService.invites()
            } catch (err: Throwable) {
                if (err !is CancellationException) {
                    onInviteError(err)
                }
            } finally {
                sending = false
            }
        }
    }

    private fun onInviteError(error: Throwable) {
        val cause = error.rootCause
        if (cause is InviteService.InviteException) {
            when {
                cause.noMoreInvites() -> errorMessage = getString(R.string.invite_no_more_invites)
                cause.emailFormat() -> errorMessage = getString(R.string.error_email)
                cause.emailInUse() -> errorMessage = getString(R.string.invite_email_in_use)
                else -> handleOnError(error)
            }
        } else {
            handleOnError(error)
        }
    }

    private fun openUserProfile(name: String) {
        val url = UriHelper.of(this).uploads(name)
        startActivity(Intent(Intent.ACTION_VIEW, url, this, MainActivity::class.java))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InviteScreen(
    invites: InviteService.Invites?,
    sending: Boolean,
    errorMessage: String?,
    successEvent: Int,
    onBack: () -> Unit,
    onDismissError: () -> Unit,
    onSend: (email: String) -> Unit,
    onUserClick: (name: String) -> Unit,
) {
    var email by rememberSaveable { mutableStateOf("") }
    var emailError by rememberSaveable { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val successText = stringResource(R.string.invite_hint_success)
    val okayText = stringResource(R.string.okay)

    LaunchedEffect(successEvent) {
        if (successEvent > 0) {
            snackbarHostState.showSnackbar(successText, actionLabel = okayText)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.invites_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(R.string.invite_description),
                style = MaterialTheme.typography.bodyLarge,
            )

            Text(
                stringResource(R.string.invite_description_warning),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(8.dp),
            )

            Text(
                text = if (invites != null) {
                    stringResource(R.string.invite_remaining, invites.inviteCount)
                } else {
                    stringResource(R.string.hint_loading)
                },
                style = MaterialTheme.typography.bodyLarge,
            )

            if (invites != null && invites.inviteCount > 0) {
                OutlinedTextField(
                    value = email,
                    onValueChange = {
                        email = it
                        emailError = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !sending,
                    singleLine = true,
                    isError = emailError,
                    label = { Text(stringResource(R.string.invite_email_hint)) },
                    supportingText = {
                        if (emailError) {
                            Text(stringResource(R.string.error_email))
                        }
                    },
                )

                Button(
                    onClick = {
                        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                            emailError = true
                        } else {
                            onSend(email)
                        }
                    },
                    enabled = !sending,
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text(stringResource(R.string.invite_send))
                }
            }

            Text(
                stringResource(R.string.invites_sent),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(top = 16.dp),
            )

            val invited = invites?.invited.orEmpty()
            if (invited.isEmpty()) {
                Text(stringResource(R.string.invites_no_invites_sent))
            } else {
                invited.forEach { invite ->
                    InviteRow(invite = invite, onUserClick = onUserClick)
                }
            }
        }
    }

    if (errorMessage != null) {
        Pr0grammAlertDialog(
            onDismissRequest = onDismissError,
            text = errorMessage,
            confirmText = stringResource(R.string.okay),
            onConfirm = onDismissError,
        )
    }
}

@Composable
private fun InviteRow(
    invite: Api.AccountInfo.Invite,
    onUserClick: (name: String) -> Unit,
) {
    val context = LocalContext.current
    val date = remember(invite.created) {
        DurationFormat.timeToPointInTime(context, invite.created, short = false)
    }

    val name = invite.name

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = name != null) { name?.let(onUserClick) }
            .padding(vertical = 10.dp),
    ) {
        if (name != null) {
            Username(name = name, mark = invite.mark ?: 0)
            Text(
                stringResource(R.string.invite_redeemed, invite.email, date),
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            Text(invite.email, style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.invite_unredeemed, date),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
