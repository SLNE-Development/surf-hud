package dev.slne.surf.hud.core.pack

import dev.slne.surf.hud.core.model.*
import net.kyori.adventure.key.Key
import team.unnamed.creative.ResourcePack
import team.unnamed.creative.base.Writable
import team.unnamed.creative.font.Font
import team.unnamed.creative.font.FontProvider
import team.unnamed.creative.texture.Texture
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

/**
 * Builds the HUD resource pack for a [HudModel]: glyph textures, one font per [FontSpec], the patched
 * `core/text.vsh` with its generated include, and blank bossbar sprites for the HUD bossbar color.
 *
 * creative cannot express `min_format`/`max_format`, so the returned pack only carries a placeholder
 * `pack.mcmeta`; [PackWriter] replaces it with [packMeta].
 */
class PackGenerator(private val model: HudModel) {
    private val ns = model.settings.namespace

    fun build(): ResourcePack {
        val pack = ResourcePack.resourcePack()
        pack.packMeta(model.assets.packFormat, "surf-hud")
        model.glyphSets.values.forEach { textures(pack, it) }
        model.fonts.values.forEach { font -> pack.font(Font.font(font.key, providers(font))) }
        val patched = TextShaderPatcher.patch(model.assets.textVsh, model.assets.versionId, "$ns:hud.glsl", "surf_hud_transform")
        pack.unknownFile("assets/minecraft/shaders/core/text.vsh", Writable.stringUtf8(patched))
        pack.unknownFile("assets/$ns/shaders/include/hud.glsl", Writable.stringUtf8(ShaderInclude.generate(model)))
        val blank = png(BufferedImage(182, 5, BufferedImage.TYPE_INT_ARGB))
        val color = model.settings.bossbarColor
        for (part in listOf("background", "progress")) {
            pack.texture(Texture.texture(Key.key("minecraft", "gui/sprites/boss_bar/${color}_$part.png"), Writable.bytes(blank)))
        }
        return pack
    }

    /** The real `pack.mcmeta` content: formats from 65 on use min/max_format; older ones only understand pack_format. */
    fun packMeta(): String {
        val format = model.assets.packFormat
        val formats = if (format >= 65) "\"min_format\":$format,\"max_format\":$format" else "\"pack_format\":$format"
        return """{"pack":{"description":"surf-hud",$formats}}"""
    }

    private fun textures(pack: ResourcePack, set: GlyphSet) {
        when (set) {
            is AsciiSet -> {}
            is ImageSet -> pack.texture(Texture.texture(textureKey(set, set.glyph), Writable.bytes(png(set.glyph.texture))))
            is StripSet -> (listOf(set.left, set.right) + set.middles).forEach {
                pack.texture(Texture.texture(textureKey(set, it), Writable.bytes(png(it.texture))))
            }
            is PixelSet -> pack.texture(Texture.texture(pixelKey(set), Writable.bytes(png(set.texture))))
        }
    }

    private fun providers(font: FontSpec): List<FontProvider> = buildList {
        val ascent = { localY: Int -> HudEncoding.ascent(font.slot, localY) }
        for (set in font.glyphSets) {
            when (set) {
                is AsciiSet -> {
                    // The space provider must come first: ascii.png also has a (blank) cell for ' ', and the
                    // first provider that knows a character wins.
                    add(FontProvider.space(mapOf(" " to set.advances.getValue(' '))))
                    add(FontProvider.bitMap(Key.key("minecraft", "font/ascii.png"), set.height, ascent(font.y), set.rows))
                }
                is ImageSet -> add(bitmap(textureKey(set, set.glyph), set.glyph, ascent(font.y)))
                is StripSet -> (listOf(set.left, set.right) + set.middles).forEach { add(bitmap(textureKey(set, it), it, ascent(font.y))) }
                is PixelSet -> set.rows.forEachIndexed { row, char ->
                    add(FontProvider.bitMap(pixelKey(set), set.size, ascent(font.y + row * set.size), listOf(char.toString())))
                }
            }
        }
        add(FontProvider.space(SpaceGlyphs.advancesByChar))
    }

    private fun bitmap(file: Key, glyph: Glyph, ascent: Int) =
        FontProvider.bitMap(file, glyph.height, ascent, listOf(glyph.char.toString()))

    private fun textureKey(set: GlyphSet, glyph: Glyph): Key = when (set) {
        is StripSet -> Key.key(ns, "font/hud/${set.id}/${glyph.name}.png")
        else -> Key.key(ns, "font/hud/${set.id}.png")
    }

    private fun pixelKey(set: PixelSet) = Key.key(ns, "font/hud/${set.id}/pixel.png")

    private fun png(image: BufferedImage): ByteArray =
        ByteArrayOutputStream().also { ImageIO.write(image, "png", it) }.toByteArray()
}
