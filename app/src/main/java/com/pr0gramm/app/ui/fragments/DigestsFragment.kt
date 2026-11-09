package com.pr0gramm.app.ui.fragments

import android.graphics.Bitmap
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.pr0gramm.app.R
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.api.pr0gramm.Message
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
import com.pr0gramm.app.ui.ListItemTypeAdapterDelegate
import com.pr0gramm.app.ui.MainActivity
import com.pr0gramm.app.ui.Pagination
import com.pr0gramm.app.ui.base.BaseFragment
import com.pr0gramm.app.ui.base.bindViews
import com.pr0gramm.app.ui.base.launchWhenCreated
import com.pr0gramm.app.util.di.injector
import com.pr0gramm.app.util.di.instance
import com.pr0gramm.app.util.layoutInflater
import com.pr0gramm.app.util.observeChange
import com.squareup.picasso.Picasso
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min

/**
 */
class DigestsFragment : BaseFragment("DigestsFragment", R.layout.fragment_inbox) {
    private val views by bindViews(FragmentInboxBinding::bind)
    private val digestsService: DigestsService by instance()
    private val feedService: FeedService by instance()
    private val inMemoryCacheService: InMemoryCacheService by instance()

    private var state by observeChange(State()) { updateAdapterValues() }
    private val adapter = DigestsAdapter { item, busyIndicator -> itemClicked(item, busyIndicator) }

    // loads of full items, preloaded so a click can open the block right away
    private val itemLoads = ConcurrentHashMap<Long, Deferred<Api.Feed.Item?>>()
    private val loadPermits = Semaphore(4)
    private var openJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        reloadInboxContent()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val spanCount = thumbnailColumnCount
        val layoutManager = GridLayoutManager(requireContext(), spanCount)
        layoutManager.spanSizeLookup = adapter.SpanSizeLookup(spanCount)

        views.messages.itemAnimator = null
        views.messages.layoutManager = layoutManager
        views.messages.adapter = adapter

        views.refresh.setOnRefreshListener { reloadInboxContent() }
        views.refresh.setColorSchemeResources(ThemeHelper.accentColor)
    }

    private fun reloadInboxContent() {
        launchWhenCreated {
            state = state.copy(loading = true)

            // get digests from server and update state
            val digests = digestsService.digests()
            state = state.copy(digests = digests, loading = false)

            digests.forEach { digest -> digest.items.forEach { item -> itemAsync(item.id) } }

            // not refreshing anymore
            views.refresh.isRefreshing = false
        }
    }

    private fun itemClicked(item: Api.DigestsInbox.ItemHighlight, busyIndicator: View) {
        val digest = state.digests.firstOrNull { digest -> item in digest.items } ?: return

        openJob?.cancel()
        openJob = launchWhenCreated {
            busyIndicator.isVisible = true

            val items = try {
                digest.items.map { highlight -> itemAsync(highlight.id) }.awaitAll()
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
     * Highlights have no image info, so we load the full item. Loaded items go to the
     * in-memory cache, failed loads are retried on the next call.
     */
    private fun itemAsync(itemId: Long): Deferred<Api.Feed.Item?> {
        inMemoryCacheService.getItem(itemId)?.let { return CompletableDeferred(it) }
        itemLoads[itemId]?.let { return it }

        val deferred = lifecycleScope.async { loadPermits.withPermit { loadItem(itemId) } }
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

    private fun updateAdapterValues() {
        context ?: return

        val values = state.digests.flatMap { digest ->
            listOf(digest) + digest.items
        }

        adapter.submitList(values)
    }

    data class State(
        val digests: List<Api.DigestsInbox.Digest> = listOf(),
        val loading: Boolean = true,
        val tailState: Pagination.EndState<Message> = Pagination.EndState(hasMore = true)
    )
}

private class DigestsAdapter(itemClicked: ItemClicked) : DelegateAdapter<Any>() {
    init {
        delegates += DigestHeaderDelegate()
        delegates += FeedItemDelegate(itemClicked)
    }

    inner class SpanSizeLookup(private val spanCount: Int) : GridLayoutManager.SpanSizeLookup() {
        init {
            isSpanIndexCacheEnabled = true
        }

        override fun getSpanSize(position: Int): Int {
            val item = getItem(position)

            if (item is Api.DigestsInbox.Digest) {
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

    override fun onBindViewHolder(holder: DigestsHeaderViewHolder, value: Api.DigestsInbox.Digest) {
        holder.views.title.isVisible = value.pushNotification.title.isNotBlank()
        holder.views.title.text = value.pushNotification.title

        holder.views.body.isVisible = value.message.isNotBlank()
        holder.views.body.text = value.message

        holder.views.extraText.isVisible = !value.notice.isNullOrBlank()
        holder.views.extraText.text = value.notice ?: ""
    }
}

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
