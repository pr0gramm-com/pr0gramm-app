package com.pr0gramm.app.ui.fragments

import android.graphics.Bitmap
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.text.format.DateUtils
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.pr0gramm.app.Logger
import com.pr0gramm.app.R
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.api.pr0gramm.Thumbnail
import com.pr0gramm.app.databinding.DigestsHeaderBinding
import com.pr0gramm.app.databinding.DigestsItemBinding
import com.pr0gramm.app.databinding.FragmentInboxBinding
import com.pr0gramm.app.feed.ContentType
import com.pr0gramm.app.feed.Feed
import com.pr0gramm.app.feed.FeedFilter
import com.pr0gramm.app.feed.FeedItem
import com.pr0gramm.app.feed.FeedService
import com.pr0gramm.app.feed.FeedType
import com.pr0gramm.app.services.DigestsService
import com.pr0gramm.app.services.InMemoryCacheService
import com.pr0gramm.app.services.ThemeHelper
import com.pr0gramm.app.services.UriHelper
import com.pr0gramm.app.ui.DelegateAdapter
import com.pr0gramm.app.ui.ErrorAdapterDelegate
import com.pr0gramm.app.ui.GridSpacingItemDecoration
import com.pr0gramm.app.ui.ListItemTypeAdapterDelegate
import com.pr0gramm.app.ui.MainActivity
import com.pr0gramm.app.ui.ScrollHideToolbarListener
import com.pr0gramm.app.ui.TitleFragment
import com.pr0gramm.app.ui.base.BaseFragment
import com.pr0gramm.app.ui.base.bindViews
import com.pr0gramm.app.ui.base.launchInViewScope
import com.pr0gramm.app.ui.staticLayoutAdapterDelegate
import com.pr0gramm.app.ui.viewModels
import com.pr0gramm.app.util.AndroidUtility
import com.pr0gramm.app.util.di.injector
import com.pr0gramm.app.util.dp
import com.pr0gramm.app.util.layoutInflater
import com.squareup.picasso.Picasso
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min

/**
 * Shows the highlights (digests) of the user.
 */
class DigestsFragment : BaseFragment("DigestsFragment", R.layout.fragment_inbox), TitleFragment {
    private val views by bindViews(FragmentInboxBinding::bind)

    private val model by viewModels {
        DigestsViewModel(
            digestsService = instance(),
            feedService = instance(),
            inMemoryCacheService = instance(),
        )
    }

    private val adapter = DigestsAdapter { item, busyIndicator -> itemClicked(item, busyIndicator) }

    private var openJob: Job? = null

