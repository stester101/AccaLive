package uk.co.simon.accalive.live

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import uk.co.simon.accalive.MainActivity
import uk.co.simon.accalive.R
import uk.co.simon.accalive.data.AccaState
import uk.co.simon.accalive.data.AccaStore
import uk.co.simon.accalive.data.Fmt
import uk.co.simon.accalive.data.LegState
import uk.co.simon.accalive.data.LegType
import uk.co.simon.accalive.data.Prefs
import uk.co.simon.accalive.logic.AccaEval
import uk.co.simon.accalive.logic.Labels

object Notifier {
    const val CH_ALERTS = "alerts"
    const val CH_LIVE = "live"
    private const val BRAND = 0xFF00A651.toInt()

    fun createChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CH_ALERTS, "Bet alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Legs winning, losing and settling, and accas landing"
                enableVibration(true)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_LIVE, "Live tracking", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Ongoing live acca summary while games are on"
                setShowBadge(false)
            }
        )
    }

    fun canPost(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun openApp(ctx: Context): PendingIntent = PendingIntent.getActivity(
        ctx, 0,
        Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun stateMap(evals: List<AccaEval>): Map<String, String> {
        val m = HashMap<String, String>()
        for (e in evals) {
            m[e.acca.id] = e.state.name
            for (l in e.legs) m[e.acca.id + "|" + l.leg.id] = l.state.name
        }
        return m
    }

    fun snapshot(store: AccaStore, evals: List<AccaEval>) = store.saveStates(stateMap(evals))

    /** Compares against the last saved states and posts alerts for anything that changed. */
    fun diff(ctx: Context, store: AccaStore, evals: List<AccaEval>, prefs: Prefs) {
        val prev = store.loadStates()
        val next = stateMap(evals)
        store.saveStates(next)
        if (prev == null) return

        val msgs = ArrayList<Pair<String, String>>()
        for (e in evals) {
            val pa = prev[e.acca.id] ?: continue
            if (pa == AccaState.WON.name || pa == AccaState.LOST.name) continue
            val accaLine = "${e.acca.name} · ${Fmt.pct(e.prob, e.acca.settled)} chance · ${Fmt.money(e.returns)}"

            for (l in e.legs) {
                val pl = prev[e.acca.id + "|" + l.leg.id] ?: continue
                if (pl == l.state.name) continue
                val title = Labels.title(l.leg)
                val body = listOf(l.scoreLine, accaLine).filter { it.isNotBlank() }.joinToString("\n")
                val wasAhead = pl == LegState.WINNING.name
                val wasBehind = pl == LegState.LOSING.name || pl == LegState.LEVEL.name
                when (l.state) {
                    LegState.WON -> if (prefs.alertResults) msgs += "✅ $title — won" to body
                    LegState.LOST -> if (prefs.alertResults) msgs += "❌ $title — lost" to body
                    LegState.VOID -> if (prefs.alertResults) msgs += "↩️ $title — void" to body
                    LegState.WINNING ->
                        if (prefs.alertLeads && wasBehind && l.leg.type != LegType.PLAYER) msgs += "📈 $title — now winning" to body
                    LegState.LOSING ->
                        if (prefs.alertLeads && (wasAhead || pl == LegState.LEVEL.name) && l.leg.type != LegType.PLAYER) msgs += "📉 $title — now losing" to body
                    else -> {}
                }
            }
            if (pa != e.state.name && prefs.alertResults) {
                if (e.state == AccaState.WON) msgs += "🎉 ${e.acca.name} has landed!" to "Returns ${Fmt.money(e.returns)}"
                if (e.state == AccaState.LOST) msgs += "${e.acca.name} is down" to "One leg has lost. Check Results for the breakdown."
            }
        }
        if (!canPost(ctx)) return
        msgs.forEach { (t, b) -> post(ctx, t, b) }
    }

    @SuppressLint("MissingPermission")
    fun post(ctx: Context, title: String, body: String) {
        if (!canPost(ctx)) return
        val n = NotificationCompat.Builder(ctx, CH_ALERTS)
            .setSmallIcon(R.drawable.ic_stat)
            .setColor(BRAND)
            .setContentTitle(title)
            .setContentText(body.substringBefore('\n'))
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openApp(ctx))
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify((title + body + System.nanoTime()).hashCode(), n)
        } catch (_: SecurityException) {
        }
    }

    fun liveNotification(ctx: Context, evals: List<AccaEval>): Notification {
        val open = evals.filter { !it.acca.settled }
        val live = open.count { it.anyLive }
        val title = when {
            open.isEmpty() -> "Acca Live"
            live > 0 -> "🔴 $live of ${open.size} acca${if (open.size == 1) "" else "s"} live"
            else -> "Tracking ${open.size} acca${if (open.size == 1) "" else "s"}"
        }
        val lines = open.map {
            "${it.acca.name}: ${it.landing}/${it.acca.legs.size} landing · ${Fmt.pct(it.prob)} · ${Fmt.money(it.returns)}"
        }
        val style = NotificationCompat.InboxStyle()
        lines.take(6).forEach { style.addLine(it) }
        val stop = PendingIntent.getService(
            ctx, 1,
            Intent(ctx, LiveService::class.java).setAction(LiveService.ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(ctx, CH_LIVE)
            .setSmallIcon(R.drawable.ic_stat)
            .setColor(BRAND)
            .setContentTitle(title)
            .setContentText(lines.firstOrNull() ?: "Waiting for games")
            .setStyle(style)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(openApp(ctx))
            .addAction(0, "Stop tracking", stop)
            .build()
    }
}
