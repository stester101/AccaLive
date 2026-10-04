package uk.co.simon.accalive.logic

import uk.co.simon.accalive.data.Acca
import uk.co.simon.accalive.data.AccaState
import uk.co.simon.accalive.data.Fmt
import uk.co.simon.accalive.data.Game
import uk.co.simon.accalive.data.Leg
import uk.co.simon.accalive.data.LegState
import uk.co.simon.accalive.data.LegType
import uk.co.simon.accalive.data.Phase
import uk.co.simon.accalive.data.PlayerStats
import uk.co.simon.accalive.data.PropStat
import uk.co.simon.accalive.data.Side
import uk.co.simon.accalive.data.Stats
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

data class LegEval(
    val leg: Leg,
    val state: LegState,
    val prob: Double,
    val game: Game?,
    val value: Double?,
    val progress: Float?,
    val scoreLine: String,
    val statusLine: String,
    val locked: Boolean
) {
    val isLive: Boolean get() = leg.result == null && game?.phase == Phase.LIVE
    fun note(): String = listOf(scoreLine, statusLine).filter { it.isNotBlank() }.joinToString(" · ")
}

data class AccaEval(
    val acca: Acca,
    val legs: List<LegEval>,
    val state: AccaState,
    val odds: Double,
    val returns: Double,
    val prob: Double,
    val fair: Double
) {
    val anyLive: Boolean get() = legs.any { it.isLive }
    val landing: Int
        get() = legs.count {
            it.state == LegState.WINNING || it.state == LegState.WON || it.state == LegState.VOID
        }
}

object Labels {
    fun team(l: Leg): String = if (l.side == Side.HOME) l.homeName else l.awayName

    fun title(l: Leg): String = when (l.type) {
        LegType.MONEYLINE -> "${team(l)} to win"
        LegType.SPREAD -> "${team(l)} ${Fmt.signed(l.line ?: 0.0)}"
        LegType.TOTAL -> "${if (l.side == Side.UNDER) "Under" else "Over"} ${Fmt.line(l.line)} pts"
        LegType.PLAYER -> {
            val name = l.playerName ?: "Player"
            val stat = l.stat ?: PropStat.ANYTIME_TD
            if (stat == PropStat.ANYTIME_TD) "$name anytime TD"
            else "$name ${if (l.side == Side.UNDER) "under" else "over"} ${Fmt.line(l.line)} ${stat.unit}"
        }
    }

    fun market(l: Leg): String = when (l.type) {
        LegType.MONEYLINE -> "Match result"
        LegType.SPREAD -> "Handicap"
        LegType.TOTAL -> "Total points"
        LegType.PLAYER -> (l.stat ?: PropStat.ANYTIME_TD).label
    }

    fun matchup(l: Leg): String = "${l.awayAbbr} @ ${l.homeAbbr}"
}

object Evaluator {

    /** Fraction of regulation still to play: 1.0 before kick-off, 0.0 at full time. */
    fun remainingFrac(g: Game): Double = when (g.phase) {
        Phase.PRE -> 1.0
        Phase.FINAL -> 0.0
        Phase.LIVE -> {
            if (g.period >= 5) 0.03
            else {
                val quartersLeft = (4 - g.period).coerceAtLeast(0)
                ((quartersLeft * 900 + g.clockSec.coerceIn(0, 900)) / 3600.0).coerceIn(0.0, 1.0)
            }
        }
    }

    fun scoreLine(g: Game): String = when (g.phase) {
        Phase.PRE -> "${g.away.abbr} @ ${g.home.abbr} · ${Fmt.kickoff(g.date)}"
        Phase.LIVE -> "${g.away.abbr} ${g.away.score}–${g.home.score} ${g.home.abbr} · ${g.liveClock}"
        Phase.FINAL -> "${g.away.abbr} ${g.away.score}–${g.home.score} ${g.home.abbr} · ${if (g.period > 4) "FT (OT)" else "FT"}"
    }

    fun statValue(p: PlayerStats, s: PropStat): Double = when (s) {
        PropStat.ANYTIME_TD -> p.touchdowns.toDouble()
        PropStat.PASS_YDS -> p.passYds.toDouble()
        PropStat.PASS_TDS -> p.passTd.toDouble()
        PropStat.RUSH_YDS -> p.rushYds.toDouble()
        PropStat.REC_YDS -> p.recYds.toDouble()
        PropStat.RECEPTIONS -> p.rec.toDouble()
        PropStat.RUSH_REC_YDS -> (p.rushYds + p.recYds).toDouble()
    }

