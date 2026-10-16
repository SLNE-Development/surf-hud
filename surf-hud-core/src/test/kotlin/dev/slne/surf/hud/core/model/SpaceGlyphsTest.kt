package dev.slne.surf.hud.core.model

import kotlin.test.*

class SpaceGlyphsTest {
    private fun sum(encoded: String) = encoded.sumOf { SpaceGlyphs.advancesByChar.getValue(it.toString()) }

    @Test fun `encodes any offset exactly`() {
        for (pixels in listOf(0, 1, -1, 7, -300, 1023, 1500, -2047)) assertEquals(pixels, sum(SpaceGlyphs.encode(pixels)))
    }
    @Test fun `uses the documented code points`() {
        assertEquals("", SpaceGlyphs.encode(1))
        assertEquals("", SpaceGlyphs.encode(-1))
        assertEquals("", SpaceGlyphs.encode(512))
    }
}
