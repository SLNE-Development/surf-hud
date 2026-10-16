package dev.slne.surf.hud.buildlogic.hud

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

/**
 * How a HUD glyph tells the shader which element it belongs to: the font ascent pushes the glyph far below
 * the screen, to `lineY + BASE + slot * STRIDE + BIAS + layerY`. The shader recovers slot and layerY from the
 * vertex y and moves the glyph to the element's anchor. Every value stays below 2^24, so it is exact in a float.
 */
object HudEncoding {
    const val BASE = 1 shl 20
    const val STRIDE = 1024
    /** Local y range of an element is [-BIAS, STRIDE - BIAS), glyph height and shadow included. */
    const val BIAS = 512
    /** Vanilla draws the first bossbar name at y = 3 and each further bossbar 19 GUI pixels lower. */
    fun bossbarLineY(row: Int) = 3 + 19 * row

    /** Bitmap glyphs are drawn with their top at `lineY + 7 - ascent`. */
    fun ascent(slot: Int, layerY: Int) = 7 - (BASE + slot * STRIDE + BIAS + layerY)

    const val MAX_SLOTS = ((1 shl 24) - BASE) / STRIDE
}

/**
 * Builds the HUD resource pack from a [HudLayout]: one font per element, the glyph textures, a space font
 * for horizontal layout, the patched `core/text.vsh` with its generated include, and transparent bossbar
 * sprites. Also returns the metadata a server needs to compose HUD components for this pack.
 */