    private fun n(x: Double): String = if (x % 1.0 == 0.0) x.toInt().toString() else Fmt.line(x)

    fun leg(leg: Leg, game: Game?, players: Map<String, PlayerStats>?): LegEval {
        val frozen = leg.result
        if (frozen != null) {
            val p = if (frozen == LegState.LOST) 0.0 else 1.0
            return LegEval(leg, frozen, p, game, null, null, leg.resultNote ?: "", "", true)
        }
        if (game == null) {
            return LegEval(
                leg, LegState.PENDING, (0.95 / leg.odds).coerceIn(0.01, 0.99), null, null, null,
                "${leg.awayAbbr} @ ${leg.homeAbbr} · ${Fmt.kickoff(leg.eventDate)}", "", false
            )
        }

        val f = remainingFrac(game)
        val m = (game.home.score - game.away.score).toDouble()
        val total = (game.home.score + game.away.score).toDouble()
        val isFinal = game.phase == Phase.FINAL
        val isPre = game.phase == Phase.PRE
        val pHome = (game.homeWinProb ?: game.odds?.impliedHome() ?: 0.5).coerceIn(0.005, 0.995)

        var value: Double? = null
        var progress: Float? = null
        var locked = false
        var status = ""

        val result: Pair<LegState, Double> = when (leg.type) {
            LegType.MONEYLINE -> {
                val sm = if (leg.side == Side.HOME) m else -m
                val p = if (leg.side == Side.HOME) pHome else 1 - pHome
                if (!isPre && !isFinal) {
                    status = when {
                        sm > 0 -> "Leading by ${n(sm)}"
                        sm < 0 -> "Trailing by ${n(-sm)}"
                        else -> "All square"
                    }
                }
                when {
                    isFinal -> when {
                        sm > 0 -> LegState.WON to 1.0
                        sm < 0 -> LegState.LOST to 0.0
                        else -> LegState.VOID to 1.0
                    }
                    isPre -> LegState.PENDING to p
                    sm > 0 -> LegState.WINNING to p
                    sm < 0 -> LegState.LOSING to p
                    else -> LegState.LEVEL to p
                }
            }

            LegType.SPREAD -> {
                val line = leg.line ?: 0.0
                val sm = if (leg.side == Side.HOME) m else -m
                val adj = sm + line
                if (isFinal) {
                    when {
                        adj > 0 -> LegState.WON to 1.0
                        adj < 0 -> LegState.LOST to 0.0
                        else -> LegState.VOID to 1.0
                    }
                } else {
                    val sd = max(0.5, Stats.MARGIN_SD * sqrt(f))
                    // Expected home margin over the rest of the game, calibrated to ESPN's live win %.
                    val homeRemMean = Stats.invPhi(pHome) * sd - m
                    val sideRemMean = if (leg.side == Side.HOME) homeRemMean else -homeRemMean
                    val p = Stats.phi((sm + sideRemMean + line) / sd)
                    if (!isPre) {
                        status = when {
                            adj > 0 -> "Covering by ${n(adj)}"
                            adj < 0 -> "Short by ${n(-adj)}"
                            else -> "Level on the handicap"
                        }
                    }
                    when {
                        isPre -> LegState.PENDING to p
                        adj > 0 -> LegState.WINNING to p
                        adj < 0 -> LegState.LOSING to p
                        else -> LegState.LEVEL to p
                    }
                }
            }

            LegType.TOTAL -> {
                val line = leg.line ?: 44.5
                val over = leg.side != Side.UNDER
                value = total
                progress = (total / line).toFloat().coerceIn(0f, 1f)
                if (!isPre) {
                    status = if (total > line) "${n(total)} pts — line passed"
                    else if (over) "${n(total)} pts · needs ${n(ceil(line - total))} more"
                    else "${n(total)} pts · ${n(ceil(line - total))} to spare"
                }
                when {
                    isFinal -> {
                        val st = when {
                            total == line -> LegState.VOID
                            (total > line) == over -> LegState.WON
                            else -> LegState.LOST
                        }
                        st to (if (st == LegState.LOST) 0.0 else 1.0)
                    }
                    total > line -> {
                        locked = true
                        if (over) LegState.WON to 1.0 else LegState.LOST to 0.0
                    }
                    else -> {
                        val preTotal = game.odds?.total ?: line
                        val sd = max(0.5, Stats.TOTAL_SD * sqrt(f))
                        val mean = total + preTotal * f
                        val pOver = Stats.phi((mean - line) / sd)
                        val p = if (over) pOver else 1 - pOver
                        val st = when {
                            isPre -> LegState.PENDING
                            p >= 0.5 -> LegState.WINNING
                            else -> LegState.LOSING
                        }
                        st to p
                    }
                }
            }

            LegType.PLAYER -> {
                val stat = leg.stat ?: PropStat.ANYTIME_TD
                val ps = leg.playerId?.let { players?.get(it) }
                val v = ps?.let { statValue(it, stat) } ?: 0.0
                value = v
                val line = if (stat == PropStat.ANYTIME_TD) 0.5 else (leg.line ?: stat.defaultLine)
                val over = leg.side != Side.UNDER
                if (stat != PropStat.ANYTIME_TD) progress = (v / line).toFloat().coerceIn(0f, 1f)
                if (!isPre) {
                    status = if (stat == PropStat.ANYTIME_TD) {
                        if (v >= 1) "Scored ✓${if (v >= 2) " (${n(v)})" else ""}" else "No TD yet"
                    } else if (v > line) {
                        "${n(v)} ${stat.unit} — line passed"
                    } else {
                        "${n(v)} ${stat.unit} · needs ${n(ceil(line - v))} more"
                    }
                }
                when {
                    v > line -> {
                        locked = true
                        if (over) LegState.WON to 1.0 else LegState.LOST to 0.0
                    }
                    isFinal -> when {
                        v == line -> LegState.VOID to 1.0
                        over -> LegState.LOST to 0.0
                        else -> LegState.WON to 1.0
                    }
                    else -> {
                        val p0 = (0.95 / leg.odds).coerceIn(0.02, 0.95)
                        val pOver = if (stat == PropStat.ANYTIME_TD) {
                            1 - (1 - p0).pow(f)
                        } else {
                            val sd = max(0.6, stat.relSd * line * sqrt(f))
                            Stats.phi((v + line * f - line) / sd)
                        }
                        val p = if (over) pOver else 1 - pOver
                        val st = when {
                            isPre -> LegState.PENDING
                            stat == PropStat.ANYTIME_TD -> LegState.LEVEL
                            p >= 0.5 -> LegState.WINNING
                            else -> LegState.LOSING
                        }
                        st to p
                    }
                }
            }
        }

        return LegEval(
            leg = leg,
            state = result.first,
            prob = result.second.coerceIn(0.0, 1.0),
            game = game,
            value = value,
            progress = progress,
            scoreLine = scoreLine(game),
            statusLine = status,
            locked = locked
        )
    }

