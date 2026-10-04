package uk.co.simon.accalive.data

import org.json.JSONArray
import org.json.JSONObject

enum class LegType { MONEYLINE, SPREAD, TOTAL, PLAYER }

enum class Side { HOME, AWAY, OVER, UNDER }

enum class PropStat(
    val label: String,
    val unit: String,
    val defaultLine: Double,
    val defaultOdds: Double,
    val relSd: Double
) {
    ANYTIME_TD("Anytime touchdown", "TD", 0.5, 2.5, 1.0),
    PASS_YDS("Passing yards", "pass yds", 224.5, 1.9, 0.33),
    PASS_TDS("Passing TDs", "pass TDs", 1.5, 1.9, 0.8),
    RUSH_YDS("Rushing yards", "rush yds", 49.5, 1.9, 0.55),
    REC_YDS("Receiving yards", "rec yds", 49.5, 1.9, 0.6),
    RECEPTIONS("Receptions", "catches", 3.5, 1.9, 0.5),
    RUSH_REC_YDS("Rush + rec yards", "rush+rec yds", 69.5, 1.9, 0.5)
}

enum class LegState(val label: String) {
    PENDING("Pending"),
    WINNING("Winning"),
    LEVEL("Level"),
    LOSING("Losing"),
    WON("Won"),
    LOST("Lost"),
    VOID("Void");

    val settled: Boolean get() = this == WON || this == LOST || this == VOID
}

enum class AccaState { UPCOMING, LIVE, WON, LOST }

data class Leg(
    val id: String,
    val eventId: String,
    val eventDate: Long,
    val homeId: String,
    val awayId: String,
    val homeAbbr: String,
    val awayAbbr: String,
    val homeName: String,
    val awayName: String,
    val type: LegType,
    val side: Side,
    val line: Double? = null,
    val odds: Double = 1.9,
    val playerId: String? = null,
    val playerName: String? = null,
    val playerTeamId: String? = null,
    val stat: PropStat? = null,
    val result: LegState? = null,
    val resultNote: String? = null
)

data class Acca(
    val id: String,
    val name: String,
    val stake: Double,
    val legs: List<Leg>,
    val createdAt: Long,
    val settled: Boolean = false,
    val outcome: AccaState? = null,
    val returns: Double = 0.0,
    val settledAt: Long? = null
)

internal fun JSONObject.str(k: String): String? =
    if (has(k) && !isNull(k)) optString(k) else null

internal fun JSONObject.dbl(k: String): Double? =
    if (has(k) && !isNull(k)) optDouble(k).takeIf { !it.isNaN() } else null

inline fun <reified T : Enum<T>> enumOrNull(name: String?): T? =
    if (name == null) null else enumValues<T>().firstOrNull { it.name == name }

object Codec {
    fun toJson(list: List<Acca>): String {
        val arr = JSONArray()
        for (a in list) {
            val o = JSONObject()
            o.put("id", a.id)
            o.put("name", a.name)
            o.put("stake", a.stake)
            o.put("createdAt", a.createdAt)
            o.put("settled", a.settled)
            a.outcome?.let { o.put("outcome", it.name) }
            o.put("returns", a.returns)
            a.settledAt?.let { o.put("settledAt", it) }
            val legs = JSONArray()
            a.legs.forEach { legs.put(legToJson(it)) }
            o.put("legs", legs)
            arr.put(o)
        }
        return arr.toString()
    }

    private fun legToJson(l: Leg): JSONObject = JSONObject().apply {
        put("id", l.id)
        put("eventId", l.eventId)
        put("eventDate", l.eventDate)
        put("homeId", l.homeId)
        put("awayId", l.awayId)
        put("homeAbbr", l.homeAbbr)
        put("awayAbbr", l.awayAbbr)
        put("homeName", l.homeName)
        put("awayName", l.awayName)
        put("type", l.type.name)
        put("side", l.side.name)
        l.line?.let { put("line", it) }
        put("odds", l.odds)
        l.playerId?.let { put("playerId", it) }
        l.playerName?.let { put("playerName", it) }
        l.playerTeamId?.let { put("playerTeamId", it) }
        l.stat?.let { put("stat", it.name) }
        l.result?.let { put("result", it.name) }
        l.resultNote?.let { put("resultNote", it) }
    }

    fun fromJson(s: String): List<Acca> {
        val arr = JSONArray(s)
        val out = ArrayList<Acca>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val legsArr = o.optJSONArray("legs") ?: JSONArray()
            val legs = ArrayList<Leg>()
            for (j in 0 until legsArr.length()) {
                val l = legsArr.optJSONObject(j) ?: continue
                legs += legFromJson(l) ?: continue
            }
            out += Acca(
                id = o.optString("id"),
                name = o.optString("name", "Acca"),
                stake = o.optDouble("stake", 0.0),
                legs = legs,
                createdAt = o.optLong("createdAt"),
                settled = o.optBoolean("settled", false),
                outcome = enumOrNull<AccaState>(o.str("outcome")),
                returns = o.optDouble("returns", 0.0),
                settledAt = if (o.has("settledAt")) o.optLong("settledAt") else null
            )
        }
        return out
    }

    private fun legFromJson(l: JSONObject): Leg? {
        val type = enumOrNull<LegType>(l.str("type")) ?: return null
        val side = enumOrNull<Side>(l.str("side")) ?: return null
        return Leg(
            id = l.optString("id"),
            eventId = l.optString("eventId"),
            eventDate = l.optLong("eventDate"),
            homeId = l.optString("homeId"),
            awayId = l.optString("awayId"),
            homeAbbr = l.optString("homeAbbr"),
            awayAbbr = l.optString("awayAbbr"),
            homeName = l.optString("homeName"),
            awayName = l.optString("awayName"),
            type = type,
            side = side,
            line = l.dbl("line"),
            odds = l.optDouble("odds", 1.9),
            playerId = l.str("playerId"),
            playerName = l.str("playerName"),
            playerTeamId = l.str("playerTeamId"),
            stat = enumOrNull<PropStat>(l.str("stat")),
            result = enumOrNull<LegState>(l.str("result")),
            resultNote = l.str("resultNote")
        )
    }
}
