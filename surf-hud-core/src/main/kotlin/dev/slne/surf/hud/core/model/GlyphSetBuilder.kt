package dev.slne.surf.hud.core.model

import dev.slne.surf.hud.core.config.ImageDefinition
import dev.slne.surf.hud.core.config.TextDefinition
import dev.slne.surf.hud.core.pack.VanillaAssets
import java.awt.image.BufferedImage
import java.io.IOException
import java.nio.file.InvalidPathException
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.io.path.exists

/** Turns `texts/` and `images/` definitions into glyph sets, reading image files below [assetRoot]. */
class GlyphSetBuilder(private val assets: VanillaAssets, private val assetRoot: Path, private val chars: CharAllocator) {
    fun text(id: String, definition: TextDefinition): AsciiSet {
        val type = definition.type ?: "ascii"
        if (type != "ascii") throw DefinitionException("unknown text type '$type' (expected ascii)", "type")
        val height = definition.height ?: assets.asciiHeight
        if (height !in 1..256) throw DefinitionException("must be between 1 and 256, was $height", "height")
        val image = assets.asciiImage
        val cellWidth = image.width / assets.asciiRows.first().length
        val cellHeight = image.height / assets.asciiRows.size
        val scale = height.toDouble() / cellHeight
        val advances = HashMap<Char, Int>()
        assets.asciiRows.forEachIndexed { row, line ->
            line.forEachIndexed { column, char ->
                if (char != '\u0000' && char != ' ') {
                    val width = opaqueWidth(image, column * cellWidth, row * cellHeight, cellWidth, cellHeight)
                    advances[char] = (0.5 + width * scale).toInt() + 1
                }
            }
        }
        // Vanilla's space is 4 px at height 8; scale it with the font.
        advances[' '] = (4 * scale + 0.5).toInt()
        return AsciiSet(id, height, advances, assets.asciiRows)
    }

    fun image(id: String, definition: ImageDefinition): GlyphSet = when (val type = definition.type) {
        "single" -> {
            val image = read(definition.file)
            ImageSet(id, Glyph(id, chars.next(), image.width, image.height, opaqueWidth(image) + 1, image))
        }
        "strip" -> strip(id, read(definition.file), definition.file!!)
        "pixel" -> pixel(id, definition)
        null -> throw DefinitionException("missing", "type")
        else -> throw DefinitionException("unknown image type '$type' (expected single, strip or pixel)", "type")
    }

    private fun strip(id: String, source: BufferedImage, file: String): StripSet {
        if (source.width != 3) throw DefinitionException("strip texture $file must be 3 px wide (left, middle, right column)", "file")
        fun column(x: Int, width: Int, name: String): Glyph {
            val image = BufferedImage(width, source.height, BufferedImage.TYPE_INT_ARGB)
            for (px in 0 until width) for (py in 0 until source.height) image.setRGB(px, py, source.getRGB(x, py))
            return Glyph(name, chars.next(), width, source.height, width + 1, image)
        }
        val left = column(0, 1, "left")
        val right = column(2, 1, "right")
        val middles = MIDDLE_WIDTHS.map { column(1, it, "middle_$it") }
        return StripSet(id, left, right, middles)
    }

    private fun pixel(id: String, definition: ImageDefinition): PixelSet {
        val size = definition.size ?: throw DefinitionException("missing", "size")
        if (size !in 1..64) throw DefinitionException("must be between 1 and 64, was $size", "size")
        val rows = definition.rows ?: 8
        if (rows !in 1..64) throw DefinitionException("must be between 1 and 64, was $rows", "rows")
        val texture = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        for (x in 0 until size) for (y in 0 until size) texture.setRGB(x, y, -1)
        return PixelSet(id, size, List(rows) { chars.next() }, texture)
    }

    private fun read(file: String?): BufferedImage {
        if (file == null) throw DefinitionException("missing", "file")
        try {
            val path = assetRoot.resolve(file).normalize()
            if (!path.startsWith(assetRoot.normalize()) || !path.exists()) {
                throw DefinitionException("file '$file' not found in assets/", "file")
            }
            return ImageIO.read(path.toFile()) ?: throw DefinitionException("file '$file' is not a readable image", "file")
        } catch (e: IOException) {
            throw DefinitionException("file '$file' is not a readable PNG: ${e.message ?: e}", "file")
        } catch (e: InvalidPathException) {
            throw DefinitionException("file '$file' is not a readable PNG: ${e.message ?: e}", "file")
        }
    }

    companion object {
        private val MIDDLE_WIDTHS = listOf(256, 128, 64, 32, 16, 8, 4, 2, 1)

        /** Width up to the rightmost column with any visible pixel, as vanilla measures bitmap glyphs. */
        fun opaqueWidth(image: BufferedImage, x0: Int = 0, y0: Int = 0, width: Int = image.width, height: Int = image.height): Int {
            for (x in width - 1 downTo 0) {
                for (y in 0 until height) {
                    if ((image.getRGB(x0 + x, y0 + y) ushr 24) != 0) return x + 1
                }
            }
            return 0
        }
    }
}
