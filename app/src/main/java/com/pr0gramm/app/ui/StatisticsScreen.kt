package com.pr0gramm.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pr0gramm.app.R
import com.pr0gramm.app.services.Graph

import java.util.concurrent.TimeUnit

// region Data model

data class StatisticsState(
    val graphState: BenisGraphState = BenisGraphState.Loading,
    val benisChangeDay: ScoreChange? = null,
    val benisChangeWeek: ScoreChange? = null,
    val benisChangeMonth: ScoreChange? = null,
    val voteCountUp: Int = 0,
    val voteCountDown: Int = 0,
    val votesByItems: List<ChartValue> = emptyList(),
    val votesByTags: List<ChartValue> = emptyList(),
    val votesByComments: List<ChartValue> = emptyList(),
    val uploadTypes: List<ChartValue> = emptyList(),
    val hasUsername: Boolean = false,
)

sealed class BenisGraphState {
    data object Loading : BenisGraphState()
    data object Empty : BenisGraphState()
    data class Ready(val graph: Graph, val sampled: Graph) : BenisGraphState()
}

data class ScoreChange(val value: Int)

data class ChartValue(val amount: Int, val color: Color)

enum class TimeRange(val label: String, val millis: Long) {
    ALL("∞", TimeUnit.DAYS.toMillis(360 * 10)),
    ONE_DAY("1d", TimeUnit.DAYS.toMillis(1)),
    SEVEN_DAYS("7d", TimeUnit.DAYS.toMillis(7)),
    ONE_MONTH("1m", TimeUnit.DAYS.toMillis(30)),
    SIX_MONTHS("6m", TimeUnit.DAYS.toMillis(180)),
    ONE_YEAR("1y", TimeUnit.DAYS.toMillis(365)),
}

class StatisticsActions(
    val onBack: () -> Unit = {},
    val onTimeRangeChanged: (TimeRange) -> Unit = {},
)

// endregion

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(state: StatisticsState, actions: StatisticsActions) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Deine Statistik") },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            // 1. Benis graph
            BenisGraphSection(state.graphState)

            // 2. Time range selector (only when graph is ready)
            if (state.graphState is BenisGraphState.Ready) {
                TimeRangeSelector(actions.onTimeRangeChanged)
            }

            Spacer(Modifier.height(8.dp))

            // 3. Benis change card
            BenisChangeCard(state.benisChangeDay, state.benisChangeWeek, state.benisChangeMonth)

            Spacer(Modifier.height(8.dp))

            // 4. Votes card
            VotesCard(
                voteCountUp = state.voteCountUp,
                voteCountDown = state.voteCountDown,
                votesByItems = state.votesByItems,
                votesByTags = state.votesByTags,
                votesByComments = state.votesByComments,
            )

            Spacer(Modifier.height(8.dp))

            // 5. Uploads card
            if (state.hasUsername) {
                UploadsCard(state.uploadTypes)
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

// region Benis graph

@Composable
private fun BenisGraphSection(graphState: BenisGraphState) {
    val bgColor = MaterialTheme.colorScheme.primaryContainer

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(2f)
            .background(bgColor),
        contentAlignment = Alignment.Center,
    ) {
        when (graphState) {
            is BenisGraphState.Loading -> {
                Text(
                    text = stringResource(R.string.hint_loading),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }

            is BenisGraphState.Empty -> {
                Text(
                    text = stringResource(R.string.benisgraph_empty),
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp),
                )
            }

            is BenisGraphState.Ready -> {
                BenisGraphCanvas(graphState.graph, graphState.sampled)
            }
        }
    }
}

@Composable
private fun BenisGraphCanvas(original: Graph, sampled: Graph) {
    val textMeasurer = rememberTextMeasurer()
    val lineColor = Color.White
    val fillColor = Color.White.copy(alpha = 0.25f)

    Canvas(modifier = Modifier.fillMaxSize()) {
        if (sampled.isEmpty) return@Canvas

        val w = size.width
        val h = size.height

        val padding = 0.1 * (sampled.maxValue - sampled.minValue)
        val minY = sampled.minValue - padding
        val maxY = sampled.maxValue + padding
        val minX = sampled.firstX
        val maxX = sampled.lastX

        val scaleX = if (maxX - minX > 0) (maxX - minX) / w else 1.0
        val scaleY = if (maxY - minY > 0) (maxY - minY) / h else 1.0

        fun xPos(x: Double): Float = ((x - minX) / scaleX).toFloat()
        fun yPos(y: Double): Float = if (scaleY > 0) (h - (y - minY) / scaleY).toFloat() else (h * 0.75f)

        // Build path
        val path = Path()
        val points = sampled.points
        if (points.isEmpty()) return@Canvas

        path.moveTo(xPos(points.first().x), yPos(points.first().y))
        for (i in 1 until points.size) {
            path.lineTo(xPos(points[i].x), yPos(points[i].y))
        }

        // Draw fill (close path to bottom)
        val fillPath = Path().apply {
            addPath(path)
            lineTo(xPos(points.last().x), h)
            lineTo(xPos(points.first().x), h)
            close()
        }

        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(fillColor, Color.Transparent),
                startY = 0f,
                endY = h,
            ),
        )

        // Draw line
        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
        )

        // Draw highlights at index 2 and 13
        drawHighlight(sampled, 2, ::xPos, ::yPos, lineColor, textMeasurer)
        drawHighlight(sampled, 13, ::xPos, ::yPos, lineColor, textMeasurer)
    }
}

