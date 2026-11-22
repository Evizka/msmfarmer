package com.msmfarmer.app.core

/** Чистые проверки безопасности доступны и в локальных unit-тестах. */
internal object SafetyChecks {
    fun captureMatches(frameWidth: Int, frameHeight: Int, surfaceWidth: Int, surfaceHeight: Int,
                       screenWidth: Int, screenHeight: Int, contentWidth: Int, contentHeight: Int): Boolean =
        screenWidth > 0 && screenHeight > 0 && frameWidth == screenWidth && frameHeight == screenHeight &&
            surfaceWidth == screenWidth && surfaceHeight == screenHeight &&
            ((contentWidth == 0 && contentHeight == 0) ||
                (contentWidth == screenWidth && contentHeight == screenHeight))

    fun dropAutomatic(ageMs: Long, paused: Boolean, learning: Boolean, valid: Boolean): Boolean =
        ageMs > 1500L || paused || learning || !valid
}
