package com.msmfarmer.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.msmfarmer.app.service.FarmerService
import com.msmfarmer.app.service.sendLearnAction
import com.msmfarmer.app.service.sendServiceAction
import com.msmfarmer.app.state.FarmerState
import com.msmfarmer.app.state.SettingsStore
import com.msmfarmer.app.ui.AppRoot
import com.msmfarmer.app.ui.MSMFarmerTheme

class MainActivity : ComponentActivity() {

    private lateinit var projectionLauncher: ActivityResultLauncher<Intent>
    private lateinit var notificationLauncher: ActivityResultLauncher<String>
    private lateinit var overlayLauncher: ActivityResultLauncher<Intent>
    private var pendingLearningAfterPermission = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        SettingsStore.load(this)

        projectionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK && result.data != null) {
                val serviceIntent = Intent(this, FarmerService::class.java).apply {
                    action = FarmerService.ACTION_START
                    putExtra(FarmerService.EXTRA_RESULT_CODE, result.resultCode)
                    putExtra(FarmerService.EXTRA_DATA, result.data)
                }
                ContextCompat.startForegroundService(this, serviceIntent)
            } else {
                FarmerState.log("Разрешение на запись экрана не получено", FarmerState.Level.WARN)
            }
        }

        notificationLauncher =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                if (!granted) FarmerState.log("Уведомления отключены — служба может быть остановлена системой", FarmerState.Level.WARN)
            }

        overlayLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                if (Settings.canDrawOverlays(this)) {
                    FarmerState.overlayReady.value = true
                    if (pendingLearningAfterPermission) {
                        pendingLearningAfterPermission = false
                        startLearning()
                    }
                } else {
                    FarmerState.log("Разрешение на слой поверх окон не получено", FarmerState.Level.WARN)
                }
            }

        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            MSMFarmerTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppRoot(
                        onEnableAccessibility = { openAccessibilitySettings() },
                        onRequestCapture = { requestCapture() },
                        onStop = { stopFarmer() },
                        onReplay = { replayFarmer() },
                        onStartLearning = { startLearning() },
                        onRequestOverlay = { requestOverlayPermission() },
                        onStopLearning = { sendLearnAction(this@MainActivity, FarmerService.ACTION_LEARN_STOP) },
                        onMarkLearned = { sendLearnAction(this@MainActivity, FarmerService.ACTION_LEARN_MARK) },
                        onToggleHud = { sendServiceAction(this@MainActivity, FarmerService.ACTION_HUD_TOGGLE) },
                        onSave = { SettingsStore.save(this@MainActivity) },
                    )
                }
            }
        }
    }

    private fun requestCapture() {
        val manager = getSystemService(MediaProjectionManager::class.java)
        projectionLauncher.launch(manager.createScreenCaptureIntent())
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun requestOverlayPermission() {
        if (Settings.canDrawOverlays(this)) {
            FarmerState.overlayReady.value = true
            return
        }
        pendingLearningAfterPermission = false
        overlayLauncher.launch(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName"),
            ),
        )
    }

    override fun onResume() {
        super.onResume()
        FarmerState.overlayReady.value = Settings.canDrawOverlays(this)
    }

    private fun startLearning() {
        if (Settings.canDrawOverlays(this)) {
            sendLearnAction(this, FarmerService.ACTION_LEARN_START)
        } else {
            pendingLearningAfterPermission = true
            overlayLauncher.launch(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName"),
                ),
            )
        }
    }

    private fun stopFarmer() {
        stopService(Intent(this, FarmerService::class.java).setAction(FarmerService.ACTION_STOP))
    }

    private fun replayFarmer() {
        ContextCompat.startForegroundService(
            this,
            Intent(this, FarmerService::class.java).setAction(FarmerService.ACTION_REPLAY),
        )
    }
}
