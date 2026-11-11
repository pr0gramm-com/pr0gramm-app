package com.pr0gramm.app.ui.views

import com.pr0gramm.app.Pr0Domain
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Parcelable
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.view.isVisible
import com.pr0gramm.app.R
import com.pr0gramm.app.databinding.ViewSearchBinding
import com.pr0gramm.app.feed.Tags
import com.pr0gramm.app.parcel.getParcelableOrNull
import com.pr0gramm.app.services.RecentSearchesServices
import com.pr0gramm.app.ui.RecentSearchesAutoCompleteAdapter
import com.pr0gramm.app.util.AndroidUtility
import com.pr0gramm.app.util.Listener
import com.pr0gramm.app.util.di.injector
import com.pr0gramm.app.util.invoke
import com.pr0gramm.app.util.layoutInflater
import com.pr0gramm.app.util.setOnProgressChanged
import java.util.Locale
import kotlin.math.pow
import kotlin.math.sign

/**
 * View for more search options.
 */
class SearchOptionsView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    FrameLayout(context, attrs) {
    var searchQuery: Listener<SearchQuery>? = null
    var searchCanceled: Listener<Unit>? = null

    private val excludedTags = hashSetOf<String>()
    private val requiredTags = hashSetOf<String>()

    private val views = ViewSearchBinding.inflate(layoutInflater, this, true)

    private var pendingState: Bundle? = null

    val initView = Once {
        views.minimumScoreSlider.max = 250 + 800
        views.minimumScoreSlider.keyProgressIncrement = 5
        filterScoreValue = 0

        if (!isInEditMode) {
            views.minimumScoreLabel.text = formatScoreValue(filterScoreValue)

            // update the value field with the slider
            views.minimumScoreSlider.setOnProgressChanged { _, _ ->
                views.minimumScoreLabel.text = formatScoreValue(roundScoreValue(filterScoreValue))
            }

            val editorListener = TextView.OnEditorActionListener { v, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    handleSearchButtonClicked()
                }

                true
            }

            // enter on search field should start the search
            views.searchTerm.setOnEditorActionListener(editorListener)
            views.customExcludes.setOnEditorActionListener(editorListener)
        }

        views.searchButton.setOnClickListener { handleSearchButtonClicked() }
        views.searchReset.setOnClickListener { reset() }
        views.filtersToggle.setOnClickListener { setFiltersExpanded(!views.filters.isVisible) }
        views.searchCancel.setOnClickListener { cancel() }
        views.searchAdvanced.setOnClickListener { showAdvancedHelpPage() }

        if (!isInEditMode) {
            initAutoCompleteView(context.injector.instance())
        }

