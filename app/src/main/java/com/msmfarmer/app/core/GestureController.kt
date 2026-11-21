package com.msmfarmer.app.core

import android.accessibilityservice.AccessibilityService
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import com.msmfarmer.app.service.FarmerAccessibilityService
import com.msmfarmer.app.state.FarmerState

/** Отправляет тапы/свайпы через службу доступности. */
object GestureController {

    private val mainHandler = Handler(Looper.getMainLooper())

    fun tap(x: Float, y: Float, durationMs: Long = 90L) {
        val path = Path().apply {
            moveTo(x, y)
            lineTo(x + 0.01f, y + 0.01f)
        }
        dispatch(buildGesture(path, durationMs), "тап ($x, $y)")
    }

    fun swipe(
        fromX: Float,
        fromY: Float,
        toX: Float,
        toY: Float,
        durationMs: Long = 320L,
    ) {
        val path = Path().apply {
            moveTo(fromX, fromY)
            lineTo(toX, toY)
        }
        dispatch(buildGesture(path, durationMs), "свайп")
    }

    private fun buildGesture(path: Path, durationMs: Long): AccessibilityService.GestureDescription =
        AccessibilityService.GestureDescription.Builder()
            .addStroke(
                AccessibilityService.GestureDescription.StrokeDescription(
                    path,
                    0,
                    durationMs.coerceAtLeast(16L),
                )
            )
            .build()

    private fun dispatch(gesture: AccessibilityService.GestureDescription, label: String) {
        val service = FarmerAccessibilityService.instance
        if (service == null) {
            FarmerState.log("Служба доступности не включена — жест «$label» пропущен", FarmerState.Level.WARN)
            return
        }
        mainHandler.post {
            val ok = service.dispatchGesture(gesture, null, null)
            if (!ok) FarmerState.log("Не удалось выполнить жест «$label»", FarmerState.Level.WARN)
        }
    }
}
