package com.pr0gramm.app.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.pr0gramm.app.R
import com.pr0gramm.app.RequestCodes
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.decodeBase64
import com.pr0gramm.app.services.ThemeHelper
import com.pr0gramm.app.services.Track
import com.pr0gramm.app.services.UserService
import com.pr0gramm.app.sync.SyncWorker
import com.pr0gramm.app.ui.base.BaseAppCompatActivity
import com.pr0gramm.app.ui.base.launchWhenCreated
import com.pr0gramm.app.ui.base.launchWhenStarted
import com.pr0gramm.app.ui.compose.setComposeContent
import com.pr0gramm.app.ui.dialogs.ErrorDialogFragment.Companion.showErrorString
import com.pr0gramm.app.util.BrowserHelper
import com.pr0gramm.app.util.DurationFormat
import com.pr0gramm.app.util.di.injector
import com.pr0gramm.app.util.di.instance

typealias Callback = () -> Unit

/**
 * Compose login screen. Renders a captcha-backed login form on the themed radial background.
 */
class LoginActivity : BaseAppCompatActivity("LoginActivity") {
    private val userService: UserService by instance()
    private val prefs: SharedPreferences by instance()

    private var captchaState by mutableStateOf<CaptchaState>(CaptchaState.Loading)
    private var submitting by mutableStateOf(false)

    private var captchaIsLoading: Boolean = false
    private var captchaToken: String? = null

    public override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(ThemeHelper.theme.whiteAccent)
        super.onCreate(savedInstanceState)

        // restore last username (never restore an e-mail address)
        val defaultUsername = prefs.getString(PREF_USERNAME, "")
            ?.takeIf { it.isNotEmpty() && "@" !in it }
            .orEmpty()

        setComposeContent {
            LoginScreen(
                initialUsername = defaultUsername,
                captcha = captchaState,
                submitting = submitting,
                onReloadCaptcha = ::updateUserCaptcha,
                onSubmit = ::onLoginClicked,
                onRegister = ::onRegisterClicked,
                onPasswordRecovery = ::onPasswordRecoveryClicked,
            )
        }

