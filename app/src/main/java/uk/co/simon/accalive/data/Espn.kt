package uk.co.simon.accalive.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import kotlin.math.abs

enum class Phase { PRE, LIVE, FINAL }

data class TeamInfo(
    val id: String,
    val abbr: String,
    val name: String,
    val shortName: String,
    val logo: String,
    val color: Long,
    val alt: Long,
    val score: Int,
    val record: String
)

data class GameOdds(
    val provider: String,
    val homeSpread: Double?,
    val homeSpreadOdds: Double?,
    val awaySpreadOdds: Double?,
    val total: Double?,
    val overOdds: Double?,
    val underOdds: Double?,
    val homeMl: Double?,
    val awayMl: Double?,
    val openHomeSpread: Double?,
    val openTotal: Double?,
    val openHomeMl: Double?,
    val openAwayMl: Double?
) {
    /** No-vig home win probability from the moneyline, falling back to the spread. */
    fun impliedHome(): Double? {
        val h = homeMl
        val a = awayMl
        if (h != null && a != null && h > 1 && a > 1) {
            val ih = 1 / h
            val ia = 1 / a
            return ih / (ih + ia)
        }
        val s = homeSpread ?: return null
        return Stats.phi(-s / Stats.MARGIN_SD)
    }
}

data class Game(
    val id: String,
    val date: Long,
    val home: TeamInfo,
    val away: TeamInfo,
    val phase: Phase,
    val period: Int,
    val clock: String,
    val clockSec: Int,
    val detail: String,
    val possession: String?,
    val downDistance: String?,
    val redZone: Boolean,
    val homeWinProb: Double?,
    val odds: GameOdds?,
    val broadcast: String?,
    val lastPlay: String?
) {
    val isLive: Boolean get() = phase == Phase.LIVE

    /** "Q3 4:12", "HT", "OT 2:01" */
    val liveClock: String
        get() = when {
            detail.contains("half", ignoreCase = true) && period == 2 -> "HT"
            detail.startsWith("End", ignoreCase = true) -> "End Q$period"
            period > 4 -> "OT $clock"
            else -> "Q$period $clock"
        }
}

data class PlayerStats(
    val id: String,
    val name: String,
    val teamId: String,
    val passYds: Int = 0,
    val passTd: Int = 0,
    val rushYds: Int = 0,
    val rushTd: Int = 0,
    val recYds: Int = 0,
    val rec: Int = 0,
    val recTd: Int = 0,
    val retTd: Int = 0
) {
    val touchdowns: Int get() = rushTd + recTd + retTd
}

data class GameDetail(
    val game: Game?,
    val players: Map<String, PlayerStats>,
    val winSeries: List<Double>,
    val predictorHome: Double?,
    val fetchedAt: Long
)

data class WeekRef(
    val seasonType: Int,
    val week: Int,
    val label: String,
    val start: Long,
    val end: Long
) {
    val key: String get() = "$seasonType-$week"
}

data class Board(
    val games: List<Game>,
    val weeks: List<WeekRef>,
    val current: WeekRef?,
    val season: Int
)

data class RosterPlayer(
    val id: String,
    val name: String,
    val pos: String,
    val jersey: String,
    val headshot: String?
)

internal fun JSONArray?.objs(): List<JSONObject> {
    if (this == null) return emptyList()
    val out = ArrayList<JSONObject>(length())
    for (i in 0 until length()) optJSONObject(i)?.let { out += it }
    return out
}

internal fun JSONArray?.strings(): List<String> {
    if (this == null) return emptyList()
    return (0 until length()).map { optString(it) }
}

object Espn {
    private const val BASE = "https://site.api.espn.com/apis/site/v2/sports/football/nfl"

    private fun fetch(url: String): JSONObject {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000
        c.readTimeout = 15_000
        c.useCaches = false
        c.setRequestProperty("User-Agent", "AccaLive/1.0 (Android)")
        c.setRequestProperty("Accept", "application/json")
        try {
            val code = c.responseCode
            if (code !in 200..299) throw IOException("ESPN returned HTTP $code")
            val body = c.inputStream.bufferedReader().use { it.readText() }
            return JSONObject(body)
        } finally {
            c.disconnect()
        }
    }

