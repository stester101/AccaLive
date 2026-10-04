package uk.co.simon.accalive.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uk.co.simon.accalive.Repo
import uk.co.simon.accalive.data.AccaState
import uk.co.simon.accalive.data.Fmt
import uk.co.simon.accalive.data.OddsFmt
import uk.co.simon.accalive.data.OddsFormat
import uk.co.simon.accalive.logic.AccaEval
import uk.co.simon.accalive.logic.Labels

@Composable
fun ResultsTab(repo: Repo, settled: List<AccaEval>, fmt: OddsFormat) {
    var confirmClear by remember { mutableStateOf(false) }
    val sorted = settled.sortedByDescending { it.acca.settledAt ?: it.acca.createdAt }
    val staked = settled.sumOf { it.acca.stake }
    val returned = settled.sumOf { it.acca.returns }
    val pl = returned - staked
    val won = settled.count { it.state == AccaState.WON }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Panel(border = if (pl >= 0 && settled.isNotEmpty()) C.Win.copy(alpha = 0.5f) else C.Line) {
                Column(Modifier.padding(16.dp)) {
                    SectionLabel("PROFIT / LOSS")
                    Text(
                        if (settled.isEmpty()) "£0.00" else Fmt.signedMoney(pl),
                        color = when {
                            settled.isEmpty() -> C.Text
                            pl >= 0 -> C.Win
                            else -> C.Lose
                        },
                        fontSize = 34.sp, fontWeight = FontWeight.Black, style = Tnum
                    )
                    Spacer(Modifier.height(10.dp))
                    Row {
                        Metric("STAKED", Fmt.money(staked), Modifier.weight(1f))
                        Metric("RETURNED", Fmt.money(returned), Modifier.weight(1f))
                        Metric(
                            "WON",
                            if (settled.isEmpty()) "—" else "$won / ${settled.size}",
                            Modifier.weight(0.8f),
                            end = true
                        )
                    }
                }
            }
        }
        if (sorted.isEmpty()) {
            item {
                Text(
                    "Settled accas land here automatically once their games finish.",
                    color = C.Sub, fontSize = 14.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(24.dp)
                )
            }
        }
        items(sorted, key = { it.acca.id }) { e ->
            ResultCard(e, fmt, onDelete = { repo.delete(e.acca.id) })
        }
        if (sorted.isNotEmpty()) {
            item {
                TextButton(onClick = { confirmClear = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Clear history", color = C.Sub)
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            containerColor = C.Surface,
            title = { Text("Clear all results?", color = C.Text) },
            text = { Text("This removes every settled acca and resets your profit/loss.", color = C.Sub) },
            confirmButton = {
                TextButton(onClick = { repo.clearHistory(); confirmClear = false }) {
                    Text("Clear", color = C.Lose, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel", color = C.Sub) } }
        )
    }
}

@Composable
private fun ResultCard(e: AccaEval, fmt: OddsFormat, onDelete: () -> Unit) {
    var open by rememberSaveable(e.acca.id) { mutableStateOf(false) }
    val won = e.state == AccaState.WON
    Panel(border = if (won) C.Win.copy(alpha = 0.6f) else C.Line) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { open = !open }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(e.acca.name, color = C.Text, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${Fmt.fold(e.acca.legs.size)} @ ${OddsFmt.format(e.odds, fmt)} · ${Fmt.date(e.acca.settledAt ?: e.acca.createdAt)}",
                        color = C.Sub, fontSize = 12.sp
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    AccaStateChip(e)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (won) "+${Fmt.money(e.acca.returns - e.acca.stake)}" else "−${Fmt.money(e.acca.stake)}",
                        color = if (won) C.Win else C.Lose,
                        fontSize = 14.sp, fontWeight = FontWeight.Black, style = Tnum
                    )
                }
            }
            if (open) {
                HorizontalDivider(color = C.Line)
                e.legs.forEach { l ->
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        StatusDot(l.state, 20.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(Labels.title(l.leg), color = C.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val note = l.leg.resultNote ?: l.note()
                            if (note.isNotBlank()) Text(note, color = C.Sub, fontSize = 12.sp, maxLines = 2)
                        }
                        Text(OddsFmt.format(l.leg.odds, fmt), color = C.Sub, fontSize = 12.sp, fontWeight = FontWeight.Bold, style = Tnum)
                    }
                }
                Row(Modifier.fillMaxWidth().padding(end = 6.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDelete) { Text("Delete", color = C.Lose) }
                }
            }
        }
    }
}
