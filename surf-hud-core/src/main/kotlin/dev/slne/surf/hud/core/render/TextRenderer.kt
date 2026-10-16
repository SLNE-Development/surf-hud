package dev.slne.surf.hud.core.render

import dev.slne.surf.hud.core.expression.Scope
import dev.slne.surf.hud.core.expression.TextTemplate
import dev.slne.surf.hud.core.expression.Value
import dev.slne.surf.hud.core.model.AsciiSet
import dev.slne.surf.hud.core.placeholder.WarningSink
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.flattener.ComponentFlattener
import net.kyori.adventure.text.flattener.FlattenerListener
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.Style
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.*

/** A run of characters with one color and boldness, and its width in GUI pixels. */
data class TextRun(val text: String, val color: TextColor, val bold: Boolean, val width: Int)

/** The runs of a rendered text and their total width in GUI pixels. */
data class RenderedText(val runs: List<TextRun>, val width: Int)

/**
 * Renders a [TextTemplate]: placeholder and expression values become MiniMessage tags whose content is
 * inserted unparsed (components as components), the result is flattened into colored runs and measured with
 * the font's advances. Characters the font lacks become `?`; italic, underline, strikethrough and obfuscated
 * are ignored with a warning.
 */
class TextRenderer(private val warnings: WarningSink) {
    private val miniMessage = MiniMessage.miniMessage()

    fun render(template: TextTemplate, font: AsciiSet, scope: Scope): RenderedText {
        val source = StringBuilder()
        val resolvers = ArrayList<TagResolver>()
        fun tag(index: Int, value: Value) {
            val name = "hud_v$index"
            resolvers += if (value is Value.Comp) Placeholder.component(name, value.value) else Placeholder.unparsed(name, value.text())
            source.append('<').append(name).append('>')
        }
        template.segments.forEachIndexed { index, segment ->
            when (segment) {
                is TextTemplate.Segment.Literal -> source.append(segment.miniMessage)
                is TextTemplate.Segment.Placeholder -> tag(index, scope.resolve(segment.ref))
                is TextTemplate.Segment.Expression -> {
                    val value = segment.expression.evaluate(scope)
                    val number = value.number()
                    tag(index, if (segment.format != null && number != null) Value.Str(format(segment.format, number)) else value)
                }
            }
        }
        return layout(miniMessage.deserialize(source.toString(), TagResolver.resolver(resolvers)), font)
    }

    private fun layout(component: Component, font: AsciiSet): RenderedText {
        val runs = ArrayList<TextRun>()
        val styles = ArrayDeque<Style>().apply { addLast(Style.empty()) }
        val current = StringBuilder()
        var currentColor: TextColor = NamedTextColor.WHITE
        var currentBold = false
        var currentWidth = 0
        var total = 0

        fun flush() {
            if (current.isNotEmpty()) runs += TextRun(current.toString(), currentColor, currentBold, currentWidth)
            current.clear()
            currentWidth = 0
        }

        fun emit(text: String, style: Style) {
            val color = style.color() ?: NamedTextColor.WHITE
            val bold = style.decoration(TextDecoration.BOLD) == TextDecoration.State.TRUE
            for (decoration in UNSUPPORTED) {
                if (style.decoration(decoration) == TextDecoration.State.TRUE) {
                    warnings.once("decoration:$decoration", "HUD text does not support ${decoration.name.lowercase()}; ignoring it")
                }
            }
            var i = 0
            while (i < text.length) {
                val codePoint = text.codePointAt(i)
                i += Character.charCount(codePoint)
                val char = if (codePoint <= 0xFFFF && font.advance(codePoint.toChar()) != null) codePoint.toChar() else {
                    warnings.once("char:$codePoint", "Character U+%04X is not in HUD font '%s'; drawing '?'".format(codePoint, font.id))
                    '?'
                }
                // Bold widens glyphs by 1 px; spaces are emitted non-bold so their width is exact.
                val charBold = bold && char != ' '
                if (current.isNotEmpty() && (color != currentColor || charBold != currentBold)) flush()
                currentColor = color
                currentBold = charBold
                val advance = font.advance(char)!! + if (charBold) 1 else 0
                current.append(char)
                currentWidth += advance
                total += advance
            }
        }

        ComponentFlattener.basic().flatten(component, object : FlattenerListener {
            override fun pushStyle(style: Style) {
                styles.addLast(style.merge(styles.last(), Style.Merge.Strategy.IF_ABSENT_ON_TARGET))
            }

            override fun component(text: String) = emit(text, styles.last())

            override fun popStyle(style: Style) {
                styles.removeLast()
            }
        })
        flush()
        return RenderedText(runs, total)
    }

    private fun format(pattern: String, number: Double) = DecimalFormat(pattern, DecimalFormatSymbols(Locale.US)).format(number)

    private companion object {
        val UNSUPPORTED = listOf(TextDecoration.ITALIC, TextDecoration.UNDERLINED, TextDecoration.STRIKETHROUGH, TextDecoration.OBFUSCATED)
    }
}
