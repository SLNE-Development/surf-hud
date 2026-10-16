package dev.slne.surf.hud.core.model

import dev.slne.surf.hud.core.config.HudSettings
import dev.slne.surf.hud.core.pack.VanillaAssets
import net.kyori.adventure.bossbar.BossBar
import net.kyori.adventure.key.Key

/** Where a shader slot is pinned on screen: the anchor plus an offset in GUI pixels. */
data class SlotSpec(val index: Int, val anchor: Anchor, val offsetX: Int, val offsetY: Int)

/** One generated font: the glyph sets drawn at local [y] in [slot]. Every font also carries the space glyphs. */
data class FontSpec(val key: Key, val slot: Int, val y: Int, val glyphSets: Set<GlyphSet>)

/** The compiled, immutable configuration: what the pack contains and what the renderer draws. */
class HudModel(
    val settings: HudSettings,
    val assets: VanillaAssets,
    val bossbarColor: BossBar.Color,
    val glyphSets: Map<String, GlyphSet>,
    val layouts: Map<String, LayoutSpec>,
    val huds: Map<String, HudSpec>,
    val popups: Map<String, PopupSpec>,
    val slots: List<SlotSpec>,
    val fonts: Map<Key, FontSpec>,
) {
    /** Font of [element] in [slot]: one per (slot, y); ascii sets get their own font so their characters cannot clash. */
    fun fontKey(slot: Int, element: ElementSpec): Key = fontKey(settings.namespace, slot, element)

    /** Any font of [slot]; used for cursor moves that are not attached to a glyph. */
    fun slotFont(slot: Int): Key = slotFonts.getValue(slot)

    private val slotFonts: Map<Int, Key> = fonts.values.groupBy { it.slot }.mapValues { (_, fonts) -> fonts.first().key }

    companion object {
        fun fontKey(namespace: String, slot: Int, element: ElementSpec): Key {
            val y = if (element.y < 0) "m${-element.y}" else "${element.y}"
            val suffix = if (element is TextSpec) "_${element.glyphSet.id}" else ""
            return Key.key(namespace, "hud/s${slot}_$y$suffix")
        }
    }
}
