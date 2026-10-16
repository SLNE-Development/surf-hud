package dev.slne.surf.hud.core.render

import dev.slne.surf.hud.core.expression.isTrue
import dev.slne.surf.hud.core.expression.number
import dev.slne.surf.hud.core.head.HeadPixels
import dev.slne.surf.hud.core.model.*
import dev.slne.surf.hud.core.placeholder.RenderScope
import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextColor
import kotlin.math.roundToInt

/** Identifies a head element within a slot, so a head can be kept while its replacement loads. */
data class HeadKey(val slot: Int, val element: Int)

/**
 * Renders a placed layout into its zero-width component: texts first (their widths feed `[width:id]`), then
 * every visible element in definition order, so earlier elements are drawn below later ones.
 */
class LayoutRenderer(private val model: HudModel, private val text: TextRenderer) {
    fun render(slot: Int, layout: LayoutSpec, scope: RenderScope, heads: (HeadKey, String) -> HeadPixels): Component {
        val visible = layout.elements.filter { element -> element.conditions.all { it.isTrue(scope) } }
        val texts = visible.filterIsInstance<TextSpec>().associateWith { text.render(it.template, it.glyphSet, scope) }
        val widthScope = scope.withWidths(texts.entries.mapNotNull { (spec, rendered) -> spec.id?.let { it to rendered.width } }.toMap())
        val writer = GlyphWriter(model.slotFont(slot))
        for (element in visible) {
            val font = model.fontKey(slot, element)
            when (element) {
                is TextSpec -> {
                    val rendered = texts.getValue(element)
                    writer.moveTo(element.x - when (element.align) {
                        HAlign.LEFT -> 0
                        HAlign.CENTER -> rendered.width / 2
                        HAlign.RIGHT -> rendered.width
                    })
                    rendered.runs.forEach { writer.glyphs(font, it.text, it.width, it.color, element.shadow, it.bold) }
                }
                is ImageSpec -> {
                    writer.moveTo(element.x)
                    writer.glyphs(font, element.glyphSet.glyph.char.toString(), element.glyphSet.glyph.advance, element.color)
                }
                is StripSpec -> {
                    writer.moveTo(element.x)
                    strip(writer, font, element.glyphSet, pixels(element.width.number(widthScope)), element.color)
                }
                is BarSpec -> {
                    val width = pixels(element.width.number(widthScope))
                    val value = element.value.number(widthScope)?.takeIf { it.isFinite() }?.coerceIn(0.0, 1.0) ?: 0.0
                    val fill = (value * width).roundToInt()
                    writer.moveTo(if (element.direction == FillDirection.RIGHT) element.x else element.x + width - fill)
                    strip(writer, font, element.glyphSet, fill, element.color)
                    if (width > 0) writer.moveTo(element.x + width)
                }
                is HeadSpec -> {
                    val name = element.player.evaluate(widthScope).text()
                    val brightness = element.brightness.number(widthScope)?.takeIf { it.isFinite() } ?: 1.0
                    head(writer, font, element, heads(HeadKey(slot, element.index), name).dimmed(brightness))
                }
            }
        }
        return writer.build(layout.align)
    }

    /** A non-negative whole pixel count; anything invalid is 0. */
    private fun pixels(value: Double?): Int = value?.takeIf { it.isFinite() }?.roundToInt()?.coerceIn(0, 4096) ?: 0

    /** Draws [set] exactly [pixels] wide (edges included). Pieces must touch, so each takes back vanilla's 1 px gap. */
    private fun strip(writer: GlyphWriter, font: Key, set: StripSet, pixels: Int, color: TextColor) {
        if (pixels <= 0) return
        fun piece(glyph: Glyph) {
            writer.glyphs(font, glyph.char.toString(), glyph.advance, color)
            writer.moveTo(writer.cursor - 1)
        }
        if (pixels == 1) {
            piece(set.middles.last())
            return
        }
        piece(set.left)
        var remaining = pixels - 2
        for (glyph in set.middles) {
            while (remaining >= glyph.width) {
                piece(glyph)
                remaining -= glyph.width
            }
        }
        piece(set.right)
    }

    /** Draws a head one tinted pixel glyph per opaque pixel; row r uses the pixel set's r-th (lower) glyph. */
    private fun head(writer: GlyphWriter, font: Key, element: HeadSpec, pixels: HeadPixels) {
        val set = element.glyphSet
        writer.moveTo(element.x)
        val left = writer.cursor
        pixels.argb.forEachIndexed { row, line ->
            writer.moveTo(left)
            for (argb in line) {
                if ((argb ushr 24) == 0) {
                    writer.moveTo(writer.cursor + set.size)
                } else {
                    writer.glyphs(font, set.rows[row].toString(), set.advance, TextColor.color(argb and 0xFFFFFF))
                    writer.moveTo(writer.cursor - 1)
                }
            }
        }
        writer.moveTo(left + 8 * set.size)
    }
}
