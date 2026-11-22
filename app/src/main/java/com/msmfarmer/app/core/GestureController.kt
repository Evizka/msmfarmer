package com.msmfarmer.app.core

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.msmfarmer.app.service.FarmerAccessibilityService
import com.msmfarmer.app.state.FarmerState
import java.util.ArrayDeque

/** Сериализация жестов: новый dispatchGesture иначе отменяет предыдущий. */
object GestureController {
    private val main = Handler(Looper.getMainLooper())
    private val queue = ArrayDeque<Pending>()
    private var inFlight: Pending? = null
    private var watchdog: Runnable? = null
    @Volatile private var completed = 0L
    @Volatile private var cancelled = 0L
    @Volatile private var dropped = 0L
    @Volatile private var queued = 0

    private data class Pending(val gesture: GestureDescription, val label: String,
        val duration: Long, val created: Long, val automatic: Boolean,
        val isValid: (() -> Boolean)? = null)

    fun summary() = "жесты: выполнено=$completed отменено=$cancelled отброшено=$dropped очередь=$queued"

    fun tap(x: Float, y: Float, durationMs: Long = 90L, automatic: Boolean = false,
            isValid: (() -> Boolean)? = null) {
        val path = Path().apply { moveTo(x, y); lineTo(x + 0.01f, y + 0.01f) }
        enqueue(path, durationMs, "тап (${x.toInt()}, ${y.toInt()})", automatic, isValid)
    }

    fun swipe(fromX: Float, fromY: Float, toX: Float, toY: Float, durationMs: Long = 320L) {
        val path = Path().apply { moveTo(fromX, fromY); lineTo(toX, toY) }
        enqueue(path, durationMs, "свайп", false, null)
    }

    private fun enqueue(path: Path, durationMs: Long, label: String, automatic: Boolean,
                        isValid: (() -> Boolean)?) {
        val duration = durationMs.coerceAtLeast(16L)
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, duration)).build()
        val item = Pending(gesture, label, duration, SystemClock.elapsedRealtime(), automatic, isValid)
        main.post {
            if (queue.size >= 20) {
                dropped++
                FarmerState.log("Очередь жестов заполнена: $label пропущен", FarmerState.Level.WARN)
            } else {
                queue.addLast(item)
                queued = queue.size
                pump()
            }
        }
    }

    /** Удаляет ожидающие жесты; уже начатый жест завершится или будет отменён системой. */
    fun clear() {
        main.post {
            dropped += queue.size
            queue.clear()
            queued = 0
        }
    }

    private fun pump() {
        if (inFlight != null) return
        while (queue.isNotEmpty()) {
            val item = queue.removeFirst()
            queued = queue.size
            if (item.automatic && SafetyChecks.dropAutomatic(SystemClock.elapsedRealtime() - item.created,
                    FarmerState.paused.value, FarmerState.learning.value, item.isValid?.invoke() != false)) {
                dropped++
                continue
            }
            val service = FarmerAccessibilityService.instance
            if (service == null) {
                dropped += 1 + queue.size
                queue.clear(); queued = 0
                FarmerState.log("Служба доступности отключена — жесты пропущены", FarmerState.Level.WARN)
                return
            }
            inFlight = item
            val callback = object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) { finish(item, true) }
                override fun onCancelled(gestureDescription: GestureDescription?) { finish(item, false) }
            }
            val accepted = try { service.dispatchGesture(item.gesture, callback, main) }
            catch (e: Exception) {
                FarmerState.log("Ошибка жеста: ${e.message}", FarmerState.Level.WARN)
                false
            }
            if (!accepted) {
                dropped++
                inFlight = null
                FarmerState.log("Система не приняла жест: ${item.label}", FarmerState.Level.WARN)
                continue
            }
            val timeout = Runnable {
                if (inFlight === item) {
                    FarmerState.log("Нет ответа о завершении жеста: ${item.label}", FarmerState.Level.WARN)
                    finish(item, null)
                }
            }
            watchdog = timeout
            main.postDelayed(timeout, item.duration + 2000L)
            return
        }
    }

    private fun finish(item: Pending, result: Boolean?) {
        if (inFlight !== item) return
        watchdog?.let(main::removeCallbacks)
        watchdog = null
        inFlight = null
        when (result) {
            true -> { completed++; if (completed == 1L) FarmerState.log("Жест завершён: ${item.label}", FarmerState.Level.OK) }
            false -> { cancelled++; FarmerState.log("Жест отменён: ${item.label}", FarmerState.Level.WARN) }
            null -> dropped++
        }
        pump()
    }
}
