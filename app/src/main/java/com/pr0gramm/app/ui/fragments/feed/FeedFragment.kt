package com.pr0gramm.app.ui.fragments.feed

import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.AndroidUiDispatcher
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.whenResumed
import com.google.android.material.snackbar.Snackbar
import com.pr0gramm.app.BuildConfig
import com.pr0gramm.app.Duration
import com.pr0gramm.app.Instant
import com.pr0gramm.app.R
import com.pr0gramm.app.Settings
import com.pr0gramm.app.api.pr0gramm.MessageConverter
import com.pr0gramm.app.databinding.FragmentFeedBinding
import com.pr0gramm.app.db.AppDB
import com.pr0gramm.app.feed.ContentType
import com.pr0gramm.app.feed.ContentType.SFW
import com.pr0gramm.app.feed.Feed
import com.pr0gramm.app.feed.FeedException
import com.pr0gramm.app.feed.FeedFilter
import com.pr0gramm.app.feed.FeedItem
import com.pr0gramm.app.feed.FeedManager
import com.pr0gramm.app.feed.FeedService
import com.pr0gramm.app.feed.FeedType
import com.pr0gramm.app.feed.withoutImplicit
import com.pr0gramm.app.parcel.getParcelableOrThrow
import com.pr0gramm.app.services.BookmarkService
import com.pr0gramm.app.services.FollowService
import com.pr0gramm.app.services.InMemoryCacheService
import com.pr0gramm.app.services.RecentSearchesServices
import com.pr0gramm.app.services.ShareService
import com.pr0gramm.app.services.SingleShotService

import com.pr0gramm.app.services.Track
import com.pr0gramm.app.services.UserService
import com.pr0gramm.app.services.preloading.PreloadService
import com.pr0gramm.app.time
import com.pr0gramm.app.ui.ContentTypeDrawable
import com.pr0gramm.app.ui.ConversationActivity

import com.pr0gramm.app.ui.FancyExifThumbnailGenerator
import com.pr0gramm.app.ui.FeedFilterFormatter
import com.pr0gramm.app.ui.FilterFragment
import com.pr0gramm.app.ui.InterstitialAdler
import com.pr0gramm.app.ui.LoginActivity
import com.pr0gramm.app.ui.MainActionHandler
import com.pr0gramm.app.ui.PreviewInfo
import com.pr0gramm.app.ui.ScrollHideToolbarListener.ToolbarActivity
import com.pr0gramm.app.ui.TitleFragment
import com.pr0gramm.app.ui.back.BackAwareFragment
import com.pr0gramm.app.ui.base.BaseFragment
import com.pr0gramm.app.ui.base.MainScope
import com.pr0gramm.app.ui.base.asEventFlow
import com.pr0gramm.app.ui.base.bindViews
import com.pr0gramm.app.ui.base.launchInViewScope

import com.pr0gramm.app.ui.base.launchUntilPause
import com.pr0gramm.app.ui.base.launchUntilViewDestroy
import com.pr0gramm.app.ui.base.launchWhenCreated
import com.pr0gramm.app.ui.base.withErrorDialog
import com.pr0gramm.app.ui.compose.theme.Pr0grammTheme
import com.pr0gramm.app.ui.configureNewStyle
import com.pr0gramm.app.ui.dialogs.PopupPlayer
import com.pr0gramm.app.ui.feed.FeedGridEntry
import com.pr0gramm.app.ui.feed.FeedScreen
import com.pr0gramm.app.ui.fragments.CommentRef
import com.pr0gramm.app.ui.fragments.ItemUserAdminDialog
import com.pr0gramm.app.ui.fragments.pager.PostPagerFragment
import com.pr0gramm.app.ui.showDialog
import com.pr0gramm.app.ui.viewModels
import com.pr0gramm.app.ui.feed.SearchBottomSheet
import com.pr0gramm.app.ui.feed.SearchQuery
import com.pr0gramm.app.ui.feed.SearchState
import com.pr0gramm.app.ui.feed.searchStateFromQueryTerm
import com.pr0gramm.app.ui.views.UserInfoView
import com.pr0gramm.app.util.AndroidUtility
import com.pr0gramm.app.util.BrowserHelper
import com.pr0gramm.app.util.ErrorFormatting
import com.pr0gramm.app.util.bundle
import com.pr0gramm.app.util.catchAll
import com.pr0gramm.app.util.debugOnly
import com.pr0gramm.app.util.di.instance
import com.pr0gramm.app.util.equalsIgnoreCase
import com.pr0gramm.app.util.fragmentArgumentWithDefault
import com.pr0gramm.app.util.hideSoftKeyboard
import com.pr0gramm.app.util.maybeShow
import com.pr0gramm.app.util.trace
import kotlinx.coroutines.Job
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.firstOrNull

import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.EnumSet
import kotlin.math.min


/**
 */
