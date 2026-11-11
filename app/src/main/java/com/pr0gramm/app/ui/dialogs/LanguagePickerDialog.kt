package com.pr0gramm.app.ui.dialogs

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.pr0gramm.app.R
import com.pr0gramm.app.ui.base.BaseDialogFragment
import org.xmlpull.v1.XmlPullParser
import java.util.Locale

/**
 * Picks the app language on Android versions without the per-app language settings.
 */
class LanguagePickerDialog : BaseDialogFragment("LanguagePickerDialog") {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val supportedLocales = getSupportedLocales()
        val currentIndex = supportedLocales.indexOf(getCurrentLocale(supportedLocales))
        val names = supportedLocales.map { it.getDisplayLanguage(it) }.toTypedArray()

        return AlertDialog.Builder(requireContext())
            .setTitle(R.string.language_picker_title)
            .setSingleChoiceItems(names, currentIndex) { dialog, which ->
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.create(supportedLocales[which]))
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .create()
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
                        locales.add(Locale.forLanguageTag(languageTag))
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
        val appLocale = applicationLocales.get(0) ?: resources.configuration.locales[0]

        return supportedLocales.firstOrNull { it.isO3Language == appLocale.isO3Language }
            ?: Locale.forLanguageTag("en-US")
    }
}
