package com.pr0gramm.app.ui

import com.pr0gramm.app.Pr0Domain
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.ScrollView
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.isVisible
import androidx.lifecycle.withStarted
import com.pr0gramm.app.R
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.decodeBase64
import com.pr0gramm.app.services.ThemeHelper
import com.pr0gramm.app.services.ThemeHelper.primaryColorDark
import com.pr0gramm.app.services.UserService
import com.pr0gramm.app.sync.SyncWorker
import com.pr0gramm.app.ui.base.BaseAppCompatActivity
import com.pr0gramm.app.ui.base.launchUntilDestroy
import com.pr0gramm.app.ui.base.launchWhenCreated
import com.pr0gramm.app.ui.base.withViewDisabled
import com.pr0gramm.app.ui.dialogs.ErrorDialogFragment.Companion.showErrorString
import com.pr0gramm.app.ui.views.AspectLayout
import com.pr0gramm.app.ui.views.BusyIndicator
import com.pr0gramm.app.util.BrowserHelper
import com.pr0gramm.app.util.DurationFormat
import com.pr0gramm.app.util.addTextChangedListener
import com.pr0gramm.app.util.di.injector
import com.pr0gramm.app.util.di.instance
import com.pr0gramm.app.util.find
import com.pr0gramm.app.util.use
import kotterknife.bindView

typealias Callback = () -> Unit

/**
 */
class LoginActivity : BaseAppCompatActivity("LoginActivity") {
    private val userService: UserService by instance()
    private val prefs: SharedPreferences by instance()

    private val usernameView: EditText by bindView(R.id.username)
    private val passwordView: EditText by bindView(R.id.password)
    private val submitView: Button by bindView(R.id.login)

    private val captchaBusy: BusyIndicator by bindView(R.id.captcha_busy)
    private val captchaImageView: ImageView by bindView(R.id.captcha_image)
    private val captchaAspect: AspectLayout by bindView(R.id.captcha_aspect)
    private val captchaAnswerView: EditText by bindView(R.id.captcha_answer)

    private var captchaIsLoading: Boolean = false
    private var captchaToken: String? = null

