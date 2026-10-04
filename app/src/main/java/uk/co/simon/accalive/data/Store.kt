package uk.co.simon.accalive.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.File

class AccaStore(ctx: Context) {
    private val file = File(ctx.filesDir, "accas.json")
    private val statesFile = File(ctx.filesDir, "states.json")

    fun load(): List<Acca> = runCatching {
        if (file.exists()) Codec.fromJson(file.readText()) else emptyList()
    }.getOrDefault(emptyList())

    fun save(list: List<Acca>) {
        val tmp = File(file.parentFile, "accas.tmp")
        tmp.writeText(Codec.toJson(list))
        tmp.renameTo(file)
    }

    /** Last-known leg/acca states, kept on disk so alerts survive the app being killed. */
    fun loadStates(): Map<String, String>? = runCatching {
        if (!statesFile.exists()) return null
        val o = JSONObject(statesFile.readText())
        val m = HashMap<String, String>()
        o.keys().forEach { k -> m[k] = o.optString(k) }
        m
    }.getOrNull()

    fun saveStates(m: Map<String, String>) {
        runCatching { statesFile.writeText(JSONObject(m as Map<*, *>).toString()) }
    }
}

data class Prefs(
    val odds: OddsFormat = OddsFormat.FRACTIONAL,
    val refreshSecs: Int = 20,
    val liveTracking: Boolean = true,
    val alertLeads: Boolean = true,
    val alertResults: Boolean = true
)

class Settings(ctx: Context) {
    private val sp = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(read())
    val state: StateFlow<Prefs> = _state.asStateFlow()

    private fun read() = Prefs(
        odds = enumOrNull<OddsFormat>(sp.getString("odds", null)) ?: OddsFormat.FRACTIONAL,
        refreshSecs = sp.getInt("refresh", 20),
        liveTracking = sp.getBoolean("live", true),
        alertLeads = sp.getBoolean("leads", true),
        alertResults = sp.getBoolean("results", true)
    )

    fun update(f: (Prefs) -> Prefs) {
        val n = f(_state.value)
        sp.edit()
            .putString("odds", n.odds.name)
            .putInt("refresh", n.refreshSecs)
            .putBoolean("live", n.liveTracking)
            .putBoolean("leads", n.alertLeads)
            .putBoolean("results", n.alertResults)
            .apply()
        _state.value = n
    }
}
