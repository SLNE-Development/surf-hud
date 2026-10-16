package dev.slne.surf.hud.core.model

import java.awt.image.BufferedImage
import kotlin.math.abs

/** Thrown while compiling a definition; [field] names the offending key, if any. */
class DefinitionException(message: String, val field: String? = null) : RuntimeException(message)

/** One bitmap glyph of a generated font; [advance] includes vanilla's 1 px gap after bitmap glyphs. */
data class Glyph(
    val name: String,
    val char: Char,
    val width: Int,
    val height: Int,
    val advance: Int,
    val texture: BufferedImage,
)

/** A set of glyphs defined by one `images/` or `texts/` entry. */
sealed interface GlyphSet {
    val id: String
    val height: Int
}

/** Vanilla `ascii.png` drawn [height] px tall; [advances] are per character (bold adds 1, except spaces). */
class AsciiSet(override val id: String, override val height: Int, val advances: Map<Char, Int>, val rows: List<String>) : GlyphSet {
    fun advance(char: Char): Int? = advances[char]
}

class ImageSet(override val id: String, val glyph: Glyph) : GlyphSet {
    override val height get() = glyph.height
}

/** Edge glyphs plus power-of-two middle pieces ([middles] widest first) that compose any width. */
class StripSet(override val id: String, val left: Glyph, val right: Glyph, val middles: List<Glyph>) : GlyphSet {
    override val height get() = left.height
}

/** A white [size] px square once per row; row r is drawn r * size px lower. Tinted per glyph to draw images. */
class PixelSet(override val id: String, val size: Int, val rows: List<Char>, val texture: BufferedImage) : GlyphSet {
    override val height get() = size * rows.size
    val advance get() = size + 1
}

/** Hands out private-use characters for non-ascii glyphs; they only need to be unique within surf-hud fonts. */
class CharAllocator {
    private var next = FIRST

    fun next(): Char {
        if (next > LAST) throw DefinitionException("too many glyphs (max ${LAST - FIRST + 1})")
        return (next++).toChar()
    }

    private companion object {
        const val FIRST = 0xE000
        const val LAST = 0xF7FF
    }
}

/** Space glyphs of ±1, ±2, ±4 … ±512 px, part of every surf-hud font; used to move the cursor. */
object SpaceGlyphs {
    private val chars: Map<Int, Char> = (0..9).flatMap { power ->
        val amount = 1 shl power
        listOf(amount to (0xF800 + 2 * power).toChar(), -amount to (0xF800 + 2 * power + 1).toChar())
    }.toMap()

    val advancesByChar: Map<String, Int> = chars.entries.associate { (amount, char) -> char.toString() to amount }

    /** Encodes an offset as a sum of space glyphs, largest first. */
    fun encode(pixels: Int): String {
        if (pixels == 0) return ""
        val sign = if (pixels < 0) -1 else 1
        var remaining = abs(pixels)
        val text = StringBuilder()
        for (power in 9 downTo 0) {
            val amount = 1 shl power
            val char = chars.getValue(sign * amount)
            while (remaining >= amount) {
                text.append(char)
                remaining -= amount
            }
        }
        return text.toString()
    }
}
