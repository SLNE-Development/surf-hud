package dev.slne.surf.hud.api.theme

import net.kyori.adventure.key.Key

data class HudTheme(
    val lineFonts: List<Key> = listOf(Key.key("surf", "hud"), Key.key("surf", "hud2")),
    val spaceFont: Key = Key.key("surf", "space"),
    val pillCap: HudGlyph = HudGlyph('', 1),
    val pillSegments: List<HudGlyph> = binaryGlyphs(0xE002, 128),
    val negativeSpaces: List<HudGlyph> = binaryGlyphs(0xF801, 256),
    val positiveSpaces: List<HudGlyph> = binaryGlyphs(0xF821, 256),
    val paddingLeft: Int = 5,
    val paddingRight: Int = 4,
    val gap: Int = 3,
) {
    init {
        require(lineFonts.isNotEmpty()) { "At least one line font is required" }
        require(pillSegments.any { it.width == 1 }) { "Pill segments require a glyph of width 1" }
        require(negativeSpaces.any { it.width == 1 }) { "Negative spaces require a glyph of width 1" }
        require(positiveSpaces.any { it.width == 1 }) { "Positive spaces require a glyph of width 1" }
        require(paddingLeft >= pillCap.width) { "Left padding must cover the pill cap" }
        require(paddingRight >= 0) { "Right padding must not be negative" }
        require(gap >= 0) { "Gap must not be negative" }
    }

    val lines: Int get() = lineFonts.size

    fun lineFont(line: Int): Key = lineFonts.getOrNull(line)
        ?: throw IllegalArgumentException("HUD line $line is not supported, theme only provides $lines lines")

    companion object {
        private fun binaryGlyphs(firstCodePoint: Int, largest: Int): List<HudGlyph> =
            generateSequence(1) { it * 2 }
                .takeWhile { it <= largest }
                .mapIndexed { index, width -> HudGlyph((firstCodePoint + index).toChar(), width) }
                .toList()
                .asReversed()
    }
}
