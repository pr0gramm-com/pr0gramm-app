package com.pr0gramm.app.ui.intro

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.RelativeLayout
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.isInvisible
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import com.github.appintro.AppIntro
import com.pr0gramm.app.BuildConfig
import com.pr0gramm.app.R
import com.pr0gramm.app.ui.intro.slides.BetaActionItemsSlide
import com.pr0gramm.app.ui.intro.slides.CategoriesActionItemsSlide
import com.pr0gramm.app.ui.intro.slides.SettingsActionItemsSlide
import com.pr0gramm.app.ui.intro.slides.ThemeActionItemsSlide
import com.pr0gramm.app.util.di.LazyInjectorAware
import com.pr0gramm.app.util.di.PropertyInjector
import com.pr0gramm.app.util.find
import com.pr0gramm.app.util.tryEnumValueOf
import kotlin.math.abs

class IntroActivity : AppIntro(), LazyInjectorAware {
    override val injector: PropertyInjector = PropertyInjector()

    override val layoutId: Int = R.layout.activity_intro

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        injector.inject(this)

        // deserialize slides to show
        val slidesToShow = intent.getStringArrayExtra("IntroActivity.steps")
                ?.mapNotNull { tryEnumValueOf<Slides>(it) }
                ?: defaultSlides

        if (Slides.INITIAL in slidesToShow) {
            addSlide(GenericAppIntroFragment.newInstance(
                    title = "",
                    description = "Die folgenden Seiten werden dir helfen, die pr0gramm App an deine Wünsche anzupassen!",
                    imageDrawable = R.drawable.ic_arrow,
                    bgColor = ContextCompat.getColor(this, R.color.orange_primary)))
        }

        if (Slides.UPDATE in slidesToShow) {
            addSlide(GenericAppIntroFragment.newInstance(
                    title = "",
                    description = "Es gab ein paar Updates in der App. Die folgenden Seiten werden dir helfen, die pr0gramm App an deine Wünsche anzupassen und neue Features zu entdecken!",
                    imageDrawable = R.drawable.ic_arrow,
                    bgColor = ContextCompat.getColor(this, R.color.orange_primary)))
        }

        if (Slides.CATEOGRIES in slidesToShow) {
            addSlide(CategoriesActionItemsSlide())
        }

        if (Slides.THEMES in slidesToShow) {
            addSlide(ThemeActionItemsSlide())
        }

        if (Slides.SETTINGS in slidesToShow) {
            addSlide(SettingsActionItemsSlide())
        }

        if (Slides.BETA in slidesToShow) {
            if (Math.random() < 0.25 || BuildConfig.DEBUG) {
                // only show beta to a few people
                addSlide(BetaActionItemsSlide())
            }
        }

        addSlide(GenericAppIntroFragment.newInstance(
                title = "Danke",
                description = """
                    Schön, dass du dich für die App entschieden hast.
                    Wenn bei dir etwas nicht funktioniert, zögere nicht die Feedback-Funktion innerhalb der App zu verwenden.
                    Diesen Willkommensbildschirm kannst du in den Einstellungen jederzeit erneut aufrufen.
                    """.trimIndent(),
                imageDrawable = R.drawable.ic_action_favorite,
                bgColor = ContextCompat.getColor(this, R.color.olive_primary)))

        // AppIntro 6 hides the status bar by default, AppIntro 5 did not
        showStatusBar(true)

        isSystemBackButtonLocked = true
        isSkipButtonEnabled = false
        setDoneText(getString(R.string.action_done))

        // AppIntro's Fade leaves off-screen pages transparent on top of the current one, stealing its touches
        setCustomTransformer { page, position ->
            page.translationX = page.width * -position
            page.alpha = 1f - abs(position).coerceAtMost(1f)
            page.isInvisible = abs(position) >= 1f
        }

        ViewCompat.setOnApplyWindowInsetsListener(find<View>(R.id.bottom)) { v, insets ->
            v.updateLayoutParams<RelativeLayout.LayoutParams> {
                bottomMargin = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            }

            insets
        }
    }

    override fun onDonePressed(currentFragment: androidx.fragment.app.Fragment?) {
        super.onDonePressed(currentFragment)
        finish()
    }

    companion object {
        private val defaultSlides = listOf(
                Slides.INITIAL, Slides.CATEOGRIES,
                Slides.THEMES, Slides.SETTINGS, Slides.BETA)

        fun intent(context: Context, steps: List<Slides> = defaultSlides): Intent {
            return Intent(context, IntroActivity::class.java)
                .putExtra("IntroActivity.steps", steps.map { it.name }.toTypedArray<String>())
        }
    }

    enum class Slides {
        INITIAL,
        UPDATE,
        CATEOGRIES,
        THEMES,
        SETTINGS,
        BETA
    }
}
