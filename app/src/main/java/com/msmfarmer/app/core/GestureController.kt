package com.msmfarmer.app.core

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Path
import android.graphics.Point
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Display
import com.msmfarmer.app.service.FarmerAccessibilityService
import com.msmfarmer.app.state.FarmerState
import java.util.ArrayDeque

/** Один жест за раз; результат означает завершение жеста, а не действие игры. */
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
        val points: FloatArray, val isValid: (() -> Boolean)?, val onResult: ((Boolean) -> Unit)?)

    fun summary() = "жесты: выполнено=$completed отменено=$cancelled отброшено=$dropped очередь=$queued"

    fun tap(x: Float, y: Float, durationMs: Long = 90L, automatic: Boolean = false,
            isValid: (() -> Boolean)? = null, onResult: ((Boolean) -> Unit)? = null) {
        val path = Path().apply { moveTo(x, y) }
        enqueue(path, durationMs, "тап (${x.toInt()}, ${y.toInt()})", automatic,
            floatArrayOf(x, y), isValid, onResult)
    }

    fun swipe(fromX: Float, fromY: Float, toX: Float, toY: Float, durationMs: Long = 320L,
              isValid: (() -> Boolean)? = null, onResult: ((Boolean) -> Unit)? = null) {
        val path = Path().apply { moveTo(fromX, fromY); lineTo(toX, toY) }
        enqueue(path, durationMs, "свайп", false, floatArrayOf(fromX, fromY, toX, toY), isValid, onResult)
    }

    private fun enqueue(path: Path, durationMs: Long, label: String, automatic: Boolean,
                        points: FloatArray, isValid: (() -> Boolean)?, onResult: ((Boolean) -> Unit)?) {
        val duration = durationMs.coerceAtLeast(16L)
        val gesture = try {
            GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, duration)).build()
        } catch (e: IllegalArgumentException) {
            main.post {
                dropped++
                FarmerState.log("Недопустимый жест $label: ${e.message}", FarmerState.Level.WARN)
                onResult?.invoke(false)
            }
            return
        }
        val item = Pending(gesture, label, duration, SystemClock.elapsedRealtime(), automatic, points, isValid, onResult)
        main.post {
            if (queue.size >= 20) {
                drop(item, "Очередь жестов заполнена: $label")
            } else {
                queue.addLast(item)
                queued = queue.size
                pump()
            }
        }
    }

    /** Ожидающие жесты удаляются, уже начатый может завершиться. */
    fun clear() {
        main.post {
            while (queue.isNotEmpty()) drop(queue.removeFirst(), null)
            queued = 0
        }
    }

    private fun drop(item: Pending, reason: String?) {
        dropped++
        if (reason != null) FarmerState.log(reason, FarmerState.Level.WARN)
        item.onResult?.invoke(false)
    }

    private fun pump() {
        if (inFlight != null) return
        while (queue.isNotEmpty()) {
            val item = queue.removeFirst()
            queued = queue.size
            val valid = item.isValid?.invoke() != false
            if (!valid || (item.automatic && SafetyChecks.dropAutomatic(SystemClock.elapsedRealtime() - item.created,
                    FarmerState.paused.value, FarmerState.learning.value, valid))) {
                drop(item, null)
                continue
            }
            val service = FarmerAccessibilityService.instance
            if (service == null) {
                drop(item, "Служба доступности отключена — жесты пропущены")
                while (queue.isNotEmpty()) drop(queue.removeFirst(), null)
                queued = 0
                return
            }
            val displays = service.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
            val size = Point()
            @Suppress("DEPRECATION")
            displays.getDisplay(Display.DEFAULT_DISPLAY)?.getRealSize(size)
            if (!SafetyChecks.pointsInBounds(item.points, size.x, size.y)) {
                drop(item, "Жест вне экрана ${size.x}x${size.y} — пропущен: ${item.label}")
                continue
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
                inFlight = null
                drop(item, "Система не приняла жест: ${item.label}")
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
        item.onResult?.invoke(result == true)
        pump()
    }
}