        if (this.pendingState != null) {
            this.applyState(pendingState)
            this.pendingState = null
        } else {
            updateTagsCheckboxes()
        }
    }

    fun enableSimpleSearch() {
        views.extendedSearchFields.isVisible = false
    }

    private var filterScoreValue: Int
        get() = views.minimumScoreSlider.progress - 250
        set(value) {
            views.minimumScoreSlider.progress = value + 250
        }

    private fun initAutoCompleteView(recentSearchesServices: RecentSearchesServices) {
        views.searchTerm.setAdapter(
            RecentSearchesAutoCompleteAdapter(
                recentSearchesServices,
                context, android.R.layout.simple_dropdown_item_1line
            )
        )
    }

    fun setQueryHint(hint: String) {
        require(initView.initialized) {
            "SearchOptionsView must be initialized."
        }

        views.searchTerm.hint = hint
    }

    /**
     * Resets the view back to its "empty" state.
     */
    private fun reset() {
        views.searchTerm.setText("")
        views.customExcludes.setText("")

        filterScoreValue = 0

        excludedTags.clear()
        requiredTags.clear()
        updateTagsCheckboxes()
    }

    private fun cancel() {
        searchCanceled(Unit)
    }

    private fun showAdvancedHelpPage() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://$Pr0Domain/new/2782197"))
        context.startActivity(intent)
    }

    override fun onSaveInstanceState(): Parcelable {
        return Bundle().apply {
            putParcelable("viewState", super.onSaveInstanceState())
            putBundle("customState", currentState())
        }
    }

    override fun onRestoreInstanceState(state: Parcelable) {
        if (state is Bundle) {
            val viewState = state.getParcelableOrNull<Parcelable>("viewState")
            if (viewState != null)
                super.onRestoreInstanceState(viewState)

            this.applyState(state.getBundle("customState"))
        }
    }

    fun currentState(): Bundle? {
        if (!initView.initialized)
            return null

        return Bundle().apply {
            putInt("minScore", filterScoreValue)
            putCharSequence("queryTerm", views.searchTerm.text)
            putCharSequence("customWithoutTerm", views.customExcludes.text)
            putStringArray("selectedWithoutTags", excludedTags.toTypedArray())
            putStringArray("selectedRequiredTags", requiredTags.toTypedArray())
        }
    }

    fun applyState(state: Bundle?) {
        if (state == null) {
            return
        }

        if (!initView.initialized) {
            this.pendingState = state
            return
        }

        filterScoreValue = state.getInt("minScore", 0)
        views.searchTerm.setText(state.getCharSequence("queryTerm", ""))
        views.customExcludes.setText(state.getCharSequence("customWithoutTerm", ""))

        // clear original tags
        excludedTags.clear()

        // set new tags
        excludedTags += state.getStringArray("selectedWithoutTags") ?: emptyArray()

        requiredTags.clear()
        requiredTags += state.getStringArray("selectedRequiredTags") ?: emptyArray()

        // rebuild the checkboxes
        updateTagsCheckboxes()

        // never hide active filters
        if (excludedTags.isNotEmpty() || requiredTags.isNotEmpty() || !views.customExcludes.text.isNullOrBlank()) {
            setFiltersExpanded(true)
        }
    }

    private fun setFiltersExpanded(expanded: Boolean) {
        views.filters.isVisible = expanded
        views.searchReset.isVisible = expanded
        views.searchAdvanced.isVisible = expanded
        views.filtersToggleArrow.rotation = if (expanded) 0f else 180f
    }

    private fun handleSearchButtonClicked() {

        // get the base search-term
        val baseTerm = views.searchTerm.text.toString().trim()

        val specialTerms = mutableListOf<String>()

        // add minimum benis score selector
        val score = roundScoreValue(filterScoreValue)
        if (score != 0) {
            specialTerms.add("s:$score")
        }

        // each required tag or expression must match
        specialTerms += requiredTags.sorted()

        // add tags to ignore
        val withoutTags = buildCurrentExcludedTags()
        if (withoutTags.isNotEmpty()) {
            val joined = withoutTags.joinToString("|")
            specialTerms.add("-($joined)")
        }

        var searchTerm = if (specialTerms.isNotEmpty()) {
            Tags.joinAnd(specialTerms.joinToString("&", prefix = "!"), baseTerm)
        } else {
            baseTerm
        }

        // replace all new line characters (why would you add a new line?)
        searchTerm = searchTerm.replace('\n', ' ')

        searchQuery(SearchQuery(searchTerm, baseTerm))
    }

    private fun roundScoreValue(score: Int): Int {
        val result = ((score / 100.0).pow(2.0) * 90).toInt()
        return (0.5 + result / 100.0).toInt() * 100 * score.sign
    }

    private fun formatScoreValue(score: Int): String {
        return when (score.sign) {
            1 -> context.getString(R.string.search_score_min, score.toString())
            -1 -> context.getString(R.string.search_score_max, score.toString())
            else -> context.getString(R.string.search_score_0)
        }
    }

    private fun buildCurrentExcludedTags(): Set<String> {
        // use tags from check-boxes
        val withoutTags = this.excludedTags.toHashSet()

        // add custom tags
        views.customExcludes.text.toString().lowercase(Locale.getDefault())
            .split("\\s+".toPattern())
            .filterTo(withoutTags) { it != "" }

        return withoutTags
    }

    private fun updateTagsCheckboxes() {
        // "video" in excludedTags means images only, in requiredTags videos only.
        val mediaContainer = views.mediaChecks
        mediaContainer.removeAllViews()

        val mediaTypes = listOf(
            R.string.search_media_all to null,
            R.string.search_media_images to excludedTags,
            R.string.search_media_videos to requiredTags,
        )

        for ((name, target) in mediaTypes) {
            val selected = if (target == null) {
                "video" !in excludedTags && "video" !in requiredTags
            } else {
                "video" in target
            }

            addChip(mediaContainer, context.getString(name), selected) {
                excludedTags.remove("video")
                requiredTags.remove("video")
                target?.add("video")
                updateTagsCheckboxes()
            }
        }

        val onlyContainer = views.onlyChecks
        onlyContainer.removeAllViews()

        val onlyTags = listOf(
            "OC" to "\"original content\"",
            "HD" to "(q:hd|q:1080p|q:4k)",
        )

        for ((name, tagValue) in onlyTags) {
            addChip(onlyContainer, name, tagValue in requiredTags) { isChecked ->
                if (isChecked) requiredTags.add(tagValue) else requiredTags.remove(tagValue)
            }
        }

        val showContainer = views.withoutChecks
        showContainer.removeAllViews()

        val showTags = listOf("sound" to "f:sound", "repost" to "f:repost", "ftb" to "m:ftb")

        for ((name, tagValue) in showTags) {
            // checked means the tag is shown, unchecked excludes it
            addChip(showContainer, name, tagValue !in excludedTags) { isChecked ->
                if (isChecked) excludedTags.remove(tagValue) else excludedTags.add(tagValue)
            }
        }
    }

    private fun addChip(container: ViewGroup, text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
        val checkbox = container.layoutInflater.inflate(R.layout.search_chip, container, false) as CheckBox
        checkbox.text = text
        checkbox.isChecked = checked
        checkbox.setOnCheckedChangeListener { _, isChecked -> onChange(isChecked) }
        container.addView(checkbox)
    }

    override fun setPadding(left: Int, top: Int, right: Int, bottom: Int) {
        super.setPadding(left, 0, right, bottom)

        // move top padding to search view (container)
        val container: View? = findViewById(R.id.search_term_container)
        container?.setPadding(0, top, 0, 0)
    }

    fun requestSearchFocus() {
        post {
            val landscape = AndroidUtility.activityFromContext(context)
                ?.let { AndroidUtility.screenIsLandscape(it) }
                ?: false

            if (landscape) {
                views.searchTerm.requestFocus()
            } else {
                AndroidUtility.showSoftKeyboard(views.searchTerm)
            }
        }
    }

    class SearchQuery(val combined: String, val queryTerm: String)

    companion object {
        /**
         * Creates a new state containing the given query term.
         */

        fun ofQueryTerm(queryTerm: String): Bundle {
            val bundle = Bundle()
            bundle.putCharSequence("queryTerm", queryTerm)
            return bundle
        }
    }
}

class Once(private val block: () -> Unit) : Function<Unit> {
    var initialized = false
        private set

    operator fun invoke() {
        if (!initialized) {
            initialized = true
            block()
        }
    }
}
