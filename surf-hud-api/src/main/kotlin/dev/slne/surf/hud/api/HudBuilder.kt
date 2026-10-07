package dev.slne.surf.hud.api

import net.kyori.adventure.text.Component

@DslMarker
annotation class HudDsl

@HudDsl
class HudBuilder {
    private val elements = LinkedHashMap<String, HudElement>()

    fun line(line: Int, block: HudLineBuilder.() -> Unit) {
        HudLineBuilder(line).apply(block).elements.forEach(::element)
    }

    fun element(element: HudElement) {
        elements[element.id] = element
    }

    fun build(): List<HudElement> = elements.values.toList()
}

@HudDsl
class HudLineBuilder(val line: Int) {
    internal val elements = mutableListOf<HudElement>()

    fun element(id: String, position: Int, content: Component, background: Boolean = true) {
        elements += HudElement(id, line, position, content, background)
    }

    fun center(id: String, content: Component, background: Boolean = true) =
        element(id, 0, content, background)
}

fun buildHud(block: HudBuilder.() -> Unit): List<HudElement> = HudBuilder().apply(block).build()
