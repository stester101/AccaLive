package uk.co.simon.accalive.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import uk.co.simon.accalive.data.AccaState
import uk.co.simon.accalive.data.Fmt
import uk.co.simon.accalive.data.Game
import uk.co.simon.accalive.data.LegState
import uk.co.simon.accalive.logic.AccaEval

@Composable
fun TeamLogo(url: String, size: Dp) {
    AsyncImage(model = url, contentDescription = null, modifier = Modifier.size(size))
}

@Composable
fun OddsPill(
    text: String,
    modifier: Modifier = Modifier,
    highlight: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val m = modifier
        .clip(RoundedCornerShape(8.dp))
        .background(if (highlight) C.Yellow else C.Brand)
        .let { if (onClick != null) it.clickable { onClick() } else it }
        .padding(horizontal = 10.dp, vertical = 5.dp)
    Box(m, contentAlignment = Alignment.Center) {
        Text(
            text,
            color = if (highlight) Color.Black else Color.White,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 14.sp,
            style = Tnum,
            maxLines = 1
        )
    }
}

@Composable
fun StatusDot(state: LegState, size: Dp = 22.dp) {
    val bg: Color
    val fg: Color
    val glyph: String
    when (state) {
        LegState.WON -> { bg = C.Win; fg = Color.Black; glyph = "✓" }
        LegState.LOST -> { bg = C.Lose; fg = Color.White; glyph = "✕" }
        LegState.WINNING -> { bg = C.Win.copy(alpha = 0.18f); fg = C.Win; glyph = "▲" }
        LegState.LOSING -> { bg = C.Lose.copy(alpha = 0.18f); fg = C.Lose; glyph = "▼" }
        LegState.LEVEL -> { bg = C.Level.copy(alpha = 0.18f); fg = C.Level; glyph = "=" }
        LegState.VOID -> { bg = C.Raised; fg = C.Sub; glyph = "–" }
        LegState.PENDING -> { bg = C.Raised; fg = C.Mute; glyph = "•" }
    }
    Box(
        Modifier.size(size).clip(CircleShape).background(bg),
        contentAlignment = Alignment.Center
    ) {
        Text(glyph, color = fg, fontSize = (size.value * 0.5f).sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun ChanceBar(p: Double, color: Color, modifier: Modifier = Modifier, height: Dp = 6.dp) {
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(height))
            .background(C.Raised)
    ) {
        Box(
            Modifier
                .fillMaxWidth(p.toFloat().coerceIn(0f, 1f))
                .fillMaxHeight()
                .clip(RoundedCornerShape(height))
                .background(color)
        )
    }
}

@Composable
fun LiveDot(size: Dp = 8.dp) {
    val t = rememberInfiniteTransition(label = "live")
    val a by t.animateFloat(
        initialValue = 1f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "liveAlpha"
    )
    Box(Modifier.size(size).alpha(a).clip(CircleShape).background(C.Live))
}

@Composable
fun Chip(text: String, bg: Color, fg: Color, modifier: Modifier = Modifier, live: Boolean = false) {
    Row(
        modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .padding(horizontal = 7.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (live) {
            LiveDot(6.dp)
            Spacer(Modifier.width(4.dp))
        }
        Text(text, color = fg, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.6.sp)
    }
}

@Composable
fun AccaStateChip(e: AccaEval) {
    when {
        e.state == AccaState.WON -> Chip("WON", C.Win, Color.Black)
        e.state == AccaState.LOST -> Chip("LOST", C.Lose, Color.White)
        e.anyLive -> Chip("LIVE", C.Live.copy(alpha = 0.16f), C.Live, live = true)
        e.state == AccaState.LIVE -> Chip("IN PLAY", C.Raised, C.Text)
        else -> Chip("UPCOMING", C.Raised, C.Sub)
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier,
        color = C.Sub,
        fontSize = 11.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 1.sp
    )
}

@Composable
fun Metric(label: String, value: String, modifier: Modifier = Modifier, color: Color = C.Text, end: Boolean = false) {
    Column(modifier, horizontalAlignment = if (end) Alignment.End else Alignment.Start) {
        SectionLabel(label)
        Text(
            value,
            color = color,
            fontSize = 17.sp,
            fontWeight = FontWeight.ExtraBold,
            style = Tnum,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun Panel(modifier: Modifier = Modifier, border: Color = C.Line, content: @Composable () -> Unit) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(C.Surface)
            .border(1.dp, border, RoundedCornerShape(16.dp))
    ) { content() }
}

/** Two-tone win probability bar in team colours with labels either side. */
@Composable
fun WinProbBar(g: Game, modifier: Modifier = Modifier) {
    val p = g.homeWinProb ?: return
    val ac = teamColor(g.away)
    val hc = teamColor(g.home)
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${g.away.abbr} ${Fmt.pct(1 - p, g.phase == uk.co.simon.accalive.data.Phase.FINAL)}", color = ac, fontSize = 12.sp, fontWeight = FontWeight.Bold, style = Tnum)
            Text("WIN %", Modifier.weight(1f), color = C.Mute, fontSize = 10.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, letterSpacing = 1.sp)
            Text("${Fmt.pct(p, g.phase == uk.co.simon.accalive.data.Phase.FINAL)} ${g.home.abbr}", color = hc, fontSize = 12.sp, fontWeight = FontWeight.Bold, style = Tnum)
        }
        Spacer(Modifier.height(4.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Box(Modifier.weight((1 - p).toFloat().coerceAtLeast(0.001f)).fillMaxHeight().background(ac))
            Box(Modifier.weight(p.toFloat().coerceAtLeast(0.001f)).fillMaxHeight().background(hc))
        }
    }
}
