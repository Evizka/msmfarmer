package com.msmfarmer.app.model

/**
 * Настройки сканирования. Все координаты — в физических пикселях экрана,
 * все задержки — в миллисекундах.
 */
data class FarmerSettings(
    val scanIntervalMs: Long = 350L,
    val sensitivity: Float = 0.55f,
    val minCoinRadiusPx: Int = 5,
    val maxCoinRadiusPx: Int = 22,
    val tapCooldownMs: Long = 1200L,
    val tapsPerScan: Int = 6,
    val ignoreTopFraction: Float = 0.10f,
    val ignoreBottomFraction: Float = 0.08f,
    val autoIslandSwitch: Boolean = false,
    val idleMsBeforeSwitch: Long = 8000L,
    val islandsCount: Int = 12,
    val loopIslands: Boolean = true,
    val replayDelayMs: Long = 400L,
    val preferLearnedRoutes: Boolean = true,
    val autoCalibrateOnIsland: Boolean = false,
    val debugMarkers: Boolean = false,
)

val FarmerSettings.minArea: Float
    get() = (minCoinRadiusPx * minCoinRadiusPx * 0.55f).coerceAtLeast(3f)

val FarmerSettings.maxArea: Float
    get() = (maxCoinRadiusPx * maxCoinRadiusPx * 2.6f).coerceAtLeast(40f)