    // ---------------------------------------------------------------- scoreboard

    fun scoreboard(week: WeekRef?): Board {
        val url = if (week == null) "$BASE/scoreboard"
        else "$BASE/scoreboard?seasontype=${week.seasonType}&week=${week.week}"
        val j = fetch(url)
        val season = j.optJSONObject("season")
        val seasonType = season?.optInt("type", 2) ?: 2
        val year = season?.optInt("year", 0) ?: 0
        val weekNum = j.optJSONObject("week")?.optInt("number", 0) ?: 0
        val weeks = parseCalendar(j)
        val current = weeks.firstOrNull { it.seasonType == seasonType && it.week == weekNum }
            ?: WeekRef(seasonType, weekNum, "Week $weekNum", 0, 0)
        val games = j.optJSONArray("events").objs()
            .mapNotNull { runCatching { parseEvent(it) }.getOrNull() }
            .sortedWith(compareBy({ it.date }, { it.id }))
        return Board(games, weeks, current, year)
    }

    private fun parseCalendar(j: JSONObject): List<WeekRef> {
        val league = j.optJSONArray("leagues").objs().firstOrNull() ?: return emptyList()
        val out = ArrayList<WeekRef>()
        for (part in league.optJSONArray("calendar").objs()) {
            val type = part.optString("value").toIntOrNull() ?: continue
            if (type != 2 && type != 3) continue
            for (e in part.optJSONArray("entries").objs()) {
                val w = e.optString("value").toIntOrNull() ?: continue
                val label = e.optString("label").ifBlank { "Week $w" }
                out += WeekRef(type, w, label, parseDate(e.optString("startDate")), parseDate(e.optString("endDate")))
            }
        }
        return out
    }

    private fun parseEvent(ev: JSONObject): Game {
        val comp = ev.getJSONArray("competitions").getJSONObject(0)
        val status = comp.optJSONObject("status") ?: ev.optJSONObject("status")
        return parseComp(
            id = ev.optString("id"),
            date = comp.optString("date").ifBlank { ev.optString("date") },
            comp = comp,
            status = status,
            situation = comp.optJSONObject("situation"),
            odds = comp.optJSONArray("odds").objs().firstOrNull()
        )
    }

    private fun parseComp(
        id: String,
        date: String,
        comp: JSONObject,
        status: JSONObject?,
        situation: JSONObject?,
        odds: JSONObject?
    ): Game {
        val cs = comp.optJSONArray("competitors").objs()
        val h = cs.firstOrNull { it.optString("homeAway") == "home" } ?: cs.getOrNull(0)
            ?: throw IllegalStateException("no home team")
        val a = cs.firstOrNull { it.optString("homeAway") == "away" } ?: cs.getOrNull(1)
            ?: throw IllegalStateException("no away team")
        val home = parseTeam(h)
        val away = parseTeam(a)

        val type = status?.optJSONObject("type")
        val phase = when (type?.optString("state")) {
            "in" -> Phase.LIVE
            "post" -> Phase.FINAL
            else -> Phase.PRE
        }
        val period = status?.optInt("period", 0) ?: 0
        val clockStr = status?.optString("displayClock")?.ifBlank { null } ?: "0:00"
        val clockSec = status?.optDouble("clock")?.takeIf { !it.isNaN() }?.toInt() ?: parseClock(clockStr)
        val detail = type?.optString("shortDetail")?.ifBlank { null } ?: type?.optString("detail") ?: ""

        val possession = situation?.optString("possession")?.ifBlank { null }
        val dd = situation?.optString("downDistanceText")?.ifBlank { null }
            ?: situation?.optString("shortDownDistanceText")?.ifBlank { null }
        val redZone = situation?.optBoolean("isRedZone", false) ?: false
        val lp = situation?.optJSONObject("lastPlay")
        val liveProb = lp?.optJSONObject("probability")?.optDouble("homeWinPercentage")?.takeIf { !it.isNaN() }
        val lastPlay = lp?.optString("text")?.ifBlank { null }

        val o = odds?.let { runCatching { parseOdds(it, home, away) }.getOrNull() }
        val bc = comp.optJSONArray("broadcasts").objs().firstOrNull()
            ?.optJSONArray("names")?.optString(0)?.ifBlank { null }
            ?: comp.optJSONArray("geoBroadcasts").objs().firstOrNull()
                ?.optJSONObject("media")?.optString("shortName")?.ifBlank { null }

        val prob = when (phase) {
            Phase.FINAL -> when {
                home.score > away.score -> 1.0
                home.score < away.score -> 0.0
                else -> 0.5
            }
            Phase.LIVE -> liveProb
            Phase.PRE -> o?.impliedHome()
        }

        return Game(
            id = id,
            date = parseDate(date),
            home = home,
            away = away,
            phase = phase,
            period = period,
            clock = clockStr,
            clockSec = clockSec,
            detail = detail,
            possession = possession,
            downDistance = dd,
            redZone = redZone,
            homeWinProb = prob,
            odds = o,
            broadcast = bc,
            lastPlay = lastPlay
        )
    }

