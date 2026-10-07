package dev.slne.surf.hud.core.layout

import dev.slne.surf.hud.api.HudElement
import dev.slne.surf.hud.api.theme.HudTheme
import dev.slne.surf.hud.core.layout.component.ComponentWidths
import net.kyori.adventure.text.Component

object HudLayout {
    fun compose(
        theme: HudTheme,
        elements: Collection<HudElement>,
        render: (Component) -> Component,
    ): Component {
        val lines = elements
            .groupBy { it.line }
            .toSortedMap()
            .values
            .take(theme.lines)

        return Component.text()
            .append(lines.mapIndexed { line, lineElements ->
                composeLine(
                    theme,
                    line,
                    lineElements,
                    render
                )
            })
            .build()
    }

    private fun composeLine(
        theme: HudTheme,
        line: Int,
        elements: List<HudElement>,
        render: (Component) -> Component,
    ): Component {
        val font = theme.lineFont(line)
        val sorted = elements.sortedBy { it.position }
        val parts = sorted.map { element ->
            val rendered = render(element.content)
            val contentWidth = ComponentWidths.width(rendered)

            HudComposer(theme, font).apply {
                if (element.background) {
                    pill(rendered, contentWidth)
                } else {
                    content(rendered, contentWidth)
                }
            }
        }

        val total = parts.sumOf { it.width } + theme.gap * (parts.size - 1)
        val firstAnchor = sorted.indexOfFirst { it.position == 0 }
        val offset = if (firstAnchor < 0) {
            total / 2
        } else {
            val lastAnchor = sorted.indexOfLast { it.position == 0 }
            val before = parts.take(firstAnchor).sumOf { it.width + theme.gap }
            val anchor = parts.subList(firstAnchor, lastAnchor + 1).sumOf { it.width } +
                    theme.gap * (lastAnchor - firstAnchor)
            before + anchor / 2
        }

        return HudComposer(theme, font).apply {
            space(-offset)
            parts.forEachIndexed { index, part ->
                if (index > 0) {
                    space(theme.gap)
                }
                append(part)
            }
            space(offset - total)
        }.build()
    }
}
