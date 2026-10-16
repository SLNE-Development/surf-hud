package dev.slne.surf.hud.core.render

import dev.slne.surf.hud.core.model.HAlign
import dev.slne.surf.hud.core.model.SpaceGlyphs
import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.ShadowColor
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.format.TextDecoration

/**
 * Builds the component of one placed layout. Glyphs are laid out left to right from x = 0; [moveTo] jumps
 * with space glyphs, which is how backgrounds end up behind text. [build] shifts the layout for its alignment
 * and returns the cursor to 0, so the component's total advance is 0 and the shader can recover each glyph's x.
 * Runs split only where font, color, shadow or boldness change. Spaces are never added to bold runs, because
 * bold could widen them.
 */
class GlyphWriter(private val spaceFont: Key) {
    private class Run(val font: Key, val color: TextColor, val shadow: Boolean, val bold: Boolean) {
        val text = StringBuilder()
    }

    private val runs = ArrayList<Run>()

    var cursor = 0
        private set

    /** Widest x reached; the layout's width for alignment. */
    var width = 0
        private set

    fun glyphs(font: Key, text: String, advance: Int, color: TextColor = WHITE, shadow: Boolean = false, bold: Boolean = false) {
        if (text.isEmpty()) return
        val last = runs.lastOrNull()
        val run = if (last != null && last.font == font && last.color == color && last.shadow == shadow && last.bold == bold) last
        else Run(font, color, shadow, bold).also(runs::add)
        run.text.append(text)
        cursor += advance
        width = maxOf(width, cursor)
    }

    fun moveTo(target: Int) {
        if (target == cursor) return
        val last = runs.lastOrNull()
        val run = if (last != null && !last.bold) last else Run(last?.font ?: spaceFont, WHITE, false, false).also(runs::add)
        run.text.append(SpaceGlyphs.encode(target - cursor))
        cursor = target
        width = maxOf(width, cursor)
    }

    fun build(align: HAlign): Component {
        if (runs.none { run -> run.text.any { it !in SPACE_CHARS } }) return Component.empty()
        val start = when (align) {
            HAlign.LEFT -> 0
            HAlign.CENTER -> -width / 2
            HAlign.RIGHT -> -width
        }
        val parts = ArrayList<Component>(runs.size + 2)
        SpaceGlyphs.encode(start).takeIf { it.isNotEmpty() }?.let { parts += Component.text(it).font(spaceFont) }
        runs.forEach { run ->
            var part = Component.text(run.text.toString(), run.color).font(run.font).decoration(TextDecoration.BOLD, run.bold)
            if (!run.shadow) part = part.shadowColor(ShadowColor.none())
            parts += part
        }
        SpaceGlyphs.encode(-(start + cursor)).takeIf { it.isNotEmpty() }?.let { parts += Component.text(it).font(spaceFont) }
        return Component.text().append(parts).build()
    }

    private companion object {
        val WHITE: TextColor = NamedTextColor.WHITE
        val SPACE_CHARS = SpaceGlyphs.advancesByChar.keys.map { it[0] }.toSet()
    }
}
