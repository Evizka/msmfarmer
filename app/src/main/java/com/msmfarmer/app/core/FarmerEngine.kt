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
    private val stubborn = HashMap<Long, Int>()
    private val blacklist = HashMap<Long, Long>()
    private val keyCoords = HashMap<Long, String>()
    @Volatile private var running = false
    @Volatile private var stopped = false

    private var idleSince = 0L
    private var islandIndex = 0
    private var lastLogAt = 0L
    private var lastDiagLogAt = 0L
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
        if (FarmerState.paused.value) {
            FarmerState.hudStatus.value = "Пауза"
            return
        }
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
        FarmerState.detectorDebug.value = CoinDetector.lastDebug.summary()
        FarmerState.addScan()

        if (now - lastDiagLogAt > 4000L) {
            lastDiagLogAt = now
            FarmerState.log("Диагностика детектора: ${CoinDetector.lastDebug.summary()}", FarmerState.Level.INFO)
        }

        // Учимся отличать монеты от статичных жёлтых деталей: если точка
        // остаётся на месте после нескольких нажатий — помечаем её как ложную.
        detections.forEach { d ->
            val key = cellKey(d.x, d.y)
            keyCoords[key] = "(${d.x.toInt()},${d.y.toInt()})"
        }
        val blackIterator = blacklist.entries.iterator()
        while (blackIterator.hasNext()) {
            if (now >= blackIterator.next().value) blackIterator.remove()
        }

        if (detections.isNotEmpty()) {
            idleSince = now
            val active = detections.filter { now >= (blacklist[cellKey(it.x, it.y)] ?: 0L) }
            FarmerState.hudStatus.value = "Кандидатов ${active.size}, ложных игнорируется ${FarmerState.ignoredSpots.value}"
            if (active.isNotEmpty() && now - lastLogAt > 1500L) {
                lastLogAt = now
                FarmerState.log(
                    "Кандидаты: ${active.take(4).joinToString { "(${it.x.toInt()},${it.y.toInt()})" }}",
                    FarmerState.Level.INFO,
                )
            }
            var taps = 0
            for (d in active) {
                if (taps >= settings.tapsPerScan) break
                val key = cellKey(d.x, d.y)
                if (now - (tappedCells[key] ?: 0L) < settings.tapCooldownMs) continue
                // Повторное нажатие по тому же месту с интервалом больше
                // задержки означает, что это не монета, а деталь интерфейса.
                val repeat = (stubborn[key] ?: 0) + 1
                stubborn[key] = repeat
                if (repeat >= 2) {
                    blacklist[key] = now + 300_000L
                    stubborn.remove(key)
                    FarmerState.ignoredSpots.value = FarmerState.ignoredSpots.value + 1
                    FarmerState.log(
                        "Статичная жёлтая деталь ${keyCoords[key] ?: ""} — добавляю в игнор на 5 мин",
                        FarmerState.Level.WARN,
                    )
                    continue
                }
                GestureController.tap(d.x, d.y, 85L)
                tappedCells[key] = now
                FarmerState.addTap()
                taps++
            }
            if (taps > 0) {
                typicalCoinCount = (typicalCoinCount * 0.8f + active.size * 0.2f)
                    .roundToInt()
                    .coerceIn(3, 24)
            }
            if (taps > 0 && now - lastLogAt > 1500L) {
                lastLogAt = now
                FarmerState.log("Нажатий за проход: $taps", FarmerState.Level.OK)
            }
            return
        }

        FarmerState.hudStatus.value = "Сканирую — монет не видно"
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
        val current = capturer.latest?.let { ScreenFingerprint.from(it) } ?: ""

        // По отпечатку экрана понимаем, на каком острове мы сейчас,
        // и берём именно тот маршрут, который начинался здесь при обучении.
        if (learned.isNotEmpty() && current.isNotEmpty()) {
            val bySource = learned.mapIndexed { i, r -> i to ScreenFingerprint.distance(current, r.source) }
                .filter { it.second < 0.14f }
                .minByOrNull { it.second }
            if (bySource != null) {
                val route = learned[bySource.first]
                return route.steps to "выученному маршруту №${bySource.first + 1} (экран узнан)"
            }
            val byDestination = learned.mapIndexed { i, r -> i to ScreenFingerprint.distance(current, r.destination) }
                .filter { it.second < 0.14f }
                .minByOrNull { it.second }
            if (byDestination != null) {
                val next = (byDestination.first + 1) % learned.size
                return learned[next].steps to "выученному маршруту №${next + 1} (следующий остров)"
            }
        }
        if (settings.preferLearnedRoutes && learned.isNotEmpty()) {
            val index = islandIndex % learned.size
            return learned[index].steps to "обученному маршруту №${index + 1}"
        }
        if (FarmerState.macro.value.isNotEmpty()) {
            return FarmerState.macro.value to "ручному макросу"
        }
        if (learned.isNotEmpty()) {
            val index = islandIndex % learned.size
            return learned[index].steps to "обученному маршруту №${index + 1}"
        }
        return emptyList<MacroStep>() to ""
    }

    private fun replayRoute(steps: List<MacroStep>, label: String) {
        if (steps.isEmpty()) return
        val delay = FarmerState.settings.value.replayDelayMs.coerceAtLeast(0L)
        val before = capturer.latest?.let { ScreenFingerprint.from(it) } ?: ""
        FarmerState.hudStatus.value = "Переход на остров №${islandIndex + 1}"
        FarmerState.log("Переход к острову №${islandIndex + 1} по $label", FarmerState.Level.OK)
        for (step in steps) {
            if (!running) break
            when (step) {
                is MacroStep.Tap -> {
                    FarmerState.hudStatus.value = "Вывожу тап (${step.x.toInt()}, ${step.y.toInt()})"
                    GestureController.tap(step.x, step.y, 90L)
                }
                is MacroStep.Swipe -> {
                    FarmerState.hudStatus.value = "Вывожу свайп"
                    GestureController.swipe(
                        step.fromX,
                        step.fromY,
                        step.toX,
                        step.toY,
                        step.durationMs,
                    )
                }
                is MacroStep.Wait -> FarmerState.hudStatus.value = "Жду загрузку острова"
            }
            SystemClock.sleep(if (step is MacroStep.Wait) step.durationMs else step.afterMs + delay)
        }
        val after = capturer.latest?.let { ScreenFingerprint.from(it) } ?: ""
        if (before.isNotEmpty() && after.isNotEmpty() && ScreenFingerprint.changed(before, after)) {
            FarmerState.log("Экран изменился — переход завершён", FarmerState.Level.OK)
            FarmerState.hudStatus.value = "Новый остров — ищу монеты"
        }
    }

    private fun cellKey(x: Float, y: Float): Long =
        ((x / 36f).toLong() shl 32) or ((y / 36f).toLong() and 0xFFFFFFFFL)
}
