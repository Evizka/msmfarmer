package com.msmfarmer.app.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import com.msmfarmer.app.state.FarmerState

/** Даёт приложению право отправлять системные жесты (тапы/свайпы). */
class FarmerAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: FarmerAccessibilityService? = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        FarmerState.accessibilityReady.value = true
        FarmerState.log("Служба доступности включена", FarmerState.Level.OK)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        instance = null
        FarmerState.accessibilityReady.value = false
        super.onDestroy()
    }
}
