package com.msmfarmer.app.core

import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Поиск по реальным фрагментам иконок. Android-независимое ядро, проверяется на кадрах. */
internal object GameIconMatcher {
    const val STEP = 4
    private const val GRID = 8
    private const val MAX_ERROR = 0.10
    private const val COARSE_ERROR = 0.20
    const val MIN_STRUCTURE = 0.60f
    private val coarseIndices = intArrayOf(0, 4, 9, 14, 18, 21, 27, 28, 34, 37, 41, 46, 49, 54, 56, 60)

    data class Pattern(val width: Int, val height: Int, val pixels: IntArray)
    data class Hit(val x: Float, val y: Float, val width: Float, val height: Float, val score: Float,
                   val patternIndex: Int, val structure: Float)
    private data class Scaled(val w: Int, val h: Int, val xs: IntArray, val ys: IntArray,
                              val colors: IntArray, val coarseEnergy: Long, val fullEnergy: Long,
                              val luminance: IntArray, val lumSum: Long, val lumEnergy: Long,
                              val coarseLimit: Double)

    /** fullPixels — несжатый RGB кадр. scale=1 соответствует высоте экрана 1080. */
    fun find(fullPixels: IntArray, width: Int, height: Int, patterns: List<Pattern>,
             scale: Float, ignoreTop: Float = 0f, ignoreBottom: Float = 0f): List<Hit> {
        if (width < STEP || height < STEP || fullPixels.size < width * height || scale <= 0f) return emptyList()
        val sw = width / STEP
        val sh = height / STEP
        val sampled = IntArray(sw * sh)
        for (y in 0 until sh) for (x in 0 until sw) sampled[y * sw + x] = fullPixels[(y * STEP + 2) * width + x * STEP + 2]
        val hits = ArrayList<Hit>()
        val firstY = ((height * ignoreTop.coerceIn(0f, 0.45f)) / STEP).toInt()
        val lastY = ((height * (1f - ignoreBottom.coerceIn(0f, 0.45f))) / STEP).toInt()
        for ((patternIndex, pattern) in patterns.withIndex()) {
            if (pattern.width <= 0 || pattern.height <= 0 || pattern.pixels.size != pattern.width * pattern.height) continue
            val widths = (56..176 step 8).map { (it * scale / STEP).roundToInt().coerceAtLeast(GRID) }.distinct()
            for (w in widths) {
                val h = (w.toFloat() * pattern.height / pattern.width).roundToInt().coerceAtLeast(GRID)
                if (w >= sw || h >= sh) continue
                val p = scalePattern(pattern, w, h)
                val endY = minOf(sh - h, lastY - h)
                if (firstY > endY) continue
                for (y in firstY..endY) for (x in 0..sw - w) {
                    var diff = 0L
                    var energy = 0L
                    for (index in coarseIndices) {
                        val a = sampled[(y + p.ys[index]) * sw + x + p.xs[index]]
                        val b = p.colors[index]
                        diff += squareDifference(a, b)
                        if (diff > p.coarseLimit) break
                        energy += squareEnergy(a)
                    }
                    if (diff > p.coarseLimit || energy == 0L ||
                        diff.toDouble() * diff >= COARSE_ERROR * COARSE_ERROR * energy * p.coarseEnergy) continue
                    diff = 0; energy = 0
                    for (i in p.colors.indices) {
                        val a = sampled[(y + p.ys[i]) * sw + x + p.xs[i]]
                        diff += squareDifference(a, p.colors[i])
                        energy += squareEnergy(a)
                    }
                    if (energy == 0L || diff.toDouble() * diff >= MAX_ERROR * MAX_ERROR * energy * p.fullEnergy) continue
                    // Пространственная корреляция яркости: однотонный цветной фон
                    // имеет нулевую дисперсию, даже если его RGB-каналы различаются.
                    var lumSum = 0L; var lumEnergy = 0L; var lumProduct = 0L
                    for (i in p.colors.indices) {
                        val a = luminance(sampled[(y + p.ys[i]) * sw + x + p.xs[i]])
                        lumSum += a; lumEnergy += a * a; lumProduct += a * p.luminance[i]
                    }
                    val structure = structureSimilarity(lumSum, lumEnergy, p.lumSum, p.lumEnergy, lumProduct, p.colors.size)
                    if (structure < MIN_STRUCTURE) continue
                    val error = diff / sqrt(energy.toDouble() * p.fullEnergy)
                    hits.add(Hit((x + w / 2f) * STEP, (y + h / 2f) * STEP,
                        w.toFloat() * STEP, h.toFloat() * STEP, (1.0 - error).toFloat(), patternIndex, structure))
                }
            }
        }
        val result = ArrayList<Hit>()
        for (hit in hits.sortedByDescending { it.score }) {
            if (result.none { hypot(it.x - hit.x, it.y - hit.y) < 0.45f * minOf(it.width, hit.width) }) result.add(hit)
        }
        return result
    }

