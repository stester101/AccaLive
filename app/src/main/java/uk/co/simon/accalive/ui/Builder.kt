package uk.co.simon.accalive.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import uk.co.simon.accalive.Repo
import uk.co.simon.accalive.data.Acca
import uk.co.simon.accalive.data.Fmt
import uk.co.simon.accalive.data.Game
import uk.co.simon.accalive.data.Leg
import uk.co.simon.accalive.data.LegType
import uk.co.simon.accalive.data.OddsFmt
import uk.co.simon.accalive.data.OddsFormat
import uk.co.simon.accalive.data.Phase
import uk.co.simon.accalive.data.PropStat
import uk.co.simon.accalive.data.RosterPlayer
import uk.co.simon.accalive.data.Side
import uk.co.simon.accalive.data.TeamInfo
import uk.co.simon.accalive.logic.Labels
import java.util.Locale
import java.util.UUID

@Composable
fun BuilderScreen(repo: Repo, initial: Acca, startEvent: String?, onClose: () -> Unit) {
    val prefs by repo.settings.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val isNew = initial.legs.isEmpty() && initial.name.isBlank()

    var name by remember { mutableStateOf(initial.name) }
    var stakeText by remember {
        mutableStateOf(if (initial.stake % 1.0 == 0.0) initial.stake.toInt().toString() else initial.stake.toString())
    }
    val legs = remember { mutableStateListOf<Leg>().apply { addAll(initial.legs) } }
    var picker by remember { mutableStateOf(startEvent != null || initial.legs.isEmpty()) }

    BackHandler { onClose() }

    val stake = stakeText.toDoubleOrNull() ?: 0.0
    val odds = legs.fold(1.0) { a, l -> a * l.odds }
    val fallbackName = "${Fmt.fold(legs.size)} · ${Fmt.date(System.currentTimeMillis())}"

    Column(Modifier.fillMaxSize().background(C.Bg).imePadding()) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(C.BrandDeep)
                .statusBarsPadding()
                .padding(start = 4.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Text(
                if (isNew) "Build acca" else "Edit acca",
                color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Black,
                modifier = Modifier.weight(1f)
            )
        }

        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(40) },
                    label = { Text("Name") },
                    placeholder = { Text(fallbackName) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors()
                )
            }
            item {
                Column {
                    OutlinedTextField(
                        value = stakeText,
                        onValueChange = { v -> stakeText = v.filter { it.isDigit() || it == '.' }.take(8) },
                        label = { Text("Stake (£)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                        colors = fieldColors()
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(2, 5, 10, 20, 50).forEach { s ->
                            val sel = stake == s.toDouble()
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (sel) C.Brand else C.Surface)
                                    .border(1.dp, if (sel) C.Brand else C.Line, RoundedCornerShape(8.dp))
                                    .clickable { stakeText = s.toString() }
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Text("£$s", color = if (sel) Color.White else C.Text, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
            item {
                SectionLabel("SELECTIONS (${legs.size})", Modifier.padding(top = 6.dp))
            }
            items(legs, key = { it.id }) { l ->
                LegEditor(
                    l, prefs.odds,
                    onChange = { n ->
                        val i = legs.indexOfFirst { it.id == n.id }
                        if (i >= 0) legs[i] = n
                    },
                    onRemove = { legs.removeAll { it.id == l.id } }
                )
            }
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.5.dp, C.Brand, RoundedCornerShape(12.dp))
                        .clickable { picker = true }
                        .padding(vertical = 14.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, tint = C.Brand)
                    Spacer(Modifier.width(6.dp))
                    Text("ADD SELECTION", color = C.Brand, fontWeight = FontWeight.ExtraBold)
                }
            }
            item {
                Text(
                    "Odds are pre-filled from ESPN's lines — change them to match your slip. Type them as 6/4, 2.5 or +150.",
                    color = C.Mute, fontSize = 12.sp
                )
            }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .background(C.Surface)
                .navigationBarsPadding()
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    SectionLabel(Fmt.fold(legs.size).uppercase())
                    Text(
                        if (legs.isEmpty()) "—" else OddsFmt.format(odds, prefs.odds),
                        color = C.Text, fontSize = 18.sp, fontWeight = FontWeight.Black, style = Tnum
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    SectionLabel("POTENTIAL RETURNS")
                    Text(Fmt.money(stake * odds), color = C.Yellow, fontSize = 20.sp, fontWeight = FontWeight.Black, style = Tnum)
                }
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    repo.upsert(
                        initial.copy(
                            name = name.trim().ifBlank { fallbackName },
                            stake = stake,
                            legs = legs.toList()
                        )
                    )
                    scope.launch { repo.refresh(force = true) }
                    onClose()
                },
                enabled = legs.isNotEmpty() && stake > 0,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = C.Yellow,
                    contentColor = Color.Black,
                    disabledContainerColor = C.Raised,
                    disabledContentColor = C.Mute
                )
            ) {
                Text(if (isNew) "PLACE ON TRACKER" else "SAVE CHANGES", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
            }
        }
    }

    if (picker) {
        PickerSheet(
            repo = repo,
            startEvent = startEvent,
            fmt = prefs.odds,
            existing = legs.toList(),
            onDismiss = { picker = false },
            onAdd = { leg ->
                legs.add(leg)
                picker = false
            }
        )
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = C.Text,
    unfocusedTextColor = C.Text,
    focusedBorderColor = C.Brand,
    unfocusedBorderColor = C.Line,
    focusedLabelColor = C.Brand,
    unfocusedLabelColor = C.Sub,
    cursorColor = C.Yellow,
    focusedPlaceholderColor = C.Mute,
    unfocusedPlaceholderColor = C.Mute
)

