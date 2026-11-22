package com.msmfarmer.app.core

import org.junit.Assert.*
import org.junit.Test

class SafetyChecksTest {
    @Test fun fullScreenAndRotation() {
        assertTrue(SafetyChecks.captureMatches(1080, 2400, 1080, 2400, 1080, 2400, 1080, 2400))
        assertFalse(SafetyChecks.captureMatches(1080, 2400, 1080, 2400, 2400, 1080, 1080, 2400))
        assertTrue(SafetyChecks.captureMatches(2400, 1080, 2400, 1080, 2400, 1080, 2400, 1080))
        assertFalse(SafetyChecks.captureMatches(2400, 1080, 2400, 1080, 2400, 1080, 1600, 900))
        assertTrue(SafetyChecks.captureMatches(2400, 1080, 2400, 1080, 2400, 1080, 0, 0))
        assertFalse(SafetyChecks.captureMatches(0, 0, 0, 0, 0, 0, 0, 0))
    }
    @Test fun expiredPausedAndInvalidAutoGesturesAreDropped() {
        assertFalse(SafetyChecks.dropAutomatic(1500, false, false, true))
        assertTrue(SafetyChecks.dropAutomatic(1501, false, false, true))
        assertTrue(SafetyChecks.dropAutomatic(1, true, false, true))
        assertTrue(SafetyChecks.dropAutomatic(1, false, true, true))
        assertTrue(SafetyChecks.dropAutomatic(1, false, false, false))
    }
    @Test fun coordinatesAndBothSwipeEndpointsChecked() {
        assertTrue(SafetyChecks.pointsInBounds(floatArrayOf(0f, 0f, 2399f, 1079f), 2400, 1080))
        assertFalse(SafetyChecks.pointsInBounds(floatArrayOf(20f, 1080f), 2400, 1080))
        assertFalse(SafetyChecks.pointsInBounds(floatArrayOf(1f, 1f, 2400f, 2f), 2400, 1080))
        assertFalse(SafetyChecks.pointsInBounds(floatArrayOf(Float.NaN, 1f), 2400, 1080))
        assertFalse(SafetyChecks.pointsInBounds(floatArrayOf(1f, 1f), 0, 0))
    }
    @Test fun interruptedRouteCannotResumeWithOldToken() {
        assertTrue(SafetyChecks.routeAllowed(true, false, false, true))
        assertFalse(SafetyChecks.routeAllowed(true, true, false, true))
        assertFalse(SafetyChecks.routeAllowed(true, false, true, true))
        assertFalse(SafetyChecks.routeAllowed(false, false, false, true))
        assertFalse(SafetyChecks.routeAllowed(true, false, false, false))
    }

}
