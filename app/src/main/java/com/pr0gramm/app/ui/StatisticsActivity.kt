package com.pr0gramm.app.ui

import com.pr0gramm.app.Pr0Domain
import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.appcompat.widget.Toolbar
import androidx.core.view.isVisible
import com.pr0gramm.app.Instant
import com.pr0gramm.app.R
import com.pr0gramm.app.Settings
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.api.pr0gramm.Message
import com.pr0gramm.app.api.pr0gramm.MessageType
import com.pr0gramm.app.feed.ContentType
import com.pr0gramm.app.milliseconds
import com.pr0gramm.app.orm.BenisRecord
import com.pr0gramm.app.orm.CachedVote
import com.pr0gramm.app.services.*
import com.pr0gramm.app.ui.base.BaseAppCompatActivity
import com.pr0gramm.app.ui.base.launchUntilDestroy
import com.pr0gramm.app.ui.base.launchWhenCreated
import com.pr0gramm.app.ui.views.CircleChartView
import com.pr0gramm.app.ui.views.TimeRangeSelectorView
import com.pr0gramm.app.ui.views.formatScore
import com.pr0gramm.app.util.di.instance
import com.pr0gramm.app.util.dp
import com.pr0gramm.app.util.find
import com.pr0gramm.app.util.getColorCompat
import com.pr0gramm.app.util.inflateDetachedChild
import com.pr0gramm.app.util.observeChange
import com.squareup.picasso.Picasso
import kotlinx.coroutines.withTimeout
import kotterknife.bindView
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.minutes

class StatisticsActivity : BaseAppCompatActivity("StatisticsActivity") {

    private val userService: UserService by instance()
    private val voteService: VoteService by instance()
    private val statsService: StatisticsService by instance()

    private val benisGraph: View by bindView(R.id.benis_graph)
    private val benisGraphLoading: View by bindView(R.id.benis_graph_loading)
    private val benisGraphEmpty: View by bindView(R.id.benis_graph_empty)
    private val benisGraphTimeSelector: TimeRangeSelectorView by bindView(R.id.graph_time_selector)

    private val benisChangeDay: TextView by bindView(R.id.stats_change_day)
    private val benisChangeWeek: TextView by bindView(R.id.stats_change_week)
    private val benisChangeMonth: TextView by bindView(R.id.stats_change_month)

    private val voteCountUp: TextView by bindView(R.id.stats_up)
    private val voteCountDown: TextView by bindView(R.id.stats_down)

    private val votesByTags: CircleChartView by bindView(R.id.votes_by_tags)
    private val votesByItems: CircleChartView by bindView(R.id.votes_by_items)
    private val votesByComments: CircleChartView by bindView(R.id.votes_by_comments)

    private val typesOfUpload: CircleChartView by bindView(R.id.types_uploads)

    private val premiumStats: LinearLayout by bindView(R.id.premium_stats)

    private val picasso: Picasso by instance()

    private var benisValues: List<BenisRecord> by observeChange(listOf()) {
        redrawBenisGraph()
        updateTimeRange()
    }

