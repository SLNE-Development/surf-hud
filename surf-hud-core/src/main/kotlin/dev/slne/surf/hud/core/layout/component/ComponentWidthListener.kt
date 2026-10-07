package dev.slne.surf.hud.core.layout.component

import dev.slne.surf.hud.core.layout.glyph.GlyphWidths
import net.kyori.adventure.text.flattener.FlattenerListener
import net.kyori.adventure.text.format.Style
import net.kyori.adventure.text.format.TextDecoration

class ComponentWidthListener : FlattenerListener {
    private val bold = ArrayDeque<Boolean>()
    var width = 0
        private set

    override fun pushStyle(style: Style) {
        val parent = bold.lastOrNull() ?: false
        bold.addLast(
            when (style.decoration(TextDecoration.BOLD)) {
                TextDecoration.State.TRUE -> true
                TextDecoration.State.FALSE -> false
                TextDecoration.State.NOT_SET -> parent
            }
        )
    }

    override fun component(text: String) {
        val isBold = bold.lastOrNull() ?: false
        width += text.sumOf { char ->
            GlyphWidths.advance(char) + if (isBold) 1 else 0
        }
    }

    override fun popStyle(style: Style) {
        bold.removeLastOrNull()
    }
}