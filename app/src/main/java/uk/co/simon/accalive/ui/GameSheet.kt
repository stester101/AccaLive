package uk.co.simon.accalive.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uk.co.simon.accalive.Repo
import uk.co.simon.accalive.data.Fmt
import uk.co.simon.accalive.data.Game
import uk.co.simon.accalive.data.GameOdds
import uk.co.simon.accalive.data.OddsFmt
import uk.co.simon.accalive.data.OddsFormat
import uk.co.simon.accalive.data.Phase
import uk.co.simon.accalive.data.PlayerStats
import uk.co.simon.accalive.data.TeamInfo
import uk.co.simon.accalive.logic.LegEval

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameSheet(repo: Repo, eventId: String, fmt: OddsFormat, onDismiss: () -> Unit, onBuild: () -> Unit) {
    LaunchedEffect(eventId) {
        repo.focusEvent.value = eventId
        repo.refresh(force = true)
    }
    DisposableEffect(eventId) {
        onDispose { if (repo.focusEvent.value == eventId) repo.focusEvent.value = null }
    }
    val details by repo.details.collectAsStateWithLifecycle()
    val board by repo.board.collectAsStateWithLifecycle()
    val evals by repo.evals.collectAsStateWithLifecycle()
    @Suppress("UNUSED_VARIABLE") val boardTick = board
    val game = repo.gameFor(eventId)
    val detail = details[eventId]

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = C.Bg
    ) {
        if (game == null) {
            Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = C.Brand)
            }
        } else {
            val mine: List<Pair<String, LegEval>> = evals.filter { !it.acca.settled }.flatMap { e ->
                e.legs.filter { it.leg.eventId == eventId }.map { e.acca.name to it }
            }
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 36.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item { BigScoreboard(game) }
                if (game.homeWinProb != null) {
                    item { WinProbCard(game, detail?.winSeries ?: emptyList()) }
                }
                val odds = game.odds
                if (odds != null) {
                    item { OddsCard(game, odds, fmt) }
                }
                if (mine.isNotEmpty()) {
                    item { MyLegsCard(mine, fmt) }
                }
                val players = detail?.players
                if (!players.isNullOrEmpty() && game.phase != Phase.PRE) {
                    item { PerformersCard(game, players) }
                }
                if (game.phase != Phase.FINAL) {
                    item {
                        Button(
                            onClick = onBuild,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = C.Yellow, contentColor = Color.Black)
                        ) { Text("START AN ACCA WITH THIS GAME", fontWeight = FontWeight.ExtraBold) }
                    }
                }
            }
        }
    }
}

