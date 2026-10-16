package dev.slne.surf.hud.core.model

import dev.slne.surf.hud.core.expression.Expr
import dev.slne.surf.hud.core.expression.PlaceholderRef
import dev.slne.surf.hud.core.expression.TextTemplate
import net.kyori.adventure.text.format.TextColor

/** A screen anchor as a fraction of the GUI width and height. */
enum class Anchor(val x: Double, val y: Double) {
    TOP_LEFT(0.0, 0.0), TOP_CENTER(0.5, 0.0), TOP_RIGHT(1.0, 0.0),
    CENTER_LEFT(0.0, 0.5), CENTER(0.5, 0.5), CENTER_RIGHT(1.0, 0.5),
    BOTTOM_LEFT(0.0, 1.0), BOTTOM_CENTER(0.5, 1.0), BOTTOM_RIGHT(1.0, 1.0);

    companion object {
        fun parse(name: String): Anchor? = entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
    }
}

enum class HAlign {
    LEFT, CENTER, RIGHT;

    companion object {
        fun parse(name: String): HAlign? = entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
    }
}

/** Side a bar fills from: `RIGHT` grows to the right (fills from the left edge), `LEFT` the opposite. */
enum class FillDirection {
    RIGHT, LEFT;

    companion object {
        fun parse(name: String): FillDirection? = entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
    }
}

/** A compiled layout element; [index] is its position in the layout's element list. */
sealed interface ElementSpec {
    val index: Int
    val id: String?
    val x: Int
    val y: Int
    val conditions: List<Expr>
    val glyphSet: GlyphSet
}

data class TextSpec(
    override val index: Int, override val id: String?, override val x: Int, override val y: Int,
    override val conditions: List<Expr>, override val glyphSet: AsciiSet,
    val template: TextTemplate, val align: HAlign, val shadow: Boolean,
) : ElementSpec

data class ImageSpec(
    override val index: Int, override val id: String?, override val x: Int, override val y: Int,
    override val conditions: List<Expr>, override val glyphSet: ImageSet, val color: TextColor,
) : ElementSpec

data class StripSpec(
    override val index: Int, override val id: String?, override val x: Int, override val y: Int,
    override val conditions: List<Expr>, override val glyphSet: StripSet, val width: Expr, val color: TextColor,
) : ElementSpec

data class BarSpec(
    override val index: Int, override val id: String?, override val x: Int, override val y: Int,
    override val conditions: List<Expr>, override val glyphSet: StripSet,
    val width: Expr, val value: Expr, val direction: FillDirection, val color: TextColor,
) : ElementSpec

data class HeadSpec(
    override val index: Int, override val id: String?, override val x: Int, override val y: Int,
    override val conditions: List<Expr>, override val glyphSet: PixelSet, val player: Expr, val brightness: Expr,
) : ElementSpec

data class LayoutSpec(val id: String, val align: HAlign, val conditions: List<Expr>, val elements: List<ElementSpec>) {
    /** Every placeholder the layout reads, except `[width:…]`; a change in any of them re-renders the layout. */
    val placeholders: Set<PlaceholderRef> = buildSet {
        conditions.forEach { addAll(it.placeholders()) }
        elements.forEach { element ->
            element.conditions.forEach { addAll(it.placeholders()) }
            when (element) {
                is TextSpec -> addAll(element.template.placeholders())
                is ImageSpec -> {}
                is StripSpec -> addAll(element.width.placeholders())
                is BarSpec -> { addAll(element.width.placeholders()); addAll(element.value.placeholders()) }
                is HeadSpec -> { addAll(element.player.placeholders()); addAll(element.brightness.placeholders()) }
            }
        }
    }.filterTo(HashSet()) { it.name != "width" }
}

/** A layout placed in its own shader [slot]. */
data class Placement(val slot: Int, val layout: LayoutSpec, val conditions: List<Expr>)

data class HudSpec(val id: String, val default: Boolean, val conditions: List<Expr>, val placements: List<Placement>)

/** [stacks]`[i]` are the placements of the instance at stack index i (newest = 0). */
data class PopupSpec(val id: String, val durationTicks: Int, val unique: Boolean, val stacks: List<List<Placement>>) {
    val maxStack: Int get() = stacks.size
}
