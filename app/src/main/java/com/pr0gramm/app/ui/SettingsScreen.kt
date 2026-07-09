package com.pr0gramm.app.ui

import android.content.SharedPreferences
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pr0gramm.app.BuildConfig
import com.pr0gramm.app.R
import com.pr0gramm.app.Settings

/**
 * Callbacks for settings actions that need Activity-level handling.
 */
class SettingsActions(
    val onBack: () -> Unit = {},
    val onCheckForUpdate: () -> Unit = {},
    val onShowChangelog: () -> Unit = {},
    val onRecommend: () -> Unit = {},
    val onCleanPreloaded: () -> Unit = {},
    val onClearTagSuggestions: () -> Unit = {},
    val onShowOnboarding: () -> Unit = {},
    val onRestoreBookmarks: () -> Unit = {},
    val onSelectDownloadTarget: () -> Unit = {},
    val onSelectLanguage: () -> Unit = {},
    val onThemeChanged: () -> Unit = {},
    val canRestoreBookmarks: Boolean = false,
    val preloadSummary: String? = null,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(actions: SettingsActions) {
    var currentScreen by remember { mutableStateOf("main") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (currentScreen) {
                            "behaviour" -> stringResource(R.string.prefcat_behaviour)
                            "visual" -> stringResource(R.string.prefcat_visual_title)
                            "update" -> stringResource(R.string.prefcat_update)
                            "privacy" -> stringResource(R.string.prefcat_privacy_screen_title)
                            else -> stringResource(R.string.prefcat_settings_title)
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (currentScreen == "main") {
                            actions.onBack()
                        } else {
                            currentScreen = "main"
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        AnimatedContent(
            targetState = currentScreen,
            modifier = Modifier.padding(padding),
            label = "settings_navigation",
        ) { screen ->
            when (screen) {
                "behaviour" -> BehaviourScreen(actions)
                "visual" -> VisualScreen()
                "update" -> UpdateScreen(actions)
                "privacy" -> PrivacyScreen()
                else -> MainSettingsScreen(
                    actions = actions,
                    onNavigate = { currentScreen = it },
                )
            }
        }
    }
}

@Composable
private fun MainSettingsScreen(
    actions: SettingsActions,
    onNavigate: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        // Theme
        PreferenceCategory(stringResource(R.string.prefcat_theme))

        val context = LocalContext.current
        val themeEntries = remember {
            Themes.entries.map { it.name to it.title(context) }
        }
        ListPreference(
            title = stringResource(R.string.pref_theme_title),
            summary = stringResource(R.string.pref_theme_summary),
            key = "pref_theme",
            defaultValue = Themes.ORANGE.name,
            entries = themeEntries,
            onChanged = { actions.onThemeChanged() },
        )

        // Settings
        PreferenceCategory(stringResource(R.string.prefcat_settings_title))

        NavigationPreference(
            title = stringResource(R.string.prefcat_behaviour),
            summary = stringResource(R.string.prefcat_behaviour_summary),
            onClick = { onNavigate("behaviour") },
        )

        NavigationPreference(
            title = stringResource(R.string.prefcat_visual_title),
            summary = stringResource(R.string.prefcat_visual_summary),
            onClick = { onNavigate("visual") },
        )

        ClickablePreference(
            title = stringResource(R.string.pref_downloadLocation_title),
            summary = stringResource(R.string.pref_downloadLocation_summary),
            onClick = actions.onSelectDownloadTarget,
        )

        NavigationPreference(
            title = stringResource(R.string.prefcat_update),
            summary = stringResource(R.string.prefcat_update_summary),
            onClick = { onNavigate("update") },
        )

        NavigationPreference(
            title = stringResource(R.string.prefcat_privacy_screen_title),
            summary = stringResource(R.string.prefcat_privacy_screen_summary),
            onClick = { onNavigate("privacy") },
        )

        ClickablePreference(
            title = stringResource(R.string.pref_pseudo_language_title),
            summary = stringResource(R.string.pref_pseudo_language_summary),
            onClick = actions.onSelectLanguage,
        )

        // Debug (only in debug builds)
        if (BuildConfig.DEBUG) {
            PreferenceCategory("Debug")
            SwitchPreference(
                title = "Use mock api endpoint",
                summary = "Requires full restart of app",
                key = "pref_debug_mock_api",
                defaultValue = false,
            )
        }

        // Misc
        PreferenceCategory(stringResource(R.string.prefcat_misc))

        ClickablePreference(
            title = stringResource(R.string.pref_pseudo_changelog),
            summary = stringResource(R.string.pref_pseudo_changelog_summary),
            onClick = actions.onShowChangelog,
        )

        ClickablePreference(
            title = stringResource(R.string.pref_pseudo_clean_preloaded_title),
            summary = actions.preloadSummary
                ?: stringResource(R.string.pseudo_clean_preloaded_summary),
            onClick = actions.onCleanPreloaded,
        )

        ClickablePreference(
            title = stringResource(R.string.pref_pseudo_onboarding_title),
            summary = stringResource(R.string.pref_pseudo_onboarding_summary),
            onClick = actions.onShowOnboarding,
        )

        if (actions.canRestoreBookmarks) {
            ClickablePreference(
                title = stringResource(R.string.pref_pseudo_restore_bookmarks_title),
                summary = stringResource(R.string.pref_pseudo_restore_bookmarks_summary),
                onClick = actions.onRestoreBookmarks,
            )
        }

        ClickablePreference(
            title = stringResource(R.string.pref_website_title),
            summary = stringResource(R.string.pref_website_summary),
            onClick = actions.onRecommend,
        )

        SwitchPreference(
            title = stringResource(R.string.pref_use_secondary_servers_title),
            summary = stringResource(R.string.pref_use_secondary_servers_summary),
            key = "pref_use_secondary_servers",
            defaultValue = false,
        )
    }
}

@Composable
private fun BehaviourScreen(actions: SettingsActions) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        // Sync
        PreferenceCategory(stringResource(R.string.prefcat_behaviour__sync_title))

        SwitchPreference(
            title = stringResource(R.string.pref_sync_site_settings_title),
            summary = stringResource(R.string.pref_sync_site_settings_summary),
            key = "pref_sync_site_settings",
            defaultValue = false,
        )

        // Data usage
        PreferenceCategory(stringResource(R.string.prefcat_behaviour__datausage_title))

        val context = LocalContext.current
        val confirmOnMobileEntries = remember {
            context.resources.getStringArray(R.array.pref_confirm_play_on_mobile_human)
                .zip(context.resources.getStringArray(R.array.pref_confirm_play_on_mobile_values))
                .map { (human, value) -> value to human }
        }
        ListPreference(
            title = stringResource(R.string.pref_confirm_play_on_mobile_title),
            summary = stringResource(R.string.pref_confirm_play_on_mobile_summary),
            key = "pref_confirm_play_on_mobile_list",
            defaultValue = stringResource(R.string.pref_confirm_play_on_mobile_default),
            entries = confirmOnMobileEntries,
        )

        val videoQualityEntries = remember {
            context.resources.getStringArray(R.array.pref_video_quality_human)
                .zip(context.resources.getStringArray(R.array.pref_video_quality_values))
                .map { (human, value) -> value to human }
        }
        ListPreference(
            title = stringResource(R.string.pref_video_quality_values_title),
            summary = stringResource(R.string.pref_video_quality_values_summary),
            key = "pref_video_quality_list",
            defaultValue = stringResource(R.string.pref_video_quality_default),
            entries = videoQualityEntries,
        )

        SwitchPreference(
            title = stringResource(R.string.pref_load_hq_image_in_zoomview_title),
            summary = stringResource(R.string.pref_load_hq_image_in_zoomview_summary),
            key = "pref_load_hq_image_in_zoomview",
            defaultValue = true,
        )

        // Feed
        PreferenceCategory(stringResource(R.string.prefcat_behaviour__feed_title))

        SwitchPreference(
            title = stringResource(R.string.pref_enable_quick_peek_title),
            summary = stringResource(R.string.pref_enable_quick_peek_summary),
            key = "pref_enable_quick_peek",
            defaultValue = true,
        )

        SwitchPreference(
            title = stringResource(R.string.pref_feed_start_at_new_title),
            summary = stringResource(R.string.pref_feed_start_at_new_summary),
            key = "pref_feed_start_at_new",
            defaultValue = false,
        )

        SwitchPreference(
            title = stringResource(R.string.pref_feed_hide_junk_in_new_title),
            summary = stringResource(R.string.pref_feed_hide_junk_in_new_summary),
            key = "pref_feed_hide_junk_in_new",
            defaultValue = true,
        )

        SwitchPreference(
            title = stringResource(R.string.pref_feed_start_at_sfw_title),
            summary = stringResource(R.string.pref_feed_start_at_sfw_summary),
            key = "pref_feed_start_at_sfw",
            defaultValue = true,
        )

        val sfwEnabled = rememberPreference("pref_feed_start_at_sfw", true)
        SwitchPreference(
            title = stringResource(R.string.pref_feed_start_at_sfwpol_title),
            summary = stringResource(R.string.pref_feed_start_at_sfwpol_summary),
            key = "pref_feed_start_at_sfwpol",
            defaultValue = true,
            enabled = sfwEnabled,
        )

        // Categories
        PreferenceCategory(stringResource(R.string.prefcat_behaviour__categories_title))

        SwitchPreference(
            title = stringResource(R.string.pref_show_category_random_title),
            summary = stringResource(R.string.pref_show_category_random_summary),
            key = "pref_show_category_random",
            defaultValue = true,
        )

        SwitchPreference(
            title = stringResource(R.string.pref_show_category_controversial_title),
            summary = stringResource(R.string.pref_show_category_controversial_summary),
            key = "pref_show_category_controversial",
            defaultValue = true,
        )

        SwitchPreference(
            title = stringResource(R.string.pref_show_category_premium_title),
            summary = stringResource(R.string.pref_show_category_premium_summary),
            key = "pref_show_category_premium",
            defaultValue = true,
        )

        SwitchPreference(
            title = stringResource(R.string.pref_show_category_junk_title),
            summary = stringResource(R.string.pref_show_category_junk_summary),
            key = "pref_show_category_junk",
            defaultValue = true,
        )

        // Post
        PreferenceCategory(stringResource(R.string.prefcat_post))

        SwitchPreference(
            title = stringResource(R.string.pref_upvote_on_collect_title),
            summary = stringResource(R.string.pref_upvote_on_collect_summary),
            key = "pref_upvote_on_collect",
            defaultValue = false,
        )

        SwitchPreference(
            title = stringResource(R.string.pref_rotate_in_fullscreen_title),
            summary = stringResource(R.string.pref_rotate_in_fullscreen_summary),
            key = "pref_rotate_in_fullscreen",
            defaultValue = true,
        )

        val tapActionEntries = remember {
            context.resources.getStringArray(R.array.pref_tap_action_human)
                .zip(context.resources.getStringArray(R.array.pref_tap_action_value))
                .map { (human, value) -> value to human }
        }

        ListPreference(
            title = stringResource(R.string.pref_single_tap_action_title),
            summary = stringResource(R.string.pref_single_tap_action_summary),
            key = "pref_single_tap_action",
            defaultValue = "NONE",
            entries = tapActionEntries,
        )

        ListPreference(
            title = stringResource(R.string.pref_double_tap_action_title),
            summary = stringResource(R.string.pref_double_tap_action_summary),
            key = "pref_double_tap_action",
            defaultValue = "UPVOTE",
            entries = tapActionEntries,
        )

        SwitchPreference(
            title = stringResource(R.string.pref_audiofocus_transient_title),
            summary = stringResource(R.string.pref_audiofocus_transient_summary),
            key = "pref_audiofocus_transient",
            defaultValue = false,
        )

        // Incognito
        PreferenceCategory("Incognito")

        SwitchPreference(
            title = stringResource(R.string.pref_use_incognito_browser_title),
            summary = stringResource(R.string.pref_use_incognito_browser_summary),
            key = "pref_use_incognito_browser",
            defaultValue = false,
        )

        val incognitoEnabled = rememberPreference("pref_use_incognito_browser", false)
        SwitchPreference(
            title = stringResource(R.string.pref_override_youtube_links_title),
            summary = stringResource(R.string.pref_override_youtube_links_summary),
            key = "pref_override_youtube_links",
            defaultValue = true,
            enabled = incognitoEnabled,
        )

        SwitchPreference(
            title = stringResource(R.string.pref_private_input_title),
            summary = stringResource(R.string.pref_private_input_summary),
            key = "pref_private_input",
            defaultValue = false,
        )

        ClickablePreference(
            title = stringResource(R.string.pref_pseudo_clear_tag_suggestions_title),
            summary = stringResource(R.string.pref_pseudo_clear_tag_suggestions_summary),
            onClick = actions.onClearTagSuggestions,
        )
    }
}

