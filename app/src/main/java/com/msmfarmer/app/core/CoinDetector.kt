package com.msmfarmer.app.core

import android.content.Context
import android.graphics.Bitmap
import com.msmfarmer.app.model.Detection
import com.msmfarmer.app.model.FarmerSettings
import com.msmfarmer.app.model.maxArea
import com.msmfarmer.app.model.minArea
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** Статистика одного прохода детектора — нужна, чтобы понять, почему нет тапов. */
data class DetectorDebug(
    val yellowPixels: Int = 0,
    val components: Int = 0,
    val rejectedArea: Int = 0,
    val rejectedShape: Int = 0,
    val candidates: Int = 0,
    val samples: String = "",
    val frameSize: String = "",
    val templateReady: Boolean = false,
    val matchedIcons: Int = 0,
    val bestTemplateScore: Float = 0f,
    val fallbackColor: Boolean = false,
) {
    fun summary(): String =
        "жёлтых=$yellowPixels компонент=$components отсеяно(размер)=$rejectedArea " +
            "отсеяно(форма)=$rejectedShape кандидатов=$candidates " +
            "иконок=$matchedIcons шаблон=${if (templateReady) "готов" else "нет"} " +
            "режим=${if (fallbackColor) "цвет" else "иконка"} " +
            "лучший=${(bestTemplateScore * 100).toInt()}% $frameSize $samples"
}

/**
 * Ищет жёлтые/золотые иконки по пикселям кадра.
 * Шаг сэмплирования 3 — мелкие иконки не теряются.
 */
object CoinDetector {

    private const val SAMPLE = 3
    private var pixelBuffer: IntArray? = null

    @Volatile
    var lastDebug: DetectorDebug = DetectorDebug()
        private set

    /** Эталон иконки монеты загружается один раз при старте приложения. */
    fun prepare(context: Context) {
        CoinTemplate.load(context.applicationContext)
    }

    @Synchronized
    fun detect(bitmap: Bitmap, settings: FarmerSettings): List<Detection> {
        val w = bitmap.width
        val h = bitmap.height
        if (w < SAMPLE || h < SAMPLE) {
            lastDebug = DetectorDebug(frameSize = "${w}x$h")
            return emptyList()
        }

        val sw = (w + SAMPLE - 1) / SAMPLE
        val sh = (h + SAMPLE - 1) / SAMPLE
        val pixels = pixelBuffer?.takeIf { it.size == w * h } ?: IntArray(w * h).also { pixelBuffer = it }
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        val topRows = (h * settings.ignoreTopFraction / SAMPLE).toInt()
        val bottomRows = (h * settings.ignoreBottomFraction / SAMPLE).toInt()
        val mask = BooleanArray(sw * sh)
        var yellowPixels = 0
        val samples = ArrayList<String>(3)

        for (sy in 0 until sh) {
            if (sy < topRows || sy >= sh - bottomRows) continue
            val py = min(h - 1, sy * SAMPLE + SAMPLE / 2)
            val row = py * w
            for (sx in 0 until sw) {
                val px = min(w - 1, sx * SAMPLE + SAMPLE / 2)
                val argb = pixels[row + px]
                val r = (argb shr 16) and 0xFF
                val g = (argb shr 8) and 0xFF
                val b = argb and 0xFF
                if (isCoinColor(r, g, b, settings.sensitivity)) {
                    mask[sy * sw + sx] = true
                    yellowPixels++
                    if (samples.size < 3 && r > 160 && g > 90 && b < 140) {
                        samples.add("RGB($r,$g,$b)")
                    }
                }
            }
        }

        val visited = BooleanArray(sw * sh)
        val queue = IntArray(sw * sh)
        val found = ArrayList<Detection>()
        var rejectedArea = 0
        var rejectedShape = 0

        for (start in mask.indices) {
            if (!mask[start] || visited[start]) continue

            var head = 0
            var tail = 0
            queue[tail++] = start
            visited[start] = true

            var count = 0
            var sumX = 0
            var sumY = 0
            var minX = sw
            var maxX = 0
            var minY = sh
            var maxY = 0

            while (head < tail) {
                val idx = queue[head++]
                val cx = idx % sw
                val cy = idx / sw
                count++
                sumX += cx
                sumY += cy
                if (cx < minX) minX = cx
                if (cx > maxX) maxX = cx
                if (cy < minY) minY = cy
                if (cy > maxY) maxY = cy

                if (cx > 0 && mask[idx - 1] && !visited[idx - 1]) {
                    visited[idx - 1] = true
                    queue[tail++] = idx - 1
                }
                if (cx < sw - 1 && mask[idx + 1] && !visited[idx + 1]) {
                    visited[idx + 1] = true
                    queue[tail++] = idx + 1
                }
                if (cy > 0 && mask[idx - sw] && !visited[idx - sw]) {
                    visited[idx - sw] = true
                    queue[tail++] = idx - sw
                }
                if (cy < sh - 1 && mask[idx + sw] && !visited[idx + sw]) {
                    visited[idx + sw] = true
                    queue[tail++] = idx + sw
                }
            }

            val bw = maxX - minX + 1
            val bh = maxY - minY + 1
            val area = count * SAMPLE * SAMPLE.toFloat()
            if (area < settings.minArea || area > settings.maxArea) {
                rejectedArea++
                continue
            }
            val aspect = bw.toFloat() / bh.toFloat()
            val fill = count.toFloat() / (bw * bh)
            if (aspect < 0.35f || aspect > 2.8f || fill < 0.15f || fill > 0.97f) {
                rejectedShape++
                continue
            }

            val radius = (max(bw, bh) * SAMPLE * 0.58f)
                .coerceIn(settings.minCoinRadiusPx.toFloat(), settings.maxCoinRadiusPx.toFloat())
            val score = fill * (1f - abs(aspect - 1f) * 0.25f)
            found.add(
                Detection(
                    x = (sumX.toFloat() / count + 0.5f) * SAMPLE,
                    y = (sumY.toFloat() / count + 0.5f) * SAMPLE,
                    radius = radius,
                    score = score,
                )
            )
        }

        val merged = mergeClose(found)
        // Если эталон загружен, доверяем только ему: жёлтый декор больше
        // не приводит к тапам по пустым местам.
        val iconHits = if (CoinTemplate.isReady) CoinTemplate.find(bitmap, pixels) else emptyList()
        // Если эталон ничего не нашёл (иконка другого размера/вида), всё же
        // продолжаем собирать по цвету — чтобы бот не вставал совсем.
        val fallback = CoinTemplate.isReady && iconHits.isEmpty()
        val result = if (iconHits.isNotEmpty() || !CoinTemplate.isReady) {
            if (CoinTemplate.isReady) iconHits else merged
        } else {
            merged
        }

        lastDebug = DetectorDebug(
            yellowPixels = yellowPixels,
            components = found.size,
            rejectedArea = rejectedArea,
            rejectedShape = rejectedShape,
            candidates = result.size,
            samples = samples.joinToString(" "),
            frameSize = "${w}x$h",
            templateReady = CoinTemplate.isReady,
            matchedIcons = iconHits.size,
            bestTemplateScore = iconHits.maxOfOrNull { it.score } ?: 0f,
            fallbackColor = fallback,
        )
        return result
    }