    private fun parseTeam(c: JSONObject): TeamInfo {
        val t = c.optJSONObject("team") ?: JSONObject()
        val abbr = t.optString("abbreviation")
        val logo = t.optString("logo").ifBlank { null }
            ?: t.optJSONArray("logos").objs().firstOrNull()?.optString("href")?.ifBlank { null }
            ?: "https://a.espncdn.com/i/teamlogos/nfl/500/${abbr.lowercase()}.png"
        val score = when (val s = c.opt("score")) {
            is JSONObject -> s.optString("displayValue").toIntOrNull() ?: s.optDouble("value", 0.0).toInt()
            is Number -> s.toInt()
            is String -> s.toDoubleOrNull()?.toInt() ?: 0
            else -> 0
        }
        val rec = (c.optJSONArray("records") ?: c.optJSONArray("record")).objs().firstOrNull()
            ?.let { it.optString("summary").ifBlank { it.optString("displayValue") } } ?: ""
        return TeamInfo(
            id = t.optString("id").ifBlank { c.optString("id") },
            abbr = abbr,
            name = t.optString("displayName"),
            shortName = t.optString("shortDisplayName").ifBlank { t.optString("name").ifBlank { abbr } },
            logo = logo,
            color = parseColor(t.optString("color"), 0xFF2A3A33),
            alt = parseColor(t.optString("alternateColor"), 0xFF8899AA),
            score = score,
            record = rec
        )
    }

    // ---------------------------------------------------------------- odds

    private fun latest(side: JSONObject?): JSONObject? =
        side?.optJSONObject("current") ?: side?.optJSONObject("close") ?: side?.optJSONObject("open")

    private fun opening(side: JSONObject?): JSONObject? = side?.optJSONObject("open")

    private fun num(s: String?): Double? {
        if (s == null) return null
        val t = s.trim().uppercase()
        if (t == "PK" || t == "PICK" || t == "EVEN") return 0.0
        val cleaned = t.filter { it.isDigit() || it == '.' || it == '-' || it == '+' }
        return cleaned.toDoubleOrNull()
    }

