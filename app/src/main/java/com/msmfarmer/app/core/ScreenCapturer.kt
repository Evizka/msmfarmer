package com.msmfarmer.app.core

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.Point
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.HandlerThread
import android.view.Display
import com.msmfarmer.app.state.FarmerState
import java.util.concurrent.atomic.AtomicReference

/** Один MediaProjection и один VirtualDisplay на согласие пользователя (Android 14+). */
class ScreenCapturer(context: Context, private val resultCode: Int, data: Intent,
                     private val onProjectionStopped: () -> Unit = {}) {
    private val app = context.applicationContext
    private val manager = app.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    private val displays = app.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    private val intent = data
    private val density = app.resources.configuration.densityDpi
    private val thread = HandlerThread("msm-capture").apply { start() }
    private val handler = Handler(thread.looper)
    private val latestRef = AtomicReference<Bitmap?>(null)
    @Volatile var width = 0; private set
    @Volatile var height = 0; private set
    @Volatile private var contentWidth = 0
    @Volatile private var contentHeight = 0
    @Volatile private var stopped = false
    @Volatile private var active = false
    private var projection: MediaProjection? = null
    private var reader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var rowBuffer: ByteArray? = null
    private var pixelBuffer: IntArray? = null

    val latest: Bitmap? get() = latestRef.get()

    private fun screenSize(): Pair<Int, Int> {
        val point = Point()
        @Suppress("DEPRECATION")
        displays.getDisplay(Display.DEFAULT_DISPLAY)?.getRealSize(point)
        return point.x to point.y
    }

    /** Проверять также перед отправкой жеста, т.к. экран может повернуться после детекции. */
    fun matchesScreen(frame: Bitmap): Boolean {
        val (w, h) = screenSize()
        return active && !stopped && SafetyChecks.captureMatches(frame.width, frame.height,
            width, height, w, h, contentWidth, contentHeight)
    }

    fun dimensions(): String {
        val (w, h) = screenSize()
        return "кадр=${width}x$height экран=${w}x$h контент=${contentWidth}x$contentHeight"
    }

    fun start() {
        check(!stopped)
        val mp = manager.getMediaProjection(resultCode, intent) ?: error("Нет MediaProjection")
        projection = mp
        mp.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                if (stopped) return
                active = false
                latestRef.set(null)
                GestureController.clear()
                FarmerState.capturing.value = false
                FarmerState.log("Захват экрана остановлен системой", FarmerState.Level.WARN)
                onProjectionStopped()
            }
            override fun onCapturedContentResize(w: Int, h: Int) {
                contentWidth = w; contentHeight = h
                latestRef.set(null)
                FarmerState.log("Размер захваченного содержимого: ${w}x$h; ${dimensions()}", FarmerState.Level.INFO)
            }
        }, handler)
        val (w, h) = screenSize()
        check(w > 0 && h > 0) { "Не удалось определить размер экрана" }
        width = w; height = h
        val newReader = makeReader(w, h)
        reader = newReader
        virtualDisplay = mp.createVirtualDisplay("MSMFarmerCapture", w, h, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, newReader.surface, null, handler)
        displays.registerDisplayListener(displayListener, handler)
        active = true
        FarmerState.capturing.value = true
        FarmerState.log("Захват экрана запущен: ${w}x$h", FarmerState.Level.OK)
    }

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(id: Int) = Unit
        override fun onDisplayRemoved(id: Int) = Unit
        override fun onDisplayChanged(id: Int) {
            if (id != Display.DEFAULT_DISPLAY || stopped) return
            val (w, h) = screenSize()
            if (w <= 0 || h <= 0 || (w == width && h == height)) return
            val vd = virtualDisplay ?: return
            latestRef.set(null)
            GestureController.clear()
            val newReader = makeReader(w, h)
            try {
                // Не вызываем createVirtualDisplay повторно: токен одноразовый на API 34+.
                vd.resize(w, h, density)
                vd.surface = newReader.surface
                val old = reader
                reader = newReader
                width = w; height = h
                old?.close()
                FarmerState.log("Размер экрана изменён: ${w}x$h", FarmerState.Level.INFO)
            } catch (e: Exception) {
                newReader.close()
                FarmerState.log("Не удалось изменить размер захвата: ${e.message}", FarmerState.Level.WARN)
                FarmerState.capturing.value = false
                onProjectionStopped()
            }
        }
    }

    private fun makeReader(w: Int, h: Int): ImageReader {
        val result = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 3)
        result.setOnImageAvailableListener({ r ->
            val image = try { r.acquireLatestImage() } catch (_: IllegalStateException) { null }
                ?: return@setOnImageAvailableListener
            try {
                if (!stopped && r === reader) image.toBitmap()?.let { latestRef.set(it) }
            } catch (e: Exception) {
                FarmerState.log("Ошибка захвата кадра: ${e.message}", FarmerState.Level.WARN)
            } finally { image.close() }
        }, handler)
        return result
    }

    fun stop() {
        if (stopped) return
        stopped = true
        active = false
        latestRef.set(null)
        FarmerState.capturing.value = false
        displays.unregisterDisplayListener(displayListener)
        handler.post {
            virtualDisplay?.release(); virtualDisplay = null
            reader?.close(); reader = null
            projection?.stop(); projection = null
            thread.quitSafely()
        }
    }

    private fun Image.toBitmap(): Bitmap? {
        val plane = planes.firstOrNull() ?: return null
        val iw = width; val ih = height
        val stride = plane.rowStride; val pixelStride = plane.pixelStride
        if (pixelStride < 4 || stride < iw * pixelStride) return null
        val buffer = plane.buffer.apply { rewind() }
        val row = rowBuffer?.takeIf { it.size >= stride } ?: ByteArray(stride).also { rowBuffer = it }
        val pixels = pixelBuffer?.takeIf { it.size >= iw * ih } ?: IntArray(iw * ih).also { pixelBuffer = it }
        for (y in 0 until ih) {
            val count = minOf(stride, buffer.remaining())
            if (count < iw * pixelStride) return null
            buffer.get(row, 0, count)
            for (x in 0 until iw) {
                val i = x * pixelStride
                pixels[y * iw + x] = ((row[i + 3].toInt() and 255) shl 24) or
                    ((row[i].toInt() and 255) shl 16) or ((row[i + 1].toInt() and 255) shl 8) or (row[i + 2].toInt() and 255)
            }
        }
        return Bitmap.createBitmap(iw, ih, Bitmap.Config.ARGB_8888).also {
            it.setPixels(pixels, 0, iw, 0, 0, iw, ih)
        }
    }
}
