package com.pr0gramm.app.ui.compose.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pr0gramm.app.R
import com.pr0gramm.app.orm.Vote
import com.pr0gramm.app.ui.compose.theme.Pr0grammTheme

/**
 * The vote up/down buttons, replacing `VoteViewController`.
 *
 * State is hoisted: [vote] is the current vote and [onVote] is invoked with the vote that should
 * become active after a tap (tapping the already-active direction toggles back to [Vote.NEUTRAL]).
 */
@Composable
fun VoteButtons(
    vote: Vote,
    onVote: (Vote) -> Unit,
    modifier: Modifier = Modifier,
    iconSize: Dp = 24.dp,
) {
    val neutralColor = MaterialTheme.colorScheme.onSurfaceVariant
    val upColor = MaterialTheme.colorScheme.secondary
    val downColor = Color.White

    Row(modifier = modifier) {
        VoteButton(
            painter = R.drawable.ic_vote_up,
            active = vote == Vote.UP,
            dimmed = vote == Vote.DOWN,
            activeColor = upColor,
            neutralColor = neutralColor,
            iconSize = iconSize,
            onClick = { onVote(if (vote == Vote.UP) Vote.NEUTRAL else Vote.UP) },
        )

        VoteButton(
            painter = R.drawable.ic_vote_down,
            active = vote == Vote.DOWN,
            dimmed = vote == Vote.UP,
            activeColor = downColor,
            neutralColor = neutralColor,
            iconSize = iconSize,
            onClick = { onVote(if (vote == Vote.DOWN) Vote.NEUTRAL else Vote.DOWN) },
        )
    }
}

@Composable
private fun VoteButton(
    painter: Int,
    active: Boolean,
    dimmed: Boolean,
    activeColor: Color,
    neutralColor: Color,
    iconSize: Dp,
    onClick: () -> Unit,
) {
    val scale by animateFloatAsState(if (active) 1.2f else 1f, label = "voteScale")
    val alpha by animateFloatAsState(if (dimmed) 0.25f else 1f, label = "voteAlpha")

    IconButton(onClick = onClick) {
        Icon(
            painter = painterResource(painter),
            contentDescription = null,
            tint = if (active) activeColor else neutralColor,
            modifier = Modifier
                .size(iconSize)
                .scale(scale)
                .alpha(alpha),
        )
    }
}

@Preview
@Composable
private fun VoteButtonsPreview() {
    Pr0grammTheme {
        VoteButtons(vote = Vote.UP, onVote = {})
    }
}
