package com.msmfarmer.app.core

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.util.Base64

/**
 * Очень компактный «отпечаток» экрана: 24×48 точек в оттенках серого.
 * ~1.1 КБ на кадр — хватает, чтобы отличать острова и экраны меню,
 * но не привязывает обучение к конкретному разрешению.
 */
object ScreenFingerprint {

    const val WIDTH = 24
    const val HEIGHT = 48

    fun from(bitmap: Bitmap): String {
        val small = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(small)
            canvas.drawBitmap(bitmap, null, Rect(0, 0, WIDTH, HEIGHT), null)
            val pixels = IntArray(WIDTH * HEIGHT)
            small.getPixels(pixels, 0, WIDTH, 0, 0, WIDTH, HEIGHT)
            val bytes = ByteArray(pixels.size)
            for (i in pixels.indices) {
                val r = (pixels[i] shr 16) and 0xFF
                val g = (pixels[i] shr 8) and 0xFF
                val b = pixels[i] and 0xFF
                bytes[i] = ((r * 299 + g * 587 + b * 114) / 1000).toByte()
            }
            return Base64.encodeToString(bytes, Base64.NO_WRAP)
        } finally {
            small.recycle()
        }
    }

    fun distance(a: String, b: String): Float {
        if (a.isEmpty() || b.isEmpty()) return Float.MAX_VALUE
        val left = decode(a) ?: return Float.MAX_VALUE
        val right = decode(b) ?: return Float.MAX_VALUE
        if (left.size != right.size) return Float.MAX_VALUE
        if (left.isEmpty()) return Float.MAX_VALUE
        return SafetyChecks.grayscaleDistance(left, right)
    }

    fun changed(before: String, after: String, threshold: Float = 0.06f): Boolean =
        distance(before, after) > threshold

    private fun decode(value: String): ByteArray? = try {
        Base64.decode(value, Base64.NO_WRAP)
    } catch (t: Throwable) {
        null
    }
}
