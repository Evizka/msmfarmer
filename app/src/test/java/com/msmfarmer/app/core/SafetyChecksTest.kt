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
}
