package uk.co.simon.accalive

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import uk.co.simon.accalive.data.Acca
import uk.co.simon.accalive.data.AccaState
import uk.co.simon.accalive.data.AccaStore
import uk.co.simon.accalive.data.Board
import uk.co.simon.accalive.data.Espn
import uk.co.simon.accalive.data.Game
import uk.co.simon.accalive.data.GameDetail
import uk.co.simon.accalive.data.Phase
import uk.co.simon.accalive.data.PlayerStats
import uk.co.simon.accalive.data.RosterPlayer
import uk.co.simon.accalive.data.Settings
import uk.co.simon.accalive.data.WeekRef
import uk.co.simon.accalive.live.Notifier
import uk.co.simon.accalive.logic.AccaEval
import uk.co.simon.accalive.logic.Evaluator

/** Single source of truth shared by the UI, the live-tracking service and the background worker. */
class Repo(private val ctx: Context) {
    private val store = AccaStore(ctx)
    val settings = Settings(ctx)

    private val _accas = MutableStateFlow(store.load())
    val accas: StateFlow<List<Acca>> = _accas.asStateFlow()

    private val _board = MutableStateFlow<Board?>(null)
    val board: StateFlow<Board?> = _board.asStateFlow()

    private val _details = MutableStateFlow<Map<String, GameDetail>>(emptyMap())
    val details: StateFlow<Map<String, GameDetail>> = _details.asStateFlow()

    private val _evals = MutableStateFlow<List<AccaEval>>(emptyList())
    val evals: StateFlow<List<AccaEval>> = _evals.asStateFlow()

    /** Week shown on the Games tab and in the picker; null = ESPN's current week. */
    val week = MutableStateFlow<WeekRef?>(null)
    val updatedAt = MutableStateFlow(0L)
    val error = MutableStateFlow<String?>(null)
    val refreshing = MutableStateFlow(false)

    /** Game open in the detail sheet, fetched in full even if it's not in an acca. */
    val focusEvent = MutableStateFlow<String?>(null)

    private val netLock = Mutex()
    private val lock = Any()
    private var lastRefresh = 0L
    private val rosters = HashMap<String, List<RosterPlayer>>()

    init {
        evaluate(alerts = false)
    }

    fun hasActive(): Boolean = _accas.value.any { !it.settled }

    suspend fun refresh(force: Boolean = false) {
        netLock.withLock {
            val now = System.currentTimeMillis()
            if (!force && now - lastRefresh < 8_000) return
            refreshing.value = true
            try {
                withContext(Dispatchers.IO) {
                    val boardResult = runCatching { Espn.scoreboard(week.value) }
                    boardResult.onSuccess { _board.value = it }

                    val cur = _details.value
                    val ids = trackedIds()
                    val toFetch = ids.filter { id ->
                        val d = cur[id]
                        d == null || d.game?.phase != Phase.FINAL || now - d.fetchedAt > 30 * 60_000
                    }
                    val fetched = coroutineScope {
                        toFetch.map { id -> async { id to runCatching { Espn.summary(id) }.getOrNull() } }.awaitAll()
                    }
                    val next = cur.toMutableMap()
                    fetched.forEach { (id, d) -> if (d != null) next[id] = d }
                    _details.value = next

                    val allFailed = boardResult.isFailure && (fetched.isEmpty() || fetched.all { it.second == null })
                    error.value = if (allFailed) "Can't reach ESPN — check your connection" else null
                }
                lastRefresh = now
                if (error.value == null) updatedAt.value = System.currentTimeMillis()
                evaluate(alerts = true)
            } finally {
                refreshing.value = false
            }
        }
    }

    private fun trackedIds(): List<String> {
        val ids = LinkedHashSet<String>()
        _accas.value.filter { !it.settled }.forEach { a ->
            a.legs.filter { it.result == null }.forEach { ids += it.eventId }
        }
        focusEvent.value?.let { ids += it }
        return ids.toList()
    }

    fun gameFor(id: String): Game? {
        val d = _details.value[id]?.game
        val b = _board.value?.games?.firstOrNull { it.id == id }
        if (d == null) return b
        if (b == null) return d
        val base = if (progress(b) > progress(d)) b.copy(homeWinProb = b.homeWinProb ?: d.homeWinProb) else d
        return base.copy(
            possession = base.possession ?: b.possession,
            downDistance = base.downDistance ?: b.downDistance,
            redZone = base.redZone || b.redZone,
            odds = b.odds ?: d.odds,
            lastPlay = base.lastPlay ?: b.lastPlay,
            broadcast = b.broadcast ?: d.broadcast
        )
    }

    private fun progress(g: Game): Long =
        g.phase.ordinal * 1_000_000L + g.period * 10_000L + (900 - g.clockSec) * 10L + (g.home.score + g.away.score)

    fun playersFor(id: String): Map<String, PlayerStats>? = _details.value[id]?.players

    private fun evaluate(alerts: Boolean) {
        synchronized(lock) {
            var changed = false
            val first = _accas.value.map { Evaluator.acca(it, ::gameFor, ::playersFor) }
            val updated = first.map { ev ->
                var a = ev.acca
                if (a.settled) return@map a
                val legs = ev.legs.map { le ->
                    val g = le.game
                    if (le.leg.result == null && le.state.settled && (g?.phase == Phase.FINAL || le.locked)) {
                        changed = true
                        le.leg.copy(result = le.state, resultNote = le.note())
                    } else le.leg
                }
                a = a.copy(legs = legs)
                if (ev.state == AccaState.WON || ev.state == AccaState.LOST) {
                    changed = true
                    a = a.copy(
                        settled = true,
                        outcome = ev.state,
                        returns = if (ev.state == AccaState.WON) ev.returns else 0.0,
                        settledAt = System.currentTimeMillis()
                    )
                }
                a
            }
            if (changed) {
                _accas.value = updated
                store.save(updated)
            }
            val evals = updated.map { Evaluator.acca(it, ::gameFor, ::playersFor) }
            _evals.value = evals
            if (alerts) Notifier.diff(ctx, store, evals, settings.state.value)
            else if (store.loadStates() == null) Notifier.snapshot(store, evals)
        }
    }

    // ---------------------------------------------------------------- mutations

    fun upsert(a: Acca) {
        synchronized(lock) {
            val l = _accas.value.toMutableList()
            val i = l.indexOfFirst { it.id == a.id }
            if (i >= 0) l[i] = a else l.add(0, a)
            _accas.value = l
            store.save(l)
        }
        evaluate(alerts = false)
    }

    fun delete(id: String) {
        synchronized(lock) {
            val l = _accas.value.filter { it.id != id }
            _accas.value = l
            store.save(l)
        }
        evaluate(alerts = false)
    }

    fun clearHistory() {
        synchronized(lock) {
            val l = _accas.value.filter { !it.settled }
            _accas.value = l
            store.save(l)
        }
        evaluate(alerts = false)
    }

    suspend fun setWeek(w: WeekRef?) {
        week.value = w
        refresh(force = true)
    }

    suspend fun roster(teamId: String): List<RosterPlayer> {
        rosters[teamId]?.let { return it }
        val r = withContext(Dispatchers.IO) { runCatching { Espn.roster(teamId) }.getOrDefault(emptyList()) }
        if (r.isNotEmpty()) rosters[teamId] = r
        return r
    }
}
