package dev.slne.surf.hud.buildlogic.hud

import dev.slne.surf.hud.buildlogic.minecraft.MojangMeta
import java.nio.file.Path

/**
 * The HUD layout read from a `hud-layout.json`: which glyph sets exist and where each HUD element is
 * pinned on screen. Every element becomes one shader slot and one font.
 */
data class HudLayout(
    val namespace: String,
    val description: String,
    val packFormat: Int,
    /** Bossbar color whose bar sprites are made transparent; HUD bossbars must use this color. */
    val bossbarColor: String,
    /** Zero-based bossbar row that carries the HUD text. */
    val bossbarRow: Int,
    val glyphSets: Map<String, GlyphSetSpec>,
    val elements: Map<String, ElementSpec>,
) {
    companion object {
        @Suppress("UNCHECKED_CAST")
        fun read(file: Path): HudLayout {
            val json = MojangMeta.parse(file)
            val bossbar = json["bossbar"] as Map<String, Any?>
            val glyphSets = (json["glyphSets"] as Map<String, Map<String, Any?>>).mapValues { (name, spec) ->
                when (val type = spec["type"]) {
                    "ascii" -> GlyphSetSpec.Ascii((spec["height"] as Number?)?.toInt())
                    "pixel" -> GlyphSetSpec.Pixel((spec["size"] as Number).toInt(), (spec["rows"] as Number?)?.toInt() ?: 8)
                    "images" -> GlyphSetSpec.Images(spec["images"] as Map<String, String>)
                    "strip" -> GlyphSetSpec.Strip(spec["texture"] as String)
                    else -> error("Glyph set '$name' has unknown type '$type' (expected ascii, pixel, images or strip)")
                }
            }
            val elements = (json["elements"] as Map<String, Map<String, Any?>>).mapValues { (name, spec) ->
                val offset = spec["offset"] as List<Number>
                val layers = (spec["layers"] as Map<String, Number>).mapValues { it.value.toInt() }
                layers.keys.forEach { require(it in glyphSets) { "Element '$name' uses unknown glyph set '$it'" } }
                ElementSpec(Anchor.parse(spec["anchor"] as String), offset[0].toInt(), offset[1].toInt(), layers)
            }
            return HudLayout(
                namespace = json["namespace"] as String,
                description = json["description"] as String? ?: "surf-hud",
                packFormat = (json["packFormat"] as Number).toInt(),
                bossbarColor = bossbar["color"] as String,
                bossbarRow = (bossbar["row"] as Number?)?.toInt() ?: 0,
                glyphSets = glyphSets,
                elements = elements,
            )
        }
    }
}

sealed interface GlyphSetSpec {
    /** Vanilla `minecraft:font/ascii.png` with the vanilla character grid, optionally drawn [height] px tall (default 8). */
    data class Ascii(val height: Int?) : GlyphSetSpec

    /** A [size] px square pixel for each of [rows] rows, tinted per glyph to draw images such as player heads. */
    data class Pixel(val size: Int, val rows: Int) : GlyphSetSpec

    /** One glyph per image, keyed by icon name; paths are relative to the source directory. */
    data class Images(val images: Map<String, String>) : GlyphSetSpec

    /**
     * A 3 px wide texture: left edge column, repeatable middle column, right edge column. It is cut into an
     * edge glyph on each side and middle glyphs of power-of-two widths so any width can be composed.
     */
    data class Strip(val texture: String) : GlyphSetSpec
}

/**
 * One HUD element: its [anchor] on screen, an [offsetX]/[offsetY] in GUI pixels from that anchor, and the
 * vertical position of each glyph set inside the element ([layers], GUI pixels from the element's top).
 */
data class ElementSpec(val anchor: Anchor, val offsetX: Int, val offsetY: Int, val layers: Map<String, Int>)

/** A screen anchor as a fraction of the GUI width and height. */
enum class Anchor(val x: Double, val y: Double) {
    TOP_LEFT(0.0, 0.0), TOP_CENTER(0.5, 0.0), TOP_RIGHT(1.0, 0.0),
    CENTER_LEFT(0.0, 0.5), CENTER(0.5, 0.5), CENTER_RIGHT(1.0, 0.5),
    BOTTOM_LEFT(0.0, 1.0), BOTTOM_CENTER(0.5, 1.0), BOTTOM_RIGHT(1.0, 1.0);

    companion object {
        fun parse(name: String): Anchor =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
                ?: error("Unknown anchor '$name' (expected one of ${entries.joinToString { it.name.lowercase() }})")
    }
}