@Composable
private fun LegEditor(l: Leg, fmt: OddsFormat, onChange: (Leg) -> Unit, onRemove: () -> Unit) {
    var oddsText by remember(l.id, fmt) { mutableStateOf(OddsFmt.format(l.odds, fmt)) }
    val parsedOk = OddsFmt.parse(oddsText) != null
    val editableLine = l.type == LegType.SPREAD || l.type == LegType.TOTAL ||
        (l.type == LegType.PLAYER && l.stat != PropStat.ANYTIME_TD)
    val hasOverUnder = l.type == LegType.TOTAL || (l.type == LegType.PLAYER && l.stat != PropStat.ANYTIME_TD)

    Panel {
        Column(Modifier.padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(Labels.title(l), color = C.Text, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${Labels.market(l)} · ${Labels.matchup(l)}", color = C.Sub, fontSize = 12.sp)
                }
                IconButton(onClick = onRemove) {
                    Icon(Icons.Filled.Close, contentDescription = "Remove", tint = C.Sub)
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.padding(end = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = oddsText,
                    onValueChange = { v ->
                        oddsText = v.take(9)
                        OddsFmt.parse(v)?.let { d -> onChange(l.copy(odds = d, result = null, resultNote = null)) }
                    },
                    label = { Text("Odds") },
                    singleLine = true,
                    isError = !parsedOk,
                    modifier = Modifier.width(110.dp),
                    colors = fieldColors()
                )
                if (editableLine) {
                    Stepper(
                        value = if (l.type == LegType.SPREAD) Fmt.signed(l.line ?: 0.0) else Fmt.line(l.line),
                        onMinus = { onChange(l.copy(line = (l.line ?: 0.0) - 0.5, result = null, resultNote = null)) },
                        onPlus = { onChange(l.copy(line = (l.line ?: 0.0) + 0.5, result = null, resultNote = null)) }
                    )
                }
            }
            if (hasOverUnder) {
                Spacer(Modifier.height(8.dp))
                Box(Modifier.padding(end = 10.dp)) {
                    Segmented(listOf("OVER", "UNDER"), if (l.side == Side.UNDER) 1 else 0) { i ->
                        onChange(l.copy(side = if (i == 1) Side.UNDER else Side.OVER, result = null, resultNote = null))
                    }
                }
            }
        }
    }
}