@Composable
private fun VisualScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        // Menu items
        PreferenceCategory(stringResource(R.string.prefcat_visual__menu_items_title))

        SwitchPreference(
            title = stringResource(R.string.pref_show_pin_button),
            summary = stringResource(R.string.pref_show_pin_button_summary),
            key = "pref_show_pin_button",
            defaultValue = true,
        )

        SwitchPreference(
            title = stringResource(R.string.pref_show_refresh_button),
            summary = stringResource(R.string.pref_show_refresh_button_summary),
            key = "pref_show_refresh_button",
            defaultValue = false,
        )

        val context = LocalContext.current
        val imageSearchEntries = remember {
            context.resources.getStringArray(R.array.pref_image_search_engine_human)
                .zip(context.resources.getStringArray(R.array.pref_image_search_engine_values))
                .map { (human, value) -> value to human }
        }
        ListPreference(
            title = stringResource(R.string.pref_image_search_engine_title),
            summary = stringResource(R.string.pref_image_search_engine_summary),
            key = "pref_image_search_engine",
            defaultValue = "GOOGLE",
            entries = imageSearchEntries,
        )

        // Tags
        PreferenceCategory(stringResource(R.string.prefcat_visual__tags))

        SwitchPreference(
            title = stringResource(R.string.pref_hide_tag_vote_buttons_title),
            summary = stringResource(R.string.pref_hide_tag_vote_buttons_summary),
            key = "pref_hide_tag_vote_buttons",
            defaultValue = false,
        )

        SwitchPreference(
            title = stringResource(R.string.pref_tag_cloud_view_title),
            summary = stringResource(R.string.pref_tag_cloud_view_summary),
            key = "pref_tag_cloud_view",
            defaultValue = false,
        )

        // Misc
        PreferenceCategory(stringResource(R.string.prefcat_misc))

        SwitchPreference(
            title = stringResource(R.string.pref_highlight_items_in_feed),
            summary = stringResource(R.string.pref_highlight_items_in_feed_summary),
            key = "pref_highlight_items_in_feed",
            defaultValue = true,
        )

        SwitchPreference(
            title = stringResource(R.string.pref_seen_indicator_style),
            summary = stringResource(R.string.pref_seen_indicator_style_summary),
            key = "pref_mark_items_as_seen",
            defaultValue = false,
        )

        SwitchPreference(
            title = stringResource(R.string.pref_use_tag_as_title_title),
            summary = stringResource(R.string.pref_use_tag_as_title_sumary),
            key = "pref_use_tag_as_title",
            defaultValue = true,
        )

        SwitchPreference(
            title = stringResource(R.string.pref_colorful_comment_lines_title),
            summary = stringResource(R.string.pref_colorful_comment_lines_summary),
            key = "pref_colorful_comment_lines",
            defaultValue = false,
        )

        SwitchPreference(
            title = stringResource(R.string.pref_fancy_scroll_vertical),
            summary = stringResource(R.string.pref_fancy_scroll_vertical_summary),
            key = "pref_fancy_scroll_vertical",
            defaultValue = true,
        )

        SwitchPreference(
            title = stringResource(R.string.pref_fancy_scroll_horizontal),
            summary = stringResource(R.string.pref_fancy_scroll_horizontal_summary),
            key = "pref_fancy_scroll_horizontal",
            defaultValue = true,
        )

        SwitchPreference(
            title = stringResource(R.string.pref_show_content_type_flag_title),
            summary = stringResource(R.string.pref_show_content_type_flag_summary),
            key = "pref_show_content_type_flag_2",
            defaultValue = false,
        )

        SwitchPreference(
            title = stringResource(R.string.pref_secure_app_title),
            summary = stringResource(R.string.pref_secure_app_summary),
            key = "pref_secure_app",
            defaultValue = false,
        )

        SwitchPreference(
            title = stringResource(R.string.pref_always_show_ads_title),
            summary = stringResource(R.string.pref_always_show_ads_summary),
            key = "pref_always_show_ads",
            defaultValue = false,
        )

        SwitchPreference(
            title = stringResource(R.string.pref_rotate_vote_view_title),
            summary = stringResource(R.string.pref_rotate_vote_view_summary),
            key = "pref_rotate_vote_view",
            defaultValue = false,
        )
    }
}

