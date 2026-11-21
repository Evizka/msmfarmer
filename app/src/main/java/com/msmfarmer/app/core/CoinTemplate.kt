package com.msmfarmer.app.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.msmfarmer.app.R
import com.msmfarmer.app.model.Detection
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Поиск иконок монет по эталону из image-1.png.
 *
 * Сначала по кадру строится грубая карта классов цвета (жёлтый / зелёный /
 * тёмный / светлый), затем по интегральным изображениям ищутся окна, чьи
 * пропорции совпадают с эталоном, и только после этого эталон накладывается
 * на кандидата точнее. Так декоративные жёлтые детали интерфейса отсекаются.
 */
object CoinTemplate {

    private const val GRID_W = 24
    private const val GRID_H = 19
    private const val STEP = 4
    private const val CLASSES = 4

    private const val YELLOW = 0
    private const val GREEN = 1
    private const val DARK = 2
    private const val LIGHT = 3
    private const val SKIP = 4

    private const val COARSE_MIN = 0.66f
    private const val DETAIL_MIN = 0.64f
    private const val MAX_REFINED = 120

    private val WIDTHS = intArrayOf(48, 64, 88, 116, 148, 188, 236, 300)

    @Volatile
    var isReady: Boolean = false
        private set

    private var fractions = FloatArray(CLASSES)
    private var cellX = IntArray(0)
    private var cellY = IntArray(0)
    private var cellClass = IntArray(0)

    /** Загружает эталон один раз, при старте приложения или службы. */
    @Synchronized
    fun load(context: Context) {
        if (isReady) return
        val raw = BitmapFactory.decodeResource(context.resources, R.drawable.coin_template) ?: return
        try {
            var left = raw.width
            var top = raw.height
            var right = 0
            var bottom = 0
            val rawPixels = IntArray(raw.width * raw.height)
            raw.getPixels(rawPixels, 0, raw.width, 0, 0, raw.width, raw.height)
            for (y in 0 until raw.height) {
                for (x in 0 until raw.width) {
                    if ((rawPixels[y * raw.width + x] ushr 24) < 80) continue
                    if (x < left) left = x
                    if (x > right) right = x
                    if (y < top) top = y
                    if (y > bottom) bottom = y
                }
            }
            if (right <= left || bottom <= top) return

            val iw = right - left + 1
            val ih = bottom - top + 1
            val counts = IntArray(CLASSES)
            val xs = ArrayList<Int>(GRID_W * GRID_H)
            val ys = ArrayList<Int>(GRID_W * GRID_H)
            val cs = ArrayList<Int>(GRID_W * GRID_H)

            for (gy in 0 until GRID_H) {
                for (gx in 0 until GRID_W) {
                    val x0 = gx * iw / GRID_W
                    val x1 = max(x0 + 1, (gx + 1) * iw / GRID_W)
                    val y0 = gy * ih / GRID_H
                    val y1 = max(y0 + 1, (gy + 1) * ih / GRID_H)
                    var sr = 0
                    var sg = 0
                    var sb = 0
                    var sa = 0
                    var n = 0
                    for (y in y0 until min(y1, ih)) {
                        val row = (top + y) * raw.width
                        for (x in x0 until min(x1, iw)) {
                            val argb = rawPixels[row + left + x]
                            sr += (argb shr 16) and 0xFF
                            sg += (argb shr 8) and 0xFF
                            sb += argb and 0xFF
                            sa += argb ushr 24
                            n++
                        }
                    }
                    if (n == 0) continue
                    val cls = classify(sr / n, sg / n, sb / n, sa / n)
                    if (cls == SKIP) continue
                    counts[cls]++
                    xs.add(gx)
                    ys.add(gy)
                    cs.add(cls)
                }
            }

            val total = counts.sum()
            if (total < 50) return
            for (i in 0 until CLASSES) fractions[i] = counts[i].toFloat() / total
            cellX = xs.toIntArray()
            cellY = ys.toIntArray()
            cellClass = cs.toIntArray()
            isReady = true
        } finally {
            raw.recycle()
        }
    }

