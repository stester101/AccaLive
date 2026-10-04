package uk.co.simon.accalive.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import uk.co.simon.accalive.Repo
import uk.co.simon.accalive.data.Acca
import uk.co.simon.accalive.data.AccaState
import uk.co.simon.accalive.data.Fmt
import uk.co.simon.accalive.data.OddsFormat
import uk.co.simon.accalive.live.Notifier
import uk.co.simon.accalive.logic.AccaEval
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot(repo: Repo, onTrackingChanged: () -> Unit) {
    val cfg = LocalConfiguration.current
    val ctx = LocalContext.current
    val evals by repo.evals.collectAsStateWithLifecycle()
    val prefs by repo.settings.state.collectAsStateWithLifecycle()
    val updatedAt by repo.updatedAt.collectAsStateWithLifecycle()
    val refreshing by repo.refreshing.collectAsStateWithLifecycle()
    val error by repo.error.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        onTrackingChanged()
    }
    fun askNotifications() {
        if (Build.VERSION.SDK_INT >= 33 && !Notifier.canPost(ctx)) {
            permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    LaunchedEffect(Unit) {
        if (prefs.liveTracking || prefs.alertResults) askNotifications()
    }

    // Folded: the Flip's cover screen gets its own glanceable layout.
    if (cfg.screenHeightDp < 500) {
        CoverScreen(evals, prefs.odds)
        return
    }

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var draft by remember { mutableStateOf<Acca?>(null) }
    var draftEvent by remember { mutableStateOf<String?>(null) }
    var sheetEvent by remember { mutableStateOf<String?>(null) }
    var settingsOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<Acca?>(null) }

    fun newAcca(eventId: String? = null) {
        draft = Acca(
            id = UUID.randomUUID().toString(),
            name = "",
            stake = 10.0,
            legs = emptyList(),
            createdAt = System.currentTimeMillis()
        )
        draftEvent = eventId
    }

    val current = draft
    if (current != null) {
        BuilderScreen(
            repo = repo,
            initial = current,
            startEvent = draftEvent,
            onClose = {
                draft = null
                draftEvent = null
                tab = 0
                onTrackingChanged()
            }
        )
        return
    }

    val open = evals.filter { !it.acca.settled }
    val settled = evals.filter { it.acca.settled }

    Box(Modifier.fillMaxSize().background(C.Bg)) {
        Column(Modifier.fillMaxSize()) {
            Header(
                open = open,
                updatedAt = updatedAt,
                refreshing = refreshing,
                error = error,
                onRefresh = { scope.launch { repo.refresh(force = true) } },
                onSettings = { settingsOpen = true }
            )
            Tabs(tab, liveCount = open.count { it.anyLive }, onSelect = { tab = it })
            Box(Modifier.weight(1f)) {
                when (tab) {
                    0 -> AccasTab(
                        evals = open,
                        fmt = prefs.odds,
                        onLeg = { sheetEvent = it },
                        onEdit = { draft = it; draftEvent = null },
                        onDelete = { confirmDelete = it },
                        onNew = { newAcca() }
                    )
                    1 -> GamesTab(repo = repo, evals = open, fmt = prefs.odds, onGame = { sheetEvent = it })
                    else -> ResultsTab(repo = repo, settled = settled, fmt = prefs.odds)
                }
            }
        }
        if (tab != 2) {
            ExtendedFloatingActionButton(
                onClick = { newAcca() },
                containerColor = C.Yellow,
                contentColor = Color.Black,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(16.dp),
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("BUILD ACCA", fontWeight = FontWeight.ExtraBold, letterSpacing = 0.5.sp) }
            )
        }
    }

    sheetEvent?.let { id ->
        GameSheet(
            repo = repo,
            eventId = id,
            fmt = prefs.odds,
            onDismiss = { sheetEvent = null },
            onBuild = {
                sheetEvent = null
                newAcca(id)
            }
        )
    }

    if (settingsOpen) {
        SettingsSheet(
            repo = repo,
            onDismiss = { settingsOpen = false },
            onTrackingChanged = {
                askNotifications()
                onTrackingChanged()
            }
        )
    }

    confirmDelete?.let { a ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            containerColor = C.Surface,
            title = { Text("Delete ${a.name}?", color = C.Text) },
            text = { Text("This removes the acca and its tracking.", color = C.Sub) },
            confirmButton = {
                TextButton(onClick = {
                    repo.delete(a.id)
                    confirmDelete = null
                    onTrackingChanged()
                }) { Text("Delete", color = C.Lose, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) { Text("Cancel", color = C.Sub) }
            }
        )
    }
}

