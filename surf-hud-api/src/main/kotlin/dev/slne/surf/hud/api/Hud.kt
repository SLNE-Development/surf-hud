package dev.slne.surf.hud.api

import net.kyori.adventure.text.Component
import java.util.*

interface Hud {
    val viewer: UUID
    var visible: Boolean
    val elements: List<HudElement>

    operator fun get(id: String): HudElement?

    fun set(element: HudElement)

    fun set(
        id: String,
        line: Int,
        position: Int,
        content: Component,
        background: Boolean = true,
    ) = set(HudElement(id, line, position, content, background))

    fun set(elements: Collection<HudElement>)

    fun set(block: HudBuilder.() -> Unit) = set(HudBuilder().apply(block).build())

    fun content(id: String, content: Component): Boolean

    fun remove(id: String): HudElement?

    fun clear()

    fun refresh()
}
