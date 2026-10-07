package dev.slne.surf.hud.core.layout.component

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.flattener.ComponentFlattener

object ComponentWidths {
    private val flattener = ComponentFlattener.basic()

    fun width(component: Component): Int {
        val listener = ComponentWidthListener()
        flattener.flatten(component, listener)
        return listener.width
    }
}