        updateUserCaptcha()
    }

    private fun updateUserCaptcha() {
        if (captchaIsLoading) {
            return
        }

        captchaIsLoading = true
        captchaToken = null
        captchaState = CaptchaState.Loading

        launchWhenCreated {
            try {
                val captcha = userService.userCaptcha()

                val bitmap = captcha.decodeBitmap()
                val aspect = bitmap.width.toFloat() / bitmap.height.toFloat()

                captchaState = CaptchaState.Loaded(bitmap.asImageBitmap(), aspect)
                captchaToken = captcha.token
            } catch (err: Exception) {
                captchaState = CaptchaState.Failed
                throw err
            } finally {
                captchaIsLoading = false
            }
        }
    }

    private fun onLoginClicked(username: String, password: String, captchaAnswer: String) {
        val token = captchaToken ?: return

        // store last username
        prefs.edit().putString(PREF_USERNAME, username).apply()

        Track.loginStarted()

        launchWhenStarted(busyIndicator = true) {
            submitting = true
            try {
                handleLoginResult(userService.login(username, password, token, captchaAnswer))
            } finally {
                submitting = false
            }
        }
    }

    private fun handleLoginResult(response: UserService.LoginResult) {
        when (response) {
            is UserService.LoginResult.Success -> {
                SyncWorker.scheduleNextSync(this, sourceTag = "Login")
                Track.loginSuccessful()

                // signal success
                setResult(Activity.RESULT_OK)
                finish()
            }

            is UserService.LoginResult.Banned -> {
                Track.loginFailed("ban")

                val date = response.ban.endTime?.let { date ->
                    DurationFormat.timeToPointInTime(this, date, short = false)
                }

                val reason = response.ban.reason
                val message = if (date == null) {
                    getString(R.string.banned_forever, reason)
                } else {
                    getString(R.string.banned, date, reason)
                }

                showErrorString(supportFragmentManager, message)

                updateUserCaptcha()
            }

            is UserService.LoginResult.FailureLogin -> {
                Track.loginFailed("generic")

                val msg = getString(R.string.login_not_successful_login)
                showErrorString(supportFragmentManager, msg)

                updateUserCaptcha()
            }

            is UserService.LoginResult.FailureCaptcha -> {
                Track.loginFailed("captcha")

                val msg = getString(R.string.login_not_successful_captcha)
                showErrorString(supportFragmentManager, msg)

                updateUserCaptcha()
            }

            else -> {
                Track.loginFailed("error")

                val msg = getString(R.string.login_not_successful_error)
                showErrorString(supportFragmentManager, msg)

                updateUserCaptcha()
            }
        }
    }

    private fun onRegisterClicked() {
        Track.registerLinkClicked()

        val uri = Uri.parse("https://pr0gramm.com/pr0mium/iap?iap=true")
        BrowserHelper.openCustomTab(this, uri)
    }

    private fun onPasswordRecoveryClicked() {
        val intent = Intent(this, RequestPasswordRecoveryActivity::class.java)
        startActivity(intent)
    }

    class DoIfAuthorizedHelper(private val fragment: androidx.fragment.app.Fragment) {
        private var retry: Callback? = null

        fun onActivityResult(requestCode: Int, resultCode: Int) {
            if (requestCode == RequestCodes.AUTHORIZED_HELPER) {
                if (resultCode == Activity.RESULT_OK) {
                    retry?.invoke()
                }

                retry = null
            }
        }

        /**
         * Executes the given runnable if a user is signed in. If not, this method shows
         * the login screen. After a successful login, the given 'retry' runnable will be called.
         */
        private fun runAuth(runnable: Callback, retry: Callback? = null): Boolean {
            val context = fragment.context ?: return false

            val userService: UserService = context.injector.instance()
            return if (userService.isAuthorized) {
                runnable()
                true

            } else {
                this.retry = retry

                val intent = Intent(context, LoginActivity::class.java)
                startActivityForResult(intent, RequestCodes.AUTHORIZED_HELPER)
                false
            }
        }

        fun runAuthWithRetry(runnable: Callback): Boolean {
            return runAuth(runnable, runnable)
        }

        fun runAuthNoRetry(runnable: Callback): Boolean {
            return runAuth(runnable, retry = null)
        }

        suspend fun runAuthNoRetrySuspend(runnable: suspend () -> Unit): Boolean {
            val context = fragment.context ?: return false
            val userService: UserService = context.injector.instance()

            if (!userService.isAuthorized) {
                val intent = Intent(context, LoginActivity::class.java)
                startActivityForResult(intent, RequestCodes.AUTHORIZED_HELPER)
                return false
            }

            runnable()

            return true
        }

        private fun startActivityForResult(intent: Intent, requestCode: Int) {
            fragment.startActivityForResult(intent, requestCode)
        }
    }

    companion object {
        private const val PREF_USERNAME = "LoginDialogFragment.username"

        /**
         * Executes the given runnable if a user is signed in. If not, this method
         * will show a login screen.
         */
        fun helper(fragment: androidx.fragment.app.Fragment) = DoIfAuthorizedHelper(fragment)
    }
}

private sealed interface CaptchaState {
    data object Loading : CaptchaState
    data object Failed : CaptchaState
    data class Loaded(val image: ImageBitmap, val aspect: Float) : CaptchaState
}

