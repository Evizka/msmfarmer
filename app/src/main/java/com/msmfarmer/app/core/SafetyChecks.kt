package com.msmfarmer.app.core

/** Чистые проверки безопасности доступны и в локальных unit-тестах. */
internal object SafetyChecks {
    fun captureMatches(frameWidth: Int, frameHeight: Int, surfaceWidth: Int, surfaceHeight: Int,
                       screenWidth: Int, screenHeight: Int, contentWidth: Int, contentHeight: Int): Boolean =
        screenWidth > 0 && screenHeight > 0 && frameWidth == screenWidth && frameHeight == screenHeight &&
            surfaceWidth == screenWidth && surfaceHeight == screenHeight &&
            ((contentWidth == 0 && contentHeight == 0) ||
                (contentWidth == screenWidth && contentHeight == screenHeight))

    /** Яркости хранятся в байтах как 0..255, не как знаковые значения Kotlin Byte. */
    fun grayscaleDistance(left: ByteArray, right: ByteArray): Float {
        require(left.isNotEmpty() && left.size == right.size)
        var sum = 0L
        for (i in left.indices) {
            sum += kotlin.math.abs((left[i].toInt() and 255) - (right[i].toInt() and 255))
        }
        return sum.toFloat() / left.size / 255f
    }

    fun pointsInBounds(points: FloatArray, width: Int, height: Int): Boolean =
        width > 0 && height > 0 && points.isNotEmpty() && points.size % 2 == 0 &&
            points.indices.step(2).all { i ->
                points[i].isFinite() && points[i + 1].isFinite() &&
                    points[i] >= 0f && points[i] < width && points[i + 1] >= 0f && points[i + 1] < height
            }

    /** Эвристика по отпечатку; отсутствие/повреждение назначения не даёт успеха. */
    fun arrivalConfirmed(change: Float, destinationDistance: Float): Boolean =
        change.isFinite() && destinationDistance.isFinite() && change > 0.06f && destinationDistance < 0.14f

    fun routeAllowed(running: Boolean, paused: Boolean, learning: Boolean, sameGeneration: Boolean): Boolean =
        running && !paused && !learning && sameGeneration

    fun dropAutomatic(ageMs: Long, paused: Boolean, learning: Boolean, valid: Boolean): Boolean =
        ageMs > 1500L || paused || learning || !valid
}
