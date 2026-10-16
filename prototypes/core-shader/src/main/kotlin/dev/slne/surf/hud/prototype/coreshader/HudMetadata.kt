package dev.slne.surf.hud.prototype.coreshader

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.nio.file.Files
import java.nio.file.Path

/** A single glyph of a HUD font: the character to send and how far it advances the cursor. */
data class HudGlyph(val char: String, val width: Int, val advance: Int)

/** A strip glyph set: edge glyphs and power-of-two middle pieces that compose any width. */
data class HudStrip(val left: HudGlyph, val right: HudGlyph, val middle: List<HudGlyph>)

/** The `hud-metadata.json` written by GenerateHudPack: fonts, glyph characters and advances of the pack. */
class HudMetadata(private val json: JsonObject) {
    val bossbarColor: String = json.getAsJsonObject("bossbar")["color"].asString
    val spaceFont: String = json.getAsJsonObject("space")["font"].asString

    /** Space glyphs by advance (±1, ±2, ±4 …). */
    val spaces: Map<Int, String> = json.getAsJsonObject("space").getAsJsonObject("chars")
        .entrySet().associate { (amount, char) -> amount.toInt() to char.asString }

    private val glyphSets = json.getAsJsonObject("glyphSets")
    private val elements = json.getAsJsonObject("elements")

    fun elementFont(element: String): String =
        elements.getAsJsonObject(element)?.get("font")?.asString ?: error("Unknown HUD element '$element'")

    fun textAdvance(set: String, char: Char): Int =
        glyphSets.getAsJsonObject(set).getAsJsonObject("advances")[char.toString()]?.asInt
            ?: error("Character '$char' is not in glyph set '$set'")

    fun image(set: String, name: String): HudGlyph =
        glyph(glyphSets.getAsJsonObject(set).getAsJsonObject("images").getAsJsonObject(name) ?: error("Unknown image '$name' in '$set'"))

    fun strip(set: String): HudStrip {
        val strip = glyphSets.getAsJsonObject(set)
        return HudStrip(
            glyph(strip.getAsJsonObject("left")),
            glyph(strip.getAsJsonObject("right")),
            strip.getAsJsonArray("middle").map { glyph(it.asJsonObject) }.sortedByDescending { it.width },
        )
    }

    private fun glyph(json: JsonObject) = HudGlyph(json["char"].asString, json["width"].asInt, json["advance"].asInt)

    companion object {
        fun read(path: Path) = HudMetadata(JsonParser.parseString(Files.readString(path)).asJsonObject)
    }
}
