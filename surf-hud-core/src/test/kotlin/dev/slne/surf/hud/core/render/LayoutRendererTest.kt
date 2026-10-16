package dev.slne.surf.hud.core.render

import dev.slne.surf.hud.core.TestDefinitions
import dev.slne.surf.hud.core.expression.PlaceholderRef
import dev.slne.surf.hud.core.expression.Value
import dev.slne.surf.hud.core.head.HeadPixels
import dev.slne.surf.hud.core.model.*
import dev.slne.surf.hud.core.placeholder.*
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextComponent
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.*
import kotlin.test.*

class LayoutRendererTest {
    @TempDir lateinit var root: Path

    private lateinit var model: HudModel
    private val values = HashMap<String, Any?>()
    private val warnings = WarningSink {}

    private fun scope(): RenderScope {
        val registry = PlaceholderRegistryImpl()
        listOf("money", "health", "max_health", "target").forEach { name -> registry.register(name) { _, _ -> values[name] } }
        val cache = PlaceholderCache(registry, HudClock { 0 }, emptyMap(), warnings, ResolverErrorLog(HudClock { 0 }) { _, _ -> })
        return RenderScope(UUID.randomUUID(), cache, emptyMap(), warnings)
    }

    /** Sums the advance of every character using the model's glyph sets and the space glyphs. */
    private fun advance(component: Component): Int {
        val advances = HashMap<Char, Int>()
        SpaceGlyphs.advancesByChar.forEach { (c, a) -> advances[c[0]] = a }
        model.glyphSets.values.forEach { set ->
            when (set) {
                is ImageSet -> advances[set.glyph.char] = set.glyph.advance
                is StripSet -> (listOf(set.left, set.right) + set.middles).forEach { advances[it.char] = it.advance }
                is PixelSet -> set.rows.forEach { advances[it] = set.advance }
                is AsciiSet -> {}
            }
        }
        val ascii = model.glyphSets.getValue("default") as AsciiSet
        fun walk(c: Component): Int = ((c as? TextComponent)?.content()?.sumOf { ch ->
            val bold = c.decoration(net.kyori.adventure.text.format.TextDecoration.BOLD) == net.kyori.adventure.text.format.TextDecoration.State.TRUE
            advances[ch] ?: (ascii.advance(ch)!! + if (bold) 1 else 0)
        } ?: 0) + c.children().sumOf(::walk)
        return walk(component)
    }

    private fun render(layoutId: String): Component {
        model = if (::model.isInitialized) model else TestDefinitions.compile(root)
        val placement = model.huds.values.flatMap { it.placements }.firstOrNull { it.layout.id == layoutId }
            ?: model.popups.values.flatMap { it.stacks.flatten() }.first { it.layout.id == layoutId }
        return LayoutRenderer(model, TextRenderer(warnings)).render(placement.slot, placement.layout, scope()) { _, _ -> HeadPixels.placeholder("x") }
    }

    @Test fun `every layout has zero total advance`() {
        values += mapOf("money" to 1500, "health" to 10, "max_health" to 20, "target" to "Notch")
        for (layout in listOf("money", "health", "head")) assertEquals(0, advance(render(layout)), layout)
    }

    @Test fun `panel width follows the text width`() {
        values["money"] = "AAA"
        val component = render("money")
        val strip = model.glyphSets.getValue("panel") as StripSet
        val text = (component.children().map { it as TextComponent }).joinToString("") { it.content() }
        // 3 * 6 px text + 14 = 32 px panel: left + 16 + 8 + 4 + 2 + right
        val middles = text.filter { c -> strip.middles.any { it.char == c } }.map { c -> strip.middles.first { it.char == c }.width }
        assertEquals(listOf(16, 8, 4, 2), middles)
    }

    @Test fun `bar fill follows the value`() {
        values += mapOf("health" to 15, "max_health" to 20)
        val rendered = render("health")
        val strip = model.glyphSets.getValue("panel") as StripSet
        val text = rendered.children().joinToString("") { (it as TextComponent).content() }
        val drawn = text.filter { c -> c == strip.left.char || c == strip.right.char || strip.middles.any { it.char == c } }
        val width = drawn.sumOf { c -> (listOf(strip.left, strip.right) + strip.middles).first { it.char == c }.width }
        assertEquals(45, width)
    }

    @Test fun `invalid widths draw nothing`() {
        values += mapOf("health" to "abc", "max_health" to 0)
        assertEquals(Component.empty(), render("health"))
        values += mapOf("health" to -5, "max_health" to 20)
        assertEquals(Component.empty(), render("health"))
    }

    @Test fun `heads draw one tinted glyph per opaque pixel`() {
        values["target"] = "Notch"
        val component = render("head")
        val pixels = model.glyphSets.getValue("head_small") as PixelSet
        val glyphs = component.children().sumOf { (it as TextComponent).content().count { c -> c in pixels.rows } }
        assertEquals(64, glyphs)
    }
}
