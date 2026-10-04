package uk.co.simon.accalive.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import uk.co.simon.accalive.Repo
import uk.co.simon.accalive.data.Board
import uk.co.simon.accalive.data.Fmt
import uk.co.simon.accalive.data.Game
import uk.co.simon.accalive.data.OddsFmt
import uk.co.simon.accalive.data.OddsFormat
import uk.co.simon.accalive.data.Phase
import uk.co.simon.accalive.data.TeamInfo
import uk.co.simon.accalive.data.WeekRef
import uk.co.simon.accalive.logic.AccaEval

@Composable
fun GamesTab(repo: Repo, evals: List<AccaEval>, fmt: OddsFormat, onGame: (String) -> Unit) {
    val board by repo.board.collectAsStateWithLifecycle()
    val week by repo.week.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val b = board

    if (b == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = C.Brand)
        }
        return
    }

    val legsByGame = evals.flatMap { e -> e.legs.filter { it.leg.result == null } }.groupingBy { it.leg.eventId }.eachCount()
    val games = b.games.map { repo.gameFor(it.id) ?: it }
    val byDay = games.groupBy { Fmt.day(it.date) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item(key = "weeks") {
            WeekChips(b, week ?: b.current) { w -> scope.launch { repo.setWeek(w) } }
        }
        if (games.isEmpty()) {
            item(key = "empty") {
                Text("No games this week.", color = C.Sub, modifier = Modifier.padding(16.dp))
            }
        }
        byDay.forEach { (day, list) ->
            item(key = "h-$day") {
                SectionLabel(day, Modifier.padding(top = 8.dp, start = 4.dp))
            }
            items(list, key = { it.id }) { g ->
                GameCard(g, legsByGame[g.id] ?: 0, fmt) { onGame(g.id) }
            }
        }
    }
}

@Composable
fun WeekChips(b: Board, selected: WeekRef?, onSelect: (WeekRef) -> Unit) {
    val weeks = b.weeks.ifEmpty { listOfNotNull(b.current) }
    val idx = weeks.indexOfFirst { it.key == selected?.key }.coerceAtLeast(0)
    val state = rememberLazyListState(initialFirstVisibleItemIndex = (idx - 1).coerceAtLeast(0))
    LaunchedEffect(idx) { state.scrollToItem((idx - 1).coerceAtLeast(0)) }
    LazyRow(state = state, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(weeks, key = { it.key }) { w ->
            val sel = w.key == selected?.key
            val label = if (w.seasonType == 2) "WK ${w.week}" else w.label.uppercase()
            Box(
                Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (sel) C.Yellow else C.Surface)
                    .border(1.dp, if (sel) C.Yellow else C.Line, RoundedCornerShape(18.dp))
                    .clickable { onSelect(w) }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(label, color = if (sel) Color.Black else C.Text, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

@Composable
fun GameCard(g: Game, myLegs: Int, fmt: OddsFormat, onClick: () -> Unit) {
    Panel(border = if (myLegs > 0) C.Brand.copy(alpha = 0.7f) else C.Line) {
        Column(
            Modifier
                .clickable(onClick = onClick)
                .padding(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                when (g.phase) {
                    Phase.LIVE -> {
                        LiveDot()
                        Spacer(Modifier.width(6.dp))
                        Text(g.liveClock, color = C.Live, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, style = Tnum)
                    }
                    Phase.PRE -> Text(Fmt.clockTime(g.date), color = C.Sub, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                    Phase.FINAL -> Text(if (g.period > 4) "FINAL / OT" else "FINAL", color = C.Sub, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                }
                Spacer(Modifier.weight(1f))
                g.broadcast?.let { Text(it, color = C.Mute, fontSize = 11.sp) }
                if (myLegs > 0) {
                    Spacer(Modifier.width(8.dp))
                    Chip("$myLegs MY LEG${if (myLegs == 1) "" else "S"}", C.Yellow, Color.Black)
                }
            }
            Spacer(Modifier.height(10.dp))
            TeamLine(g.away, g)
            Spacer(Modifier.height(6.dp))
            TeamLine(g.home, g)

            if (g.phase == Phase.LIVE && g.downDistance != null) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(g.downDistance, color = C.Text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    if (g.redZone) {
                        Spacer(Modifier.width(6.dp))
                        Chip("RED ZONE", C.Live, Color.White)
                    }
                }
            }

            if (g.homeWinProb != null) {
                Spacer(Modifier.height(10.dp))
                WinProbBar(g)
            }

            val o = g.odds
            if (o != null && g.phase != Phase.FINAL) {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MiniMarket(
                        "HANDICAP",
                        if (o.homeSpread != null) "${g.home.abbr} ${Fmt.signed(o.homeSpread)}" else "—",
                        Modifier.weight(1f)
                    )
                    MiniMarket("TOTAL", if (o.total != null) "O/U ${Fmt.line(o.total)}" else "—", Modifier.weight(1f))
                    MiniMarket(
                        "MATCH",
                        "${OddsFmt.format(o.awayMl, fmt)} · ${OddsFmt.format(o.homeMl, fmt)}",
                        Modifier.weight(1.2f)
                    )
                }
            }
        }
    }
}

@Composable
private fun TeamLine(t: TeamInfo, g: Game) {
    val lost = g.phase == Phase.FINAL && t.score < (if (t.id == g.home.id) g.away.score else g.home.score)
    Row(verticalAlignment = Alignment.CenterVertically) {
        TeamLogo(t.logo, 28.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    t.name.ifBlank { t.abbr },
                    color = if (lost) C.Sub else C.Text,
                    fontSize = 15.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                if (g.phase == Phase.LIVE && g.possession == t.id) {
                    Spacer(Modifier.width(6.dp))
                    Text("🏈", fontSize = 11.sp)
                }
            }
            if (t.record.isNotBlank()) Text(t.record, color = C.Mute, fontSize = 11.sp)
        }
        if (g.phase != Phase.PRE) {
            Text(
                "${t.score}",
                color = if (lost) C.Sub else C.Text,
                fontSize = 24.sp, fontWeight = FontWeight.Black, style = Tnum
            )
        }
    }
}

@Composable
private fun MiniMarket(label: String, value: String, modifier: Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(C.Raised)
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Text(label, color = C.Mute, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.8.sp)
        Text(value, color = C.Text, fontSize = 12.sp, fontWeight = FontWeight.Bold, style = Tnum, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