private fun DrawScope.drawHighlight(
    sampled: Graph,
    index: Int,
    xPos: (Double) -> Float,
    yPos: (Double) -> Float,
    lineColor: Color,
    textMeasurer: TextMeasurer,
) {
    if (index >= sampled.points.size) return

    val point = sampled[index]
    val cx = xPos(point.x)
    val cy = yPos(point.y)
    val lineWidth = 3.dp.toPx()

    // Filled circle
    drawCircle(
        color = Color(0xFF424242),
        radius = lineWidth,
        center = Offset(cx, cy),
    )

    // Stroke circle
    drawCircle(
        color = lineColor,
        radius = 1.5f * lineWidth,
        center = Offset(cx, cy),
        style = Stroke(width = lineWidth),
    )

    // Score label
    val text = formatScore(point.y.toInt())
    val style = TextStyle(color = lineColor, fontSize = 11.sp)
    val measured = textMeasurer.measure(text, style)

    // Position text: left side puts text to the right, right side to the left
    val midX = (sampled.firstX + sampled.lastX) / 2.0
    val textOffsetX = if (point.x > midX) {
        cx - measured.size.width - 2 * lineWidth
    } else {
        cx + 2 * lineWidth
    }

    // Position text above or below depending on graph direction
    val nextIndex = (index + 1).coerceAtMost(sampled.points.size - 1)
    val goesUp = sampled[nextIndex].y >= point.y
    val textOffsetY = if (goesUp) {
        cy + 2 * lineWidth
    } else {
        cy - measured.size.height - 2 * lineWidth
    }

    drawText(measured, topLeft = Offset(textOffsetX, textOffsetY))
}

// endregion

// region Time range selector

@Composable
private fun TimeRangeSelector(onTimeRangeChanged: (TimeRange) -> Unit) {
    val selectedColor = MaterialTheme.colorScheme.secondary
    val dimColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)

    // Track selected range internally — the activity will maintain the actual state
    val (selected, setSelected) = androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf(TimeRange.ALL)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        TimeRange.entries.forEach { range ->
            val isSelected = range == selected
            TextButton(onClick = {
                setSelected(range)
                onTimeRangeChanged(range)
            }) {
                Text(
                    text = range.label,
                    color = if (isSelected) selectedColor else dimColor,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

// endregion

// region Benis change card

@Composable
private fun BenisChangeCard(day: ScoreChange?, week: ScoreChange?, month: ScoreChange?) {
    val upColor = colorResource(R.color.stats_up)
    val downColor = colorResource(R.color.stats_down)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.stats_title_benis_change),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                ScoreChangeColumn(stringResource(R.string.stats_day), day, upColor, downColor)
                ScoreChangeColumn(stringResource(R.string.stats_week), week, upColor, downColor)
                ScoreChangeColumn(stringResource(R.string.monat), month, upColor, downColor)
            }
        }
    }
}

@Composable
private fun ScoreChangeColumn(label: String, change: ScoreChange?, upColor: Color, downColor: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        )

        Spacer(Modifier.height(4.dp))

        val displayValue = change?.value ?: 0
        Text(
            text = formatScore(displayValue),
            style = MaterialTheme.typography.titleMedium,
            color = if (displayValue < 0) downColor else upColor,
        )
    }
}

// endregion

// region Votes card