@Composable
private fun Header(
    open: List<AccaEval>,
    updatedAt: Long,
    refreshing: Boolean,
    error: String?,
    onRefresh: () -> Unit,
    onSettings: () -> Unit
) {
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(1000)
            value = System.currentTimeMillis()
        }
    }
    Column(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(C.BrandDeep, C.BrandDark)))
            .statusBarsPadding()
            .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("ACCA", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic)
            Text("LIVE", color = C.Yellow, fontSize = 26.sp, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic)
            if (open.any { it.anyLive }) {
                Spacer(Modifier.width(8.dp))
                LiveDot(9.dp)
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onRefresh) {
                if (refreshing) {
                    CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Filled.Refresh, contentDescription = "Refresh", tint = Color.White)
                }
            }
            IconButton(onClick = onSettings) {
                Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = Color.White)
            }
        }
        Spacer(Modifier.height(6.dp))
        val alive = open.filter { it.state != AccaState.LOST }
        Row(Modifier.padding(end = 12.dp)) {
            HeaderStat("OPEN", "${open.size}", Modifier.weight(0.6f))
            HeaderStat("STAKED", Fmt.money(open.sumOf { it.acca.stake }), Modifier.weight(1f))
            HeaderStat("POTENTIAL", Fmt.money(alive.sumOf { it.returns }), Modifier.weight(1.2f), C.Yellow)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            error ?: Fmt.ago(updatedAt, now),
            color = if (error != null) C.Yellow else Color.White.copy(alpha = 0.6f),
            fontSize = 11.sp
        )
    }
}

@Composable
private fun HeaderStat(label: String, value: String, modifier: Modifier, color: Color = Color.White) {
    Column(modifier) {
        Text(label, color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Text(value, color = color, fontSize = 18.sp, fontWeight = FontWeight.Black, style = Tnum, maxLines = 1)
    }
}

@Composable
private fun Tabs(selected: Int, liveCount: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().background(C.BrandDark)) {
        listOf("MY ACCAS", "GAMES", "RESULTS").forEachIndexed { i, label ->
            Column(
                Modifier
                    .weight(1f)
                    .clickable { onSelect(i) }
                    .padding(top = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        label,
                        color = if (selected == i) Color.White else Color.White.copy(alpha = 0.55f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.8.sp
                    )
                    if (i == 0 && liveCount > 0) {
                        Spacer(Modifier.width(5.dp))
                        LiveDot(6.dp)
                    }
                }
                Spacer(Modifier.height(9.dp))
                Box(
                    Modifier
                        .fillMaxWidth(0.6f)
                        .height(3.dp)
                        .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                        .background(if (selected == i) C.Yellow else Color.Transparent)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSheet(repo: Repo, onDismiss: () -> Unit, onTrackingChanged: () -> Unit) {
    val prefs by repo.settings.state.collectAsStateWithLifecycle()
    val cfg = LocalConfiguration.current
    val ctx = LocalContext.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = C.Bg
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Text("Settings", color = C.Text, fontSize = 22.sp, fontWeight = FontWeight.Black)

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("ODDS FORMAT")
                Segmented(OddsFormat.entries.map { it.label }, prefs.odds.ordinal) { i ->
                    repo.settings.update { it.copy(odds = OddsFormat.entries[i]) }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("REFRESH WHILE OPEN")
                val opts = listOf(15, 20, 30, 60)
                Segmented(opts.map { "${it}s" }, opts.indexOf(prefs.refreshSecs).coerceAtLeast(0)) { i ->
                    repo.settings.update { it.copy(refreshSecs = opts[i]) }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SectionLabel("LIVE TRACKING & ALERTS")
                ToggleRow(
                    "Track in the background",
                    "Keeps scores updating with a live summary notification while games are on",
                    prefs.liveTracking
                ) { v ->
                    repo.settings.update { it.copy(liveTracking = v) }
                    onTrackingChanged()
                }
                ToggleRow("Lead changes", "When a leg flips between winning and losing", prefs.alertLeads) { v ->
                    repo.settings.update { it.copy(alertLeads = v) }
                    if (v) onTrackingChanged()
                }
                ToggleRow("Results", "Legs settling and accas landing or losing", prefs.alertResults) { v ->
                    repo.settings.update { it.copy(alertResults = v) }
                    if (v) onTrackingChanged()
                }
                TextButton(onClick = {
                    Notifier.post(ctx, "📈 Bills -3.5 — now winning", "MIA 14–21 BUF · Q3 4:12\nSunday treble · 41% chance · £87.40")
                }) { Text("Send a test alert", color = C.Yellow, fontWeight = FontWeight.Bold) }
                if (!Notifier.canPost(ctx)) {
                    Text(
                        "Notifications are off for Acca Live — allow them in Android settings to get alerts.",
                        color = C.Level, fontSize = 12.sp
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SectionLabel("ABOUT")
                Text(
                    "Scores, win probabilities and lines come from ESPN's public API (lines via ESPN's sportsbook partner). " +
                        "Leg and acca chances are estimates built on ESPN's live win probability. \"Fair value\" is that chance × your returns — not a bookmaker's cash-out offer.",
                    color = C.Sub, fontSize = 12.sp, lineHeight = 17.sp
                )
                Text(
                    "Screen ${cfg.screenWidthDp}×${cfg.screenHeightDp} dp · cover layout below 500 dp tall",
                    color = C.Mute, fontSize = 11.sp
                )
                Text("Please gamble responsibly · begambleaware.org", color = C.Mute, fontSize = 11.sp)
            }
        }
    }
}

@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(C.Surface)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        options.forEachIndexed { i, s ->
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (i == selected) C.Brand else Color.Transparent)
                    .clickable { onSelect(i) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    s,
                    color = if (i == selected) Color.White else C.Sub,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun ToggleRow(title: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = C.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(sub, color = C.Sub, fontSize = 12.sp)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.Black,
                checkedTrackColor = C.Yellow,
                uncheckedThumbColor = C.Sub,
                uncheckedTrackColor = C.Raised,
                uncheckedBorderColor = C.Line
            )
        )
    }
}