    fun acca(
        a: Acca,
        gameFor: (String) -> Game?,
        playersFor: (String) -> Map<String, PlayerStats>?
    ): AccaEval {
        val legs = a.legs.map { leg(it, gameFor(it.eventId), playersFor(it.eventId)) }
        val active = legs.filter { it.state != LegState.VOID }
        val odds = active.fold(1.0) { acc, l -> acc * l.leg.odds }
        val returns = a.stake * odds
        val state = when {
            a.settled && a.outcome != null -> a.outcome
            legs.isEmpty() -> AccaState.UPCOMING
            legs.any { it.state == LegState.LOST } -> AccaState.LOST
            legs.all { it.state == LegState.WON || it.state == LegState.VOID } -> AccaState.WON
            legs.any { it.isLive || it.state.settled } -> AccaState.LIVE
            else -> AccaState.UPCOMING
        }
        val prob = when (state) {
            AccaState.WON -> 1.0
            AccaState.LOST -> 0.0
            else -> legs.fold(1.0) { acc, l ->
                acc * when (l.state) {
                    LegState.WON, LegState.VOID -> 1.0
                    LegState.LOST -> 0.0
                    else -> l.prob
                }
            }
        }
        val fair = if (state == AccaState.LOST) 0.0 else returns * prob
        return AccaEval(a, legs, state, odds, returns, prob, fair)
    }

    /** Leg win chance expressed as fair decimal odds. */
    fun fairOdds(p: Double): Double? = if (p <= 0.005) null else 1.0 / p

    @Suppress("unused")
    private fun near(a: Double, b: Double) = abs(a - b) < 1e-9
}
