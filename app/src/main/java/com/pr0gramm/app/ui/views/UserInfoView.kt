package com.pr0gramm.app.ui.views

import com.pr0gramm.app.isPr0Host
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.text.buildSpannedString
import androidx.core.text.color
import androidx.core.view.isNotEmpty
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.pr0gramm.app.Duration
import com.pr0gramm.app.Instant
import com.pr0gramm.app.R
import com.pr0gramm.app.UserClassesService
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.services.PostCollection
import com.pr0gramm.app.services.UriHelper
import com.pr0gramm.app.ui.configureNewStyle
import com.pr0gramm.app.util.*
import com.pr0gramm.app.util.di.injector
import com.squareup.picasso.Picasso
import java.util.concurrent.TimeUnit

/**
 * Shows the users avatar, or the generated face if the user has none.
 */
internal fun loadAvatar(view: ImageView, name: String, avatar: String?) {
    view.clipToOutline = true

    val picasso = view.context.injector.instance<Picasso>()
    val fallback = UserDrawables(view.context).drawable(name)

    val uri = UriHelper.NoPreload.avatarSmall(avatar)
    if (uri == null) {
        picasso.cancelRequest(view)
        view.setImageDrawable(fallback)
        return
    }

    picasso.load(uri).placeholder(fallback).error(fallback).fit().centerCrop().into(view)
}

/**
 */
@SuppressLint("ViewConstructor")
class UserInfoView(context: Context) : FrameLayout(context) {
    init {
        View.inflate(context, R.layout.user_info_cell_v2, this)
    }

    private val username: UsernameView = find(R.id.username)
    private val benis: TextView = find(R.id.kpi_benis)
    private val collected: TextView = find(R.id.kpi_collected)
    private val comments: TextView = find(R.id.kpi_comments)
    private val tags: TextView = find(R.id.kpi_tags)
    private val uploads: TextView = find(R.id.kpi_uploads)
    private val avatar: ImageView = find(R.id.user_avatar)
    private val writeNewMessage: View = find(R.id.action_new_message)
    private val blockUser: View = find(R.id.action_block)
    private val shareUserProfile: View = find(R.id.action_share)
    private val badgesContainer: RecyclerView = find(R.id.badges_container)
    private val userTypeName: TextView = find(R.id.user_type_name)
    private val appLinksContainer: ViewGroup = find(R.id.app_links)
    private val showCommentsContainer: View = find(R.id.kpi_comments_container)
    private val showUploadsContainer: View = find(R.id.kpi_uploads_container)
    private val showCollectedContainer: View = find(R.id.kpi_collected_container)

    private val picasso = context.injector.instance<Picasso>()
    private val userClassesService = context.injector.instance<UserClassesService>()

