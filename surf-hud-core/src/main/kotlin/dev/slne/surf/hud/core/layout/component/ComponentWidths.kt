package dev.slne.surf.hud.core.layout.component

import dev.slne.surf.hud.core.layout.glyph.GlyphWidths
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.ObjectComponent
import net.kyori.adventure.text.flattener.ComponentFlattener

object ComponentWidths {
    private val flattener = ComponentFlattener.basic().toBuilder()
        .mapper(ObjectComponent::class.java) { GlyphWidths.OBJECT.toString() }
        .build()

    fun width(component: Component): Int {
        val listener = ComponentWidthListener()
        flattener.flatten(component, listener)
        return listener.width
    }
}
