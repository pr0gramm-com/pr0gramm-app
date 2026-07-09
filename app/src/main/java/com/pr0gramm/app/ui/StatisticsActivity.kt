package com.pr0gramm.app.ui

import android.os.Bundle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.colorResource
import com.pr0gramm.app.R
import com.pr0gramm.app.feed.ContentType
import com.pr0gramm.app.milliseconds
import com.pr0gramm.app.orm.BenisRecord
import com.pr0gramm.app.orm.CachedVote
import com.pr0gramm.app.services.Graph
import com.pr0gramm.app.services.StatisticsService
import com.pr0gramm.app.services.ThemeHelper
import com.pr0gramm.app.services.UserService
import com.pr0gramm.app.services.VoteService
import com.pr0gramm.app.services.optimizeValuesBy
import com.pr0gramm.app.ui.base.BaseAppCompatActivity
import com.pr0gramm.app.ui.base.launchWhenCreated
import com.pr0gramm.app.ui.compose.setComposeContent
import com.pr0gramm.app.util.di.instance
import kotlinx.coroutines.withTimeout
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.minutes

class StatisticsActivity : BaseAppCompatActivity("StatisticsActivity") {

    private val userService: UserService by instance()
    private val voteService: VoteService by instance()
    private val statsService: StatisticsService by instance()

    private var state by mutableStateOf(StatisticsState())

    private var benisValues: List<BenisRecord> = emptyList()
    private var benisTimeRangeStart: Long = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(ThemeHelper.theme.noActionBar)
        super.onCreate(savedInstanceState)

        val hasUsername = userService.name != null

        state = state.copy(hasUsername = hasUsername)

        setComposeContent {
            // Read color resources inside composition
            val statsUp = colorResource(R.color.stats_up)
            val statsDown = colorResource(R.color.stats_down)
            val typeSfw = colorResource(R.color.type_sfw)
            val typeNsfp = colorResource(R.color.type_nsfp)
            val typeNsfw = colorResource(R.color.type_nsfw)
            val typeNsfl = colorResource(R.color.type_nsfl)
            val typePol = colorResource(R.color.type_pol)

            // Store colors for use in background coroutines
            colors = StatColors(statsUp, statsDown, typeSfw, typeNsfp, typeNsfw, typeNsfl, typePol)

            StatisticsScreen(
                state = state,
                actions = StatisticsActions(
                    onBack = { finish() },
                    onTimeRangeChanged = { range ->
                        benisTimeRangeStart = System.currentTimeMillis() - range.millis
                        rebuildGraph()
                    },
                ),
            )
        }

        launchWhenCreated(ignoreErrors = true) {
            handleVoteCounts(voteService.summary())
        }

        launchWhenCreated(ignoreErrors = true) {
            // delay querying the data for a moment
            com.pr0gramm.app.util.delay(200.milliseconds)

            // and get the values now.
            benisValues = userService.loadBenisRecords().records
            rebuildGraph()
        }

        if (hasUsername) {
            userService.name?.let { username ->
                launchWhenCreated {
                    loadContentTypes(username)
                }
            }
        }
    }

    private var colors = StatColors()

    private data class StatColors(
        val statsUp: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified,
        val statsDown: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified,
        val typeSfw: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified,
        val typeNsfp: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified,
        val typeNsfw: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified,
        val typeNsfl: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified,
        val typePol: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified,
    )

    private suspend fun loadContentTypes(username: String) {
        withTimeout(1.minutes) {
            statsService.statsForUploads(username).collect { stats ->
                showContentTypes(stats)
            }
        }
    }

    private fun showContentTypes(stats: StatisticsService.Stats) {
        val counts = stats.counts
        val sfw = counts[ContentType.SFW] ?: 0
        val nsfp = counts[ContentType.NSFP] ?: 0
        val nsfw = counts[ContentType.NSFW] ?: 0
        val nsfl = counts[ContentType.NSFL] ?: 0
        val pol = counts[ContentType.POL] ?: 0

        state = state.copy(
            uploadTypes = listOf(
                ChartValue(sfw, colors.typeSfw),
                ChartValue(nsfp, colors.typeNsfp),
                ChartValue(nsfw, colors.typeNsfw),
                ChartValue(nsfl, colors.typeNsfl),
                ChartValue(pol, colors.typePol),
            )
        )
    }

    private fun handleVoteCounts(votes: Map<CachedVote.Type, VoteService.Summary>) {
        state = state.copy(
            voteCountUp = votes.values.sumOf { it.up },
            voteCountDown = votes.values.sumOf { it.down },
            votesByTags = toChartValues(votes[CachedVote.Type.TAG]),
            votesByItems = toChartValues(votes[CachedVote.Type.ITEM]),
            votesByComments = toChartValues(votes[CachedVote.Type.COMMENT]),
        )
    }

    private fun toChartValues(summary: VoteService.Summary?): List<ChartValue> {
        summary ?: return emptyList()
        return listOf(
            ChartValue(summary.up, colors.statsUp),
            ChartValue(-summary.down, colors.statsDown),
        )
    }

    private fun rebuildGraph() {
        var actualValues = true
        var records = optimizeValuesBy(benisValues) { it.benis.toDouble() }

        if (records.size < 2 ||
            records.all { it.benis == records[0].benis } ||
            System.currentTimeMillis() - records[0].time < 60 * 1000
        ) {
            records = randomBenisGraph()
            actualValues = false
        }

        val original = Graph(records.map { Graph.Point(it.time.toDouble(), it.benis.toDouble()) })
        val startValue = benisTimeRangeStart.toDouble().coerceAtLeast(original.first.x)
        val sampled = original.sampleEquidistant(steps = 16, start = startValue)

        val graphState = if (!actualValues) {
            BenisGraphState.Empty
        } else {
            BenisGraphState.Ready(graph = original, sampled = sampled)
        }

        val dayChange = if (actualValues) ScoreChange(computeChange(original, 1)) else null
        val weekChange = if (actualValues) ScoreChange(computeChange(original, 7)) else null
        val monthChange = if (actualValues) ScoreChange(computeChange(original, 30)) else null

        state = state.copy(
            graphState = graphState,
            benisChangeDay = dayChange,
            benisChangeWeek = weekChange,
            benisChangeMonth = monthChange,
        )
    }

    private fun computeChange(graph: Graph, days: Long): Int {
        val millis = TimeUnit.DAYS.toMillis(days).toDouble()
        val nowValue = graph.last.y
        val baseValue = graph.valueAt(graph.last.x - millis)
        return (nowValue - baseValue).toInt()
    }

    private fun randomBenisGraph(): List<BenisRecord> {
        val offset = (Math.random() * 10000).toInt()
        val timeScale = TimeUnit.DAYS.toMillis(3L)
        val values = listOf(0, 100, 75, 150, 90, 60, 130, 160, 90, 70, 60, 130, 170, 210)
        return values.mapIndexed { index, value -> BenisRecord(timeScale * index.toLong(), offset + 10 * value) }
    }
}
