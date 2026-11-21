package com.msmfarmer.app.core

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.msmfarmer.app.state.FarmerState
import com.msmfarmer.app.ui.AppColors

/**
 * Небольшая панель поверх игр: показывает, что делает бот прямо сейчас.
 * Работает только при выданном разрешении «поверх других окон».
 */
class FarmerHud(
    context: Context,
    private val onTogglePause: () -> Unit,
    private val onStop: () -> Unit,
    private val onHide: () -> Unit,
) {
    private val appContext = context.applicationContext
    private val windowManager = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val density = appContext.resources.displayMetrics.density
    private val handler = Handler(Looper.getMainLooper())
    private var shown = false

    private val statusView = TextView(appContext).apply {
        textSize = 13f
        setTextColor(AppColors.Text)
    }
    private val statsView = TextView(appContext).apply {
        textSize = 11f
        setTextColor(AppColors.TextMuted)
    }
    private val pauseButton = Button(appContext).apply {
        text = "⏸"
        textSize = 14f
        setOnClickListener { onTogglePause() }
    }
    private val stopButton = Button(appContext).apply {
        text = "■"
        textSize = 14f
        setOnClickListener { onStop() }
    }
    private val hideButton = Button(appContext).apply {
        text = "✕"
        textSize = 14f
        setOnClickListener { onHide() }
    }

    private val root = LinearLayout(appContext).apply {
        orientation = LinearLayout.VERTICAL
        val pad = (12f * density).toInt()
        setPadding(pad, pad, pad, pad)
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 16f * density
            setColor(Color.argb(242, 27, 38, 32))
            setStroke((1f * density).toInt(), AppColors.Border)
        }
        addView(
            TextView(appContext).apply {
                text = "MSM Farmer"
                textSize = 12f
                setTextColor(AppColors.Primary)
            },
        )
        addView(statusView)
        addView(statsView)
        addView(
            LinearLayout(appContext).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(pauseButton, buttonParams())
                addView(stopButton, buttonParams())
                addView(hideButton, buttonParams())
            },
        )
    }

    private val params = WindowManager.LayoutParams().apply {
        type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        format = PixelFormat.TRANSLUCENT
        gravity = Gravity.TOP or Gravity.START
        x = (12f * density).toInt()
        y = (70f * density).toInt()
        width = (240f * density).toInt()
        height = ViewGroup.LayoutParams.WRAP_CONTENT
    }

    private val refresh = object : Runnable {
        override fun run() {
            if (!shown) return
            val stats = FarmerState.stats.value
            statusView.text = when {
                FarmerState.learning.value -> "Обучение: запоминаю экран и жесты"
                FarmerState.paused.value -> "Пауза"
                !FarmerState.running.value -> "Ожидание запуска"
                else -> FarmerState.hudStatus.value
            }
            statsView.text = "монет ${stats.taps} · островов ${stats.islandsVisited} · сканов ${stats.scans}"
            pauseButton.text = if (FarmerState.paused.value) "▶" else "⏸"
            handler.postDelayed(this, 250L)
        }
    }

    fun show(): Boolean {
        if (!Settings.canDrawOverlays(appContext)) return false
        if (shown) return true
        windowManager.addView(root, params)
        shown = true
        handler.post(refresh)
        FarmerState.hudVisible.value = true
        return true
    }

    fun hide() {
        if (!shown) return
        handler.removeCallbacks(refresh)
        try {
            windowManager.removeView(root)
        } finally {
            shown = false
            FarmerState.hudVisible.value = false
        }
    }

    private fun buttonParams() = LinearLayout.LayoutParams(
        0,
        ViewGroup.LayoutParams.WRAP_CONTENT,
        1f,
    ).apply { setMargins((2f * density).toInt(), 0, (2f * density).toInt(), 0) }
}
