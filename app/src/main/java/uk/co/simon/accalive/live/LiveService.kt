package uk.co.simon.accalive.live

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import uk.co.simon.accalive.App
import uk.co.simon.accalive.data.Phase

/**
 * Keeps scores polling while the phone is locked or the app is in the background,
 * with an ongoing notification summarising every open acca. Stops itself when
 * nothing is live or kicking off within the next 12 hours.
 */
class LiveService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val repo = (application as App).repo
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        try {
            ServiceCompat.startForeground(this, NOTIF_ID, Notifier.liveNotification(this, repo.evals.value), type)
        } catch (e: Exception) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_STOP) {
            stopped = true
            stopSelf()
            return START_NOT_STICKY
        }
        if (job == null) {
            job = scope.launch {
                var loops = 0
                while (isActive) {
                    try {
                        repo.refresh()
                    } catch (_: Exception) {
                    }
                    if (Notifier.canPost(this@LiveService)) {
                        try {
                            NotificationManagerCompat.from(this@LiveService)
                                .notify(NOTIF_ID, Notifier.liveNotification(this@LiveService, repo.evals.value))
                        } catch (_: SecurityException) {
                        }
                    }
                    loops++
                    if (loops > 2 && !shouldRun()) break
                    delay(repo.settings.state.value.refreshSecs.coerceAtLeast(15) * 1000L)
                }
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun shouldRun(): Boolean {
        val repo = (application as App).repo
        if (!repo.settings.state.value.liveTracking) return false
        val now = System.currentTimeMillis()
        return repo.evals.value.any { e ->
            !e.acca.settled && e.legs.any { l ->
                val g = l.game
                l.leg.result == null && (
                    g == null ||
                        g.phase == Phase.LIVE ||
                        (g.phase == Phase.PRE && g.date - now < 12 * 3_600_000L)
                    )
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        job = null
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "uk.co.simon.accalive.STOP"
        private const val NOTIF_ID = 42

        /** Set when the user taps "Stop tracking"; cleared next time the app is opened fresh. */
        @Volatile
        var stopped = false

        fun start(ctx: Context) {
            if (stopped) return
            try {
                ContextCompat.startForegroundService(ctx, Intent(ctx, LiveService::class.java))
            } catch (_: Exception) {
            }
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, LiveService::class.java))
        }
    }
}
