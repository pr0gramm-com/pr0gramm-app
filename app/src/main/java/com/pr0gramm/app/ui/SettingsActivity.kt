package com.pr0gramm.app.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.pr0gramm.app.Instant
import com.pr0gramm.app.R
import com.pr0gramm.app.Settings
import com.pr0gramm.app.services.BookmarkService
import com.pr0gramm.app.services.RecentSearchesServices
import com.pr0gramm.app.services.Storage
import com.pr0gramm.app.services.ThemeHelper
import com.pr0gramm.app.services.UserService
import com.pr0gramm.app.services.preloading.PreloadManager
import com.pr0gramm.app.ui.base.BaseAppCompatActivity
import com.pr0gramm.app.ui.base.launchWhenStarted
import com.pr0gramm.app.ui.compose.setComposeContent
import com.pr0gramm.app.ui.dialogs.LanguagePickerDialog
import com.pr0gramm.app.ui.dialogs.UpdateDialogFragment
import com.pr0gramm.app.ui.intro.IntroActivity
import com.pr0gramm.app.util.AndroidUtility
import com.pr0gramm.app.util.di.instance
import com.pr0gramm.app.util.doInBackground
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible

class SettingsActivity : BaseAppCompatActivity("SettingsActivity") {

    private val userService: UserService by instance()
    private val bookmarkService: BookmarkService by instance()
    private val preloadManager: PreloadManager by instance()
    private val recentSearchesServices: RecentSearchesServices by instance()

    private var preloadSummary by mutableStateOf<String?>(null)

    private val downloadPathLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val intent = result.data ?: return@registerForActivityResult
            if (!Storage.persistTreeUri(this, intent)) {
                Toast.makeText(this, R.string.error_invalid_download_directory, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(ThemeHelper.theme.noActionBar)
        super.onCreate(savedInstanceState)

        if (!userService.isAuthorized) {
            Settings.resetContentTypeSettings()
        }

        Settings.edit {
            putLong("_settings_last_seen", Instant.now().millis)
        }

        setComposeContent {
            SettingsScreen(
                actions = SettingsActions(
                    onBack = { finish() },
                    onCheckForUpdate = {
                        UpdateDialogFragment.checkForUpdatesInteractive(this@SettingsActivity)
                    },
                    onShowChangelog = {
                        ChangeLogDialog().show(supportFragmentManager, null)
                    },
                    onRecommend = {
                        val text = "Probiere mal die offizielle pr0gramm App aus: https://app.pr0gramm.com/"
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "pr0gramm app")
                            putExtra(Intent.EXTRA_TEXT, text)
                        }
                        startActivity(Intent.createChooser(intent, getString(R.string.share_using)))
                    },
                    onCleanPreloaded = {
                        doInBackground { preloadManager.deleteOlderThan(Instant.now()) }
                    },
                    onClearTagSuggestions = {
                        recentSearchesServices.clearHistory()
                        Toast.makeText(
                            this@SettingsActivity,
                            R.string.pref_pseudo_clear_tag_suggestions_notification,
                            Toast.LENGTH_SHORT,
                        ).show()
                    },
                    onShowOnboarding = {
                        IntroActivity.launch(this@SettingsActivity)
                    },
                    onRestoreBookmarks = {
                        launchWhenStarted(busyIndicator = true) {
                            bookmarkService.restore()
                        }
                    },
                    onSelectDownloadTarget = {
                        val intent = Storage.openTreeIntent(this@SettingsActivity)
                        downloadPathLauncher.launch(intent)
                    },
                    onSelectLanguage = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            val intent = Intent(
                                android.provider.Settings.ACTION_APP_LOCALE_SETTINGS,
                                Uri.parse("package:${packageName}"),
                            )
                            startActivity(intent)
                        } else {
                            LanguagePickerDialog().show(supportFragmentManager, null)
                        }
                    },
                    onThemeChanged = {
                        ThemeHelper.updateTheme()
                        AndroidUtility.recreateActivity(this@SettingsActivity)
                    },
                    canRestoreBookmarks = bookmarkService.canEdit,
                    preloadSummary = preloadSummary,
                ),
            )
        }

        // Update preload info
        launchWhenStarted {
            preloadManager.items.collect { items ->
                val totalSize = runInterruptible(Dispatchers.IO) {
                    items.values().sumOf { item ->
                        item.media.length() +
                                item.thumbnail.length() +
                                (item.thumbnailFull?.length() ?: 0)
                    }
                }
                preloadSummary = getString(
                    R.string.pseudo_clean_preloaded_summary_with_size,
                    totalSize / (1024f * 1024f),
                )
            }
        }
    }
}
