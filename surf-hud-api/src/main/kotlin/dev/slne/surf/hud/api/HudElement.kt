package dev.slne.surf.hud.api

import net.kyori.adventure.text.Component

data class HudElement(
    val id: String,
    val line: Int,
    val position: Int,
    val content: Component,
    val background: Boolean = true,
) {
    init {
        require(id.isNotBlank()) { "HUD element id must not be blank" }
        require(line >= 0) { "HUD line must not be negative, was $line" }
    }
}