    private fun scalePattern(pattern: Pattern, w: Int, h: Int): Scaled {
        val xs = IntArray(GRID * GRID); val ys = IntArray(GRID * GRID); val colors = IntArray(GRID * GRID)
        var allEnergy = 0L
        for (gy in 0 until GRID) for (gx in 0 until GRID) {
            val i = gy * GRID + gx
            // Как при приведении шаблона к размеру окна с билинейной интерполяцией.
            val x = ((gx + 0.5f) * w / GRID - 0.5f).roundToInt().coerceIn(0, w - 1)
            val y = ((gy + 0.5f) * h / GRID - 0.5f).roundToInt().coerceIn(0, h - 1)
            xs[i] = x; ys[i] = y
            colors[i] = interpolate(pattern, (x + 0.5f) * pattern.width / w - 0.5f,
                (y + 0.5f) * pattern.height / h - 0.5f)
            allEnergy += squareEnergy(colors[i])
        }
        val luma = IntArray(colors.size) { luminance(colors[it]) }
        val lumSum = luma.sumOf { it.toLong() }
        val lumEnergy = luma.sumOf { (it * it).toLong() }
        val coarseEnergy = coarseIndices.sumOf { squareEnergy(colors[it]) }
        val maxSampleEnergy = coarseIndices.size.toDouble() * 3 * 255 * 255
        val limit = COARSE_ERROR * sqrt(maxSampleEnergy * coarseEnergy)
        return Scaled(w, h, xs, ys, colors, coarseEnergy, allEnergy, luma, lumSum, lumEnergy, limit)
    }

    private fun luminance(c: Int): Int =
        (((c shr 16) and 255) * 77 + ((c shr 8) and 255) * 150 + (c and 255) * 29) shr 8

    /** Корреляция по положениям пикселей, не между R/G/B одного пикселя. */
    internal fun structureSimilarity(sumA: Long, energyA: Long, sumB: Long, energyB: Long,
                                     product: Long, n: Int): Float {
        if (n <= 0) return 0f
        val varianceA = n.toDouble() * energyA - sumA.toDouble() * sumA
        val varianceB = n.toDouble() * energyB - sumB.toDouble() * sumB
        if (varianceA <= 1e-6 || varianceB <= 1e-6) return 0f
        val covariance = n.toDouble() * product - sumA.toDouble() * sumB
        return (covariance / sqrt(varianceA * varianceB)).toFloat().coerceIn(-1f, 1f)
    }

    private fun interpolate(p: Pattern, x: Float, y: Float): Int {
        val xx = x.coerceIn(0f, (p.width - 1).toFloat()); val yy = y.coerceIn(0f, (p.height - 1).toFloat())
        val x0 = xx.toInt(); val y0 = yy.toInt(); val x1 = minOf(x0 + 1, p.width - 1); val y1 = minOf(y0 + 1, p.height - 1)
        val fx = xx - x0; val fy = yy - y0
        val a = p.pixels[y0 * p.width + x0]; val b = p.pixels[y0 * p.width + x1]
        val c = p.pixels[y1 * p.width + x0]; val d = p.pixels[y1 * p.width + x1]
        fun channel(shift: Int): Int {
            val top = ((a shr shift) and 255) * (1f - fx) + ((b shr shift) and 255) * fx
            val bottom = ((c shr shift) and 255) * (1f - fx) + ((d shr shift) and 255) * fx
            return (top * (1f - fy) + bottom * fy).roundToInt().coerceIn(0, 255)
        }
        return (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }

    private fun squareDifference(a: Int, b: Int): Long {
        val r = ((a shr 16) and 255) - ((b shr 16) and 255)
        val g = ((a shr 8) and 255) - ((b shr 8) and 255)
        val bl = (a and 255) - (b and 255)
        return (r * r + g * g + bl * bl).toLong()
    }
    private fun squareEnergy(a: Int): Long {
        val r = (a shr 16) and 255; val g = (a shr 8) and 255; val b = a and 255
        return (r * r + g * g + b * b).toLong()
    }
}
