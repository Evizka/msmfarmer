package com.msmfarmer.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.msmfarmer.app.MainActivity
import com.msmfarmer.app.R
import com.msmfarmer.app.core.FarmerEngine
import com.msmfarmer.app.core.ScreenCapturer
import com.msmfarmer.app.core.TouchLearningOverlay
import com.msmfarmer.app.model.MacroStep
import com.msmfarmer.app.state.FarmerState
import com.msmfarmer.app.state.SettingsStore

/** Фоновая служба: держит захват экрана и цикл сбора. */
class FarmerService : Service() {

    companion object {
        const val ACTION_START = "com.msmfarmer.app.START"
        const val ACTION_STOP = "com.msmfarmer.app.STOP"
        const val ACTION_REPLAY = "com.msmfarmer.app.REPLAY"
        const val ACTION_LEARN_START = "com.msmfarmer.app.LEARN_START"
        const val ACTION_LEARN_MARK = "com.msmfarmer.app.LEARN_MARK"
        const val ACTION_LEARN_STOP = "com.msmfarmer.app.LEARN_STOP"
        const val EXTRA_RESULT_CODE = "resultCode"
        const val EXTRA_DATA = "data"

        private const val CHANNEL_ID = "msm_farmer"
        private const val NOTIFICATION_ID = 1001
    }

    private var capturer: ScreenCapturer? = null
    private var engine: FarmerEngine? = null
    private var learningOverlay: TouchLearningOverlay? = null
    private val pendingLearnSteps = mutableListOf<MacroStep>()

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action != null && intent.action != ACTION_STOP) {
            startForeground(NOTIFICATION_ID, buildNotification())
        }
        when (intent?.action) {
            ACTION_START -> startInternal(intent)
            ACTION_STOP -> stopSelf()
            ACTION_LEARN_START -> startLearning()
            ACTION_LEARN_MARK -> commitLearnedRoute()
            ACTION_LEARN_STOP -> stopLearning()
            ACTION_REPLAY -> {
                val farmer = engine
                if (farmer != null) {
                    farmer.replayMacroNow()
                } else {
                    startForeground(NOTIFICATION_ID, buildNotification())
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

        startForeground(NOTIFICATION_ID, buildNotification())

        try {
            val capture = ScreenCapturer(this, intent.getIntExtra(EXTRA_RESULT_CODE, -1), data)
            capture.start()
            capturer = capture
            val farmer = FarmerEngine(capture)
            farmer.start()
            engine = farmer
            FarmerState.running.value = true
            FarmerState.projectionReady.value = true
        } catch (t: Throwable) {
            FarmerState.log("Не удалось запустить сбор: ${t.message}", FarmerState.Level.WARN)
            stopSelf()
        }
    }

    private fun startLearning() {
        startForeground(NOTIFICATION_ID, buildNotification())
        if (FarmerAccessibilityService.instance == null) {
            FarmerState.log("Для обучения включите службу доступности", FarmerState.Level.WARN)
            return
        }
        learningOverlay?.hide()
        pendingLearnSteps.clear()
        val overlay = TouchLearningOverlay(
            context = this,
            onGesture = { step -> pendingLearnSteps.add(step) },
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
        FarmerState.log("Обучение начато. Совершите переход и нажмите «✓ Остров»", FarmerState.Level.OK)
    }

    private fun commitLearnedRoute() {
        if (pendingLearnSteps.isEmpty()) {
            FarmerState.log("Нет записанных действий — нечего сохранять", FarmerState.Level.WARN)
            return
        }
        val routes = FarmerState.learnedRoutes.value.toMutableList()
        routes.add(pendingLearnSteps.toList())
        FarmerState.learnedRoutes.value = routes
        SettingsStore.save(this)
        pendingLearnSteps.clear()
        FarmerState.log("Маршрут №${routes.size} сохранён", FarmerState.Level.OK)
        if (engine == null && learningOverlay == null) stopSelf()
    }

    private fun stopLearning(stopServiceIfIdle: Boolean = true) {
        learningOverlay?.hide()
        learningOverlay = null
        pendingLearnSteps.clear()
        FarmerState.learning.value = false
        FarmerState.overlayReady.value = false
        if (stopServiceIfIdle && engine == null) stopSelf()
    }

    override fun onDestroy() {
        stopLearning(stopServiceIfIdle = false)
        engine?.stop()
        capturer?.stop()
        engine = null
        capturer = null
        FarmerState.running.value = false
        FarmerState.projectionReady.value = false
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
