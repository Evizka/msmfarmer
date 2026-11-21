package com.msmfarmer.app.core

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.DisplayMetrics
import android.view.WindowManager
import com.msmfarmer.app.state.FarmerState
import java.util.concurrent.atomic.AtomicReference

/** Захват экрана через MediaProjection (без root). */
class ScreenCapturer(
    context: Context,
    private val resultCode: Int,
    data: Intent,
) {
    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    private val projectionIntent = data

    val width: Int
    val height: Int
    private val density: Int

    @Volatile
    private var projection: MediaProjection? = null
    private var reader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null
    private val latestRef = AtomicReference<Bitmap?>(null)

    private val thread = HandlerThread("msm-capture").apply { start() }
    private val handler = Handler(thread.looper)
    private var rowBuffer: ByteArray? = null
    private var pixelBuffer: IntArray? = null
    private var stopped = false

    init {
        val wm = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = wm.currentWindowMetrics.bounds
            width = bounds.width()
            height = bounds.height()
        } else {
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(metrics)
            width = metrics.widthPixels
            height = metrics.heightPixels
        }
        density = appContext.resources.configuration.densityDpi
    }

    val latest: Bitmap?
        get() = latestRef.get()

    fun start() {
        if (stopped) return
        val mp = manager.getMediaProjection(resultCode, projectionIntent)
        projection = mp

        mp.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                FarmerState.log("Захват экрана остановлен системой", FarmerState.Level.WARN)
                latestRef.set(null)
                FarmerState.capturing.value = false
            }
        }, handler)

        val imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 3)
        reader = imageReader
        imageReader.setOnImageAvailableListener({ r ->
            val image = r.acquireLatestImage() ?: return@setOnImageAvailableListener
            try {
                val bitmap = image.toBitmap()
                if (bitmap != null) latestRef.set(bitmap)
            } catch (t: Throwable) {
                FarmerState.log("Ошибка захвата кадра: ${t.message}", FarmerState.Level.WARN)
            } finally {
                image.close()
            }
        }, handler)

        virtualDisplay = mp.createVirtualDisplay(
            "MSMFarmerCapture",
            width,
            height,
            density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader.surface,
            null,
            handler,
        )
        FarmerState.capturing.value = true
        FarmerState.log("Захват экрана запущен: ${width}x$height", FarmerState.Level.OK)
    }

    fun stop() {
        stopped = true
        try {
            virtualDisplay?.release()
            reader?.close()
            projection?.stop()
            projection = null
        } catch (t: Throwable) {
            FarmerState.log("Ошибка остановки захвата: ${t.message}", FarmerState.Level.WARN)
        }
        latestRef.set(null)
        FarmerState.capturing.value = false
        thread.quitSafely()
    }

    private fun Image.toBitmap(): Bitmap? {
        val plane = planes.firstOrNull() ?: return null
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        if (rowStride < width * pixelStride) return null

        val buffer = plane.buffer
        buffer.rewind()

        val row = rowBuffer?.takeIf { it.size >= rowStride } ?: ByteArray(rowStride).also { rowBuffer = it }
        val pixels = pixelBuffer?.takeIf { it.size >= width * height }
            ?: IntArray(width * height).also { pixelBuffer = it }

        for (y in 0 until height) {
            buffer.get(row, 0, rowStride)
            val offset = y * width
            for (x in 0 until width) {
                val i = x * pixelStride
                val r = row[i].toInt() and 0xFF
                val g = row[i + 1].toInt() and 0xFF
                val b = row[i + 2].toInt() and 0xFF
                val a = row[i + 3].toInt() and 0xFF
                pixels[offset + x] = (a shl 24) or (r shl 16) or (g shl 8) or b
            }
        }

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }
}