    private var benisTimeRangeStart: Long by observeChange(0L) { redrawBenisGraph() }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(ThemeHelper.theme.noActionBar)
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_statistics)

        // setup toolbar as actionbar
        val tb = find<Toolbar>(R.id.toolbar)
        setSupportActionBar(tb)

        // and show back button
        supportActionBar?.apply {
            setDisplayShowHomeEnabled(true)
            setDisplayHomeAsUpEnabled(true)
        }

        launchUntilDestroy {
            benisGraphTimeSelector.selectedTimeRange.collect { millis ->
                benisTimeRangeStart = System.currentTimeMillis() - millis
            }
        }

        launchWhenCreated(ignoreErrors = true) {
            handleVoteCounts(voteService.summary())
        }

        launchWhenCreated(ignoreErrors = true) {
            // delay querying the data for a moment
            com.pr0gramm.app.util.delay(200.milliseconds)

            // and get the values now.
            benisValues = userService.loadBenisRecords().records
        }

        userService.name?.let { username ->
            launchWhenCreated {
                showContentTypes(typesOfUpload, username)
            }
        }

        logger.info { "Premium stats: userIsPremium=${userService.userIsPremium}" }

        if (userService.userIsPremium) {
            launchWhenCreated(ignoreErrors = true) {
                val stats = statsService.userStats()
                logger.info { "Premium stats loaded: ${stats.topScoredItems.size} top posts, ${stats.topScoredComments.size} top comments" }
                showPremiumStats(stats)
            }
        }
    }

    private fun showPremiumStats(stats: Api.UserStats) {
        fun scoreBlocks(scores: Api.UserStats.Scores) = listOf(
            R.string.stats_posts to scores.items,
            R.string.stats_comments to scores.comments,
            R.string.stats_tags to scores.tags,
        )

        addCard(R.string.stats_title_score).addBlockRow(scoreBlocks(stats.score))
        addCard(R.string.stats_title_abs_score).addBlockRow(scoreBlocks(stats.absScore))

        addCard(R.string.stats_title_account).apply {
            addBlockRow(listOf(R.string.stats_faved_posts to stats.favCount.items, R.string.stats_faved_comments to stats.favCount.comments))
            addBlockRow(listOf(R.string.stats_followers to stats.followers.count, R.string.stats_subscribed to stats.followers.subscribed))

            addBlockRow(listOf(
                R.string.stats_messages_received to stats.messages.received,
                R.string.stats_messages_sent to stats.messages.send,
                R.string.stats_conversations to stats.messages.conversations,
            ))

            addBlockRow(listOf(
                R.string.stats_blocked_users to stats.blocks.blockedUsers,
                R.string.stats_blocked_tags to stats.blocks.blockedTags,
                R.string.stats_blocked_by to stats.blocks.blockedByUsers,
            ))

        }

        addPosts(R.string.stats_title_top_posts, stats.topScoredItems)
        addPosts(R.string.stats_title_lowest_posts, stats.lowestScoredItems)
        addPosts(R.string.stats_title_collected_posts, stats.topCollectedItems)

        addComments(R.string.stats_title_top_comments, stats.topScoredComments.map { c ->
            commentMessage(c.id, c.itemId, c.content, c.up - c.down, c.thumb, c.flags, c.commentCreated)
        })

        addComments(R.string.stats_title_lowest_comments, stats.lowestScoredComments.map { c ->
            commentMessage(c.id, c.itemId, c.content, c.up - c.down, c.thumb, c.flags, c.commentCreated)
        })

        addComments(R.string.stats_title_faved_comments, stats.topFavedComments.map { c ->
            commentMessage(c.id, c.itemId, c.content, c.up - c.down, c.thumb, c.flags, c.commentCreated)
        })

        // tags are shown like comments, the tag being the text. id 0 opens the post without a comment.
        addComments(R.string.stats_title_top_tags, stats.topScoredTags.map { t ->
            commentMessage(0, t.itemId, t.tag, t.up - t.down, t.thumb, t.flags, t.created)
        })

        addComments(R.string.stats_title_lowest_tags, stats.lowestScoredTags.map { t ->
            commentMessage(0, t.itemId, t.tag, t.up - t.down, t.thumb, t.flags, t.created)
        })
    }

    private fun commentMessage(id: Long, itemId: Long, text: String, score: Int, thumb: String?, flags: Int, created: Instant): Message {
        val loginState = userService.loginState

        return Message(
            type = MessageType.COMMENT, id = id, itemId = itemId, message = text.trim(),
            name = loginState.name ?: "", mark = loginState.mark, senderId = loginState.id,
            score = score, creationTime = created, thumbnail = thumb, image = null,
            flags = flags, read = true,
        )
    }

    /** Adds a card and returns the container to put content into. */
    private fun addCard(@StringRes title: Int): LinearLayout {
        val card = premiumStats.inflateDetachedChild<View>(R.layout.stats_card)
        card.find<TextView>(R.id.stats_card_title).setText(title)
        premiumStats.addView(card)
        return card.find(R.id.stats_card_content)
    }

    private fun LinearLayout.addBlockRow(blocks: List<Pair<Int, Int>>) {
        val row = LinearLayout(context)

        for ((title, value) in blocks) {
            val block = row.inflateDetachedChild<View>(R.layout.stats_block)
            block.find<TextView>(R.id.stats_block_title).setText(title)
            block.find<TextView>(R.id.stats_block_value).text = formatScore(value)
            row.addView(block)
        }

        addView(row)
    }

    private fun addPosts(@StringRes title: Int, items: List<Api.UserStats.Item>) {
        if (items.isEmpty()) return

        val row = LinearLayout(this)
        val scroller = HorizontalScrollView(this)
        scroller.addView(row)
        addCard(title).addView(scroller)

        val contentTypes = Settings.contentType

        for (item in items) {
            val cell = row.inflateDetachedChild<View>(R.layout.stats_post_cell)

            val image = cell.find<ImageView>(R.id.stats_post_image)
            picasso.load("https://thumb.$Pr0Domain/${item.thumb}").apply {
                if (ContentType.firstOf(item.flags) !in contentTypes) {
                    transform(BlurTransformation(12))
                }

                into(image)
            }

            cell.find<TextView>(R.id.stats_post_info).text = buildString {
                append(formatScore(item.up - item.down))
                item.count?.let { append(" · ♥ ").append(formatScore(it)) }
            }

            cell.setOnClickListener {
                startActivity(MainActivity.openItemIntent(this, item.id))
            }

            row.addView(cell)
        }
    }

    private fun addComments(@StringRes title: Int, messages: List<Message>) {
        if (messages.isEmpty()) return

        val container = addCard(title)

        for (message in messages) {
            val view = container.inflateDetachedChild<MessageView>(R.layout.user_info_comment)
            view.update(message, message.name)

            view.setOnClickListener {
                val commentId = message.commentId.takeIf { it > 0 }
                startActivity(MainActivity.openItemIntent(this, message.itemId, commentId))
            }

            container.addView(view)
        }
    }

    private suspend fun showContentTypes(view: CircleChartView, username: String) {
        withTimeout(1.minutes) {
            statsService.statsForUploads(username).collect { state ->
                showContentTypes(view, state)
            }
        }
    }

    private fun showContentTypes(view: CircleChartView, stats: StatisticsService.Stats) {
        val counts = stats.counts

        val sfw = counts[ContentType.SFW] ?: 0
        val nsfp = counts[ContentType.NSFP] ?: 0
        val nsfw = counts[ContentType.NSFW] ?: 0
        val nsfl = counts[ContentType.NSFL] ?: 0
        val pol = counts[ContentType.POL] ?: 0

        val values = listOf(
            CircleChartView.Value(sfw, getColorCompat(R.color.type_sfw)),
            CircleChartView.Value(nsfp, getColorCompat(R.color.type_nsfp)),
            CircleChartView.Value(nsfw, getColorCompat(R.color.type_nsfw)),
            CircleChartView.Value(nsfl, getColorCompat(R.color.type_nsfl)),
            CircleChartView.Value(pol, getColorCompat(R.color.type_pol)),
        )

        view.chartValues = values
    }

    @SuppressLint("SetTextI18n")
    private fun handleVoteCounts(votes: Map<CachedVote.Type, VoteService.Summary>) {
        voteCountUp.text = "BLUSSI " + votes.values.sumOf { it.up }
        voteCountDown.text = "MINUS " + votes.values.sumOf { it.down }

        votesByTags.chartValues = toChartValues(votes[CachedVote.Type.TAG])
        votesByItems.chartValues = toChartValues(votes[CachedVote.Type.ITEM])
        votesByComments.chartValues = toChartValues(votes[CachedVote.Type.COMMENT])
    }

    private fun toChartValues(summary: VoteService.Summary?): List<CircleChartView.Value> {
        summary ?: return listOf()

        return listOf(
            CircleChartView.Value(summary.up, getColorCompat(R.color.stats_up)),
            CircleChartView.Value(-summary.down, getColorCompat(R.color.stats_down))
        )
    }

    private fun updateTimeRange() {
        if (benisValues.size > 2) {
            val min = benisValues.minOf { v -> v.time }
            val max = benisValues.maxOf { v -> v.time }

            benisGraphTimeSelector.maxRangeInMillis = (max - min)
        }
    }

    private fun redrawBenisGraph() {
        benisGraphLoading.isVisible = false
        benisGraphTimeSelector.isVisible = true

        var actualValues = true
        var records = optimizeValuesBy(benisValues) { it.benis.toDouble() }

        // don't show if not enough data available
        if (records.size < 2 ||
            records.all { it.benis == records[0].benis } ||
            System.currentTimeMillis() - records[0].time < 60 * 1000
        ) {

            benisGraphEmpty.isVisible = true
            benisGraphTimeSelector.isVisible = false

            records = randomBenisGraph()
            actualValues = false
        }

        // convert to graph
        val original = Graph(records.map { Graph.Point(it.time.toDouble(), it.benis.toDouble()) })

        // sub-sample to only a few points.
        val startValue = benisTimeRangeStart.toDouble().coerceAtLeast(original.first.x)
        val sampled = original.sampleEquidistant(steps = 16, start = startValue)

        // build the visual
        val dr = GraphDrawable(sampled).apply {
            lineColor = Color.WHITE
            fillColor = 0xa0ffffffL.toInt()
            lineWidth = dp(4f)
            highlightFillColor = getColorCompat(ThemeHelper.primaryColorDark)
        }

        // add highlight for the a left-ish and a right-ish point.
        dr.highlights.add(GraphDrawable.Highlight(2, formatScore(sampled[2].y.toInt())))
        dr.highlights.add(GraphDrawable.Highlight(13, formatScore(sampled[13].y.toInt())))

        // and show the graph
        benisGraph.background = dr

        if (actualValues) {
            // calculate the recent changes of benis
            formatChange(original, 1, benisChangeDay)
            formatChange(original, 7, benisChangeWeek)
            formatChange(original, 30, benisChangeMonth)
        }
    }

    private fun randomBenisGraph(): List<BenisRecord> {
        val offset = (Math.random() * 10000).toInt()
        val timeScale = TimeUnit.DAYS.toMillis(3L)

        val values = listOf(0, 100, 75, 150, 90, 60, 130, 160, 90, 70, 60, 130, 170, 210)
        return values.mapIndexed { index, value -> BenisRecord(timeScale * index.toLong(), offset + 10 * value) }
    }

    private fun formatChange(graph: Graph, days: Long, view: TextView) {
        val millis = TimeUnit.DAYS.toMillis(days).toDouble()

        val nowValue = graph.last.y
        val baseValue = graph.valueAt(graph.last.x - millis)

        val absChange = nowValue - baseValue

        view.text = formatScore(absChange.toInt())
        view.setTextColor(getColorCompat(if (absChange < 0) R.color.stats_down else R.color.stats_up))
    }
}