@Composable
private fun VotesCard(
    voteCountUp: Int,
    voteCountDown: Int,
    votesByItems: List<ChartValue>,
    votesByTags: List<ChartValue>,
    votesByComments: List<ChartValue>,
) {
    val upColor = colorResource(R.color.stats_up)
    val downColor = colorResource(R.color.stats_down)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.stats_title_votes),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                Text(
                    text = "BLUSSI $voteCountUp",
                    style = MaterialTheme.typography.labelMedium,
                    color = upColor,
                )
                Text(
                    text = "MINUS $voteCountDown",
                    style = MaterialTheme.typography.labelMedium,
                    color = downColor,
                )
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                CircleChart(
                    values = votesByItems,
                    lineTop = "",
                    chartType = "ITEMS",
                    lineBottom = "",
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(1f),
                )
                CircleChart(
                    values = votesByTags,
                    lineTop = "",
                    chartType = "TAGS",
                    lineBottom = "",
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(1f),
                )
                CircleChart(
                    values = votesByComments,
                    lineTop = "",
                    chartType = "COMMENTS",
                    lineBottom = "",
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(1f),
                )
            }
        }
    }
}

// endregion

// region Uploads card

@Composable
private fun UploadsCard(uploadTypes: List<ChartValue>) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.stats_title_uploads_favs),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircleChart(
                    values = uploadTypes,
                    lineTop = "",
                    chartType = "UPLOADS",
                    lineBottom = "",
                    modifier = Modifier.size(120.dp),
                )

                Spacer(Modifier.width(24.dp))

                UploadTypeLegend()
            }
        }
    }
}

@Composable
private fun UploadTypeLegend() {
    val entries = listOf(
        "SFW" to colorResource(R.color.type_sfw),
        "NSFP" to colorResource(R.color.type_nsfp),
        "NSFW" to colorResource(R.color.type_nsfw),
        "NSFL" to colorResource(R.color.type_nsfl),
        "POL" to colorResource(R.color.type_pol),
    )

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        entries.forEach { (label, color) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(modifier = Modifier.size(12.dp)) {
                    drawCircle(color = color)
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// endregion

// region Circle chart

@Composable
private fun CircleChart(
    values: List<ChartValue>,
    lineTop: String,
    chartType: String,
    lineBottom: String,
    modifier: Modifier = Modifier,
) {
    val totalScore = values.sumOf { it.amount }
    val onSurface = MaterialTheme.colorScheme.onSurface
    val dimColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        // Draw arcs
        Canvas(modifier = Modifier.fillMaxSize()) {
            val totalValue = values.sumOf { kotlin.math.abs(it.amount) }
            if (totalValue == 0) return@Canvas

            val strokeWidth = 4.dp.toPx()
            val offset = 0.75f * strokeWidth
            val arcSize = Size(size.width - 2 * offset, size.height - 2 * offset)
            val arcTopLeft = Offset(offset, offset)

            val angleGap = 5f
            val nonZeroCount = values.count { it.amount != 0 }
            val totalAngle = 360f - angleGap * nonZeroCount

            var currentAngle = -90f // start from top
            values.filter { it.amount != 0 }.forEach { value ->
                val sweep = totalAngle * (kotlin.math.abs(value.amount) / totalValue.toFloat())

                // Inner shadow arc
                drawArc(
                    color = Color.Black.copy(alpha = 0.25f),
                    startAngle = currentAngle,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = Offset(
                        arcTopLeft.x + 0.9f * strokeWidth,
                        arcTopLeft.y + 0.9f * strokeWidth,
                    ),
                    size = Size(
                        arcSize.width - 1.8f * strokeWidth,
                        arcSize.height - 1.8f * strokeWidth,
                    ),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Butt),
                )

                // Colored arc
                drawArc(
                    color = value.color,
                    startAngle = currentAngle,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Butt),
                )

                currentAngle += sweep + angleGap
            }
        }

        // Center labels
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (lineTop.isNotEmpty()) {
                Text(
                    text = lineTop,
                    style = MaterialTheme.typography.labelSmall,
                    color = dimColor,
                )
            }
            Text(
                text = chartType,
                style = MaterialTheme.typography.labelSmall,
                color = onSurface,
            )
            Text(
                text = formatScore(totalScore),
                style = MaterialTheme.typography.titleLarge,
                color = onSurface,
            )
            if (lineBottom.isNotEmpty()) {
                Text(
                    text = lineBottom,
                    style = MaterialTheme.typography.labelSmall,
                    color = dimColor,
                )
            }
        }
    }
}

private fun formatScore(value: Int): String {
    val abs = Math.abs(value)
    return when {
        abs >= 1_000_000 -> "%1.2fm".format(value / 1000000f)
        abs >= 100_000 -> "%1.0fk".format(value / 1000f)
        abs >= 10_000 -> "%1.1fk".format(value / 1000f)
        abs >= 1_000 -> "%1.2fk".format(value / 1000f)
        else -> value.toString()
    }
}

// endregion
