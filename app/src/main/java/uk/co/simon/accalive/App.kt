package uk.co.simon.accalive

import android.app.Application
import uk.co.simon.accalive.live.CheckWorker
import uk.co.simon.accalive.live.Notifier

class App : Application() {
    lateinit var repo: Repo
        private set

    override fun onCreate() {
        super.onCreate()
        repo = Repo(this)
        Notifier.createChannels(this)
        CheckWorker.schedule(this)
    }
}
