package dev.slne.surf.hud.core.render

import dev.slne.surf.hud.core.TestAssets
import dev.slne.surf.hud.core.config.definition.definitions.TextDefinition
import dev.slne.surf.hud.core.expression.PlaceholderRef
import dev.slne.surf.hud.core.expression.Scope
import dev.slne.surf.hud.core.expression.TextTemplate
import dev.slne.surf.hud.core.expression.Value
import dev.slne.surf.hud.core.model.CharAllocator
import dev.slne.surf.hud.core.model.GlyphSetBuilder
import dev.slne.surf.hud.core.placeholder.WarningSink
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import java.nio.file.Path
import kotlin.test.*

class TextRendererTest {
    private val font = GlyphSetBuilder(TestAssets.vanilla(), Path.of("."), CharAllocator()).text(
        "default",
        TextDefinition()
    )
    private val warnings = mutableListOf<String>()
    private val renderer = TextRenderer(WarningSink { warnings += it })

    private fun render(text: String, vararg values: Pair<String, Any?>): RenderedText {
        val map = values.associate { (k, v) -> PlaceholderRef(k) to Value.of(v) }
        return renderer.render(TextTemplate.parse(text), font, Scope { map[it] ?: Value.Missing })
    }

    @Test
    fun `measures and splits runs by color`() {
        val text = render("<gold>Ai</gold> A")
        assertEquals(listOf("Ai", " A"), text.runs.map { it.text })
        assertEquals(NamedTextColor.GOLD, text.runs[0].color)
        assertEquals(NamedTextColor.WHITE, text.runs[1].color)
        assertEquals(6 + 2 + 4 + 6, text.width)
    }

    @Test
    fun `bold adds one pixel except for spaces`() {
        val text = render("<bold>A A</bold>")
        assertEquals(7 + 4 + 7, text.width)
        assertEquals(listOf(true, false, true), text.runs.map { it.bold })
    }

    @Test
    fun `placeholder values are not parsed as MiniMessage`() {
        val text = render("[name]", "name" to "<red>x")
        assertEquals("<red>x", text.runs.joinToString("") { it.text })
        assertEquals(NamedTextColor.WHITE, text.runs.single().color)
    }

    @Test
    fun `component values keep their style`() {
        val text = render("[rank]", "rank" to Component.text("A", NamedTextColor.RED))
        assertEquals(NamedTextColor.RED, text.runs.single().color)
    }

    @Test
    fun `expressions are formatted`() {
        assertEquals("1,500", render("{[money] | #,##0}", "money" to 1500).runs.single().text)
        assertEquals("75", render("{[a] / [b] * 100 | 0}", "a" to 3, "b" to 4).runs.single().text)
        assertEquals("1.5", render("{[a]}", "a" to 1.5).runs.single().text)
    }

    @Test
    fun `characters outside the font become question marks`() {
        val text = render("[name]", "name" to "Jürgen 😀")
        assertEquals("J?rgen ?", text.runs.joinToString("") { it.text })
        assertTrue(warnings.isNotEmpty())
    }

    @Test
    fun `missing placeholders render as question mark`() {
        assertEquals("?", render("[nope]").runs.single().text)
    }

    @Test
    fun `unsupported decorations are ignored with a warning`() {
        render("<italic>A</italic>")
        assertTrue(warnings.any { "italic" in it })
    }
}
