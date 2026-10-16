package dev.slne.surf.hud.prototype.coreshader

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlin.math.abs

/** Horizontal alignment of an element relative to its anchor. */
enum class Align { LEFT, CENTER, RIGHT }

/**
 * Builds the text component of one HUD element. Glyphs are laid out left to right from x = 0; [moveTo]
 * jumps back or forward with space glyphs, which is how backgrounds end up behind text. [build] shifts the
 * element for its alignment and returns the cursor to 0, so every element has a total advance of 0 and the
 * shader can recover each glyph's x relative to the element.
 *
 * Everything is in the element's own font (which also contains the space glyphs), so the component only
 * splits where color or shadow changes.
 */
class HudElementBuilder(private val meta: HudMetadata, element: String) {
    private val font = meta.elementFont(element)
    private val runs = mutableListOf<Run>()
    private var cursor = 0

    /** Widest x reached so far; the element's width for alignment. */
    var width = 0
        private set

    val x: Int get() = cursor

    private class Run(val color: String, val shadow: Boolean) {
        val text = StringBuilder()
    }

    fun textWidth(text: String, set: String = "text") = text.sumOf { meta.textAdvance(set, it) }

    fun text(text: String, color: String = "#FFFFFF", shadow: Boolean = true, set: String = "text") = apply {
        glyphs(text, color, shadow, textWidth(text, set))
    }

    /** Draws an icon; its advance includes vanilla's 1 px gap after bitmap glyphs. */
    fun icon(name: String, set: String = "icon") = apply {
        val glyph = meta.image(set, name)
        glyphs(glyph.char, WHITE, false, glyph.advance)
    }

    /** Draws a strip glyph set exactly [pixels] wide (edges included), e.g. a background or bar. */
    fun strip(set: String, pixels: Int, color: String = WHITE) = apply {
        if (pixels <= 0) return@apply
        val strip = meta.strip(set)
        if (pixels == 1) {
            piece(strip.middle.last(), color)
            return@apply
        }
        piece(strip.left, color)
        var remaining = pixels - 2
        for (glyph in strip.middle) {
            while (remaining >= glyph.width) {
                piece(glyph, color)
                remaining -= glyph.width
            }
        }
        piece(strip.right, color)
    }

    /** Draws only the first [fill] pixels of a [pixels] wide strip and leaves the cursor at the strip's end. */
    fun partialStrip(set: String, pixels: Int, fill: Int, color: String = WHITE) = apply {
        val start = cursor
        strip(set, fill.coerceIn(0, pixels), color)
        moveTo(start + pixels)
    }

    fun gap(pixels: Int) = moveTo(cursor + pixels)

    fun moveTo(target: Int) = apply {
        if (target != cursor) {
            currentRun().text.append(spaces(target - cursor))
            cursor = target
            width = maxOf(width, cursor)
        }
    }

    private fun piece(glyph: HudGlyph, color: String) {
        glyphs(glyph.char, color, false, glyph.advance)
        // Strip pieces must touch, so take back the 1 px gap vanilla adds after every bitmap glyph.
        moveTo(cursor - 1)
    }

    private fun glyphs(text: String, color: String, shadow: Boolean, advance: Int) {
        val last = runs.lastOrNull()
        val run = if (last != null && last.color == color && last.shadow == shadow) last else Run(color, shadow).also { runs += it }
        run.text.append(text)
        cursor += advance
        width = maxOf(width, cursor)
    }

    private fun currentRun() = runs.lastOrNull() ?: Run(WHITE, false).also { runs += it }

    fun build(align: Align): JsonObject {
        val start = when (align) {
            Align.LEFT -> 0
            Align.CENTER -> -width / 2
            Align.RIGHT -> -width
        }
        val first = runs.firstOrNull() ?: currentRun()
        first.text.insert(0, spaces(start))
        runs.last().text.append(spaces(-(start + cursor)))
        val extra = JsonArray()
        for (run in runs) {
            extra.add(JsonObject().apply {
                addProperty("text", run.text.toString())
                addProperty("font", font)
                addProperty("color", run.color)
                if (!run.shadow) addProperty("shadow_color", 0)
            })
        }
        return JsonObject().apply {
            addProperty("text", "")
            add("extra", extra)
        }
    }

    /** Encodes an offset as the binary sum of ±2^n space glyphs. */
    private fun spaces(pixels: Int): String {
        require(abs(pixels) < 2048) { "Offset $pixels is too large for the space glyphs" }
        val sign = if (pixels < 0) -1 else 1
        var remaining = abs(pixels)
        val text = StringBuilder()
        for (power in 10 downTo 0) {
            val amount = 1 shl power
            val char = meta.spaces[sign * amount] ?: continue
            while (remaining >= amount) {
                text.append(char)
                remaining -= amount
            }
        }
        return text.toString()
    }

    private companion object {
        const val WHITE = "#FFFFFF"
    }
}
