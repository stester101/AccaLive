package uk.co.simon.accalive

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import uk.co.simon.accalive.live.LiveService
import uk.co.simon.accalive.ui.AccaTheme
import uk.co.simon.accalive.ui.AppRoot

class MainActivity : ComponentActivity() {
    private val repo: Repo get() = (application as App).repo

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        if (savedInstanceState == null) LiveService.stopped = false

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    try {
                        repo.refresh()
                    } catch (_: Exception) {
                    }
                    delay(repo.settings.state.value.refreshSecs.coerceAtLeast(10) * 1000L)
                }
            }
        }

        setContent {
            AccaTheme {
                AppRoot(repo = repo, onTrackingChanged = { syncService() })
            }
        }
    }

    override fun onResume() {
        super.onResume()
        syncService()
    }

    private fun syncService() {
        val prefs = repo.settings.state.value
        if (prefs.liveTracking && repo.hasActive()) LiveService.start(this) else LiveService.stop(this)
    }
}
