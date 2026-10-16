package dev.slne.surf.hud.core.config

import org.spongepowered.configurate.objectmapping.ConfigSerializable
import org.spongepowered.configurate.objectmapping.meta.Setting

// Raw YAML shapes. Every field is optional so files are written back without added defaults;
// the compiler applies defaults and reports missing or invalid values with their path.

/** `images/`: `single` icon, `strip` (3 px wide: left, middle, right column) or `pixel` set. */
@ConfigSerializable
data class ImageDefinition(
    val type: String? = null,
    val file: String? = null,
    val size: Int? = null,
    val rows: Int? = null,
)

/** `texts/`: an `ascii` font at [height] (default 8). */
@ConfigSerializable
data class TextDefinition(val type: String? = null, val height: Int? = null)

/** One layout element; which fields apply depends on [type] (`text`, `image`, `bar`, `head`). */
@ConfigSerializable
data class ElementDefinition(
    val type: String? = null,
    val id: String? = null,
    val x: Int? = null,
    val y: Int? = null,
    val conditions: List<String>? = null,
    val font: String? = null,
    val text: String? = null,
    val align: String? = null,
    val shadow: Boolean? = null,
    val image: String? = null,
    val width: String? = null,
    val color: String? = null,
    val value: String? = null,
    val direction: String? = null,
    val player: String? = null,
    val brightness: String? = null,
)

/** `layouts/`: a reusable group of elements. */
@ConfigSerializable
data class LayoutDefinition(
    val align: String? = null,
    val conditions: List<String>? = null,
    val elements: List<ElementDefinition>? = null,
)

/** A layout placed in a HUD or popup. In YAML either a map or just the layout id. */
data class PlacementDefinition(
    val layout: String,
    val anchor: String? = null,
    val x: Int? = null,
    val y: Int? = null,
    val conditions: List<String>? = null,
)

/** `huds/`: what a player sees. */
@ConfigSerializable
data class HudDefinition(
    val default: Boolean? = null,
    val conditions: List<String>? = null,
    val layouts: List<PlacementDefinition>? = null,
)

/** `popups/`: temporary layouts, optionally stacking. */
@ConfigSerializable
data class PopupDefinition(
    val anchor: String? = null,
    val x: Int? = null,
    val y: Int? = null,
    val duration: Int? = null,
    val unique: Boolean? = null,
    @Setting("max-stack") val maxStack: Int? = null,
    @Setting("stack-offset") val stackOffset: List<Int>? = null,
    val layouts: List<PlacementDefinition>? = null,
)

// One wrapper per folder: the entries are the top-level keys of the file.

/** File wrapper of `images/`. */
@ConfigSerializable
data class ImageFile(@Setting(nodeFromParent = true) val entries: Map<String, ImageDefinition> = emptyMap())

/** File wrapper of `texts/`. */
@ConfigSerializable
data class TextFile(@Setting(nodeFromParent = true) val entries: Map<String, TextDefinition> = emptyMap())

/** File wrapper of `layouts/`. */
@ConfigSerializable
data class LayoutFile(@Setting(nodeFromParent = true) val entries: Map<String, LayoutDefinition> = emptyMap())

/** File wrapper of `huds/`. */
@ConfigSerializable
data class HudFile(@Setting(nodeFromParent = true) val entries: Map<String, HudDefinition> = emptyMap())

/** File wrapper of `popups/`. */
@ConfigSerializable
data class PopupFile(@Setting(nodeFromParent = true) val entries: Map<String, PopupDefinition> = emptyMap())