@Composable
private fun Stepper(value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(C.Raised),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(44.dp).clickable(onClick = onMinus), contentAlignment = Alignment.Center) {
            Text("−", color = C.Text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(64.dp)) {
            Text("LINE", color = C.Mute, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold)
            Text(value, color = C.Yellow, fontSize = 17.sp, fontWeight = FontWeight.Black, style = Tnum)
        }
        Box(Modifier.size(44.dp).clickable(onClick = onPlus), contentAlignment = Alignment.Center) {
            Text("+", color = C.Text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// ---------------------------------------------------------------- picker

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PickerSheet(
    repo: Repo,
    startEvent: String?,
    fmt: OddsFormat,
    existing: List<Leg>,
    onDismiss: () -> Unit,
    onAdd: (Leg) -> Unit
) {
    var eventId by remember { mutableStateOf(startEvent) }
    val board by repo.board.collectAsStateWithLifecycle()
    val week by repo.week.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = C.Bg
    ) {
        val b = board
        val selected = eventId?.let { id -> repo.gameFor(id) ?: b?.games?.firstOrNull { it.id == id } }
        if (selected != null) {
            MarketPicker(selected, fmt, repo, existing, onBack = { eventId = null }, onAdd = onAdd)
        } else if (b == null) {
            Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = C.Brand)
            }
        } else {
            val games = b.games.map { repo.gameFor(it.id) ?: it }.filter { it.phase != Phase.FINAL }
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text("Pick a game", color = C.Text, fontSize = 20.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(10.dp))
                WeekChips(b, week ?: b.current) { w -> scope.launch { repo.setWeek(w) } }
                Spacer(Modifier.height(10.dp))
            }
            LazyColumn(
                Modifier.heightIn(max = 560.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (games.isEmpty()) {
                    item { Text("No games left to bet on this week — try the next week.", color = C.Sub) }
                }
                items(games, key = { it.id }) { g ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(C.Surface)
                            .clickable { eventId = g.id }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TeamLogo(g.away.logo, 26.dp)
                        Spacer(Modifier.width(4.dp))
                        TeamLogo(g.home.logo, 26.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("${g.away.shortName} @ ${g.home.shortName}", color = C.Text, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                if (g.phase == Phase.LIVE) "LIVE · ${g.away.score}–${g.home.score} · ${g.liveClock}" else Fmt.kickoff(g.date),
                                color = if (g.phase == Phase.LIVE) C.Live else C.Sub,
                                fontSize = 12.sp
                            )
                        }
                        val count = existing.count { it.eventId == g.id }
                        if (count > 0) Chip("$count ADDED", C.Brand, Color.White)
                    }
                }
            }
        }
    }
}

private fun newLeg(
    g: Game,
    type: LegType,
    side: Side,
    line: Double?,
    odds: Double,
    player: RosterPlayer? = null,
    playerTeam: TeamInfo? = null,
    stat: PropStat? = null
) = Leg(
    id = UUID.randomUUID().toString(),
    eventId = g.id,
    eventDate = g.date,
    homeId = g.home.id,
    awayId = g.away.id,
    homeAbbr = g.home.abbr,
    awayAbbr = g.away.abbr,
    homeName = g.home.shortName,
    awayName = g.away.shortName,
    type = type,
    side = side,
    line = line,
    odds = odds,
    playerId = player?.id,
    playerName = player?.name,
    playerTeamId = playerTeam?.id,
    stat = stat
)