    /** Ищет эталонные иконки в уже прочитанном кадре. */
    @Synchronized
    fun find(bitmap: Bitmap, pixels: IntArray): List<Detection> {
        if (!isReady || cellX.isEmpty()) return emptyList()
        val w = bitmap.width
        val h = bitmap.height
        if (w < WIDTHS[0] || h < WIDTHS[0]) return emptyList()

        val sw = (w + STEP - 1) / STEP
        val sh = (h + STEP - 1) / STEP
        val classes = ByteArray(sw * sh)
        for (sy in 0 until sh) {
            val py = min(h - 1, sy * STEP + STEP / 2)
            val row = py * w
            for (sx in 0 until sw) {
                val px = min(w - 1, sx * STEP + STEP / 2)
                val argb = pixels[row + px]
                val c = classify(
                    (argb shr 16) and 0xFF,
                    (argb shr 8) and 0xFF,
                    argb and 0xFF,
                    argb ushr 24,
                )
                classes[sy * sw + sx] = c.coerceIn(0, CLASSES - 1).toByte()
            }
        }

        val stride = sw + 1
        val integrals = Array(CLASSES) { IntArray((sw + 1) * (sh + 1)) }
        for (k in 0 until CLASSES) {
            val ii = integrals[k]
            for (y in 0 until sh) {
                val rowBase = (y + 1) * stride
                val prevBase = y * stride
                for (x in 0 until sw) {
                    val v = if (classes[y * sw + x].toInt() == k) 1 else 0
                    ii[rowBase + x + 1] = v + ii[prevBase + x + 1] + ii[rowBase + x] - ii[prevBase + x]
                }
            }
        }

        val coarse = ArrayList<Coarse>()
        for (iconWidth in WIDTHS) {
            val ww = max(8, iconWidth / STEP)
            val wh = max(6, (ww.toFloat() * GRID_H / GRID_W).roundInt())
            if (ww >= sw || wh >= sh) continue
            for (cy in 0 until sh - wh step 3) {
                for (cx in 0 until sw - ww step 3) {
                    val total = (ww * wh).toFloat()
                    val fy = rect(integrals[YELLOW], stride, cx, cy, cx + ww, cy + wh) / total
                    if (fy < 0.10f) continue
                    val fg = rect(integrals[GREEN], stride, cx, cy, cx + ww, cy + wh) / total
                    if (fg < 0.005f) continue
                    val fd = rect(integrals[DARK], stride, cx, cy, cx + ww, cy + wh) / total
                    val fl = rect(integrals[LIGHT], stride, cx, cy, cx + ww, cy + wh) / total
                    val diff = abs(fy - fractions[YELLOW]) + abs(fg - fractions[GREEN]) +
                        abs(fd - fractions[DARK]) + abs(fl - fractions[LIGHT])
                    val score = 1f - diff * 0.5f
                    if (score >= COARSE_MIN) coarse.add(Coarse(cx, cy, ww, wh, score))
                }
            }
        }
        if (coarse.isEmpty()) return emptyList()
        if (coarse.size > MAX_REFINED) {
            coarse.sortByDescending { it.score }
            val trimmed = ArrayList<Coarse>(MAX_REFINED)
            for (i in 0 until MAX_REFINED) trimmed.add(coarse[i])
            coarse.clear()
            coarse.addAll(trimmed)
        }

        val hits = ArrayList<Detection>()
        for (c in coarse) {
            var best = 0f
            var bestX = c.cx
            var bestY = c.cy
            val cw = c.ww * STEP.toFloat() / GRID_W
            val ch = c.wh * STEP.toFloat() / GRID_H
            for (dy in -2..2) {
                val y0 = (c.cy + dy) * STEP
                if (y0 < 0 || y0 >= h) continue
                for (dx in -2..2) {
                    val x0 = (c.cx + dx) * STEP
                    if (x0 < 0 || x0 >= w) continue
                    var matched = 0
                    var checked = 0
                    var i = 0
                    while (i < cellX.size) {
                        val sx = (x0 + (cellX[i] + 0.5f) * cw).toInt()
                        val sy = (y0 + (cellY[i] + 0.5f) * ch).toInt()
                        if (sx in 0 until w && sy in 0 until h) {
                            val argb = pixels[sy * w + sx]
                            val sc = classify(
                                (argb shr 16) and 0xFF,
                                (argb shr 8) and 0xFF,
                                argb and 0xFF,
                                argb ushr 24,
                            )
                            if (sc == cellClass[i]) matched++
                            checked++
                        }
                        i++
                    }
                    if (checked == 0) continue
                    val score = matched.toFloat() / checked
                    if (score > best) {
                        best = score
                        bestX = c.cx + dx
                        bestY = c.cy + dy
                    }
                }
            }
            if (best < DETAIL_MIN) continue
            val iconW = c.ww * STEP.toFloat()
            val iconH = c.wh * STEP.toFloat()
            hits.add(
                Detection(
                    x = (bestX + c.ww * 0.5f) * STEP,
                    y = (bestY + c.wh * 0.5f) * STEP,
                    radius = min(iconW, iconH) * 0.45f,
                    score = best,
                )
            )
        }

        val result = ArrayList<Detection>()
        for (hit in hits.sortedByDescending { it.score }) {
            val duplicate = result.any { other -> hypot(other.x - hit.x, other.y - hit.y) < hit.radius * 1.4f }
            if (!duplicate) result.add(hit)
        }
        return result
    }

    private data class Coarse(
        val cx: Int,
        val cy: Int,
        val ww: Int,
        val wh: Int,
        val score: Float,
    )

    private fun rect(ii: IntArray, stride: Int, x0: Int, y0: Int, x1: Int, y1: Int): Int =
        ii[y1 * stride + x1] - ii[y0 * stride + x1] - ii[y1 * stride + x0] + ii[y0 * stride + x0]

    private fun classify(r: Int, g: Int, b: Int, a: Int): Int {
        if (a < 80) return SKIP
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val delta = max - min
        val hue: Float
        val saturation: Float
        if (delta == 0) {
            hue = 0f
            saturation = 0f
        } else {
            saturation = delta.toFloat() / max
            hue = when (max) {
                r -> ((g - b).toFloat() / delta) % 6f
                g -> (b - r).toFloat() / delta + 2f
                else -> (r - g).toFloat() / delta + 4f
            } * 60f
        }
        if (r > 150 && g in 91..229 && b < 150 && hue in 25f..70f && saturation > 0.25f) return YELLOW
        if (g > 90 && g > r * 0.9f && g > b * 1.2f) return GREEN
        if (max < 130) return DARK
        return LIGHT
    }

    private fun Float.roundInt(): Int = (this + 0.5f).toInt()
}
