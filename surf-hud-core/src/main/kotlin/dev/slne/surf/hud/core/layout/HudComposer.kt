package dev.slne.surf.hud.core.layout

import dev.slne.surf.hud.api.theme.HudGlyph
import dev.slne.surf.hud.api.theme.HudTheme
import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.ShadowColor
import kotlin.math.abs

class HudComposer(private val theme: HudTheme, private val font: Key) {
    private val parts = mutableListOf<Component>()

    var width = 0
        private set

    fun space(pixels: Int) {
        if (pixels == 0) {
            return
        }

        val table = if (pixels < 0) theme.negativeSpaces else theme.positiveSpaces
        val text = buildString { fill(abs(pixels), table) { append(it.char) } }

        parts += Component.text(text).font(theme.spaceFont)
        width += pixels
    }

    fun glyph(glyph: HudGlyph) {
        parts += Component.text(glyph.char.toString(), NamedTextColor.WHITE)
            .font(font)
            .shadowColor(ShadowColor.none())
        width += glyph.width + 1
        space(-1)
    }

    fun content(component: Component, contentWidth: Int) {
        parts += Component.text().font(font).append(component).build()
        width += contentWidth
    }

    fun append(other: HudComposer) {
        parts += other.parts
        width += other.width
    }

    fun pill(component: Component, contentWidth: Int) {
        val total = theme.paddingLeft + contentWidth + theme.paddingRight

        background(total)
        space(theme.paddingLeft - total)
        content(component, contentWidth)
        space(theme.paddingRight)
    }

    private fun background(total: Int) {
        val cap = theme.pillCap
        val middle = (total - cap.width * 2).coerceAtLeast(0)

        glyph(cap)
        fill(middle, theme.pillSegments, ::glyph)
        glyph(cap)
    }

    private inline fun fill(pixels: Int, table: List<HudGlyph>, action: (HudGlyph) -> Unit) {
        var remaining = pixels
        for (glyph in table.sortedByDescending { it.width }) {
            while (remaining >= glyph.width) {
                action(glyph)
                remaining -= glyph.width
            }
        }
    }

    fun build(): Component = Component.text().append(parts).build()
}