@Composable
private fun UpdateScreen(actions: SettingsActions) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        ClickablePreference(
            title = stringResource(R.string.pref_pseudo_update),
            summary = stringResource(R.string.pref_pseudo_update_summary),
            onClick = actions.onCheckForUpdate,
        )

        SwitchPreference(
            title = stringResource(R.string.pref_use_beta_channel),
            summary = stringResource(R.string.pref_use_beta_channel_summary),
            key = "pref_use_beta_channel",
            defaultValue = false,
        )
    }
}

@Composable
private fun PrivacyScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        SwitchPreference(
            title = stringResource(R.string.perf_sync_backup_title),
            summary = stringResource(R.string.pref_sync_backup_summary),
            key = "pref_sync_backup",
            defaultValue = true,
        )

        SwitchPreference(
            title = stringResource(R.string.pref_use_doh_title),
            summary = stringResource(R.string.pref_use_doh_summary),
            key = "pref_use_doh2",
            defaultValue = true,
        )
    }
}

// ---- Reusable preference composables ----

@Composable
private fun PreferenceCategory(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 24.dp, end = 16.dp, bottom = 8.dp),
    )
}

@Composable
private fun SwitchPreference(
    title: String,
    summary: String,
    key: String,
    defaultValue: Boolean,
    enabled: Boolean = true,
    onChanged: ((Boolean) -> Unit)? = null,
) {
    val prefs = Settings.raw()
    var checked by remember { mutableStateOf(prefs.getBoolean(key, defaultValue)) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) {
                val newValue = !checked
                checked = newValue
                Settings.edit { putBoolean(key, newValue) }
                onChanged?.invoke(newValue)
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            )
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = { newValue ->
                checked = newValue
                Settings.edit { putBoolean(key, newValue) }
                onChanged?.invoke(newValue)
            },
            enabled = enabled,
        )
    }
}

