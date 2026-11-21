package com.msmfarmer.app.core

import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import com.msmfarmer.app.model.MacroStep
import com.msmfarmer.app.state.FarmerState
import kotlin.math.roundToInt

/**
 * Основной цикл: захват кадра → поиск жёлтых иконок → тапы
 * → при простое проигрывание макроса перехода на следующий остров.
 */
class FarmerEngine(private val capturer: ScreenCapturer) {

    private val thread = HandlerThread("msm-farmer").apply { start() }
    private val handler = Handler(thread.looper)
    private val tappedCells = HashMap<Long, Long>()
    @Volatile private var running = false
    @Volatile private var stopped = false

    private var idleSince = 0L
    private var islandIndex = 0
    private var lastLogAt = 0L
    private var typicalCoinCount = 8
    private var calibrateAt = 0L

    fun start() {
        if (running || stopped) return
        running = true
        idleSince = SystemClock.elapsedRealtime()
        FarmerState.resetStats()
        FarmerState.log("Сбор монет запущен", FarmerState.Level.OK)
        handler.post(loop)
    }

    fun stop() {
        if (stopped) return
        running = false
        stopped = true
        handler.removeCallbacksAndMessages(null)
        thread.quitSafely()
    }

    fun replayMacroNow() {
        if (!running) {
            FarmerState.log("Сначала запустите сбор", FarmerState.Level.WARN)
            return
        }
        handler.post {
            val (steps, label) = selectRoute()
            if (steps.isEmpty()) {
                FarmerState.log("Нет ни обученных маршрутов, ни ручного макроса", FarmerState.Level.WARN)
            } else {
                replayRoute(steps, label)
            }
        }
    }

    private val loop = object : Runnable {
        override fun run() {
            if (!running) return
            try {
                tick()
            } catch (t: Throwable) {
                FarmerState.log("Ошибка цикла: ${t.message}", FarmerState.Level.WARN)
            }
            if (running) {
                handler.postDelayed(this, FarmerState.settings.value.scanIntervalMs.coerceAtLeast(80L))
            }
        }
    }

    private fun tick() {
        // Пока пользователь показывает маршрут вручную, автосбор мешал бы ему.
        if (FarmerState.learning.value) {
            idleSince = SystemClock.elapsedRealtime()
            return
        }

        val bitmap = capturer.latest ?: return
        val now = SystemClock.elapsedRealtime()

        var settings = FarmerState.settings.value
        if (calibrateAt > 0L && now >= calibrateAt) {
            calibrateAt = 0L
            val tuned = CoinDetector.autoCalibrate(bitmap, settings, typicalCoinCount)
            settings = settings.copy(sensitivity = tuned)
            FarmerState.settings.value = settings
            FarmerState.log("Чувствительность подстроена под новый остров: ${(tuned * 100).roundToInt()}%", FarmerState.Level.OK)
        }

        FarmerState.lastFrame.value = bitmap
        val detections = CoinDetector.detect(bitmap, settings)
        FarmerState.lastDetections.value = if (settings.debugMarkers) detections else emptyList()
        FarmerState.addScan()

        if (detections.isNotEmpty()) {
            idleSince = now
            var taps = 0
            for (d in detections) {
                if (taps >= settings.tapsPerScan) break
                val key = cellKey(d.x, d.y)
                if (now - (tappedCells[key] ?: 0L) < settings.tapCooldownMs) continue
                GestureController.tap(d.x, d.y, 85L)
                tappedCells[key] = now
                FarmerState.addTap()
                taps++
            }
            if (taps > 0) {
                typicalCoinCount = (typicalCoinCount * 0.8f + detections.size * 0.2f)
                    .roundToInt()
                    .coerceIn(3, 24)
            }
            if (taps > 0 && now - lastLogAt > 1500L) {
                lastLogAt = now
                FarmerState.log("Собрано монет: $taps", FarmerState.Level.OK)
            }
            return
        }

        if (!settings.autoIslandSwitch) return

        val (route, label) = selectRoute()
        if (route.isEmpty()) {
            if (now - lastLogAt > 10_000L) {
                lastLogAt = now
                FarmerState.log(
                    "Нет маршрута перехода — обучите его кнопкой «Начать обучение» или задайте макрос вручную",
                    FarmerState.Level.WARN,
                )
            }
            return
        }
        if (now - idleSince < settings.idleMsBeforeSwitch) return

        replayRoute(route, label)

        val totalIslands = settings.islandsCount.coerceAtLeast(1)
        islandIndex++
        if (islandIndex >= totalIslands) {
            if (!settings.loopIslands) {
                FarmerState.log("Обошли все острова ($totalIslands). Остановка", FarmerState.Level.OK)
                stop()
                return
            }
            islandIndex = 0
        }
        FarmerState.islandVisited(islandIndex)
        if (settings.autoCalibrateOnIsland) calibrateAt = now + 2000L
        tappedCells.clear()
        idleSince = now
    }

    /** Обученные маршруты имеют приоритет, если пользователь их не отключил. */
    private fun selectRoute(): Pair<List<MacroStep>, String> {
        val settings = FarmerState.settings.value
        val learned = FarmerState.learnedRoutes.value
        if (settings.preferLearnedRoutes && learned.isNotEmpty()) {
            val index = islandIndex % learned.size
            return learned[index] to "обученному маршруту №${index + 1}"
        }
        if (FarmerState.macro.value.isNotEmpty()) {
            return FarmerState.macro.value to "ручному макросу"
        }
        if (learned.isNotEmpty()) {
            val index = islandIndex % learned.size
            return learned[index] to "обученному маршруту №${index + 1}"
        }
        return emptyList<MacroStep>() to ""
    }

    private fun replayRoute(steps: List<MacroStep>, label: String) {
        if (steps.isEmpty()) return
        val delay = FarmerState.settings.value.replayDelayMs.coerceAtLeast(0L)
        FarmerState.log("Переход к острову №${islandIndex + 1} по $label", FarmerState.Level.OK)
        for (step in steps) {
            if (!running) break
            when (step) {
                is MacroStep.Tap -> GestureController.tap(step.x, step.y, 90L)
                is MacroStep.Swipe -> GestureController.swipe(
                    step.fromX,
                    step.fromY,
                    step.toX,
                    step.toY,
                    step.durationMs,
                )
                is MacroStep.Wait -> Unit
            }
            SystemClock.sleep(if (step is MacroStep.Wait) step.durationMs else step.afterMs + delay)
        }
    }

    private fun cellKey(x: Float, y: Float): Long =
        ((x / 36f).toLong() shl 32) or ((y / 36f).toLong() and 0xFFFFFFFFL)
}
