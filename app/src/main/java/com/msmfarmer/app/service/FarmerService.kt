package com.msmfarmer.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.msmfarmer.app.MainActivity
import com.msmfarmer.app.R
import com.msmfarmer.app.core.CoinDetector
import com.msmfarmer.app.core.FarmerEngine
import com.msmfarmer.app.core.GestureController
import com.msmfarmer.app.core.FarmerHud
import com.msmfarmer.app.core.ScreenCapturer
import com.msmfarmer.app.core.ScreenFingerprint
import com.msmfarmer.app.core.TouchLearningOverlay
import com.msmfarmer.app.model.LearnedRoute
import com.msmfarmer.app.model.MacroStep
import com.msmfarmer.app.state.FarmerState
import com.msmfarmer.app.state.SettingsStore

/** Фоновая служба: держит захват экрана, цикл сбора, обучение и HUD. */
class FarmerService : Service() {

    companion object {
        const val ACTION_START = "com.msmfarmer.app.START"
        const val ACTION_STOP = "com.msmfarmer.app.STOP"
        const val ACTION_REPLAY = "com.msmfarmer.app.REPLAY"
        const val ACTION_LEARN_START = "com.msmfarmer.app.LEARN_START"
        const val ACTION_LEARN_MARK = "com.msmfarmer.app.LEARN_MARK"
        const val ACTION_LEARN_STOP = "com.msmfarmer.app.LEARN_STOP"
        const val ACTION_PAUSE = "com.msmfarmer.app.PAUSE"
        const val ACTION_HUD_TOGGLE = "com.msmfarmer.app.HUD_TOGGLE"
        const val EXTRA_RESULT_CODE = "resultCode"
        const val EXTRA_DATA = "data"

        private const val CHANNEL_ID = "msm_farmer"
        private const val NOTIFICATION_ID = 1001
    }

    private var capturer: ScreenCapturer? = null
    private var engine: FarmerEngine? = null
    private var learningOverlay: TouchLearningOverlay? = null
    private var hud: FarmerHud? = null
    private val pendingLearnSteps = mutableListOf<MacroStep>()
    private var pendingLearnSource = ""