    override val title: TitleFragment.Title
        get() = TitleFragment.Title(getString(R.string.inbox_type_digests))

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // the main activity toolbar floats above the content
        view.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            topMargin = AndroidUtility.getActionBarContentOffset(view.context)
        }

        (activity as? ScrollHideToolbarListener.ToolbarActivity)?.scrollHideToolbarListener?.reset()

        val spanCount = thumbnailColumnCount
        val layoutManager = GridLayoutManager(requireContext(), spanCount)
        layoutManager.spanSizeLookup = adapter.SpanSizeLookup(spanCount)

        views.messages.itemAnimator = null
        views.messages.clipToPadding = false
        views.messages.updatePadding(bottom = requireContext().dp(12))
        views.messages.layoutManager = layoutManager
        views.messages.addItemDecoration(GridSpacingItemDecoration(spacingDp = 4) { position ->
            adapter.getItem(position) is Api.DigestsInbox.ItemHighlight
        })

        // fill the list before the view restores its state, so a rotation keeps the scroll position
        updateAdapterValues(model.state.value)
        views.messages.adapter = adapter

        views.refresh.setOnRefreshListener { model.reload() }
        views.refresh.setColorSchemeResources(ThemeHelper.accentColor)

        launchInViewScope {
            model.state.collect { state ->
                updateAdapterValues(state)

                if (!state.loading) {
                    views.refresh.isRefreshing = false
                }
            }
        }
    }

    private fun itemClicked(item: Api.DigestsInbox.ItemHighlight, busyIndicator: View) {
        val digest = model.state.value.digests.firstOrNull { digest -> item in digest.items } ?: return

        openJob?.cancel()
        openJob = launchInViewScope {
            busyIndicator.isVisible = true

            val items = try {
                digest.items.map { highlight -> model.itemAsync(highlight.id) }.awaitAll()
            } finally {
                busyIndicator.isVisible = false
            }

            val feed = Feed(
                filter = FeedFilter().withFeedType(FeedType.NEW),
                contentType = ContentType.AllSet,
                items = items.filterNotNull().map { FeedItem(it) },
                isAtStart = true,
                isAtEnd = true,
            )

            val intent = if (item.id in feed) {
                MainActivity.openFeedIntent(requireContext(), feed, item.id, digest.pushNotification.title.ifBlank { null })
            } else {
                MainActivity.openItemIntent(requireContext(), item.id)
            }

            startActivity(intent)
        }
    }

    /**
     * Depending on whether the screen is landscape or portrait, and how large
     * the screen is, we show a different number of items per row.
     */
    private val thumbnailColumnCount: Int by lazy(LazyThreadSafetyMode.NONE) {
        val config = resources.configuration
        val portrait = config.screenWidthDp < config.screenHeightDp

        val screenWidth = config.screenWidthDp
        min((screenWidth / 120.0 + 0.5).toInt(), if (portrait) 5 else 7)
    }

    private fun updateAdapterValues(state: DigestsViewModel.State) {
        val error = state.error?.let { ErrorAdapterDelegate.errorValueOf(requireContext(), it) }

        val values = when {
            state.loading && state.digests.isEmpty() -> listOf(LoadingHint)
            else -> listOfNotNull(error) + state.digests.flatMap { digest -> listOf(digest) + digest.items }
        }

        adapter.submitList(values)
    }
}

class DigestsViewModel(
    private val digestsService: DigestsService,
    private val feedService: FeedService,
    private val inMemoryCacheService: InMemoryCacheService,
) : ViewModel() {
    private val logger = Logger("DigestsViewModel")

    private val mutableState = MutableStateFlow(State())
    val state: StateFlow<State> = mutableState

    // loads of full items, preloaded so a click can open the block right away
    private val itemLoads = ConcurrentHashMap<Long, Deferred<Api.Feed.Item?>>()
    private val loadPermits = Semaphore(4)

    init {
        reload()
    }

    fun reload() {
        viewModelScope.launch {
            mutableState.update { it.copy(loading = true, error = null) }

            val digests = try {
                digestsService.digests()
            } catch (err: Exception) {
                if (err is CancellationException) throw err

                logger.warn("Could not load digests", err)
                mutableState.update { it.copy(loading = false, error = err) }
                return@launch
            }

            mutableState.update { it.copy(loading = false, digests = digests) }

            digests.forEach { digest -> digest.items.forEach { item -> itemAsync(item.id) } }
        }
    }

    /**
     * Highlights have no image info, so we load the full item. Loaded items go to the
     * in-memory cache, failed loads are retried on the next call.
     */
    fun itemAsync(itemId: Long): Deferred<Api.Feed.Item?> {
        inMemoryCacheService.getItem(itemId)?.let { return CompletableDeferred(it) }
        itemLoads[itemId]?.let { return it }

        val deferred = viewModelScope.async { loadPermits.withPermit { loadItem(itemId) } }
        itemLoads[itemId] = deferred

        deferred.invokeOnCompletion { itemLoads.remove(itemId, deferred) }

        return deferred
    }

    private suspend fun loadItem(itemId: Long): Api.Feed.Item? {
        val query = FeedService.FeedQuery(
            filter = FeedFilter().withFeedType(FeedType.NEW),
            contentTypes = ContentType.AllSet,
            around = itemId,
        )

        // deleted posts are missing in the response
        val item = feedService.load(query).items.firstOrNull { it.id == itemId }
        item?.let(inMemoryCacheService::cacheItem)
        return item
    }

    data class State(
        val digests: List<Api.DigestsInbox.Digest> = listOf(),
        val loading: Boolean = true,
        val error: Exception? = null,
    )
}