/**
 * Remembers the current boolean value of a SharedPreference key.
 * Useful for dependency-style enabling/disabling of other preferences.
 */
@Composable
private fun rememberPreference(key: String, defaultValue: Boolean): Boolean {
    val prefs = Settings.raw()
    // Re-read on each recomposition since SwitchPreference triggers recomposition via state changes
    return prefs.getBoolean(key, defaultValue)
}

@Composable
private fun ListPreference(
    title: String,
    summary: String,
    key: String,
    defaultValue: String,
    entries: List<Pair<String, String>>, // value to human-readable
    onChanged: (() -> Unit)? = null,
) {
    val prefs = Settings.raw()
    var showDialog by remember { mutableStateOf(false) }
    var currentValue by remember {
        mutableStateOf(
            prefs.getString(key, defaultValue) ?: defaultValue
        )
    }

    ClickablePreference(
        title = title,
        summary = entries.firstOrNull { it.first == currentValue }?.second ?: summary,
        onClick = { showDialog = true },
    )

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(title) },
            text = {
                Column {
                    entries.forEach { (value, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    currentValue = value
                                    Settings.edit { putString(key, value) }
                                    showDialog = false
                                    onChanged?.invoke()
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = currentValue == value,
                                onClick = {
                                    currentValue = value
                                    Settings.edit { putString(key, value) }
                                    showDialog = false
                                    onChanged?.invoke()
                                },
                            )
                            Text(
                                text = label,
                                modifier = Modifier.padding(start = 8.dp),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun ClickablePreference(
    title: String,
    summary: String,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = summary,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun NavigationPreference(
    title: String,
    summary: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
