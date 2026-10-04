package uk.co.simon.accalive.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt

object Stats {
    /** Standard deviation of NFL final margin over a full game. */
    const val MARGIN_SD = 13.86
    /** Standard deviation of NFL game total over a full game. */
    const val TOTAL_SD = 13.0

    fun phi(x: Double): Double {
        val t = 1.0 / (1.0 + 0.2316419 * abs(x))
        val d = 0.3989422804014327 * exp(-x * x / 2.0)
        val p = d * t * (0.319381530 + t * (-0.356563782 + t * (1.781477937 + t * (-1.821255978 + t * 1.330274429))))
        return if (x >= 0) 1.0 - p else p
    }

    fun invPhi(p: Double): Double {
        val target = p.coerceIn(1e-6, 1 - 1e-6)
        var lo = -8.0
        var hi = 8.0
        repeat(64) {
            val m = (lo + hi) / 2
            if (phi(m) < target) lo = m else hi = m
        }
        return (lo + hi) / 2
    }
}

enum class OddsFormat(val label: String) {
    FRACTIONAL("Fractional"),
    DECIMAL("Decimal"),
    AMERICAN("American")
}

object OddsFmt {
    private val fracs = listOf(
        1 to 10, 1 to 8, 1 to 7, 1 to 6, 1 to 5, 2 to 9, 1 to 4, 2 to 7, 3 to 10, 1 to 3,
        4 to 11, 2 to 5, 4 to 9, 1 to 2, 8 to 15, 4 to 7, 8 to 13, 4 to 6, 8 to 11, 4 to 5,
        5 to 6, 10 to 11, 1 to 1, 11 to 10, 6 to 5, 5 to 4, 11 to 8, 6 to 4, 13 to 8, 7 to 4,
        15 to 8, 2 to 1, 9 to 4, 5 to 2, 11 to 4, 3 to 1, 10 to 3, 7 to 2, 4 to 1, 9 to 2,
        5 to 1, 11 to 2, 6 to 1, 13 to 2, 7 to 1, 15 to 2, 8 to 1, 9 to 1, 10 to 1, 11 to 1,
        12 to 1, 14 to 1, 16 to 1, 18 to 1, 20 to 1
    )

    fun format(d: Double?, f: OddsFormat): String {
        if (d == null || d <= 1.0 || d.isNaN()) return "—"
        return when (f) {
            OddsFormat.DECIMAL -> if (d >= 100) d.roundToInt().toString() else String.format(Locale.UK, "%.2f", d)
            OddsFormat.AMERICAN -> if (d >= 2.0) "+${((d - 1) * 100).roundToInt()}" else "${(-100 / (d - 1)).roundToInt()}"
            OddsFormat.FRACTIONAL -> frac(d)
        }
    }

    private fun frac(d: Double): String {
        val x = d - 1
        if (x >= 20.5) return "${x.roundToInt()}/1"
        val best = fracs.minBy { abs(it.first.toDouble() / it.second - x) }
        if (best.first == best.second) return "EVS"
        return "${best.first}/${best.second}"
    }

    /** Accepts "6/4", "evs", "2.5", "+150", "-120". Returns decimal odds. */
    fun parse(input: String): Double? {
        val s = input.trim().lowercase(Locale.UK)
        if (s.isEmpty()) return null
        if (s == "evs" || s == "evens" || s == "ev") return 2.0
        if (s.contains('/')) {
            val a = s.substringBefore('/').trim().toDoubleOrNull() ?: return null
            val b = s.substringAfter('/').trim().toDoubleOrNull() ?: return null
            if (b <= 0) return null
            return 1 + a / b
        }
        val v = s.toDoubleOrNull() ?: return null
        if ((s.startsWith("+") || s.startsWith("-")) && abs(v) >= 100) {
            return if (v > 0) 1 + v / 100 else 1 + 100 / -v
        }
        return if (v > 1.0) v else null
    }

    fun american(any: Any?): Double? {
        val v: Double = when (any) {
            null -> return null
            is Number -> any.toDouble()
            is String -> {
                val s = any.trim().uppercase(Locale.US)
                if (s == "EVEN" || s == "EV" || s == "EVS") return 2.0
                s.toDoubleOrNull() ?: return null
            }
            else -> return null
        }
        return when {
            v >= 100 -> 1 + v / 100
            v <= -100 -> 1 + 100 / -v
            else -> null
        }
    }
}

object Fmt {
    private val zone: ZoneId get() = ZoneId.systemDefault()
    private val dayTime = DateTimeFormatter.ofPattern("EEE HH:mm", Locale.UK)
    private val time = DateTimeFormatter.ofPattern("HH:mm", Locale.UK)
    private val dayHeader = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.UK)
    private val shortDate = DateTimeFormatter.ofPattern("d MMM", Locale.UK)

    fun money(v: Double): String = "£" + String.format(Locale.UK, "%,.2f", v)

    fun signedMoney(v: Double): String = (if (v >= 0) "+" else "−") + money(abs(v))

    fun pct(p: Double, settled: Boolean = false): String {
        val v = (p * 100).roundToInt()
        return if (settled) "$v%" else "${v.coerceIn(1, 99)}%"
    }

    fun kickoff(ms: Long): String {
        if (ms <= 0) return "TBC"
        val t = Instant.ofEpochMilli(ms).atZone(zone)
        return if (t.toLocalDate() == LocalDate.now(zone)) "Today ${time.format(t)}" else dayTime.format(t)
    }

    fun clockTime(ms: Long): String = time.format(Instant.ofEpochMilli(ms).atZone(zone))

    fun day(ms: Long): String = dayHeader.format(Instant.ofEpochMilli(ms).atZone(zone)).uppercase(Locale.UK)

    fun date(ms: Long): String = shortDate.format(Instant.ofEpochMilli(ms).atZone(zone))

    fun line(x: Double?): String {
        if (x == null) return "—"
        return if (x % 1.0 == 0.0) x.toInt().toString() else String.format(Locale.UK, "%.1f", x)
    }

    fun signed(x: Double?): String = when {
        x == null -> "—"
        x > 0 -> "+" + line(x)
        x == 0.0 -> "PK"
        else -> line(x)
    }

    fun ago(ms: Long, now: Long): String {
        if (ms <= 0) return "Not updated yet"
        val s = ((now - ms) / 1000).coerceAtLeast(0)
        return when {
            s < 5 -> "Updated just now"
            s < 60 -> "Updated ${s}s ago"
            else -> "Updated ${s / 60}m ago"
        }
    }

    fun fold(n: Int): String = when (n) {
        0 -> "No legs"
        1 -> "Single"
        2 -> "Double"
        3 -> "Treble"
        else -> "$n-fold"
    }
}
