package uk.co.simon.accalive.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uk.co.simon.accalive.data.Acca
import uk.co.simon.accalive.data.AccaState
import uk.co.simon.accalive.data.Fmt
import uk.co.simon.accalive.data.OddsFmt
import uk.co.simon.accalive.data.OddsFormat
import uk.co.simon.accalive.logic.AccaEval
import uk.co.simon.accalive.logic.LegEval
import uk.co.simon.accalive.logic.Labels

@Composable
fun AccasTab(
    evals: List<AccaEval>,
    fmt: OddsFormat,
    onLeg: (String) -> Unit,
    onEdit: (Acca) -> Unit,
    onDelete: (Acca) -> Unit,
    onNew: () -> Unit
) {
    if (evals.isEmpty()) {
        EmptyState(onNew)
        return
    }
    // Live accas first, then upcoming by first kick-off.
    val sorted = evals.sortedWith(
        compareByDescending<AccaEval> { it.anyLive }
            .thenBy { e -> e.legs.mapNotNull { it.game?.date }.minOrNull() ?: Long.MAX_VALUE }
    )
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(sorted, key = { it.acca.id }) { e ->
            AccaCard(e, fmt, onLeg = onLeg, onEdit = { onEdit(e.acca) }, onDelete = { onDelete(e.acca) })
        }
    }
}

@Composable
private fun EmptyState(onNew: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("🏈", fontSize = 48.sp)
        Spacer(Modifier.height(12.dp))
        Text("No accas yet", color = C.Text, fontSize = 22.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(6.dp))
        Text(
            "Add the legs from your slip and watch them live — scores, win % and how your acca's tracking.",
            color = C.Sub, fontSize = 14.sp, textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onNew,
            colors = ButtonDefaults.buttonColors(containerColor = C.Yellow, contentColor = Color.Black),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("BUILD YOUR FIRST ACCA", fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
fun AccaCard(
    e: AccaEval,
    fmt: OddsFormat,
    onLeg: (String) -> Unit,
    onEdit: (() -> Unit)?,
    onDelete: (() -> Unit)?
) {
    var menu by remember { mutableStateOf(false) }
    val border = when {
        e.state == AccaState.WON -> C.Win
        e.state == AccaState.LOST -> C.Lose.copy(alpha = 0.5f)
        e.anyLive -> C.Brand.copy(alpha = 0.7f)
        else -> C.Line
    }
    Panel(border = border) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(C.Raised)
                    .padding(start = 14.dp, end = 2.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        e.acca.name,
                        color = C.Text, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "${Fmt.fold(e.acca.legs.size)} · ${e.landing}/${e.acca.legs.size} landing",
                        color = C.Sub, fontSize = 12.sp
                    )
                }
                AccaStateChip(e)
                if (onEdit != null || onDelete != null) {
                    Box {
                        IconButton(onClick = { menu = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "More", tint = C.Sub)
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            if (onEdit != null) DropdownMenuItem(text = { Text("Edit") }, onClick = { menu = false; onEdit() })
                            if (onDelete != null) DropdownMenuItem(text = { Text("Delete", color = C.Lose) }, onClick = { menu = false; onDelete() })
                        }
                    }
                } else {
                    Spacer(Modifier.width(12.dp))
                }
            }

            Row(Modifier.padding(start = 14.dp, end = 14.dp, top = 12.dp)) {
                Metric("STAKE", Fmt.money(e.acca.stake), Modifier.weight(1f))
                Metric("ODDS", OddsFmt.format(e.odds, fmt), Modifier.weight(1f))
                Metric(
                    if (e.state == AccaState.WON) "RETURNED" else "RETURNS",
                    Fmt.money(if (e.state == AccaState.LOST) 0.0 else e.returns),
                    Modifier.weight(1.3f),
                    color = if (e.state == AccaState.LOST) C.Mute else C.Yellow,
                    end = true
                )
            }

            if (e.state != AccaState.WON && e.state != AccaState.LOST) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SectionLabel("ACCA CHANCE")
                        Spacer(Modifier.weight(1f))
                        Text(Fmt.pct(e.prob), color = probColor(e.prob), fontSize = 14.sp, fontWeight = FontWeight.Black, style = Tnum)
                        Text("  ·  fair value ${Fmt.money(e.fair)}", color = C.Sub, fontSize = 12.sp, style = Tnum)
                    }
                    Spacer(Modifier.height(6.dp))
                    ChanceBar(e.prob, probColor(e.prob))
                }
            } else {
                Spacer(Modifier.height(12.dp))
            }

            HorizontalDivider(color = C.Line)
            e.legs.forEachIndexed { i, l ->
                LegRow(l, fmt) { onLeg(l.leg.eventId) }
                if (i < e.legs.lastIndex) HorizontalDivider(Modifier.padding(start = 48.dp), color = C.Line.copy(alpha = 0.6f))
            }
        }
    }
}

@Composable
fun LegRow(l: LegEval, fmt: OddsFormat, onClick: () -> Unit) {
    val live = l.isLive
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatusDot(l.state)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                Labels.title(l.leg),
                color = C.Text, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (live) {
                    LiveDot(6.dp)
                    Spacer(Modifier.width(5.dp))
                }
                Text(
                    l.scoreLine,
                    color = if (live) C.Text else C.Sub,
                    fontSize = 12.sp, style = Tnum, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            if (l.statusLine.isNotBlank()) {
                Text(l.statusLine, color = stateColor(l.state), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
            val prog = l.progress
            if (prog != null && live) {
                Spacer(Modifier.height(5.dp))
                ChanceBar(prog.toDouble(), stateColor(l.state), height = 3.dp)
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(horizontalAlignment = Alignment.End) {
            OddsPill(OddsFmt.format(l.leg.odds, fmt))
            Spacer(Modifier.height(4.dp))
            Text(
                if (l.state.settled) l.state.label.uppercase() else Fmt.pct(l.prob),
                color = if (l.state.settled) stateColor(l.state) else probColor(l.prob),
                fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, style = Tnum
            )
        }
    }
}