    private fun parseOdds(o: JSONObject, home: TeamInfo, away: TeamInfo): GameOdds {
        val provider = o.optJSONObject("provider")?.optString("name")?.ifBlank { null } ?: "ESPN"
        val hto = o.optJSONObject("homeTeamOdds")
        val ato = o.optJSONObject("awayTeamOdds")

        val ml = o.optJSONObject("moneyline")
        val homeMl = OddsFmt.american(latest(ml?.optJSONObject("home"))?.optString("odds"))
            ?: OddsFmt.american(hto?.opt("moneyLine"))
        val awayMl = OddsFmt.american(latest(ml?.optJSONObject("away"))?.optString("odds"))
            ?: OddsFmt.american(ato?.opt("moneyLine"))

        val ps = o.optJSONObject("pointSpread")
        var homeSpread = num(latest(ps?.optJSONObject("home"))?.optString("line"))
        if (homeSpread == null) {
            val sp = o.optDouble("spread").takeIf { !it.isNaN() }
            homeSpread = if (sp != null) {
                val homeFav = hto?.optBoolean("favorite", false) ?: false
                val awayFav = ato?.optBoolean("favorite", false) ?: false
                when {
                    homeFav -> -abs(sp)
                    awayFav -> abs(sp)
                    else -> sp
                }
            } else parseDetails(o.optString("details"), home, away)
        }
        val homeSpreadOdds = OddsFmt.american(latest(ps?.optJSONObject("home"))?.optString("odds"))
            ?: OddsFmt.american(hto?.opt("spreadOdds"))
        val awaySpreadOdds = OddsFmt.american(latest(ps?.optJSONObject("away"))?.optString("odds"))
            ?: OddsFmt.american(ato?.opt("spreadOdds"))

        val tot = o.optJSONObject("total")
        val total = o.optDouble("overUnder").takeIf { !it.isNaN() }
            ?: num(latest(tot?.optJSONObject("over"))?.optString("line"))
        val overOdds = OddsFmt.american(latest(tot?.optJSONObject("over"))?.optString("odds"))
            ?: OddsFmt.american(o.opt("overOdds"))
        val underOdds = OddsFmt.american(latest(tot?.optJSONObject("under"))?.optString("odds"))
            ?: OddsFmt.american(o.opt("underOdds"))

        return GameOdds(
            provider = provider,
            homeSpread = homeSpread,
            homeSpreadOdds = homeSpreadOdds,
            awaySpreadOdds = awaySpreadOdds,
            total = total,
            overOdds = overOdds,
            underOdds = underOdds,
            homeMl = homeMl,
            awayMl = awayMl,
            openHomeSpread = num(opening(ps?.optJSONObject("home"))?.optString("line")),
            openTotal = num(opening(tot?.optJSONObject("over"))?.optString("line")),
            openHomeMl = OddsFmt.american(opening(ml?.optJSONObject("home"))?.optString("odds")),
            openAwayMl = OddsFmt.american(opening(ml?.optJSONObject("away"))?.optString("odds"))
        )
    }

    /** "IND -4.5" -> home spread from the home side's perspective. */
    private fun parseDetails(details: String?, home: TeamInfo, away: TeamInfo): Double? {
        if (details.isNullOrBlank()) return null
        val parts = details.trim().split(" ")
        if (parts.size < 2) return if (details.trim().uppercase() == "EVEN") 0.0 else null
        val v = num(parts.last()) ?: return null
        return when (parts.first()) {
            home.abbr -> v
            away.abbr -> -v
            else -> null
        }
    }

    // ---------------------------------------------------------------- summary

    fun summary(eventId: String): GameDetail {
        val j = fetch("$BASE/summary?event=$eventId")
        val header = j.optJSONObject("header")
        val comp = header?.optJSONArray("competitions").objs().firstOrNull()
        val situation = j.optJSONObject("situation") ?: comp?.optJSONObject("situation")
        val odds = j.optJSONArray("pickcenter").objs().firstOrNull()
            ?: j.optJSONArray("odds").objs().firstOrNull()

        var game: Game? = null
        if (comp != null) {
            game = runCatching {
                parseComp(
                    id = eventId,
                    date = comp.optString("date"),
                    comp = comp,
                    status = comp.optJSONObject("status") ?: header?.optJSONObject("status"),
                    situation = situation,
                    odds = odds
                )
            }.getOrNull()
        }

        val series = j.optJSONArray("winprobability").objs()
            .mapNotNull { it.optDouble("homeWinPercentage").takeIf { v -> !v.isNaN() } }
        val pred = j.optJSONObject("predictor")?.optJSONObject("homeTeam")
            ?.optString("gameProjection")?.toDoubleOrNull()?.div(100.0)

        val g = game
        if (g != null) {
            val p = when (g.phase) {
                Phase.LIVE -> series.lastOrNull() ?: g.homeWinProb
                Phase.PRE -> pred ?: g.homeWinProb
                Phase.FINAL -> g.homeWinProb
            }
            game = g.copy(homeWinProb = p)
        }

        return GameDetail(
            game = game,
            players = parsePlayers(j.optJSONObject("boxscore")),
            winSeries = series,
            predictorHome = pred,
            fetchedAt = System.currentTimeMillis()
        )
    }