class HudPackGenerator(
    private val layout: HudLayout,
    private val sourceDir: Path,
    private val versionId: String,
    private val vanillaAsset: (String) -> ByteArray,
) {
    private val ns = layout.namespace

    fun generate(packDir: Path): Map<String, Any?> {
        require(layout.elements.size <= HudEncoding.MAX_SLOTS) { "Too many HUD elements (max ${HudEncoding.MAX_SLOTS})" }
        packDir.toFile().deleteRecursively()
        packDir.createDirectories()
        write(
            packDir.resolve("pack.mcmeta"),
            mapOf("pack" to mapOf("description" to layout.description, "min_format" to layout.packFormat, "max_format" to layout.packFormat)),
        )

        val glyphSets = layout.glyphSets.entries.mapIndexed { index, (name, spec) ->
            val firstChar = 0xE000 + index * 0x100
            name to when (spec) {
                GlyphSetSpec.Ascii -> asciiGlyphs()
                is GlyphSetSpec.Images -> imageGlyphs(packDir, name, spec, firstChar)
                is GlyphSetSpec.Strip -> stripGlyphs(packDir, name, spec, firstChar)
            }
        }.toMap()

        val spaces = writeSpaceFont(packDir)
        // Space glyphs have no vertices, so every element font can carry them; a HUD element then needs only
        // one font and splits into components only where its color or shadow changes.
        val spaceProvider = mapOf("type" to "space", "advances" to spaces.entries.associate { (amount, c) -> c to amount.toInt() })
        val elements = layout.elements.entries.mapIndexed { slot, (name, element) ->
            val providers = element.layers.flatMap { (set, layerY) ->
                glyphSets.getValue(set).providers(HudEncoding.ascent(slot, layerY))
            } + spaceProvider
            write(packDir.resolve("assets/$ns/font/hud/$name.json"), mapOf("providers" to providers))
            name to mapOf(
                "font" to "$ns:hud/$name",
                "slot" to slot,
                "anchor" to element.anchor.name.lowercase(Locale.ROOT),
                "layers" to element.layers,
            )
        }.toMap()

        writeShader(packDir)
        writeTransparentBossbar(packDir)

        return mapOf(
            "minecraftVersion" to versionId,
            "namespace" to ns,
            "bossbar" to mapOf("color" to layout.bossbarColor, "row" to layout.bossbarRow),
            "space" to mapOf("font" to "$ns:hud/space", "chars" to spaces),
            "glyphSets" to glyphSets.mapValues { it.value.metadata },
            "elements" to elements,
        )
    }

    /** A glyph set's font providers for a given ascent, plus what the composer needs to know about it. */
    private class GlyphSet(val metadata: Map<String, Any?>, val providers: (ascent: Int) -> List<Map<String, Any?>>)

    @Suppress("UNCHECKED_CAST")
    private fun asciiGlyphs(): GlyphSet {
        val vanillaFont = JsonSlurper().parse(vanillaAsset("assets/minecraft/font/include/default.json")) as Map<String, Any?>
        val provider = (vanillaFont["providers"] as List<Map<String, Any?>>)
            .first { it["file"] == "minecraft:font/ascii.png" }
        val rows = provider["chars"] as List<String>
        val image = readImage(vanillaAsset("assets/minecraft/textures/font/ascii.png"))
        val cellWidth = image.width / rows.first().length
        val cellHeight = image.height / rows.size
        val height = (provider["height"] as Number?)?.toInt() ?: 8
        val scale = height.toDouble() / cellHeight
        val advances = sortedMapOf<String, Int>()
        rows.forEachIndexed { row, chars ->
            chars.forEachIndexed { column, char ->
                if (char != '\u0000' && char != ' ') {
                    val width = opaqueWidth(image, column * cellWidth, row * cellHeight, cellWidth, cellHeight)
                    advances[char.toString()] = (0.5 + width * scale).toInt() + 1
                }
            }
        }
        advances[" "] = 4
        return GlyphSet(mapOf("type" to "ascii", "height" to height, "advances" to advances)) { ascent ->
            // The space provider must come first: ascii.png also has a (blank) cell for ' ', and the first
            // provider that knows a character wins.
            listOf(
                mapOf("type" to "space", "advances" to mapOf(" " to 4)),
                mapOf("type" to "bitmap", "file" to "minecraft:font/ascii.png", "height" to height, "ascent" to ascent, "chars" to rows),
            )
        }
    }

    private fun imageGlyphs(packDir: Path, set: String, spec: GlyphSetSpec.Images, firstChar: Int): GlyphSet {
        val glyphs = spec.images.entries.mapIndexed { index, (name, file) ->
            val image = readImage(Files.readAllBytes(sourceDir.resolve(file)))
            val texture = "font/hud/$set/$name.png"
            writeImage(packDir.resolve("assets/$ns/textures/$texture"), image)
            Glyph(name, (firstChar + index).toChar(), "$ns:$texture", image.width, image.height, opaqueWidth(image) + 1)
        }
        return GlyphSet(
            mapOf("type" to "images", "images" to glyphs.associate { it.name to it.metadata() }),
        ) { ascent -> glyphs.map { it.provider(ascent) } }
    }

    private fun stripGlyphs(packDir: Path, set: String, spec: GlyphSetSpec.Strip, firstChar: Int): GlyphSet {
        val source = readImage(Files.readAllBytes(sourceDir.resolve(spec.texture)))
        require(source.width == 3) { "Strip texture ${spec.texture} must be 3 px wide (left, middle, right column)" }
        val height = source.height
        fun column(x: Int, width: Int, name: String, char: Int): Glyph {
            val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
            for (px in 0 until width) for (py in 0 until height) image.setRGB(px, py, source.getRGB(x, py))
            val texture = "font/hud/$set/$name.png"
            writeImage(packDir.resolve("assets/$ns/textures/$texture"), image)
            return Glyph(name, char.toChar(), "$ns:$texture", width, height, width + 1)
        }
        val left = column(0, 1, "left", firstChar)
        val right = column(2, 1, "right", firstChar + 1)
        val middle = MIDDLE_WIDTHS.mapIndexed { index, width -> column(1, width, "middle_$width", firstChar + 2 + index) }
        val metadata = mapOf(
            "type" to "strip",
            "height" to height,
            "left" to left.metadata(),
            "right" to right.metadata(),
            "middle" to middle.map { it.metadata() },
        )
        return GlyphSet(metadata) { ascent -> (listOf(left, right) + middle).map { it.provider(ascent) } }
    }

    private data class Glyph(val name: String, val char: Char, val file: String, val width: Int, val height: Int, val advance: Int) {
        fun metadata() = mapOf("char" to char.toString(), "width" to width, "height" to height, "advance" to advance)
        fun provider(ascent: Int) =
            mapOf("type" to "bitmap", "file" to file, "height" to height, "ascent" to ascent, "chars" to listOf(char.toString()))
    }

    /** Space glyphs for ±1, ±2, ±4 … ±512 GUI pixels; any offset is a sum of at most ten of them per sign. */
    private fun writeSpaceFont(packDir: Path): Map<String, String> {
        val spaces = linkedMapOf<String, String>()
        var char = 0xF000
        for (power in 0..9) {
            val amount = 1 shl power
            spaces["$amount"] = (char++).toChar().toString()
            spaces["-$amount"] = (char++).toChar().toString()
        }
        val advances = spaces.entries.associate { (amount, c) -> c to amount.toInt() }
        write(packDir.resolve("assets/$ns/font/hud/space.json"), mapOf("providers" to listOf(mapOf("type" to "space", "advances" to advances))))
        return spaces
    }

    private fun writeShader(packDir: Path) {
        val vanilla = String(vanillaAsset("assets/minecraft/shaders/core/text.vsh"), Charsets.UTF_8)
        val patched = TextShaderPatcher.patch(vanilla, versionId, "$ns:hud.glsl", "surf_hud_transform")
        packDir.resolve("assets/minecraft/shaders/core/text.vsh").also { it.parent.createDirectories() }.writeText(patched)
        packDir.resolve("assets/$ns/shaders/include/hud.glsl").also { it.parent.createDirectories() }.writeText(hudInclude())
    }

    /** The generated include: encoding constants, the per-slot layout table and the vertex transform. */
    private fun hudInclude(): String {
        val slots = layout.elements.entries.withIndex().joinToString("\n") { (index, entry) ->
            val (name, e) = entry
            val separator = if (index < layout.elements.size - 1) "," else ""
            "    vec4(${e.anchor.x}, ${e.anchor.y}, ${e.offsetX}.0, ${e.offsetY}.0)$separator // $name"
        }
        val count = layout.elements.size
        return """
            |#ifndef SURF_HUD_GLSL
            |#define SURF_HUD_GLSL
            |
            |// Generated by surf-hud GenerateHudPack from hud-layout.json; regenerate instead of editing.
            |// Needs ProjMat (minecraft:projection.glsl) to be declared before this include.
            |
            |const float SURF_HUD_BASE = ${HudEncoding.BASE}.0;
            |const float SURF_HUD_STRIDE = ${HudEncoding.STRIDE}.0;
            |const float SURF_HUD_BIAS = ${HudEncoding.BIAS}.0;
            |const float SURF_HUD_LINE_Y = ${HudEncoding.bossbarLineY(layout.bossbarRow)}.0;
            |const int SURF_HUD_SLOT_COUNT = $count;
            |
            |// Per element: anchor x/y as a fraction of the GUI size, then the offset from the anchor in GUI pixels.
            |const vec4 SURF_HUD_LAYOUT[SURF_HUD_SLOT_COUNT] = vec4[SURF_HUD_SLOT_COUNT](
            |$slots
            |);
            |
            |// Moves HUD glyphs (identified by their encoded y, see HudEncoding) from below the screen to their anchor.
            |// Text sent on the HUD bossbar row must have a total advance of 0, so the line starts at the GUI center.
            |vec3 surf_hud_transform(vec3 position) {
            |    // Only GUI rendering uses the orthographic projection that maps x = 0 to -1.
            |    if (ProjMat[3].x != -1.0) {
            |        return position;
            |    }
            |    float encoded = position.y - SURF_HUD_LINE_Y - SURF_HUD_BASE;
            |    if (encoded < 0.0) {
            |        return position;
            |    }
            |    int slot = int(floor(encoded / SURF_HUD_STRIDE));
            |    if (slot >= SURF_HUD_SLOT_COUNT) {
            |        return position;
            |    }
            |    vec2 gui = round(2.0 / vec2(ProjMat[0][0], -ProjMat[1][1]));
            |    vec4 placement = SURF_HUD_LAYOUT[slot];
            |    float localX = position.x - floor(gui.x * 0.5);
            |    float localY = encoded - float(slot) * SURF_HUD_STRIDE - SURF_HUD_BIAS;
            |    return vec3(placement.x * gui.x + placement.z + localX, placement.y * gui.y + placement.w + localY, position.z);
            |}
            |
            |#endif
            |""".trimMargin()
    }

    private fun writeTransparentBossbar(packDir: Path) {
        val empty = BufferedImage(182, 5, BufferedImage.TYPE_INT_ARGB)
        for (part in listOf("background", "progress")) {
            writeImage(packDir.resolve("assets/minecraft/textures/gui/sprites/boss_bar/${layout.bossbarColor}_$part.png"), empty)
        }
    }

    private fun write(file: Path, json: Any) {
        file.parent.createDirectories()
        file.writeText(JsonOutput.prettyPrint(JsonOutput.toJson(json)))
    }

    private companion object {
        val MIDDLE_WIDTHS = listOf(1, 2, 4, 8, 16, 32, 64, 128, 256)

        fun readImage(bytes: ByteArray): BufferedImage =
            ImageIO.read(ByteArrayInputStream(bytes)) ?: error("Not a readable image")

        fun writeImage(file: Path, image: BufferedImage) {
            file.parent.createDirectories()
            ImageIO.write(image, "png", file.toFile())
        }

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