private class DigestsAdapter(itemClicked: ItemClicked) : DelegateAdapter<Any>() {
    init {
        delegates += DigestHeaderDelegate()
        delegates += FeedItemDelegate(itemClicked)
        delegates += staticLayoutAdapterDelegate(R.layout.feed_hint_loading, LoadingHint)
        delegates += ErrorAdapterDelegate()
    }

    inner class SpanSizeLookup(private val spanCount: Int) : GridLayoutManager.SpanSizeLookup() {
        init {
            isSpanIndexCacheEnabled = true
        }

        override fun getSpanSize(position: Int): Int {
            val item = getItem(position)

            if (item !is Api.DigestsInbox.ItemHighlight) {
                return spanCount
            }

            // default is just ne column
            return 1
        }
    }
}

private class DigestHeaderDelegate :
    ListItemTypeAdapterDelegate<Api.DigestsInbox.Digest, Any, DigestsHeaderViewHolder>(Api.DigestsInbox.Digest::class) {
    override fun onCreateViewHolder(parent: ViewGroup): DigestsHeaderViewHolder {
        val views = DigestsHeaderBinding.inflate(parent.layoutInflater, parent, false)
        return DigestsHeaderViewHolder(views)
    }

    private val dateFormat = SimpleDateFormat(
        android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEdMMM"),
        Locale.getDefault(),
    )

    override fun onBindViewHolder(holder: DigestsHeaderViewHolder, value: Api.DigestsInbox.Digest) {
        val context = holder.itemView.context
        val millis = value.creationTime.millis

        holder.views.date.text = when {
            DateUtils.isToday(millis) -> context.getString(R.string.conversation_today)
            DateUtils.isToday(millis + DateUtils.DAY_IN_MILLIS) -> context.getString(R.string.conversation_yesterday)
            else -> value.creationTime.toString(dateFormat)
        }

        holder.views.title.isVisible = value.pushNotification.title.isNotBlank()
        holder.views.title.text = value.pushNotification.title

        val count = context.resources.getQuantityString(R.plurals.digest_post_count, value.items.size, value.items.size)
        holder.views.body.text = listOf(value.message, count).filter { it.isNotBlank() }.joinToString(" · ")

        holder.views.extraText.isVisible = !value.notice.isNullOrBlank()
        holder.views.extraText.text = value.notice ?: ""
    }
}

private object LoadingHint

private typealias ItemClicked = (Api.DigestsInbox.ItemHighlight, busyIndicator: View) -> Unit

private class FeedItemDelegate(private val itemClicked: ItemClicked) :
    ListItemTypeAdapterDelegate<Api.DigestsInbox.ItemHighlight, Any, DigestsItemViewHolder>(Api.DigestsInbox.ItemHighlight::class) {
    override fun onCreateViewHolder(parent: ViewGroup): DigestsItemViewHolder {
        val views = DigestsItemBinding.inflate(parent.layoutInflater)
        return DigestsItemViewHolder(views)
    }

    override fun onBindViewHolder(holder: DigestsItemViewHolder, value: Api.DigestsInbox.ItemHighlight) {
        val imageView = holder.views.image

        holder.views.busyIndicator.isVisible = false
        imageView.setOnClickListener { itemClicked(value, holder.views.busyIndicator) }

        val thumbnail = Thumbnail(value.id, value.thumbnail)
        val imageUri = UriHelper.of(imageView.context).thumbnail(thumbnail)

        val picasso = imageView.context.injector.instance<Picasso>()

        picasso.load(imageUri)
            .config(Bitmap.Config.RGB_565)
            .placeholder(ColorDrawable(0xff333333.toInt()))
            .into(imageView)
    }
}

private class DigestsHeaderViewHolder(val views: DigestsHeaderBinding) : RecyclerView.ViewHolder(views.root)
private class DigestsItemViewHolder(val views: DigestsItemBinding) : RecyclerView.ViewHolder(views.root)
