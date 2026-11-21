package com.msmfarmer.app.state

import android.content.Context
import android.graphics.Bitmap
import com.msmfarmer.app.model.Detection
import com.msmfarmer.app.model.FarmerSettings
import com.msmfarmer.app.model.LearnedRoute
import com.msmfarmer.app.model.MacroCodec
import com.msmfarmer.app.model.MacroStep
import kotlinx.coroutines.flow.MutableStateFlow

object FarmerState {
    val running = MutableStateFlow(false)
    val capturing = MutableStateFlow(false)
    val accessibilityReady = MutableStateFlow(false)
    val projectionReady = MutableStateFlow(false)
    val overlayReady = MutableStateFlow(false)
    val settings = MutableStateFlow(FarmerSettings())
    val macro = MutableStateFlow<List<MacroStep>>(emptyList())
    val learnedRoutes = MutableStateFlow<List<LearnedRoute>>(emptyList())
    val learning = MutableStateFlow(false)
    val paused = MutableStateFlow(false)
    val hudVisible = MutableStateFlow(false)
    val hudStatus = MutableStateFlow("Ожидание")
    val detectorDebug = MutableStateFlow("Детектор ещё не запускался")
    val lastFrame = MutableStateFlow<Bitmap?>(null)
    val lastDetections = MutableStateFlow<List<Detection>>(emptyList())
    val stats = MutableStateFlow(Stats())
    val logs = MutableStateFlow<List<LogEntry>>(emptyList())

    data class Stats(
        val scans: Long = 0L,
        val taps: Long = 0L,
        val islandsVisited: Int = 0,
        val startedAt: Long = 0L,
        val lastActionAt: Long = 0L,
    )

    enum class Level { INFO, OK, WARN }

    data class LogEntry(val time: String, val message: String, val level: Level)

    fun log(message: String, level: Level = Level.INFO) {
        val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date())
        logs.value = listOf(LogEntry(time, message, level)) + logs.value.take(49)
    }

    fun addScan() {
        val s = stats.value
        stats.value = s.copy(scans = s.scans + 1)
    }

    fun addTap() {
        val s = stats.value
        stats.value = s.copy(taps = s.taps + 1, lastActionAt = System.currentTimeMillis())
    }

    fun islandVisited(index: Int) {
        val s = stats.value
        stats.value = s.copy(islandsVisited = index + 1, lastActionAt = System.currentTimeMillis())
    }

    fun resetStats() {
        stats.value = Stats(startedAt = System.currentTimeMillis())
    }
}

private const val PREFS = "msm_farmer_prefs"

object SettingsStore {
    fun load(context: Context) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        FarmerState.settings.value = FarmerSettings(
            scanIntervalMs = p.getLong("scanIntervalMs", 350L),
            sensitivity = p.getFloat("sensitivity", 0.55f),
            minCoinRadiusPx = p.getInt("minCoinRadiusPx", 5),
            maxCoinRadiusPx = p.getInt("maxCoinRadiusPx", 34),
            tapCooldownMs = p.getLong("tapCooldownMs", 1200L),
            tapsPerScan = p.getInt("tapsPerScan", 6),
            ignoreTopFraction = p.getFloat("ignoreTopFraction", 0.10f),
            ignoreBottomFraction = p.getFloat("ignoreBottomFraction", 0.08f),
            autoIslandSwitch = p.getBoolean("autoIslandSwitch", false),
            idleMsBeforeSwitch = p.getLong("idleMsBeforeSwitch", 8000L),
            islandsCount = p.getInt("islandsCount", 12),
            loopIslands = p.getBoolean("loopIslands", true),
            replayDelayMs = p.getLong("replayDelayMs", 400L),
            preferLearnedRoutes = p.getBoolean("preferLearnedRoutes", true),
            autoCalibrateOnIsland = p.getBoolean("autoCalibrateOnIsland", false),
            debugMarkers = p.getBoolean("debugMarkers", false),
        )
        FarmerState.macro.value = MacroCodec.decode(p.getString("macro", "") ?: "")
        FarmerState.learnedRoutes.value = decodeRoutes(p.getString("learnedRoutes", "") ?: "")
    }

    fun save(context: Context) {
        val s = FarmerState.settings.value
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            putLong("scanIntervalMs", s.scanIntervalMs)
            putFloat("sensitivity", s.sensitivity)
            putInt("minCoinRadiusPx", s.minCoinRadiusPx)
            putInt("maxCoinRadiusPx", s.maxCoinRadiusPx)
            putLong("tapCooldownMs", s.tapCooldownMs)
            putInt("tapsPerScan", s.tapsPerScan)
            putFloat("ignoreTopFraction", s.ignoreTopFraction)
            putFloat("ignoreBottomFraction", s.ignoreBottomFraction)
            putBoolean("autoIslandSwitch", s.autoIslandSwitch)
            putLong("idleMsBeforeSwitch", s.idleMsBeforeSwitch)
            putInt("islandsCount", s.islandsCount)
            putBoolean("loopIslands", s.loopIslands)
            putLong("replayDelayMs", s.replayDelayMs)
            putBoolean("preferLearnedRoutes", s.preferLearnedRoutes)
            putBoolean("autoCalibrateOnIsland", s.autoCalibrateOnIsland)
            putBoolean("debugMarkers", s.debugMarkers)
            putString("macro", MacroCodec.encode(FarmerState.macro.value))
            putString("learnedRoutes", encodeRoutes(FarmerState.learnedRoutes.value))
            apply()
        }
    }

    private fun encodeRoutes(routes: List<LearnedRoute>): String =
        routes.joinToString("||") { route ->
            "${route.id}~${route.source}~${route.destination}~${MacroCodec.encode(route.steps).trim()}"
        }

    private fun decodeRoutes(text: String): List<LearnedRoute> =
        text.split("||").filter { it.isNotBlank() }.mapNotNull { chunk ->
            val parts = chunk.split("~", limit = 4)
            if (parts.size != 4) return@mapNotNull null
            LearnedRoute(
                id = parts[0].toLongOrNull() ?: System.currentTimeMillis(),
                steps = MacroCodec.decode(parts[3]),
                source = parts[1],
                destination = parts[2],
            )
        }
}
