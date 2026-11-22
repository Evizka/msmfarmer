package com.msmfarmer.app.core

import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import com.msmfarmer.app.model.MacroStep
import com.msmfarmer.app.state.FarmerState
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicBoolean

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
    private val routeGeneration = AtomicLong(0)

    /** Также инвалидирует шаги, ещё не доставленные в main-поток. */
    fun interruptRoute() { routeGeneration.incrementAndGet() }

    private var idleSince = 0L
    private var islandIndex = 0
    private var lastLogAt = 0L
    private var lastDiagLogAt = 0L
    private var lastMineLogAt = 0L
    private data class SelectedRoute(val steps: List<MacroStep>, val label: String, val destination: String = "")

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
        interruptRoute()
        GestureController.clear()
        handler.removeCallbacksAndMessages(null)
        thread.quitSafely()
    }

    fun replayMacroNow() {
        if (!running) {
            FarmerState.log("Сначала запустите сбор", FarmerState.Level.WARN)
            return
        }
        val requestedGeneration = routeGeneration.get()
        if (FarmerState.paused.value || FarmerState.learning.value) {
            FarmerState.log("Нельзя запустить маршрут во время паузы или обучения", FarmerState.Level.WARN)
            return
        }
        handler.post {
            val route = selectRoute()
            if (route.steps.isEmpty()) {
                FarmerState.log("Нет ни обученных маршрутов, ни ручного макроса", FarmerState.Level.WARN)
            } else {
                replayRoute(route, requestedGeneration)
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
        if (!capturer.matchesScreen(bitmap)) {
            FarmerState.hudStatus.value = "Ожидаю полноэкранный захват"
            if (now - lastDiagLogAt > 4000L) {
                lastDiagLogAt = now
                FarmerState.log("Тапы приостановлены: ${capturer.dimensions()}. Выберите захват всего экрана.", FarmerState.Level.WARN)
            }
            return
        }

        val settings = FarmerState.settings.value

        FarmerState.lastFrame.value = bitmap
        val detections = CoinDetector.detect(bitmap, settings)
        if (settings.mineDetectionOnly && now - lastMineLogAt >= 8000L) {
            lastMineLogAt = now
            val mineHits = CoinDetector.findMineCandidates(bitmap)
            FarmerState.log("Шахта: кандидатов=${mineHits.size}, только диагностика, без нажатий " +
                mineHits.take(3).joinToString { "(${it.x.toInt()},${it.y.toInt()} совп=${(it.score * 100).toInt()}%)" }, FarmerState.Level.INFO)
        }
        FarmerState.lastDetections.value = if (settings.debugMarkers) detections else emptyList()
        FarmerState.detectorDebug.value = CoinDetector.lastDebug.summary()
        FarmerState.addScan()

        if (now - lastDiagLogAt > 4000L) {
            lastDiagLogAt = now
            FarmerState.log("Диагностика: ${CoinDetector.lastDebug.summary()} | ${capturer.dimensions()} | ${GestureController.summary()}", FarmerState.Level.INFO)
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
                // В режиме поиска по иконке доверяем детектору и не баним
                // реальные монеты, которые держатся на месте несколько секунд.
                // Список игнора нужен только при откате на поиск по цвету.
                val repeat = (stubborn[key] ?: 0) + 1
                stubborn[key] = repeat
                if (CoinDetector.lastDebug.fallbackColor && repeat >= 2) {
                    blacklist[key] = now + 300_000L
                    stubborn.remove(key)
                    FarmerState.ignoredSpots.value = FarmerState.ignoredSpots.value + 1
                    FarmerState.log(
                        "Статичная жёлтая деталь ${keyCoords[key] ?: ""} — добавляю в игнор на 5 мин",
                        FarmerState.Level.WARN,
                    )
                    continue
                }
                GestureController.tap(d.x, d.y, 85L, automatic = true,
                    isValid = { running && capturer.matchesScreen(bitmap) })
                tappedCells[key] = now
                FarmerState.addTap()
                taps++
            }
            if (taps > 0 && now - lastLogAt > 1500L) {
                lastLogAt = now
                FarmerState.log("Нажатий за проход: $taps", FarmerState.Level.OK)
            }
            return
        }

        FarmerState.hudStatus.value = "Сканирую — монет не видно"
        if (!settings.autoIslandSwitch) return

        val route = selectRoute()
        if (route.steps.isEmpty()) {
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

        val routeGenerationAtStart = routeGeneration.get()
        if (!replayRoute(route, routeGenerationAtStart)) {
            idleSince = SystemClock.elapsedRealtime()
            if (running && !FarmerState.paused.value && !FarmerState.learning.value &&
                routeGenerationAtStart == routeGeneration.get()) {
                FarmerState.settings.value = FarmerState.settings.value.copy(autoIslandSwitch = false)
                FarmerState.log("Автопереход отключён: прибытие не подтверждено. Проверьте маршрут и включите снова.", FarmerState.Level.WARN)
            }
            return
        }

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
        tappedCells.clear()
        idleSince = now
    }

    /** Обученные маршруты имеют приоритет, если пользователь их не отключил. */
    private fun selectRoute(): SelectedRoute {
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
                return SelectedRoute(route.steps, "выученному маршруту №${bySource.first + 1} (экран узнан)", route.destination)
            }
            val byDestination = learned.mapIndexed { i, r -> i to ScreenFingerprint.distance(current, r.destination) }
                .filter { it.second < 0.14f }
                .minByOrNull { it.second }
            if (byDestination != null) {
                val next = (byDestination.first + 1) % learned.size
                return SelectedRoute(learned[next].steps, "выученному маршруту №${next + 1} (следующий остров)", learned[next].destination)
            }
        }
        if (settings.preferLearnedRoutes && learned.isNotEmpty()) {
            val index = islandIndex % learned.size
            return SelectedRoute(learned[index].steps, "обученному маршруту №${index + 1}", learned[index].destination)
        }
        if (FarmerState.macro.value.isNotEmpty()) {
            return SelectedRoute(FarmerState.macro.value, "ручному макросу")
        }
        if (learned.isNotEmpty()) {
            val index = islandIndex % learned.size
            return SelectedRoute(learned[index].steps, "обученному маршруту №${index + 1}", learned[index].destination)
        }
        return SelectedRoute(emptyList(), "")
    }

    /** Ждём фактический callback каждого шага, иначе пауза и задержки неверны. */
    private fun replayRoute(route: SelectedRoute, generation: Long = routeGeneration.get()): Boolean {
        val steps = route.steps
        if (steps.isEmpty()) return false
        val frame = capturer.latest ?: return false
        fun allowed(): Boolean = SafetyChecks.routeAllowed(running, FarmerState.paused.value,
            FarmerState.learning.value, routeGeneration.get() == generation) && capturer.matchesScreen(frame)
        fun interruptibleWait(ms: Long): Boolean {
            val deadline = SystemClock.elapsedRealtime() + ms.coerceAtLeast(0L)
            while (allowed()) {
                val remaining = deadline - SystemClock.elapsedRealtime()
                if (remaining <= 0) return true
                SystemClock.sleep(minOf(remaining, 100L))
            }
            return false
        }
        if (!allowed()) return false
        val delay = FarmerState.settings.value.replayDelayMs.coerceAtLeast(0L)
        val before = ScreenFingerprint.from(frame)
        FarmerState.hudStatus.value = "Переход на остров №${islandIndex + 1}"
        FarmerState.log("Переход к острову №${islandIndex + 1} по ${route.label}", FarmerState.Level.INFO)
        for (step in steps) {
            if (!allowed()) {
                FarmerState.log("Маршрут прерван: пауза, обучение, остановка или изменение экрана", FarmerState.Level.WARN)
                return false
            }
            if (step is MacroStep.Wait) {
                FarmerState.hudStatus.value = "Жду загрузку острова"
                if (!interruptibleWait(step.durationMs)) return false
                continue
            }
            val done = CountDownLatch(1)
            val success = AtomicBoolean(false)
            val callback: (Boolean) -> Unit = { ok -> success.set(ok); done.countDown() }
            when (step) {
                is MacroStep.Tap -> GestureController.tap(step.x, step.y, 90L,
                    isValid = { allowed() }, onResult = callback)
                is MacroStep.Swipe -> GestureController.swipe(step.fromX, step.fromY, step.toX, step.toY,
                    step.durationMs, isValid = { allowed() }, onResult = callback)
                is MacroStep.Wait -> Unit
            }
            while (allowed() && !done.await(100, TimeUnit.MILLISECONDS)) { /* ожидание callback */ }
            if (!allowed() || !success.get()) {
                FarmerState.log("Маршрут прерван: жест не завершён", FarmerState.Level.WARN)
                return false
            }
            if (!interruptibleWait(step.afterMs + delay)) return false
        }
        if (!allowed()) return false
        // Выполненные жесты не доказывают прибытия: ищем совпадение с обученным экраном.
        val deadline = SystemClock.elapsedRealtime() + 6000L
        var delta = 0f
        var destinationDelta = Float.MAX_VALUE
        while (allowed() && SystemClock.elapsedRealtime() < deadline) {
            val after = capturer.latest?.let { ScreenFingerprint.from(it) } ?: ""
            if (after.isNotEmpty()) {
                delta = ScreenFingerprint.distance(before, after)
                if (route.destination.isNotEmpty()) destinationDelta = ScreenFingerprint.distance(after, route.destination)
                if (SafetyChecks.arrivalConfirmed(delta, destinationDelta)) {
                    FarmerState.log("Экран изменился и похож на обученное прибытие (Δ=${String.format(java.util.Locale.US, "%.3f", delta)}, до цели=${String.format(java.util.Locale.US, "%.3f", destinationDelta)})", FarmerState.Level.OK)
                    FarmerState.hudStatus.value = "Найден похожий остров — ищу монеты"
                    return true
                }
            }
            if (!interruptibleWait(200L)) return false
        }
        if (!allowed()) return false
        FarmerState.log(if (route.destination.isEmpty())
            "Маршрут выполнен, но экран назначения не обучен — остров не засчитан"
            else "Прибытие не подтверждено: Δ=${String.format(java.util.Locale.US, "%.3f", delta)}, до цели=${String.format(java.util.Locale.US, "%.3f", destinationDelta)}", FarmerState.Level.WARN)
        return false
    }

    private fun cellKey(x: Float, y: Float): Long =
        ((x / 36f).toLong() shl 32) or ((y / 36f).toLong() and 0xFFFFFFFFL)
}