    @Synchronized
    fun autoCalibrate(bitmap: Bitmap, baseSettings: FarmerSettings, target: Int = 8): Float {
        val candidates = listOf(0.20f, 0.30f, 0.40f, 0.50f, 0.60f, 0.70f, 0.80f, 0.90f, 0.95f)
        var best = baseSettings.sensitivity
        var bestScore = Int.MAX_VALUE
        for (candidate in candidates) {
            val count = detect(bitmap, baseSettings.copy(sensitivity = candidate)).size
            val score = abs(count - target)
            if (score <= bestScore) {
                bestScore = score
                best = candidate
            }
        }
        return best
    }

    private fun mergeClose(items: List<Detection>): List<Detection> {
        val result = ArrayList<Detection>()
        for (item in items.sortedByDescending { it.score }) {
            val threshold = item.radius * 1.5f
            val duplicate = result.any { other -> hypot(other.x - item.x, other.y - item.y) < threshold }
            if (!duplicate) result.add(item)
        }
        return result
    }

    /**
     * Более мягкий фильтр, чем раньше: ловим жёлтые, золотые и оранжевые
     * иконки, включая полупрозрачные и с белым бликом.
     */
    private fun isCoinColor(r: Int, g: Int, b: Int, sensitivity: Float): Boolean {
        val minR = 165 - (sensitivity * 25f).toInt()
        val minG = 85 - (sensitivity * 15f).toInt()
        val maxB = 165 - (sensitivity * 35f).toInt()

        if (r < minR || g < minG || b > maxB) return false
        if (g > r + 10) return false
        if (b > g - 12) return false
        if (r < 210 && b > 90) return false

        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val saturation = (max - min) / max.toFloat()
        if (saturation < 0.28f - sensitivity * 0.08f) return false

        val hue = hueDegrees(r, g, b)
        return hue in 25f..70f
    }

    private fun hueDegrees(r: Int, g: Int, b: Int): Float {
        val rf = r / 255f
        val gf = g / 255f
        val bf = b / 255f
        val max = maxOf(rf, gf, bf)
        val min = minOf(rf, gf, bf)
        val delta = max - min
        if (delta == 0f) return 0f
        val h = when (max) {
            rf -> ((gf - bf) / delta) % 6f
            gf -> (bf - rf) / delta + 2f
            else -> (rf - gf) / delta + 4f
        }
        return ((h * 60f) + 360f) % 360f
    }
}