@Composable
private fun LoginScreen(
    initialUsername: String,
    captcha: CaptchaState,
    submitting: Boolean,
    onReloadCaptcha: () -> Unit,
    onSubmit: (username: String, password: String, captchaAnswer: String) -> Unit,
    onRegister: () -> Unit,
    onPasswordRecovery: () -> Unit,
) {
    val background = Brush.radialGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.primaryContainer,
        ),
    )

    var username by rememberSaveable { mutableStateOf(initialUsername) }
    var password by rememberSaveable { mutableStateOf("") }
    var captchaAnswer by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }

    val isMailAddress = "@" in username
    val captchaLoaded = captcha is CaptchaState.Loaded

    val canSubmit = !submitting && captchaLoaded &&
            username.isNotBlank() && !isMailAddress &&
            password.isNotBlank() && captchaAnswer.isNotBlank()

    fun submit() {
        if (canSubmit) onSubmit(username, password, captchaAnswer)
    }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White,
        disabledTextColor = Color.White.copy(alpha = 0.6f),
        cursorColor = Color.White,
        focusedBorderColor = Color.White,
        unfocusedBorderColor = Color.White.copy(alpha = 0.7f),
        disabledBorderColor = Color.White.copy(alpha = 0.3f),
        focusedLabelColor = Color.White,
        unfocusedLabelColor = Color.White.copy(alpha = 0.7f),
        focusedTrailingIconColor = Color.White,
        unfocusedTrailingIconColor = Color.White,
    )

    Box(
        Modifier
            .fillMaxSize()
            .background(background),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 32.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(24.dp))

            Image(
                painter = painterResource(R.drawable.ic_arrow),
                contentDescription = null,
                modifier = Modifier
                    .padding(horizontal = 48.dp)
                    .height(112.dp),
                contentScale = ContentScale.Fit,
            )

            Spacer(Modifier.height(32.dp))

            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                modifier = Modifier.fillMaxWidth(),
                enabled = !submitting,
                singleLine = true,
                label = { Text(stringResource(R.string.hint_username)) },
                isError = isMailAddress,
                supportingText = if (isMailAddress) {
                    { Text(stringResource(R.string.hint_no_email)) }
                } else null,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                colors = fieldColors,
            )

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                modifier = Modifier.fillMaxWidth(),
                enabled = !submitting,
                singleLine = true,
                label = { Text(stringResource(R.string.hint_password)) },
                visualTransformation = if (passwordVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) {
                                Icons.Filled.Visibility
                            } else {
                                Icons.Filled.VisibilityOff
                            },
                            contentDescription = null,
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Next,
                ),
                colors = fieldColors,
            )

            Spacer(Modifier.height(16.dp))

            CaptchaBox(
                captcha = captcha,
                onReload = onReloadCaptcha,
            )

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = captchaAnswer,
                onValueChange = { captchaAnswer = it },
                modifier = Modifier.fillMaxWidth(),
                enabled = !submitting && captchaLoaded,
                singleLine = true,
                label = { Text(stringResource(R.string.hint_captcha)) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                colors = fieldColors,
            )

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = ::submit,
                modifier = Modifier.fillMaxWidth(),
                enabled = canSubmit,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = MaterialTheme.colorScheme.primary,
                ),
            ) {
                Text(stringResource(R.string.login))
            }

            Spacer(Modifier.height(8.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(
                    onClick = onRegister,
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
                ) {
                    Text(stringResource(R.string.login_register))
                }

                TextButton(
                    onClick = onPasswordRecovery,
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
                ) {
                    Text(stringResource(R.string.login_password_recovery))
                }
            }
        }
    }
}

@Composable
private fun CaptchaBox(
    captcha: CaptchaState,
    onReload: () -> Unit,
) {
    val aspect = (captcha as? CaptchaState.Loaded)?.aspect ?: 4f

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(aspect)
            .background(Color.Black.copy(alpha = 0.12f))
            .clickable(onClick = onReload),
        contentAlignment = Alignment.Center,
    ) {
        when (captcha) {
            is CaptchaState.Loaded -> Image(
                bitmap = captcha.image,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )

            CaptchaState.Loading -> CircularProgressIndicator(color = Color.White)

            CaptchaState.Failed -> Text(
                text = stringResource(R.string.could_not_load_image),
                color = Color.White,
            )
        }
    }
}

private fun Api.UserCaptcha.decodeBitmap(): Bitmap {
    val index = image.indexOf(',')
    val offset = if (index < 0) 0 else index + 1

    val bytes = image.substring(offset).decodeBase64(urlSafe = false)
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
}