    fun updateUserInfo(
        info: Api.Info,
        comments: List<Api.UserComments.UserComment>,
        myself: Boolean,
        selectedTab: Tab,
        actions: UserInfoView.UserActionListener
    ) {
        showUploadsContainer.isSelected = selectedTab == Tab.Uploads
        showCollectedContainer.isSelected = selectedTab == Tab.Collected
        showCommentsContainer.isSelected = selectedTab == Tab.Comments

        // user info
        val user = info.user
        username.setUsername(user.name, user.mark)
        benis.text = user.score.toString()

        // counts
        tags.text = info.tagCount.toString()
        uploads.text = info.uploadCount.toString()
        this.comments.text = info.commentCount.toString()

        loadAvatar(avatar, user.name, user.avatar)

        showCommentsContainer.isVisible = comments.isNotEmpty()

        writeNewMessage.isVisible = !myself && user.canReceiveMessages
        blockUser.isVisible = !myself && user.canReceiveMessages

        // open message dialog for user
        writeNewMessage.setOnClickListener {
            actions.onWriteMessageClicked(user.name)
        }

        // open confirmation dialog to block user
        blockUser.setOnClickListener {
            actions.onBlockUserClicked(user.name)
        }

        // open share popup
        shareUserProfile.setOnClickListener {
            actions.shareUserProfile(user.name)
        }

        showCommentsContainer.setOnClickListener {
            actions.onShowCommentsClicked()
        }

        showUploadsContainer.setOnClickListener {
            actions.onShowUploadsClicked(user.name)
        }

        showCollectedContainer.isVisible = info.collectedCount > 0 || myself
        collected.text = info.collectedCount.toString()
        showCollectedContainer.setOnClickListener {
            val publicCollections = PostCollection.fromApi(info).filter { it.isPublic }
            if (myself || publicCollections.isNotEmpty()) {
                actions.onUserViewCollectionsClicked(user.name)
            }
        }

        val badges = mutableListOf<BadgeInfo>()
        // add badge for "x comments"
        (info.commentCount / 1000).takeIf { it > 0 }?.let {
            badges += BadgeInfo(
                "comments.png",
                context.getString(R.string.badge_comments, it.toString()),
                text = "${it}k", textColor = Color.BLACK
            )
        }

        // add badge for "x tags"
        (info.tagCount / 1000).takeIf { it > 0 }?.let {
            badges += BadgeInfo(
                "tags.png",
                context.getString(R.string.badge_tags, it.toString()),
                text = "${it}k", textColor = Color.WHITE
            )
        }

        if (info.user.itemDeleteCount > 0) {
            val count = info.user.itemDeleteCount
            badges += BadgeInfo(
                "itemdelete.png",
                context.getString(R.string.badge_deleted_items, count),
                text = "$count", textColor = Color.WHITE
            )
        }

        if (info.user.commentDeleteCount > 0) {
            val count = info.user.commentDeleteCount
            badges += BadgeInfo(
                "commentdelete.png",
                context.getString(R.string.badge_deleted_comments, count),
                text = "$count", textColor = Color.WHITE
            )
        }

        // add badge for "x years on pr0gramm"
        val years = Duration.between(Instant.now(), info.user.registered).convertTo(TimeUnit.DAYS) / 365
        if (years > 0) {
            badges += BadgeInfo(
                "years.png",
                context.getString(R.string.badge_time, years.toString()),
                text = years.toString(), textColor = Color.WHITE
            )
        }

        // add badge for "has X month of promium"
        val premiumMonths = info.premiumTime / 30
        if (premiumMonths > 0) {
            badges += BadgeInfo(
                "pr0miumTime.png",
                context.getString(R.string.badge_premium, premiumMonths),
                text = premiumMonths.toString(), textColor = Color.WHITE
            )
        }

        // add badge for "has X month of promium"
        val premiumGift = info.premiumGift
        if (premiumGift > 0) {
            badges += BadgeInfo(
                "pr0miumGift.png",
                context.getString(R.string.badge_premium_gift, premiumGift),
                text = premiumGift.toString(), textColor = Color.WHITE
            )
        }

        info.badges.mapTo(badges) { badge ->
            BadgeInfo(badge.image, badge.description ?: "")
        }

        badgesContainer.adapter = BadgeAdapter(badges)
        badgesContainer.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        badgesContainer.isHorizontalFadingEdgeEnabled = true
        badgesContainer.setFadingEdgeLength(context.dp(24))

        if (badges.isNotEmpty()) {
            // scroll to the end after a short delay.
            val r = { badgesContainer.smoothScrollToPosition(badges.size - 1) }
            badgesContainer.postDelayed(r, 500)
        }

        // info about banned/register date
        val extraInfo = if (user.banned && !user.inactive) {
            val bannedUntil = user.bannedUntil
            if (bannedUntil == null) {
                context.getString(R.string.user_banned_forever)
            } else {
                val durationStr = DurationFormat.timeSpan(context, bannedUntil, short = false)
                val stringId = if (user.inactive) R.string.user_banned_self else R.string.user_banned
                context.getString(stringId, durationStr)
            }
        } else {
            val dateStr = DurationFormat.timeToPointInTime(context, user.registered, short = false)
            val resId = if (user.inactive) R.string.user_registered_inactive else R.string.user_registered
            context.getString(resId, dateStr)
        }

        val userClass = userClassesService.get(user.mark)
        userTypeName.text = buildSpannedString {
            color(userClass.color) { append(userClass.name) }
            append(" · ")
            append(extraInfo)
        }

        appLinksContainer.removeAllViews()

        catchAll {
            for (appLink in info.appLinks.orEmpty()) {
                val appLinkView = layoutInflater.inflate(R.layout.app_link, appLinksContainer, true)
                appLinkView.find<TextView>(R.id.app_link_text).text = appLink.text

                appLink.icon?.let { iconUrl ->
                    val imageView = appLinkView.find<ImageView>(R.id.app_link_image)
                    picasso.load(iconUrl).into(imageView)
                }

                val uri = appLink.link?.let { Uri.parse(it) }
                if (uri != null) {
                    appLinkView.setOnClickListener {
                        val handover = isPr0Host(uri.host)
                        BrowserHelper.openCustomTab(context, uri, handover)
                    }
                }
            }

            if (appLinksContainer.isNotEmpty()) {
                appLinksContainer.isVisible = true
            }
        }
    }

