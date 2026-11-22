package com.msmfarmer.app.core

import org.junit.Assert.*
import org.junit.Test

class GameIconMatcherStructureTest {
    private fun rgb(r: Int, g: Int, b: Int) = (r shl 16) or (g shl 8) or b
    private fun pattern(): GameIconMatcher.Pattern {
        val n = 96
        val data = IntArray(n * n) { i ->
            val x = i % n; val y = i / n
            val v = 180 + (x - 48) / 3 + (y - 48) / 6
            rgb(v, (v * 0.92f).toInt(), (v * 0.55f).toInt())
        }
        return GameIconMatcher.Pattern(n, n, data)
    }

    @Test fun colorfulUniformBackgroundIsRejected() {
        val image = IntArray(320 * 160) { rgb(180, 165, 99) }
        val hits = GameIconMatcher.find(image, 320, 160, listOf(pattern()), 1f)
        assertTrue("однотонный цветной фон не должен быть иконкой: $hits", hits.isEmpty())
    }

    @Test fun sameIconIsFound() {
        val ref = pattern()
        val image = IntArray(320 * 160) { rgb(52, 104, 58) }
        for (y in 0 until 96) for (x in 0 until 96) image[(y + 40) * 320 + x + 100] = ref.pixels[y * 96 + x]
        val hits = GameIconMatcher.find(image, 320, 160, listOf(ref), 1f)
        assertTrue("образец не найден: $hits", hits.any { kotlin.math.abs(it.x - 148f) < 12f && kotlin.math.abs(it.y - 88f) < 12f })
        assertTrue(hits.all { it.structure >= GameIconMatcher.MIN_STRUCTURE })
    }

    @Test fun zeroSpatialVarianceIsRejected() {
        // Значения R, G и B могут отличаться, но яркость во всех позициях одна.
        assertEquals(0f, GameIconMatcher.structureSimilarity(6400, 640000, 6400, 650000, 645000, 64), 0f)
    }
}
