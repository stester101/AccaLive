package uk.co.simon.accalive.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uk.co.simon.accalive.data.AccaState
import uk.co.simon.accalive.data.Fmt
import uk.co.simon.accalive.data.OddsFormat
import uk.co.simon.accalive.data.Phase
import uk.co.simon.accalive.logic.AccaEval
import uk.co.simon.accalive.logic.LegEval
import uk.co.simon.accalive.logic.Labels

/** Glanceable layout for the Z Flip's cover screen: one acca per page, swipe between them. */
@Composable
fun CoverScreen(evals: List<AccaEval>, fmt: OddsFormat) {
    val open = evals.filter { !it.acca.settled }
        .sortedWith(compareByDescending<AccaEval> { it.anyLive }.thenBy { it.acca.createdAt })
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (open.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Row {
                    Text("ACCA", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic)
                    Text("LIVE", color = C.Yellow, fontSize = 24.sp, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic)
                }
                Spacer(Modifier.height(8.dp))
                Text("No open accas.\nOpen your phone to build one.", color = C.Sub, fontSize = 13.sp, textAlign = TextAlign.Center)
            }
        } else {
            val pager = rememberPagerState { open.size }
            HorizontalPager(pager, Modifier.fillMaxSize()) { i ->
                CoverAcca(open[i])
            }
            if (open.size > 1) {
                Row(
                    Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    repeat(open.size) { i ->
                        Box(
                            Modifier
                                .size(if (i == pager.currentPage) 7.dp else 5.dp)
                                .clip(CircleShape)
                                .background(if (i == pager.currentPage) C.Yellow else C.Mute)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CoverAcca(e: AccaEval) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 24.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                e.acca.name,
                color = C.Text, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
            )
            AccaStateChip(e)
        }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            val lost = e.state == AccaState.LOST
            Text(
                if (lost) "0%" else Fmt.pct(e.prob, e.state == AccaState.WON),
                color = if (lost) C.Lose else probColor(e.prob),
                fontSize = 40.sp, fontWeight = FontWeight.Black, style = Tnum
            )
            Spacer(Modifier.width(8.dp))
            Column(Modifier.padding(bottom = 7.dp)) {
                Text(Fmt.money(e.returns), color = C.Yellow, fontSize = 15.sp, fontWeight = FontWeight.Black, style = Tnum)
                Text("${e.landing}/${e.acca.legs.size} landing", color = C.Sub, fontSize = 11.sp)
            }
        }
        Spacer(Modifier.height(8.dp))
        e.legs.forEach { l -> CoverLeg(l) }
    }
}

@Composable
private fun CoverLeg(l: LegEval) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        StatusDot(l.state, 16.dp)
        Spacer(Modifier.width(8.dp))
        Text(
            Labels.title(l.leg),
            color = C.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(6.dp))
        val g = l.game
        val text = when {
            l.leg.result != null -> l.state.label.uppercase()
            g == null -> "—"
            g.phase == Phase.PRE -> Fmt.clockTime(g.date)
            g.phase == Phase.LIVE -> "${g.away.score}-${g.home.score} ${g.liveClock}"
            else -> "${g.away.score}-${g.home.score} FT"
        }
        Text(
            text,
            color = if (g?.phase == Phase.LIVE && l.leg.result == null) C.Live else stateColor(l.state),
            fontSize = 11.sp, fontWeight = FontWeight.Bold, style = Tnum, maxLines = 1
        )
    }
}

@Suppress("unused")
private fun unusedFmt(f: OddsFormat) = f
