package dev.slne.surf.hud.api.theme

data class HudGlyph(val char: Char, val width: Int) {
    init {
        require(width > 0) { "Glyph width must be positive, was $width" }
    }
}