@Composable
private fun BigScoreboard(g: Game) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            when (g.phase) {
                Phase.LIVE -> {
                    LiveDot()
                    Spacer(Modifier.width(6.dp))
                    Text(g.liveClock, color = C.Live, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                }
                Phase.PRE -> Text(Fmt.kickoff(g.date).uppercase(), color = C.Sub, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                Phase.FINAL -> Text(if (g.period > 4) "FINAL / OT" else "FINAL", color = C.Sub, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            BigTeam(g.away, Modifier.weight(1f), g.phase == Phase.LIVE && g.possession == g.away.id)
            Text(
                if (g.phase == Phase.PRE) "@" else "${g.away.score}  –  ${g.home.score}",
                color = C.Text, fontSize = if (g.phase == Phase.PRE) 22.sp else 38.sp,
                fontWeight = FontWeight.Black, style = Tnum
            )
            BigTeam(g.home, Modifier.weight(1f), g.phase == Phase.LIVE && g.possession == g.home.id)
        }
        if (g.phase == Phase.LIVE) {
            g.downDistance?.let {
                Spacer(Modifier.height(10.dp))
                Text(it + if (g.redZone) "  ·  RED ZONE" else "", color = if (g.redZone) C.Live else C.Text, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            g.lastPlay?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, color = C.Sub, fontSize = 12.sp, textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
        g.broadcast?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, color = C.Mute, fontSize = 11.sp)
        }
    }
}

@Composable
private fun BigTeam(t: TeamInfo, modifier: Modifier, hasBall: Boolean) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        TeamLogo(t.logo, 56.dp)
        Spacer(Modifier.height(6.dp))
        Text(t.shortName + if (hasBall) " 🏈" else "", color = C.Text, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
        if (t.record.isNotBlank()) Text(t.record, color = C.Mute, fontSize = 11.sp)
    }
}

@Composable
private fun SheetCard(title: String, content: @Composable () -> Unit) {
    Panel {
        Column(Modifier.padding(14.dp)) {
            SectionLabel(title)
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun WinProbCard(g: Game, series: List<Double>) {
    val hc = teamColor(g.home)
    SheetCard(if (g.phase == Phase.PRE) "PRE-GAME WIN PROBABILITY" else "WIN PROBABILITY") {
        WinProbBar(g)
        if (series.size > 1) {
            Spacer(Modifier.height(12.dp))
            Row {
                Text(g.home.abbr, color = hc, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text("100%", color = C.Mute, fontSize = 10.sp)
            }
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .padding(vertical = 4.dp)
            ) {
                val w = size.width
                val h = size.height
                drawLine(
                    color = C.Line,
                    start = Offset(0f, h / 2),
                    end = Offset(w, h / 2),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
                )
                val path = Path()
                series.forEachIndexed { i, p ->
                    val x = w * i / (series.size - 1)
                    val y = h * (1f - p.toFloat())
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, hc, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            Row {
                Text(g.away.abbr, color = teamColor(g.away), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text("Kick-off → now", color = C.Mute, fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun OddsCard(g: Game, o: GameOdds, fmt: OddsFormat) {
    SheetCard("ODDS · ${o.provider.uppercase()} VIA ESPN") {
        Row {
            Spacer(Modifier.weight(1f))
            Text(g.away.abbr, Modifier.weight(1.2f), color = C.Sub, fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text(g.home.abbr, Modifier.weight(1.2f), color = C.Sub, fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(6.dp))
        OddsRow("Match", OddsFmt.format(o.awayMl, fmt), OddsFmt.format(o.homeMl, fmt))
        val hs = o.homeSpread
        if (hs != null) {
            OddsRow(
                "Handicap",
                "${Fmt.signed(-hs)}  ${OddsFmt.format(o.awaySpreadOdds, fmt)}",
                "${Fmt.signed(hs)}  ${OddsFmt.format(o.homeSpreadOdds, fmt)}"
            )
        }
        val t = o.total
        if (t != null) {
            OddsRow(
                "Total",
                "O ${Fmt.line(t)}  ${OddsFmt.format(o.overOdds, fmt)}",
                "U ${Fmt.line(t)}  ${OddsFmt.format(o.underOdds, fmt)}"
            )
        }
        val p = g.homeWinProb
        if (p != null && g.phase == Phase.LIVE) {
            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = C.Line)
            OddsRow(
                "Live fair",
                OddsFmt.format(if (p < 0.995) 1 / (1 - p) else null, fmt),
                OddsFmt.format(if (p > 0.005) 1 / p else null, fmt),
                highlight = true
            )
            Text("Fair price from ESPN's live win %, no bookmaker margin.", color = C.Mute, fontSize = 11.sp)
        }
        val opens = listOfNotNull(
            o.openHomeSpread?.let { "${g.home.abbr} ${Fmt.signed(it)}" },
            o.openTotal?.let { "O/U ${Fmt.line(it)}" },
            if (o.openAwayMl != null && o.openHomeMl != null) "${OddsFmt.format(o.openAwayMl, fmt)} / ${OddsFmt.format(o.openHomeMl, fmt)}" else null
        )
        if (opens.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text("Opened: " + opens.joinToString(" · "), color = C.Mute, fontSize = 11.sp)
        }
    }
}

@Composable
private fun OddsRow(label: String, away: String, home: String, highlight: Boolean = false) {
    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), color = C.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Box(Modifier.weight(1.2f).padding(horizontal = 3.dp), contentAlignment = Alignment.Center) {
            OddsPill(away, Modifier.fillMaxWidth(), highlight = highlight)
        }
        Box(Modifier.weight(1.2f).padding(horizontal = 3.dp), contentAlignment = Alignment.Center) {
            OddsPill(home, Modifier.fillMaxWidth(), highlight = highlight)
        }
    }
}

@Composable
private fun MyLegsCard(mine: List<Pair<String, LegEval>>, fmt: OddsFormat) {
    SheetCard("YOUR SELECTIONS") {
        mine.forEachIndexed { i, (accaName, l) ->
            Column {
                Text(accaName.uppercase(), color = C.Mute, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                Box(Modifier.padding(top = 2.dp)) {
                    Box(Modifier.background(Color.Transparent)) {
                        LegRow(l, fmt) {}
                    }
                }
            }
            if (i < mine.lastIndex) HorizontalDivider(color = C.Line)
        }
    }
}

@Composable
private fun PerformersCard(g: Game, players: Map<String, PlayerStats>) {
    SheetCard("TOP PERFORMERS") {
        listOf(g.away, g.home).forEachIndexed { idx, team ->
            val mine = players.values.filter { it.teamId == team.id }
            if (mine.isEmpty()) return@forEachIndexed
            if (idx == 1) HorizontalDivider(Modifier.padding(vertical = 8.dp), color = C.Line)
            Row(verticalAlignment = Alignment.CenterVertically) {
                TeamLogo(team.logo, 18.dp)
                Spacer(Modifier.width(6.dp))
                Text(team.shortName.uppercase(), color = teamColor(team), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
            }
            Spacer(Modifier.height(4.dp))
            mine.filter { it.passYds > 0 }.maxByOrNull { it.passYds }?.let {
                PerfLine(it.name, "${it.passYds} pass yds · ${it.passTd} TD")
            }
            mine.filter { it.rushYds != 0 }.sortedByDescending { it.rushYds }.take(2).forEach {
                PerfLine(it.name, "${it.rushYds} rush yds${if (it.rushTd > 0) " · ${it.rushTd} TD" else ""}")
            }
            mine.filter { it.rec > 0 }.sortedByDescending { it.recYds }.take(3).forEach {
                PerfLine(it.name, "${it.rec} rec · ${it.recYds} yds${if (it.recTd > 0) " · ${it.recTd} TD" else ""}")
            }
        }
    }
}

@Composable
private fun PerfLine(name: String, stat: String) {
    Row(Modifier.padding(vertical = 3.dp)) {
        Text(name, Modifier.weight(1f), color = C.Text, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(stat, color = C.Sub, fontSize = 13.sp, style = Tnum)
    }
}
