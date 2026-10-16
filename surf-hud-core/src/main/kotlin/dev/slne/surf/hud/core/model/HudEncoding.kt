package dev.slne.surf.hud.core.model

/**
 * How a HUD glyph tells the shader which slot it belongs to: the font ascent pushes the glyph far below the
 * screen, to `lineY + BASE + slot * STRIDE + BIAS + localY`. The shader recovers slot and localY from the
 * vertex y and moves the glyph to the slot's anchor. Every value stays below 2^24, so it is exact in a float.
 */
object HudEncoding {
    const val BASE = 1 shl 20
    const val STRIDE = 1024

    /** Local y range of a slot is [-BIAS, STRIDE - BIAS), glyph height and shadow included. */
    const val BIAS = 512
    const val MAX_SLOTS = ((1 shl 24) - BASE) / STRIDE

    /** Vanilla draws the first bossbar name at y = 3 and each further bossbar 19 GUI pixels lower. */
    fun bossbarLineY(row: Int) = 3 + 19 * row

    /** Bitmap glyphs are drawn with their top at `lineY + 7 - ascent`. */
    fun ascent(slot: Int, localY: Int) = 7 - (BASE + slot * STRIDE + BIAS + localY)

    /** Whether a glyph of [height] drawn at local [y] stays inside its slot's range (1 px shadow included). */
    fun fitsLocalY(y: Int, height: Int) = y >= -BIAS && y + height + 1 <= STRIDE - BIAS
}