class FeedFragment : BaseFragment("FeedFragment", R.layout.fragment_feed), FilterFragment,
    TitleFragment,
    BackAwareFragment {

    private val feedStateModel by viewModels { handle ->
        val start = arguments?.getParcelable<CommentRef?>(ARG_FEED_START)
        if (start != null) {
            logger.debug { "Requested to open item $start on load" }
            autoScrollRef = ScrollRef(start, autoOpen = true)
        }

        FeedViewModel(
            savedState = FeedViewModel.SavedState(handle),
            filter = requireArguments().getParcelableOrThrow(ARG_FEED_FILTER),
            loadAroundItemId = autoScrollRef?.ref?.itemId,

            feedService = instance(),
            userService = instance(),
            seenService = instance(),
            inMemoryCacheService = instance(),
            preloadManager = instance(),
            adService = instance(),
            itemQueries = instance<AppDB>().feedItemInfoQueries,
        )
    }

    private val userStateModel by viewModels {
        UserStateModel(
            filter = requireArguments().getParcelableOrThrow(ARG_FEED_FILTER),
            queryForUserInfo = isNormalMode,
            userService = instance(),
            inboxService = instance()
        )
    }

    private val feedService: FeedService by instance()
    private val bookmarkService: BookmarkService by instance()
    private val userService: UserService by instance()
    private val singleShotService: SingleShotService by instance()
    private val inMemoryCacheService: InMemoryCacheService by instance()
    private val recentSearchesServices: RecentSearchesServices by instance()
    private val followService: FollowService by instance()
    private val shareService: ShareService by instance()

    private val views by bindViews(FragmentFeedBinding::bind)

    private val isNormalMode: Boolean by fragmentArgumentWithDefault(true, ARG_NORMAL_MODE)

    private val doIfAuthorizedHelper = LoginActivity.helper(this)

    private var bookmarkable: Boolean = false
    private var autoScrollRef: ScrollRef? = null

    private var lastCheckForNewItemsTime = Instant(0)

    private lateinit var interstitialAdler: InterstitialAdler

    private val feedEntriesState = mutableStateOf<List<FeedGridEntry>>(emptyList())
    private val refreshingState = mutableStateOf(false)
    private var gridState = LazyGridState()

    private val searchVisibleState = mutableStateOf(false)
    private val searchInitialState = mutableStateOf(SearchState())

    private val scrollToolbar: Boolean
        get() = isNormalMode

    private val feed: Feed get() = feedStateModel.feedState.value.feed

    /**
     * Initialize a new feed fragment.
     */
    init {
        setHasOptionsMenu(true)

        debugOnly {
            MainScope.launch {
                lifecycle.asEventFlow().collect { event ->
                    this@FeedFragment.trace { "$event" }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        interstitialAdler = InterstitialAdler(requireActivity())
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val activity = requireActivity()

        // Restore grid scroll position on configuration change
        if (savedInstanceState != null && autoScrollRef == null) {
            val index = savedInstanceState.getInt(STATE_GRID_INDEX, 0)
            val offset = savedInstanceState.getInt(STATE_GRID_OFFSET, 0)
            gridState = LazyGridState(index, offset)
        }

        // Set up Compose content
        views.composeView.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        views.composeView.setContent {
            Pr0grammTheme {
                val entries by feedEntriesState
                val isRefreshing by refreshingState

                FeedScreen(
                    entries = entries,
                    columnCount = thumbnailColumnCount,
                    isRefreshing = isRefreshing,
                    canRefresh = true,
                    gridState = gridState,
                    onRefresh = { refreshContent() },
                    onItemClicked = { item ->
                        interstitialAdler.runWithAd {
                            onItemClicked(item)
                        }
                    },
                    onItemLongPress = if (Settings.enableQuickPeek) { item ->
                        if (!isStateSaved) {
                            activity?.let { PopupPlayer.open(it, item) }
                        }
                    } else null,
                    onLoadNext = { feedStateModel.triggerLoadNext() },
                    onLoadPrev = { feedStateModel.triggerLoadPrev() },
                )

                // Observe scroll for toolbar hiding
                LaunchedEffect(gridState) {
                    var previousIndex = gridState.firstVisibleItemIndex
                    var previousOffset = gridState.firstVisibleItemScrollOffset
                    snapshotFlow { gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset }
                        .collect { (index, offset) ->
                            val dy = if (index > previousIndex) 1
                            else if (index < previousIndex) -1
                            else offset - previousOffset
                            previousIndex = index
                            previousOffset = offset
                            if (scrollToolbar) {
                                (activity as? ToolbarActivity)?.scrollHideToolbarListener?.onScrolled(
                                    dy
                                )
                            }
                        }
                }

                // Observe scroll stop for toolbar finish
                LaunchedEffect(gridState) {
                    snapshotFlow { gridState.isScrollInProgress }
                        .collect { scrolling ->
                            if (!scrolling && scrollToolbar) {
                                (activity as? ToolbarActivity)
                                    ?.scrollHideToolbarListener?.onScrollFinished(Int.MAX_VALUE)
                            }
                        }
                }
                // Search bottom sheet
                if (searchVisibleState.value) {
                    val typeName = FeedFilterFormatter.feedTypeToString(
                        requireContext(),
                        currentFilter.withTagsNoReset("dummy")
                    )
                    SearchBottomSheet(
                        initialState = searchInitialState.value,
                        queryHint = getString(R.string.action_search, typeName),
                        showExtended = isNormalMode,
                        recentSearches = recentSearchesServices.searches(),
                        onSearch = { query ->
                            hideSearchContainer()
                            performSearch(query)
                        },
                        onDismiss = { hideSearchContainer() },
                    )
                }
            }
        }

        // Observe gridState for scroll position saving
        launchInViewScope {
            snapshotFlow { gridState.firstVisibleItemIndex }
                .collect { firstVisible ->
                    val entries = feedEntriesState.value
                    if (firstVisible in entries.indices) {
                        val feedItem = entries.subList(
                            firstVisible,
                            entries.size.coerceAtMost(firstVisible + 10)
                        )
                            .filterIsInstance<FeedGridEntry.Item>()
                            .firstOrNull()?.item
                        if (feedItem != null) {
                            feedStateModel.updateScrollItemId(feedItem.id)
                        }
                    }
                }
        }

        // Observe adapter state for auto-open
        launchInViewScope {
            snapshotFlow { feedEntriesState.value }
                .collect {
                    this@FeedFragment.autoScrollRef?.let { autoScrollRef ->
                        if (feedStateModel.feedState.value.ready) {
                            if (autoScrollRef.autoOpen) {
                                performAutoOpen(autoScrollRef.ref)
                            }
                        }
                    }
                }
        }

        resetToolbar()

        // restore open search
        if (savedInstanceState != null && savedInstanceState.getBoolean("searchContainerVisible")) {
            showSearchContainer()
        }

        launchInViewScope {
            data class Update(
                val feedState: FeedViewModel.FeedState,
                val userState: UserStateModel.UserState
            )

            combine(feedStateModel.feedState, userStateModel.userState) { feedState, userState ->
                Update(feedState, userState)
            }.collect { update ->
                logger.debug { "Apply update: $update" }

                update.feedState.errorConsumable?.consume { error ->
                    displayFeedError(error)
                }

                update.feedState.autoScrollRef?.consume { ref ->
                    autoScrollRef = ref
                }

                updateAdapterState(update.feedState, update.userState)
            }
        }

        launchInViewScope {
            whenResumed {
                userStateModel.userState.collectLatest { userState ->
                    requireActivity().invalidateOptionsMenu()

                    val userId = userState.userInfo?.info?.user?.id
                    if (userId != null) {
                        followService.getState(userId.toLong()).collect {
                            requireActivity().invalidateOptionsMenu()
                        }
                    }
                }
            }
        }

        launchInViewScope {
            userService.selectedContentTypes.collect { contentTypes ->
                if (feed.contentType != contentTypes) {
                    replaceFeedFilter()
                }

                activity.invalidateOptionsMenu()
            }
        }

        launchInViewScope {
            userService.loginStates.drop(1).collect {
                activity.invalidateOptionsMenu()
            }
        }
    }

    private fun updateAdapterState(
        feedState: FeedViewModel.FeedState,
        userState: UserStateModel.UserState
    ) {
        trace { "updateAdapterState()" }

        val context = context
        if (this.activity == null || context == null) {
            logger.warn { "updateAdapterState called with activity already null." }
            return
        }

        val filter = feedState.feed.filter
        val density = resources.displayMetrics.density

        val entries = mutableListOf<FeedGridEntry>()

        logger.time("Update adapter") {
            // add a little spacer to the top to account for the action bar
            if (useToolbarTopMargin()) {
                val offset = AndroidUtility.getActionBarContentOffset(context)
                if (offset > 0) {
                    entries += FeedGridEntry.Spacer(1, heightDp = (offset / density).toInt())
                }
            }

            if (feedState.loading == FeedManager.LoadingSpace.PREV) {
                entries += FeedGridEntry.LoadingHint
            }

            if (userState.userInfo != null) {
                val userInfo = userState.userInfo
                val isSelfInfo =
                    userInfo.info.user.name.equals(userState.ownUsername, ignoreCase = true)

                // if we found this user using a normal 'search', we will show a hint
                // that the user exists
                if (filter.tags != null) {
                    if (!isSelfInfo) {
                        val userAndMark = userInfo.info.user.run { UserAndMark(name, mark) }
                        entries += FeedGridEntry.UserHint(
                            userAndMark.name,
                            userAndMark.mark,
                            this::openUserUploads
                        )
                    }

                } else {
                    entries += FeedGridEntry.UserInfo(
                        userState.userInfo,
                        isSelfInfo,
                        userActionListener
                    )

                    if (userState.userInfoCommentsOpen) {
                        val user = userService.name
                        userInfo.comments.mapTo(entries) { comment ->
                            val msg = MessageConverter.of(userState.userInfo.info.user, comment)
                            FeedGridEntry.UserComment(msg, user)
                        }
                    }

                    entries += FeedGridEntry.Spacer(2, heightDp = 8)
                }

            } else if (filter.username != null) {
                val item = feedState.feed.firstOrNull {
                    it.user.equals(
                        filter.username,
                        ignoreCase = true
                    )
                }
                if (item != null) {
                    val user = UserAndMark(item.user, item.mark)
                    entries += FeedGridEntry.UserLoading(user.name, user.mark)
                    entries += FeedGridEntry.Spacer(2, heightDp = 8)
                }
            }

            if (feedState.missingContentType != null) {
                if (userService.isAuthorized) {
                    entries += FeedGridEntry.MissingContentType(
                        contentType = feedState.missingContentType,
                        isAuthorized = true,
                        errorMessage = null,
                        onAddContentType = {
                            // Enable the missing content type
                            val prefKey = when (feedState.missingContentType) {
                                ContentType.NSFW -> "pref_feed_type_nsfw"
                                ContentType.NSFL -> "pref_feed_type_nsfl"
                                ContentType.POL -> "pref_feed_type_pol"
                                else -> return@MissingContentType
                            }
                            Settings.edit { putBoolean(prefKey, true) }
                        },
                    )
                } else {
                    val msg = buildString {
                        append(
                            getString(
                                R.string.could_not_load_feed_content_type,
                                feedState.missingContentType.name
                            )
                        )
                        append(" ")
                        append(
                            getString(
                                R.string.could_not_load_feed_content_type__signin,
                                feedState.missingContentType.name
                            )
                        )
                    }

                    entries += FeedGridEntry.Error(msg)
                }

            } else if (!userState.userInfoCommentsOpen) {
                // check if we need to check if the posts are 'seen'
                val markAsSeen = feedState.markItemsAsSeen && !run {
                    userState.ownUsername != null && userState.ownUsername.equalsIgnoreCase(filter.username)
                }

                // always show at least one ad banner - e.g. during load
                if (feedState.adsVisible && feedState.feed.isEmpty()) {
                    entries += FeedGridEntry.Ad(0)
                }

                var itemColumnIndex = 0

                for (item in feedState.feed) {
                    val id = item.id
                    val seen = markAsSeen && id in feedState.seen
                    val repost = inMemoryCacheService.isRepost(id)
                    val preloaded = id in feedState.preloadedItemIds

                    // show an ad banner every ~50 lines
                    if (feedState.adsVisible && (itemColumnIndex % (50 * thumbnailColumnCount)) == 0) {
                        entries += FeedGridEntry.Ad(itemColumnIndex.toLong())
                    }

                    val highlight = thumbnailColumnCount <= 3
                            && Settings.highlightItemsInFeed
                            && item.id in feedState.highlightedItemIds

                    var indexToInsert = entries.size

                    if (highlight) {
                        indexToInsert -= itemColumnIndex % thumbnailColumnCount
                    } else {
                        itemColumnIndex++
                    }

                    entries.add(
                        indexToInsert,
                        FeedGridEntry.Item(item, repost, preloaded, seen, highlight)
                    )
                }

                when {
                    feedState.loading == FeedManager.LoadingSpace.NEXT ->
                        entries += FeedGridEntry.LoadingHint

                    feedState.error != null -> {
                        val errorStr = ErrorFormatting.format(requireContext(), feedState.error)
                        entries += FeedGridEntry.Error(errorStr)
                    }

                    feedState.empty ->
                        entries += FeedGridEntry.EmptyHint
                }
            }

            autoScrollRef?.let { ref ->
                logger.debug { "autoScrollRef before setting new items: $autoScrollRef" }
                if (ref.keepScroll) {
                    autoScrollRef = null
                }
            }

            feedEntriesState.value = entries
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        if (view != null) {
            outState.putBoolean("searchContainerVisible", searchContainerIsVisible())
            outState.putInt(STATE_GRID_INDEX, gridState.firstVisibleItemIndex)
            outState.putInt(STATE_GRID_OFFSET, gridState.firstVisibleItemScrollOffset)
        }
    }

    private fun initialSearchViewState(): SearchState {
        return arguments?.getBundle(ARG_SEARCH_QUERY_STATE)?.let { bundle ->
            SearchState(
                queryTerm = bundle.getCharSequence("queryTerm", "").toString(),
                customExcludes = bundle.getCharSequence("customWithoutTerm", "").toString(),
                minScore = bundle.getInt("minScore", 0),
                excludedTags = bundle.getStringArray("selectedWithoutTags")?.toSet() ?: emptySet(),
            )
        } ?: currentFilter.tags?.let { tags ->
            searchStateFromQueryTerm(tags)
        } ?: SearchState()
    }

    private fun useToolbarTopMargin(): Boolean {
        return isNormalMode
    }

    private val userActionListener = object : UserInfoView.UserActionListener {
        override fun onWriteMessageClicked(name: String) {
            doIfAuthorizedHelper.run {
                ConversationActivity.start(requireContext(), name)
            }
        }

        override fun onUserViewCollectionsClicked(name: String) {
            val filter = currentFilter.basic().basicWithCollection(name, "**ANY", "**ANY")

            if (filter != currentFilter) {
                (activity as MainActionHandler).onFeedFilterSelected(filter)
            }

            userStateModel.closeUserComments()
        }

        override fun onShowUploadsClicked(name: String) {
            val filter = currentFilter.basic().withFeedType(FeedType.NEW).basicWithUser(name)
            if (filter != currentFilter) {
                (activity as MainActionHandler).onFeedFilterSelected(filter)
            }

            userStateModel.closeUserComments()
        }

        override fun onBlockUserClicked(name: String) {
            showDialog(this@FeedFragment) {
                content(
                    buildString {
                        append(getString(R.string.block_user_confirm, name))

                        if (!userService.userIsPremium) {
                            append("\n")
                            append(getString(R.string.block_user_pr0mium_hint))
                        }
                    }
                )

                positive() {
                    launchWhenCreated {
                        withErrorDialog { userService.blockUser(name) }
                    }
                }

                negative()
            }
        }

        override fun onShowCommentsClicked() {
            userStateModel.openUserComments()
        }

        override fun shareUserProfile(name: String) {
            shareService.shareUserProfile(requireActivity(), name)
        }
    }

    private fun openUserUploads(name: String) {
        val handler = requireActivity() as MainActionHandler
        handler.onFeedFilterSelected(
            currentFilter.basic()
                .withFeedType(FeedType.NEW)
                .basicWithUser(name)
        )
    }

    private fun resetToolbar() {
        val activity = activity
        if (activity is ToolbarActivity) {
            activity.scrollHideToolbarListener.reset()
        }
    }

    private fun hideToolbar() {
        if (isNormalMode) {
            val activity = activity
            if (activity is ToolbarActivity) {
                activity.scrollHideToolbarListener.hide()
            }
        }
    }

    private fun onBookmarkableStateChanged(bookmarkable: Boolean) {
        if (this.bookmarkable != bookmarkable) {
            this.bookmarkable = bookmarkable
            activity?.invalidateOptionsMenu()
        }
    }

    private val selectedContentType: EnumSet<ContentType>
        get() {
            if (!userService.isAuthorized)
                return EnumSet.of(SFW)

            return Settings.contentType
        }

    override fun onResume() {
        super.onResume()

        performAutoScroll()

        Track.openFeed(currentFilter)

        // check if we should show the pin button or not.
        if (Settings.showPinButton) {
            val bookmarkable = bookmarkService.isBookmarkable(currentFilter)
            onBookmarkableStateChanged(bookmarkable)
        }

        // we might want to check for new items on resume, but only once every two minutes.
        val checkForNewItemInterval = Duration.seconds(if (BuildConfig.DEBUG) 5 else 60)
        val threshold = Instant.now().minus(checkForNewItemInterval)
        if (feed.created.isBefore(threshold) && lastCheckForNewItemsTime.isBefore(threshold)) {
            lastCheckForNewItemsTime = Instant.now()
            checkForNewItems()
        }
    }

    private fun performAutoScroll() {
        val ref = autoScrollRef ?: return

        if (ref.autoOpen) {
            return
        }

        val containsRef = feedEntriesState.value.any { entry ->
            entry is FeedGridEntry.Item && entry.item.id == ref.itemId
        }

        if (containsRef) {
            autoScrollRef = null
            scrollToItem(ref.itemId, ref.smoothScroll)
        } else if (ref.feed != null) {
            // mark the feed as applied
            autoScrollRef = ref.copy(feed = null)

            // apply the updated feed reference
            feedStateModel.replaceCurrentFeed(feed.mergeIfPossible(ref.feed) ?: ref.feed)
        }
    }

    fun updateFeedItemTarget(feed: Feed, item: FeedItem) {
        val entries = feedEntriesState.value

        for (visibleItem in gridState.layoutInfo.visibleItemsInfo) {
            val entry = entries.getOrNull(visibleItem.index) ?: context
            if (entry is FeedGridEntry.Item && entry.item.id == item.id) {
                // already in view
                return
            }
        }

        logger.info { "Want to resume from $item" }
        autoScrollRef = ScrollRef(CommentRef(item), feed, smoothScroll = true)
    }

    private fun checkForNewItems() {
        if (!feed.isAtStart || feed.filter.feedType == FeedType.RANDOM || feed.isEmpty()) {
            logger.debug { "Not checking for new items as we are not at the beginning of the feed" }
            return
        }

        launchUntilPause {
            logger.info { "Checking for new items in current feed" }

            val query = FeedService.FeedQuery(feed.filter, feed.contentType)
            catchAll {
                val response = feedService.load(query)

                val previousIds = feed.mapTo(mutableSetOf()) { it.id }
                val itemCount = response.items.count { it.id !in previousIds }
                if (itemCount > 0 && feed.isNotEmpty() && feed.filter == query.filter) {
                    newItemsSnackbar(itemCount)
                }
            }
        }
    }

    private fun newItemsSnackbar(itemCount: Int) {
        val text = when {
            itemCount == 1 -> getString(R.string.hint_new_items_one)
            itemCount <= 16 -> getString(R.string.hint_new_items_some, itemCount)
            else -> getString(R.string.hint_new_items_many)
        }

        val view = view ?: return

        val snackbar = Snackbar.make(view, text, Snackbar.LENGTH_LONG).apply {
            configureNewStyle()
            setAction(R.string.hint_refresh_load) { refreshContent() }
            show()
        }

        // dismiss once the fragment stops.
        launchUntilViewDestroy {
            // wait for the stop event
            lifecycle.asEventFlow().firstOrNull { it == Lifecycle.Event.ON_STOP }
            snackbar.dismiss()
        }
    }

    private fun replaceFeedFilter(feedFilter: FeedFilter? = null, item: Long? = null) {
        val startAtItemId = item
            ?: autoScrollRef?.ref?.itemId
            ?: findLastVisibleFeedItem(userService.selectedContentType)?.id

        if (autoScrollRef == null) {
            autoScrollRef = startAtItemId?.let { id -> ScrollRef(CommentRef(id)) }
        }

        // this clears the current feed immediately
        val filter = feedFilter ?: feed.filter
        feedStateModel.restart(
            feed = Feed(filter, userService.selectedContentType),
            aroundItemId = startAtItemId
        )

        activity?.invalidateOptionsMenu()
    }

    /**
     * Finds the last item in the feed that is visible and of one of the given content types
     *
     * @param contentType The target-content type.
     */
    private fun findLastVisibleFeedItem(
        contentType: Set<ContentType> = ContentType.AllSet
    ): FeedItem? {
        val entries = feedEntriesState.value
        if (view == null || entries.isEmpty()) {
            return null
        }

        val visibleItems = gridState.layoutInfo.visibleItemsInfo
        if (visibleItems.isEmpty()) return null

        val firstVisible = visibleItems.first().index
        if (firstVisible == 0) return null

        val lastVisible = visibleItems.last().index.coerceIn(entries.indices)
        return entries.take(lastVisible + 1)
            .filterIsInstance<FeedGridEntry.Item>()
            .lastOrNull { contentType.contains(it.item.contentType) }
            ?.item
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

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.menu_feed, menu)
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        val activity = activity ?: return

        val filter = currentFilter
        val feedType = filter.feedType

        menu.findItem(R.id.action_refresh)?.isVisible = Settings.showRefreshButton
        menu.findItem(R.id.action_bookmark)?.isVisible = bookmarkable
        menu.findItem(R.id.action_preload)?.isVisible = feedType.preloadable

        // hide search item, if we are not searchable
        val searchable = currentFilter.feedType.searchable
        menu.findItem(R.id.action_search)?.isVisible = searchable

        // switching to normal mode leaves the special favorites fragment.
        menu.findItem(R.id.action_feedtype)?.isVisible = isNormalMode

        val adminOnUserProfile =
            userService.userIsAdmin && userStateModel.userInfo?.info?.user?.name != null
        menu.findItem(R.id.action_block_user)?.isVisible = adminOnUserProfile
        menu.findItem(R.id.action_open_in_admin)?.isVisible = adminOnUserProfile

        menu.findItem(R.id.action_feedtype)?.let { item ->
            item.isVisible = !filter.isBasic && isNormalMode

            item.setTitle(
                if (switchFeedTypeTarget(filter) === FeedType.PROMOTED)
                    R.string.action_switch_to_top else R.string.action_switch_to_new
            )
        }

        menu.findItem(R.id.action_change_content_type)?.let { item ->
            val icon = ContentTypeDrawable(activity, selectedContentType)
            icon.textSize = resources.getDimensionPixelSize(
                R.dimen.feed_content_type_action_icon_text_size
            ).toFloat()

            item.icon = icon
            item.isVisible = true

            updateContentTypeItems(menu)
        }

        val bookmark = menu.findItem(R.id.action_bookmark)
        if (bookmark != null && filter.username != null) {
            // never bookmark a user
            bookmark.isVisible = false
        }
    }

    private fun switchFeedTypeTarget(filter: FeedFilter): FeedType {
        return if (filter.feedType !== FeedType.PROMOTED) FeedType.PROMOTED else FeedType.NEW
    }

    private fun updateContentTypeItems(menu: Menu) {
        // only one content type selected?
        val withoutImplicits = Settings.contentType.withoutImplicit()
        val single = withoutImplicits.size == 1

        val types = mapOf(
            R.id.action_content_type_sfw to Settings.contentTypeSfw,
            R.id.action_content_type_nsfw to Settings.contentTypeNsfw,
            R.id.action_content_type_nsfl to Settings.contentTypeNsfl,
            R.id.action_content_type_pol to Settings.contentTypePol,
        )

        for ((key, value) in types) {
            menu.findItem(key)?.let { item ->
                item.isChecked = value
                item.isEnabled = !single || !value
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val contentTypes = mapOf(
            R.id.action_content_type_sfw to "pref_feed_type_sfw",
            R.id.action_content_type_nsfw to "pref_feed_type_nsfw",
            R.id.action_content_type_nsfl to "pref_feed_type_nsfl",
            R.id.action_content_type_pol to "pref_feed_type_pol",
        )

        val requireVerification = setOf(
            R.id.action_content_type_nsfw,
            R.id.action_content_type_nsfl,
        )

        if (contentTypes.containsKey(item.itemId)) {
            val newState = !item.isChecked

            if (newState && item.itemId in requireVerification) {
                if (userService.isAuthorized && !userService.userIsVerified) {
                    hintUserIsNotVerified()
                    return true
                }
            }

            // this applies the new content types and refreshes the menu.
            Settings.edit {
                putBoolean(contentTypes[item.itemId], newState)
            }

            return true
        }

        return true == when (item.itemId) {
            R.id.action_feedtype -> switchFeedType()
            R.id.action_refresh -> refreshFeedWithIndicator()
            R.id.action_bookmark -> pinCurrentFeedFilter()
            R.id.action_preload -> preloadCurrentFeed()
            R.id.action_block_user -> onBlockUserClicked()
            R.id.action_search -> resetAndShowSearchContainer()
            R.id.action_open_in_admin -> openUserInAdmin()
            R.id.action_scroll_seen -> scrollToNextSeenAsync()
            R.id.action_scroll_unseen -> scrollToNextUnseenAsync()

            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun scrollToNextSeenAsync() {
        val maxId = findLastVisibleFeedItem()?.id
        scrollToNextAsync { state, itemId -> itemId in state.seen && (maxId == null || itemId < maxId) }
    }

    private fun scrollToNextUnseenAsync() {
        val maxId = findLastVisibleFeedItem()?.id
        scrollToNextAsync { state, itemId -> itemId !in state.seen && (maxId == null || itemId < maxId) }
    }

    private fun scrollToNextAsync(matcher: (state: FeedViewModel.FeedState, itemId: Long) -> Boolean): Job {
        return launchUntilViewDestroy(busyIndicator = true) {
            val targetItem = feedStateModel.findNextWith(matcher) ?: return@launchUntilViewDestroy
            autoScrollRef = ScrollRef(CommentRef(targetItem), smoothScroll = true)

            // ensure we're starting the scroll even if the view is already layouted.
            performAutoScroll()
        }
    }

    private fun openUserInAdmin() {
        val uri =
            "https://pr0gramm.com/admin/?view=users&action=show&id=${userStateModel.userInfo?.info?.user?.id}"
        BrowserHelper.openCustomTab(requireContext(), Uri.parse(uri), handover = true)
    }

    private fun switchFeedType() {
        var filter = currentFilter
        filter = filter.withFeedType(switchFeedTypeTarget(filter))
        (activity as MainActionHandler).onFeedFilterSelected(filter, null as Bundle?)
    }

    private fun refreshFeedWithIndicator() {
        refreshContent()
    }

    private fun refreshContent() {
        resetToolbar()
        feedStateModel.refresh()
    }

    private fun pinCurrentFeedFilter() {
        // not bookmarkable anymore.
        onBookmarkableStateChanged(false)

        val filter = currentFilter
        val title = FeedFilterFormatter.format(requireContext(), filter).singleline
        (activity as MainActionHandler).bookmarkFilter(filter, title)
    }

    private fun hintUserIsNotVerified() {
        showDialog(this) {
            content(R.string.user_is_not_verified)

            positive(R.string.action_verify) {
                val uri = Uri.parse("https://pr0gramm.com/verify")
                BrowserHelper.openCustomTab(requireContext(), uri, handover = true)
            }

            negative(R.string.action_not_now)
        }
    }

    private fun preloadCurrentFeed() {
        if (AndroidUtility.isOnMobile(activity)) {
            showDialog(this) {
                content(R.string.preload_not_on_mobile)
                negative()
                positive {
                    doPreloadCurrentFeed(allowOnMobile = true)
                }
            }

            return
        } else {
            doPreloadCurrentFeed(allowOnMobile = false)
        }
    }

    private fun doPreloadCurrentFeed(allowOnMobile: Boolean) {
        val activity = activity ?: return

        // start preloading now
        PreloadService.preload(activity, feed, allowOnMobile)

        Track.preloadCurrentFeed(feed.size)

        singleShotService.doOnce("preload_info_hint") {
            showDialog(this) {
                content(R.string.preload_info_hint)
                positive()
            }
        }
    }

    private fun onBlockUserClicked() {
        this.userStateModel.userInfo?.info?.user?.name?.let { name ->
            val dialog = ItemUserAdminDialog.forUser(name)
            dialog.maybeShow(parentFragmentManager, "BlockUserDialog")
        }
    }

    private fun performSearch(query: SearchQuery) {
        view ?: return
        hideSearchContainer()

        val current = currentFilter
        var filter = current.withTagsNoReset(query.combined)

        if (current == filter) return

        var startAt: CommentRef? = null
        if (query.combined.trim().matches("[1-9][0-9]{5,}|id:[0-9]+".toRegex())) {
            filter = filter.basicWithTags("")
            startAt = CommentRef(query.combined.filter { it in '0'..'9' }.toLong())
        }

        (activity as MainActionHandler).onFeedFilterSelected(filter, null, startAt)

        if (query.queryTerm.isNotBlank()) {
            recentSearchesServices.storeTerm(query.queryTerm)
        }
    }

    private fun onItemClicked(
        item: FeedItem,
        commentRef: CommentRef? = null,
        preview: ImageView? = null
    ) {
        val activity = activity ?: return

        // reset auto open.
        autoScrollRef = null

        val idx = feed.indexById(item.id) ?: return
        trace { "onItemClicked(feedIndex=$idx, id=${item.id})" }

        try {
            val generator: FancyExifThumbnailGenerator by instance()

            val currentTitle = title
            val title = when {
                currentTitle?.useSubtitleInTitle == true && currentTitle.subtitle?.isNotBlank() == true ->
                    "${currentTitle.subtitle} in ${currentTitle.title}"

                else -> currentTitle?.title
            }

            val fragment = PostPagerFragment.newInstance(feed, idx, commentRef, title)
            if (preview != null) {
                // pass pixels info to target fragment.
                val image = preview.drawable

                val info = PreviewInfo.of(requireContext(), item, image)
                info.preloadFancyPreviewImage(generator)
                fragment.setPreviewInfo(info)
            }

            // Only set the target fragment if we are using the same fragment manager
            // to replace the current fragment. This is not the case, if we were started
            // from the Favorites page.
            if (parentFragmentManager === activity.supportFragmentManager) {
                fragment.setTargetFragment(this, 0)
            }

            activity.supportFragmentManager.beginTransaction()
                .replace(R.id.content_container, fragment)
                .addToBackStack(null)
                .commit()

        } catch (error: Exception) {
            logger.warn("Error while showing post", error)
        }
    }

    override val currentFilter: FeedFilter
        get() = feed.filter

    override val title: TitleFragment.Title?
        get() {
            val context = context ?: return null
            return FeedFilterFormatter.toTitle(context, feed.filter)
        }

    private fun displayFeedError(error: Throwable) {
        logger.error("Error loading the feed", error)

        when (error) {
            is FeedException.NotPublicException -> showFeedNotPublicError()
            is FeedException.NotFoundException -> showFeedNotFoundError()
        }
    }

    private fun showFeedNotFoundError() {
        showDialog(context ?: return) {
            content(R.string.error_feed_not_found)
            positive {
                // open top instead
                autoScrollRef = null
                replaceFeedFilter(FeedFilter())
            }
        }
    }

    private fun showFeedNotPublicError() {
        val username = currentFilter.username ?: return

        val targetItem = autoScrollRef

        if (targetItem != null) {
            showDialog(this) {
                content(R.string.error_feed_not_public__item, username)

                negative()

                positive {
                    val filter = currentFilter.basic()
                    replaceFeedFilter(filter, targetItem.itemId)
                }
            }
        } else {
            showDialog(this) {
                content(R.string.error_feed_not_public__general, username)
                positive()
            }
        }
    }

    private fun resetAndShowSearchContainer() {
        searchInitialState.value = initialSearchViewState()
        showSearchContainer()
    }

    private fun showSearchContainer() {
        searchVisibleState.value = true
    }

    override fun onBackButton(): Boolean {
        if (searchContainerIsVisible()) {
            hideSearchContainer()
            return true
        }

        return false
    }

    private fun searchContainerIsVisible(): Boolean {
        return searchVisibleState.value
    }

    private fun hideSearchContainer() {
        if (!searchVisibleState.value) return
        searchVisibleState.value = false
        resetToolbar()
        hideSoftKeyboard()
    }

    private fun performAutoOpen(ref: CommentRef) {
        if (isStateSaved || view == null)
            return

        logger.info { "Trying to do auto load of $ref" }
        val idx = feed.indexById(ref.itemId) ?: return

        logger.debug { "Found item at idx=$idx" }

        // scroll to item now and click
        scrollToItem(ref.itemId)

        launchInViewScope {
            awaitFrame()
            onItemClicked(feed[idx], ref)
        }
    }

    private fun scrollToItem(itemId: Long, smoothScroll: Boolean = false) {
        trace { "scrollToItem($itemId, smooth=$smoothScroll)" }

        logger.debug { "Checking if we can scroll to item $itemId" }
        val idx = feedEntriesState.value
            .indexOfFirst { it is FeedGridEntry.Item && it.item.id == itemId }
            .takeIf { it >= 0 } ?: return

        logger.debug { "Found item at idx=$idx, will scroll now (smooth=$smoothScroll)" }

        launchInViewScope {
            if (smoothScroll) {
                withContext(AndroidUiDispatcher.Main) {
                    gridState.animateScrollToItem(idx)
                }
            } else {
                gridState.scrollToItem(idx)
            }
        }
    }

    companion object {
        private const val ARG_FEED_FILTER = "FeedFragment.filter"
        private const val ARG_FEED_START = "FeedFragment.start"
        private const val ARG_NORMAL_MODE = "FeedFragment.simpleMode"
        private const val ARG_SEARCH_QUERY_STATE = "FeedFragment.searchQueryState"

        private const val STATE_GRID_INDEX = "gridState.firstVisibleItemIndex"
        private const val STATE_GRID_OFFSET = "gridState.firstVisibleItemScrollOffset"

        fun newInstance(
            feedFilter: FeedFilter,
            start: CommentRef?,
            searchQueryState: Bundle?
        ): FeedFragment {

            return FeedFragment().apply {
                arguments = bundle {
                    putParcelable(ARG_FEED_FILTER, feedFilter)
                    putParcelable(ARG_FEED_START, start)
                    putBoolean(ARG_NORMAL_MODE, true)
                    putBundle(ARG_SEARCH_QUERY_STATE, searchQueryState)
                }
            }
        }

        fun newEmbedArguments(filter: FeedFilter) = bundle {
            putParcelable(ARG_FEED_FILTER, filter)
            putBoolean(ARG_NORMAL_MODE, false)
        }
    }
}
