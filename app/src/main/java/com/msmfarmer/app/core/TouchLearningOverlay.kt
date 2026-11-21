package com.msmfarmer.app.core

import android.content.Context
import android.graphics.PixelFormat
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.msmfarmer.app.model.MacroStep
import kotlin.math.hypot

/**
 * Прозрачный слой для «обучения по демонстрации».
 *
 * Перехватывает тап/свайп пользователя, запоминает его как шаг маршрута
 * и тут же отправляет то же действие в игру через доступность.
 * Кнопка «✓ Остров» отмечает, что переход завершён, и сохраняет маршрут.
 *
 * Экспериментальная функция: на некоторых оболочках система может блокировать
 * наложение или собственные жесты доступности.
 */
class TouchLearningOverlay(
    context: Context,
    private val onGesture: (MacroStep) -> Unit,
    private val onMark: () -> Unit,
    private val onStop: () -> Unit,
) {
    private val appContext = context.applicationContext
    private val windowManager = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val density = appContext.resources.displayMetrics.density

    private var shown = false
    private var ignoreUntil = 0L
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var downAt = 0L

    private val touchView = View(appContext).apply {
        isClickable = true
        setOnTouchListener { _, event -> handleTouch(event) }
    }

    private val markButton = Button(appContext).apply {
        text = "✓ Остров"
        textSize = 12f
        setOnClickListener { onMark() }
    }

    private val stopButton = Button(appContext).apply {
        text = "⏹"
        textSize = 12f
        setOnClickListener { onStop() }
    }

    private val controls = LinearLayout(appContext).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.END
        val lp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { setMargins(4, 0, 4, 0) }
        addView(markButton, lp)
        addView(stopButton, lp)
    }

    private val root = FrameLayout(appContext).apply {
        addView(
            touchView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        addView(
            controls,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.END,
            ).apply { topMargin = (8f * density).toInt() },
        )
    }

    private val params = WindowManager.LayoutParams().apply {
        type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        format = PixelFormat.TRANSLUCENT
        gravity = Gravity.TOP or Gravity.START
        width = ViewGroup.LayoutParams.MATCH_PARENT
        height = ViewGroup.LayoutParams.MATCH_PARENT
    }

    fun show(): Boolean {
        if (!Settings.canDrawOverlays(appContext)) return false
        if (!shown) {
            windowManager.addView(root, params)
            shown = true
        }
        return true
    }

    fun hide() {
        if (!shown) return
        try {
            windowManager.removeView(root)
        } finally {
            shown = false
        }
    }

    private fun handleTouch(event: MotionEvent): Boolean {
        // Жесты, которые мы сами отправляем через доступность, не записываем.
        if (SystemClock.uptimeMillis() < ignoreUntil) return false

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.rawX
                downY = event.rawY
                lastX = event.rawX
                lastY = event.rawY
                downAt = SystemClock.uptimeMillis()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                lastX = event.rawX
                lastY = event.rawY
                return true
            }
            MotionEvent.ACTION_UP -> {
                lastX = event.rawX
                lastY = event.rawY
                emit()
                return true
            }
            MotionEvent.ACTION_CANCEL -> return true
        }
        return false
    }

    private fun emit() {
        val dx = lastX - downX
        val dy = lastY - downY
        val elapsed = (SystemClock.uptimeMillis() - downAt).coerceIn(30L, 1200L)

        if (hypot(dx, dy) < 18f && elapsed < 600L) {
            val step = MacroStep.Tap(downX, downY, elapsed)
            onGesture(step)
            ignoreUntil = SystemClock.uptimeMillis() + elapsed + 300L
            GestureController.tap(downX, downY, elapsed)
        } else {
            val duration = elapsed.coerceIn(80L, 1200L)
            val step = MacroStep.Swipe(downX, downY, lastX, lastY, duration)
            onGesture(step)
            ignoreUntil = SystemClock.uptimeMillis() + duration + 300L
            GestureController.swipe(downX, downY, lastX, lastY, duration)
        }
    }
}
