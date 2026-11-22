package com.msmfarmer.app.core

import org.junit.Assert.*
import org.junit.Test
import java.awt.image.BufferedImage
import javax.imageio.ImageIO

class GameIconMatcherTest {
    private fun image(name: String): BufferedImage = ImageIO.read(javaClass.getResourceAsStream("/recognition/$name"))
    private fun pixels(image: BufferedImage) = image.getRGB(0, 0, image.width, image.height, null, 0, image.width)
    private fun pattern(name: String): GameIconMatcher.Pattern {
        val image = image(name)
        return GameIconMatcher.Pattern(image.width, image.height, pixels(image))
    }
    private val patterns by lazy { listOf(pattern("ref-gold.png"), pattern("ref-purple.png")) }
    private fun find(name: String): List<GameIconMatcher.Hit> {
        val image = image(name)
        return GameIconMatcher.find(pixels(image), image.width, image.height, patterns, 1f)
    }
    private fun hasNear(hits: List<GameIconMatcher.Hit>, x: Int, y: Int): Boolean =
        hits.any { kotlin.math.abs(it.x - x) < 25 && kotlin.math.abs(it.y - y) < 25 }

    @Test fun goldButtonsOnIsland() {
        val hits = find("gold-island.png")
        assertTrue("isolated button: $hits", hasNear(hits, 188, 80))
        assertTrue("front button: $hits", hasNear(hits, 435, 175))
        assertTrue("expected multiple buttons: $hits", hits.size >= 3)
    }
    @Test fun goldButtonsAtCloserZoom() {
        val hits = find("gold-zoom.png")
        assertTrue("left button: $hits", hasNear(hits, 130, 126))
        assertTrue("right button: $hits", hasNear(hits, 546, 210))
    }
    @Test fun purpleButtons() {
        val hits = find("purple-island.png")
        assertTrue("purple front button: $hits", hasNear(hits, 212, 168))
    }
    @Test fun mapAndPopupDoNotMatch() {
        assertTrue("mine is not a coin button", find("mine-negative.png").isEmpty())
        assertTrue("map should not generate hits", find("map-negative.png").isEmpty())
        assertTrue("popup should not generate hits", find("popup-negative.png").isEmpty())
    }
}
