package com.pr0gramm.app.ui.dialogs

import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.core.os.LocaleListCompat
import com.pr0gramm.app.R
import com.pr0gramm.app.ui.compose.ComposeDialogFragment
import org.xmlpull.v1.XmlPullParser
import java.util.Locale

class LanguagePickerDialog : ComposeDialogFragment("LanguagePickerDialog") {
    @Composable
    override fun DialogContent() {
        val supportedLocales = remember { getSupportedLocales() }
        val selectedLocale = remember { mutableStateOf(getCurrentLocale(supportedLocales)) }

        AlertDialog(
            onDismissRequest = { dismiss() },
            title = { Text(stringResource(R.string.language_picker_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.language_picker_subtitle))
                    supportedLocales.forEach {
                        LanguageItem(
                            it,
                            selected = it == selectedLocale.value,
                            onClick = {
                                selectedLocale.value = it
                            },
                        )
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { dismiss() }) {
                    Text(stringResource(R.string.language_picker_dismiss))
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        AppCompatDelegate.setApplicationLocales(
                            LocaleListCompat.create(selectedLocale.value)
                        )
                        dismiss()
                    },
                ) {
                    Text(stringResource(R.string.language_picker_confirm))
                }
            },
        )
    }

    @Composable
    fun LanguageItem(
        locale: Locale,
        selected: Boolean,
        onClick: (() -> Unit),
    ) {
        ListItem(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            headlineContent = { Text(locale.getDisplayLanguage(locale)) },
            trailingContent = {
                RadioButton(
                    selected = selected,
                    onClick = onClick,
                )
            },
        )
    }

    private fun getSupportedLocales(): List<Locale> {
        val locales = mutableListOf<Locale>()

        try {
            val parser = resources.getXml(R.xml.locales_config)
            var eventType = parser.eventType
            val namespace = "http://schemas.android.com/apk/res/android"

            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG && parser.name == "locale") {
                    val languageTag = parser.getAttributeValue(namespace, "name")
                    if (languageTag != null) {
                        val locale = Locale.forLanguageTag(languageTag)
                        locales.add(locale)
                    }
                }
                eventType = parser.next()
            }

            parser.close()
        } catch (e: Exception) {
            logger.error("Error parsing locales config!", e)
        }

        return locales
    }

    private fun getCurrentLocale(supportedLocales: List<Locale>): Locale {
        val applicationLocales = AppCompatDelegate.getApplicationLocales()
        val appLocale = if (!applicationLocales.isEmpty) {
            applicationLocales.get(0)!!
        } else {
            getCurrentAppLocale()
        }

        val candidate = supportedLocales.firstOrNull { it.isO3Language == appLocale.isO3Language }

        if (candidate != null) {
            return candidate
        }

        return Locale.forLanguageTag("en-US")
    }

    private fun getCurrentAppLocale(): Locale {
        val config = requireContext().resources.configuration
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.locales[0]
        } else {
            @Suppress("DEPRECATION")
            config.locale
        }
    }
}
