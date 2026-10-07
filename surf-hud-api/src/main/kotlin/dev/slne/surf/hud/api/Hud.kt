package dev.slne.surf.hud.api

import net.kyori.adventure.text.Component
import java.util.*

interface Hud {
    val viewer: UUID
    var visible: Boolean
    val elements: List<HudElement>

    operator fun get(id: String): HudElement?

    fun add(element: HudElement)

    fun add(
        id: String,
        line: Int,
        position: Int,
        content: Component,
        background: Boolean = true,
    ) = add(HudElement(id, line, position, content, background))

    fun add(elements: Collection<HudElement>)

    fun add(block: HudBuilder.() -> Unit) = add(HudBuilder().apply(block).build())

    fun content(id: String, content: Component): Boolean

    fun remove(id: String): HudElement?

    fun remove(ids: Collection<String>)

    fun clear()

    fun refresh()
}