    public override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(ThemeHelper.theme.whiteAccent)
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_login)

        // restore last username
        val defaultUsername = prefs.getString(PREF_USERNAME, "")
        if (!defaultUsername.isNullOrEmpty() && "@" !in defaultUsername) {
            usernameView.setText(defaultUsername)
        }

        submitView.setOnClickListener { onLoginClicked() }

        find<View>(R.id.register).setOnClickListener { onRegisterClicked() }
        find<View>(R.id.password_recovery).setOnClickListener { onPasswordRecoveryClicked() }

        updateActivityBackground()

        usernameView.addTextChangedListener { updateSubmitViewEnabled() }
        passwordView.addTextChangedListener { updateSubmitViewEnabled() }
        captchaAnswerView.addTextChangedListener { updateSubmitViewEnabled() }

        captchaAspect.setOnClickListener { updateUserCaptcha() }

        // keep the login button visible above the keyboard
        val scrollView = find<ScrollView>(R.id.scroll)
        scrollView.addOnLayoutChangeListener { _, _, top, _, bottom, _, oldTop, _, oldBottom ->
            if (bottom - top < oldBottom - oldTop && currentFocus is EditText) {
                scrollView.post {
                    val margin = (16 * resources.displayMetrics.density).toInt()
                    scrollView.smoothScrollTo(0, submitView.bottom + margin - scrollView.height)
                }
            }
        }

        updateSubmitViewEnabled()

        updateUserCaptcha()
    }

    private fun updateUserCaptcha() {
        if (captchaIsLoading) {
            return
        }

        captchaIsLoading = true

        captchaToken = null

        // show busy indicator while loading image
        captchaBusy.isVisible = true
        captchaImageView.isVisible = false

        // clear previous input value
        captchaAnswerView.setText("")
        captchaAnswerView.isEnabled = false

        launchWhenCreated {
            try {
                val captcha = userService.userCaptcha()

                // decode the image
                val image = captcha.decodeImage(this@LoginActivity)
                val aspect = image.intrinsicWidth.toFloat() / image.intrinsicHeight.toFloat()

                // and set it on the view
                captchaImageView.setImageDrawable(image)
                captchaImageView.isVisible = true

                // also correct the aspect ratio
                captchaAspect.aspect = aspect

                captchaAnswerView.isEnabled = true

                captchaToken = captcha.token
            } finally {
                captchaIsLoading = false
                captchaBusy.isVisible = false
            }
        }
    }

    private fun updateSubmitViewEnabled() {
        val usernameSet = usernameView.text.isNotBlank()
        val passwordSet = passwordView.text.isNotBlank()
        val captchaSet = captchaAnswerView.text.isNotBlank()

        // only accept usernames
        val isMailAddress = "@" in usernameView.text

        if (isMailAddress) {
            usernameView.error = getString(R.string.hint_no_email)
        }

        submitView.isEnabled = usernameSet && passwordSet && captchaSet && !isMailAddress
    }

    private fun updateActivityBackground() {
        val style = ThemeHelper.theme.whiteAccent

        @DrawableRes
        val drawableId = theme.obtainStyledAttributes(style, R.styleable.AppTheme).use {
            it.getResourceId(R.styleable.AppTheme_loginBackground, 0)
        }

        if (drawableId == 0)
            return

        val fallbackColor = ContextCompat.getColor(this, primaryColorDark)
        val background = createBackgroundDrawable(drawableId, fallbackColor)
        findViewById<View>(R.id.content).background = background

        // the gradient ends in the dark primary color, continue it below the content
        window.decorView.setBackgroundColor(fallbackColor)
        window.navigationBarColor = fallbackColor
    }

    private fun createBackgroundDrawable(drawableId: Int, fallbackColor: Int): Drawable {
        val drawable = ResourcesCompat.getDrawable(resources, drawableId, theme)!!
        return WrapCrashingDrawable(fallbackColor, drawable)
    }

    private fun onLoginClicked() {
        val token = captchaToken ?: return

        val username = usernameView.text.toString()
        val password = passwordView.text.toString()
        val captchaAnswer = captchaAnswerView.text.toString()

        if (username.isEmpty()) {
            usernameView.error = getString(R.string.must_not_be_empty)
            return
        }

        if (password.isEmpty()) {
            passwordView.error = getString(R.string.must_not_be_empty)
            return
        }

        if (captchaAnswer.isEmpty()) {
            captchaAnswerView.error = getString(R.string.must_not_be_empty)
            return
        }

        // store last username
        prefs.edit().putString(PREF_USERNAME, username).apply()

        launchUntilDestroy(busyIndicator = true) {
            withViewDisabled(usernameView, passwordView, submitView) {
                val result = userService.login(username, password, token, captchaAnswer)

                // error dialogs are fragments, wait until we can show them
                withStarted { handleLoginResult(result) }
            }
        }
    }

    private fun handleLoginResult(response: UserService.LoginResult) {
        when (response) {
            is UserService.LoginResult.Success -> {
                SyncWorker.scheduleNextSync(this, sourceTag = "Login")

                // signal success
                setResult(Activity.RESULT_OK)
                finish()
            }

            is UserService.LoginResult.Banned -> {
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
                val msg = getString(R.string.login_not_successful_login)
                showErrorString(supportFragmentManager, msg)

                updateUserCaptcha()
            }

            is UserService.LoginResult.FailureCaptcha -> {
                val msg = getString(R.string.login_not_successful_captcha)
                showErrorString(supportFragmentManager, msg)

                updateUserCaptcha()
            }

            else -> {
                val msg = getString(R.string.login_not_successful_error)
                showErrorString(supportFragmentManager, msg)

                updateUserCaptcha()
            }
        }
    }

    private fun onRegisterClicked() {
        val uri = Uri.parse("https://$Pr0Domain/pr0mium/iap?iap=true")
        BrowserHelper.openCustomTab(this, uri)
    }

    private fun onPasswordRecoveryClicked() {
        val intent = Intent(this, RequestPasswordRecoveryActivity::class.java)
        startActivity(intent)
    }

    // must be created before the fragment is created, e.g. as a field initializer.
    class DoIfAuthorizedHelper(private val fragment: androidx.fragment.app.Fragment) {
        private var retry: Callback? = null

        private val loginLauncher = fragment.registerForActivityResult(StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                retry?.invoke()
            }

            retry = null
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

                loginLauncher.launch(Intent(context, LoginActivity::class.java))
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
                loginLauncher.launch(Intent(context, LoginActivity::class.java))
                return false
            }

            runnable()

            return true
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

fun Api.UserCaptcha.decodeImage(context: Context): Drawable {
    val index = image.indexOf(',')
    val offset = if (index < 0) 0 else index + 1

    val bytes = image.substring(offset).decodeBase64(urlSafe = false)
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    return BitmapDrawable(context.resources, bitmap)
}