@Composable
private fun MarketPicker(
    g: Game,
    fmt: OddsFormat,
    repo: Repo,
    existing: List<Leg>,
    onBack: () -> Unit,
    onAdd: (Leg) -> Unit
) {
    val o = g.odds
    var propTeam by remember(g.id) { mutableStateOf<TeamInfo?>(null) }
    var roster by remember(g.id) { mutableStateOf<List<RosterPlayer>?>(null) }
    var player by remember(g.id) { mutableStateOf<RosterPlayer?>(null) }

    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(max = 640.dp)
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = C.Text)
            }
            Column {
                Text("${g.away.shortName} @ ${g.home.shortName}", color = C.Text, fontSize = 18.sp, fontWeight = FontWeight.Black)
                Text(
                    if (g.phase == Phase.LIVE) "LIVE · ${g.away.score}–${g.home.score} · ${g.liveClock}" else Fmt.kickoff(g.date),
                    color = if (g.phase == Phase.LIVE) C.Live else C.Sub, fontSize = 12.sp
                )
            }
        }

        if (player == null) {
            SectionLabel("MATCH RESULT")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MarketButton(g.away.shortName, OddsFmt.format(o?.awayMl, fmt), Modifier.weight(1f)) {
                    onAdd(newLeg(g, LegType.MONEYLINE, Side.AWAY, null, o?.awayMl ?: 1.9))
                }
                MarketButton(g.home.shortName, OddsFmt.format(o?.homeMl, fmt), Modifier.weight(1f)) {
                    onAdd(newLeg(g, LegType.MONEYLINE, Side.HOME, null, o?.homeMl ?: 1.9))
                }
            }

            val hs = o?.homeSpread ?: 0.0
            SectionLabel("HANDICAP")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MarketButton("${g.away.shortName} ${Fmt.signed(-hs)}", OddsFmt.format(o?.awaySpreadOdds ?: 1.91, fmt), Modifier.weight(1f)) {
                    onAdd(newLeg(g, LegType.SPREAD, Side.AWAY, -hs, o?.awaySpreadOdds ?: 1.91))
                }
                MarketButton("${g.home.shortName} ${Fmt.signed(hs)}", OddsFmt.format(o?.homeSpreadOdds ?: 1.91, fmt), Modifier.weight(1f)) {
                    onAdd(newLeg(g, LegType.SPREAD, Side.HOME, hs, o?.homeSpreadOdds ?: 1.91))
                }
            }

            val total = o?.total ?: 44.5
            SectionLabel("TOTAL POINTS")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MarketButton("Over ${Fmt.line(total)}", OddsFmt.format(o?.overOdds ?: 1.91, fmt), Modifier.weight(1f)) {
                    onAdd(newLeg(g, LegType.TOTAL, Side.OVER, total, o?.overOdds ?: 1.91))
                }
                MarketButton("Under ${Fmt.line(total)}", OddsFmt.format(o?.underOdds ?: 1.91, fmt), Modifier.weight(1f)) {
                    onAdd(newLeg(g, LegType.TOTAL, Side.UNDER, total, o?.underOdds ?: 1.91))
                }
            }

            SectionLabel("PLAYER PROPS")
            Segmented(
                listOf(g.away.shortName, g.home.shortName),
                when (propTeam?.id) { g.away.id -> 0; g.home.id -> 1; else -> -1 }
            ) { i ->
                propTeam = if (i == 0) g.away else g.home
                roster = null
            }
            val team = propTeam
            if (team != null) {
                LaunchedEffect(team.id) { roster = repo.roster(team.id) }
                val r = roster
                when {
                    r == null -> Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = C.Brand, modifier = Modifier.size(26.dp))
                    }
                    r.isEmpty() -> Text("Couldn't load the ${team.shortName} roster.", color = C.Sub)
                    else -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        r.forEach { p ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(C.Surface)
                                    .clickable { player = p }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(p.pos, color = C.Brand, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.width(32.dp))
                                Text(p.name, color = C.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                if (p.jersey.isNotBlank()) Text("#${p.jersey}", color = C.Mute, fontSize = 12.sp)
                            }
                        }
                    }
                }
            } else {
                Text("Pick a team to see its players.", color = C.Mute, fontSize = 12.sp)
            }
        } else {
            val p = player!!
            val team = propTeam
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${p.pos} · ${p.name}", color = C.Yellow, fontSize = 16.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                Text(
                    "Change",
                    color = C.Sub, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { player = null }.padding(8.dp)
                )
            }
            SectionLabel("CHOOSE A MARKET")
            val stats = when (p.pos) {
                "QB" -> listOf(PropStat.PASS_YDS, PropStat.PASS_TDS, PropStat.RUSH_YDS, PropStat.ANYTIME_TD)
                "RB", "FB" -> listOf(PropStat.ANYTIME_TD, PropStat.RUSH_YDS, PropStat.RUSH_REC_YDS, PropStat.RECEPTIONS, PropStat.REC_YDS)
                else -> listOf(PropStat.ANYTIME_TD, PropStat.REC_YDS, PropStat.RECEPTIONS, PropStat.RUSH_REC_YDS)
            }
            stats.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pair.forEach { s ->
                        MarketButton(
                            s.label,
                            if (s == PropStat.ANYTIME_TD) "Yes" else "O/U ${Fmt.line(s.defaultLine)}",
                            Modifier.weight(1f)
                        ) {
                            onAdd(newLeg(g, LegType.PLAYER, Side.OVER, s.defaultLine, s.defaultOdds, p, team, s))
                        }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            Text(
                "Lines start at a typical number — set the exact line and odds from your slip on the next screen.",
                color = C.Mute, fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun MarketButton(top: String, bottom: String, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(C.Surface)
            .border(1.dp, C.Line, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(top, color = C.Sub, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(3.dp))
        Text(bottom.uppercase(Locale.UK), color = C.Yellow, fontSize = 17.sp, fontWeight = FontWeight.Black, style = Tnum)
    }
}