    private fun statInt(s: String): Int =
        s.trim().substringBefore('/').substringBefore('-', s.trim()).toDoubleOrNull()?.toInt()
            ?: s.trim().toDoubleOrNull()?.toInt() ?: 0

    private fun parsePlayers(box: JSONObject?): Map<String, PlayerStats> {
        val map = HashMap<String, PlayerStats>()
        for (team in box?.optJSONArray("players").objs()) {
            val teamId = team.optJSONObject("team")?.optString("id") ?: ""
            for (cat in team.optJSONArray("statistics").objs()) {
                val name = cat.optString("name")
                val keys = cat.optJSONArray("keys").strings()
                for (ath in cat.optJSONArray("athletes").objs()) {
                    val a = ath.optJSONObject("athlete") ?: continue
                    val id = a.optString("id")
                    if (id.isBlank()) continue
                    val stats = ath.optJSONArray("stats").strings()
                    val v = { k: String ->
                        val i = keys.indexOf(k)
                        if (i >= 0 && i < stats.size) statInt(stats[i]) else 0
                    }
                    val cur = map[id] ?: PlayerStats(id, a.optString("displayName"), teamId)
                    map[id] = when (name) {
                        "passing" -> cur.copy(passYds = v("passingYards"), passTd = v("passingTouchdowns"))
                        "rushing" -> cur.copy(rushYds = v("rushingYards"), rushTd = v("rushingTouchdowns"))
                        "receiving" -> cur.copy(rec = v("receptions"), recYds = v("receivingYards"), recTd = v("receivingTouchdowns"))
                        "kickReturns" -> cur.copy(retTd = cur.retTd + v("kickReturnTouchdowns"))
                        "puntReturns" -> cur.copy(retTd = cur.retTd + v("puntReturnTouchdowns"))
                        else -> cur
                    }
                }
            }
        }
        return map
    }

    // ---------------------------------------------------------------- roster

    fun roster(teamId: String): List<RosterPlayer> {
        val j = fetch("$BASE/teams/$teamId/roster")
        val keep = setOf("QB", "RB", "FB", "WR", "TE")
        val out = ArrayList<RosterPlayer>()
        for (group in j.optJSONArray("athletes").objs()) {
            for (p in group.optJSONArray("items").objs()) {
                val pos = p.optJSONObject("position")?.optString("abbreviation") ?: ""
                if (pos !in keep) continue
                out += RosterPlayer(
                    id = p.optString("id"),
                    name = p.optString("displayName").ifBlank { p.optString("fullName") },
                    pos = pos,
                    jersey = p.optString("jersey"),
                    headshot = p.optJSONObject("headshot")?.optString("href")?.ifBlank { null }
                )
            }
        }
        val order = listOf("QB", "RB", "WR", "TE", "FB")
        return out.sortedWith(compareBy({ order.indexOf(it.pos) }, { it.name }))
    }

    // ---------------------------------------------------------------- helpers

    private fun parseClock(s: String): Int {
        val p = s.split(":")
        return if (p.size == 2) (p[0].toIntOrNull() ?: 0) * 60 + (p[1].toDoubleOrNull()?.toInt() ?: 0)
        else s.toDoubleOrNull()?.toInt() ?: 0
    }

    private fun parseColor(hex: String?, def: Long): Long {
        if (hex.isNullOrBlank()) return def
        val v = hex.trim().removePrefix("#").toLongOrNull(16) ?: return def
        return 0xFF000000L or v
    }

    fun parseDate(s: String?): Long {
        if (s.isNullOrBlank()) return 0
        return runCatching { Instant.parse(s).toEpochMilli() }.getOrNull()
            ?: runCatching { Instant.parse(s.replace("Z", ":00Z")).toEpochMilli() }.getOrNull()
            ?: 0
    }
}