    override fun onCreate() {
        super.onCreate()
        CoinDetector.prepare(this)
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action != null && intent.action != ACTION_STOP) {
            val type = when {
                Build.VERSION.SDK_INT < Build.VERSION_CODES.Q -> 0
                intent.action == ACTION_START || capturer != null -> ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                else -> 0
            }
            try {
                ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), type)
            } catch (e: Exception) {
                FarmerState.log("Не удалось запустить фоновую службу: ${e.message}", FarmerState.Level.WARN)
                stopSelf()
                return START_NOT_STICKY
            }
        }
        when (intent?.action) {
            ACTION_START -> startInternal(intent)
            ACTION_STOP -> stopSelf()
            ACTION_LEARN_START -> startLearning()
            ACTION_LEARN_MARK -> commitLearnedRoute()
            ACTION_LEARN_STOP -> stopLearning()
            ACTION_PAUSE -> {
                val paused = !FarmerState.paused.value
                FarmerState.paused.value = paused
                if (paused) {
                    engine?.interruptRoute()
                    GestureController.clear()
                }
                FarmerState.log(if (paused) "Сбор на паузе" else "Сбор продолжен", FarmerState.Level.INFO)
                if (engine == null && learningOverlay == null && hud == null) stopSelf()
            }
            ACTION_HUD_TOGGLE -> {
                if (hud != null) hideHud() else showHud()
                if (engine == null && learningOverlay == null && hud == null) stopSelf()
            }
            ACTION_REPLAY -> {
                val farmer = engine
                if (farmer != null) {
                    farmer.replayMacroNow()
                } else {
                    FarmerState.log("Сначала запустите сбор", FarmerState.Level.WARN)
                    stopSelf()
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun startInternal(intent: Intent) {
        if (engine != null) {
            FarmerState.log("Сбор уже запущен", FarmerState.Level.WARN)
            return
        }
        @Suppress("DEPRECATION")
        val data: Intent? = intent.getParcelableExtra(EXTRA_DATA)
        if (data == null) {
            FarmerState.log("Нет разрешения на запись экрана", FarmerState.Level.WARN)
            stopSelf()
            return
        }

        try {
            val capture = ScreenCapturer(
                context = this,
                resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, -1),
                data = data,
                onProjectionStopped = { stopSelf() },
            )
            capturer = capture
            capture.start()
            val farmer = FarmerEngine(capture)
            farmer.start()
            engine = farmer
            FarmerState.running.value = true
            FarmerState.projectionReady.value = true
            FarmerState.paused.value = false
            showHud()
        } catch (t: Throwable) {
            FarmerState.log("Не удалось запустить сбор: ${t.message}", FarmerState.Level.WARN)
            stopSelf()
        }
    }

    private fun startLearning() {
        engine?.interruptRoute()
        GestureController.clear()
        if (FarmerAccessibilityService.instance == null) {
            FarmerState.log("Для обучения включите службу доступности", FarmerState.Level.WARN)
            return
        }
        learningOverlay?.hide()
        pendingLearnSteps.clear()
        pendingLearnSource = capturer?.latest?.let { ScreenFingerprint.from(it) } ?: ""

        val overlay = TouchLearningOverlay(
            context = this,
            onGesture = { step ->
                pendingLearnSteps.add(step)
                FarmerState.hudStatus.value = "Обучение: ${MacroCodecShort.describe(step)}"
            },
            onMark = { commitLearnedRoute() },
            onStop = { stopLearning() },
        )
        if (!overlay.show()) {
            FarmerState.log("Разрешите приложению рисовать поверх других окон", FarmerState.Level.WARN)
            stopSelf()
            return
        }
        learningOverlay = overlay
        FarmerState.learning.value = true
        FarmerState.overlayReady.value = true
        FarmerState.hudStatus.value = "Обучение: снимаю экран и жесты"
        FarmerState.log("Обучение начато. Жесты и отпечаток экрана запоминаются", FarmerState.Level.OK)
    }

    private fun commitLearnedRoute() {
        if (pendingLearnSteps.isEmpty()) {
            FarmerState.log("Нет записанных действий — нечего сохранять", FarmerState.Level.WARN)
            return
        }
        val destination = capturer?.latest?.let { ScreenFingerprint.from(it) } ?: ""
        val route = LearnedRoute(
            steps = pendingLearnSteps.toList(),
            source = pendingLearnSource,
            destination = destination,
        )
        val routes = FarmerState.learnedRoutes.value.toMutableList()
        routes.add(route)
        FarmerState.learnedRoutes.value = routes
        SettingsStore.save(this)
        pendingLearnSteps.clear()
        pendingLearnSource = destination
        FarmerState.hudStatus.value = "Маршрут №${routes.size} выучен"
        FarmerState.log("Маршрут №${routes.size} сохранён с отпечатком экрана", FarmerState.Level.OK)
        if (engine == null && learningOverlay == null && hud == null) stopSelf()
    }

    private fun stopLearning(stopServiceIfIdle: Boolean = true) {
        learningOverlay?.hide()
        learningOverlay = null
        pendingLearnSteps.clear()
        FarmerState.learning.value = false
        FarmerState.overlayReady.value = false
        FarmerState.hudStatus.value = "Сканирование экрана"
        if (stopServiceIfIdle && engine == null && hud == null) stopSelf()
    }

    private fun showHud() {
        if (!Settings.canDrawOverlays(this)) return
        if (hud == null) {
            hud = FarmerHud(
                context = this,
                onTogglePause = { sendAction(ACTION_PAUSE) },
                onStop = { stopSelf() },
                onHide = { sendAction(ACTION_HUD_TOGGLE) },
            )
        }
        hud?.show()
    }

    private fun hideHud() {
        hud?.hide()
        hud = null
    }

    private fun sendAction(action: String) {
        ContextCompat.startForegroundService(
            this,
            Intent(this, FarmerService::class.java).setAction(action),
        )
    }

    override fun onDestroy() {
        GestureController.clear()
        stopLearning(stopServiceIfIdle = false)
        hideHud()
        engine?.stop()
        capturer?.stop()
        engine = null
        capturer = null
        FarmerState.running.value = false
        FarmerState.projectionReady.value = false
        FarmerState.paused.value = false
        FarmerState.hudVisible.value = false
        FarmerState.hudStatus.value = "Ожидание"
        FarmerState.lastFrame.value = null
        FarmerState.lastDetections.value = emptyList()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = getString(R.string.channel_description) }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val openApp = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val pending = PendingIntent.getActivity(this, 0, openApp, flags)

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        return builder
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(R.drawable.ic_stat)
            .setContentIntent(pending)
            .setOngoing(true)
            .build()
    }
}

private object MacroCodecShort {
    fun describe(step: MacroStep): String = when (step) {
        is MacroStep.Tap -> "тап (${step.x.toInt()}, ${step.y.toInt()})"
        is MacroStep.Swipe -> "свайп в (${step.toX.toInt()}, ${step.toY.toInt()})"
        is MacroStep.Wait -> "пауза ${step.durationMs} мс"
    }
}

fun stopFarmer(context: Context) {
    context.stopService(Intent(context, FarmerService::class.java).setAction(FarmerService.ACTION_STOP))
}

fun replayMacro(context: Context) {
    ContextCompat.startForegroundService(
        context,
        Intent(context, FarmerService::class.java).setAction(FarmerService.ACTION_REPLAY),
    )
}

fun sendLearnAction(context: Context, action: String) {
    ContextCompat.startForegroundService(
        context,
        Intent(context, FarmerService::class.java).setAction(action),
    )
}

fun sendServiceAction(context: Context, action: String) {
    ContextCompat.startForegroundService(
        context,
        Intent(context, FarmerService::class.java).setAction(action),
    )
}