    enum class Tab { Uploads, Collected, Comments }

    interface UserActionListener {
        fun onWriteMessageClicked(name: String)
        fun onUserViewCollectionsClicked(name: String)
        fun onShowCommentsClicked()
        fun onShowUploadsClicked(name: String)
        fun onBlockUserClicked(name: String)
        fun shareUserProfile(name: String)
    }

    private data class BadgeInfo(
        val image: String, val description: String,
        val text: String? = null,
        val textColor: Int = Color.WHITE
    )

    private class BadgeAdapter(val badges: List<BadgeInfo>) : RecyclerView.Adapter<BadgeViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, idx: Int): BadgeViewHolder {
            val view = parent.layoutInflater.inflate(R.layout.badge, parent, false)
            return BadgeViewHolder(view)
        }

        override fun onBindViewHolder(holder: BadgeViewHolder, idx: Int) {
            holder.set(badges[idx])
        }

        override fun getItemCount(): Int {
            return badges.size
        }
    }

    private class BadgeViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val imageView: ImageView = view.find(R.id.image)
        private val textView: TextView = view.find(R.id.text)

        fun set(badge: BadgeInfo) {
            if (badge.text == null) {
                textView.isVisible = false
            } else {
                textView.isVisible = true
                textView.text = badge.text
                textView.setTextColor(badge.textColor)
            }

            itemView.setOnClickListener {
                Snackbar.make(itemView.context, itemView, badge.description, Snackbar.LENGTH_SHORT)
                    .setAction(R.string.okay) { /* do nothing */ }
                    .configureNewStyle()
                    .show()
            }

            if (!itemView.isInEditMode) {
                val context = itemView.context
                val picasso = context.injector.instance<Picasso>()

                val localImageId = knownImages[badge.image]
                if (localImageId != null) {
                    imageView.setImageResource(localImageId)
                } else {
                    val url = UriHelper.of(context).badgeImageUrl(badge.image)
                    picasso.load(url).into(imageView)
                }
            }
        }

        companion object {
            private val knownImages = mapOf(
                "art13.png" to R.drawable.badge_art13,
                "benitrat0r-lose.png" to R.drawable.badge_benitrator_lose,
                "benitrat0r-win.png" to R.drawable.badge_benitrator_win,
                "commentdelete.png" to R.drawable.deleted_comment,
                "comments.png" to R.drawable.badge_comments,
                "connect4-blue.png" to R.drawable.badge_connect4_blue,
                "connect4-red.png" to R.drawable.badge_connect4_red,
                "contract.png" to R.drawable.badge_contract,
                "itemdelete.png" to R.drawable.deleted_item,
                "krebs-donation.png" to R.drawable.badge_krebs,
                "secret-santa-2014.png" to R.drawable.badge_secret_santa,
                "social-share.png" to R.drawable.badge_social,
                "tags.png" to R.drawable.badge_tags,
                "years.png" to R.drawable.badge_years,
            )
        }
    }

}
